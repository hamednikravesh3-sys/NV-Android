package app.organicmaps;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import app.organicmaps.search.SearchPageViewModel;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.downloader.CountryItem;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.location.LocationState;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.widget.placepage.PlacePageViewModel;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runtime integration layer for NV on top of the Organic Maps engine.
 *
 * Responsibilities:
 * - hide the NV home overlay while native Search/Place/Routing UI is active;
 * - show real GPS accuracy from Organic Maps' LocationHelper;
 * - provide a visible Persian online search fallback;
 * - let the user put a marker on any point and generate a reversible NV code;
 * - generate a shareable QR (online map link) plus Code-128 barcode (NV code).
 */
public final class NvRuntimeController implements DefaultLifecycleObserver, LocationListener
{
  private static final int NAVY = Color.rgb(4, 18, 33);
  private static final int PANEL = Color.rgb(15, 23, 42);
  private static final int PANEL_2 = Color.rgb(30, 41, 59);
  private static final int CYAN = Color.rgb(40, 206, 255);
  private static final int BLUE = Color.rgb(45, 139, 255);
  private static final int GREEN = Color.rgb(42, 214, 113);
  private static final int AMBER = Color.rgb(255, 188, 54);
  private static final int RED = Color.rgb(255, 70, 89);
  // Route point picker colors: intentionally distinct from route blue and traffic green.
  private static final int ORIGIN_PICKER = Color.rgb(126, 87, 194);      // purple
  private static final int DESTINATION_PICKER = Color.rgb(255, 111, 0); // orange
  private static final int WHITE = Color.WHITE;
  private static final int MUTED = Color.rgb(205, 220, 231);
  private static final int OUTLINE = Color.rgb(71, 85, 105);
  private static final int SHEET_BG = Color.WHITE;
  private static final int SHEET_SOFT = Color.rgb(244, 247, 250);
  private static final int SHEET_TEXT = Color.rgb(20, 29, 43);
  private static final int SHEET_MUTED = Color.rgb(100, 116, 139);
  private static final int SHEET_BORDER = Color.rgb(226, 232, 240);

  private static final double TEHRAN_LAT = 35.6892;
  private static final double TEHRAN_LON = 51.3890;
  private static final long FRESH_LOCATION_MS = 30_000L;

  private static final Map<MwmActivity, NvRuntimeController> CONTROLLERS = new WeakHashMap<>();

  public interface MapPointSelectionListener
  {
    void onPointSelected(double lat, double lon, String address);
  }


  private final MwmActivity activity;
  private final ViewGroup host;
  private final int density;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final LocationHelper locationHelper;
  private final FrameLayout runtimeLayer;
  private final FrameLayout customLayer;
  private final TextView selectedCodeButton;

  private boolean nativeSearchActive;
  private boolean customMode;
  private boolean destroyed;
  private MapObject selectedObject;
  private Location lastLocation;

  private final Runnable statePoll = new Runnable() {
    @Override
    public void run()
    {
      if (destroyed)
        return;
      refreshLocationChip();
      updateBaseVisibility();
      mainHandler.postDelayed(this, 800L);
    }
  };

  private NvRuntimeController(MwmActivity activity, ViewGroup host)
  {
    this.activity = activity;
    this.host = host;
    density = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
    locationHelper = MwmApplication.from(activity).getLocationHelper();

    runtimeLayer = new FrameLayout(activity);
    runtimeLayer.setTag("nv-runtime-layer");
    runtimeLayer.setClipChildren(false);
    runtimeLayer.setClipToPadding(false);
    runtimeLayer.setClickable(false);
    host.addView(runtimeLayer, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    customLayer = new FrameLayout(activity);
    customLayer.setVisibility(View.GONE);
    customLayer.setClickable(false);
    runtimeLayer.addView(customLayer, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    selectedCodeButton = label("NV  کد / QR", 13, WHITE, Typeface.BOLD, Gravity.CENTER);
    selectedCodeButton.setBackground(round(PANEL, GREEN, 16));
    selectedCodeButton.setElevation(dp(10));
    selectedCodeButton.setVisibility(View.GONE);
    selectedCodeButton.setClickable(true);
    selectedCodeButton.setOnClickListener(v -> {
      if (selectedObject != null)
        generateAndShow(selectedObject.getTitle(), selectedObject.getLat(), selectedObject.getLon());
    });
    FrameLayout.LayoutParams codeLp = new FrameLayout.LayoutParams(dp(124), dp(48), Gravity.TOP | Gravity.RIGHT);
    codeLp.setMargins(0, dp(158), dp(14), 0);
    runtimeLayer.addView(selectedCodeButton, codeLp);
  }

  public static void install(MwmActivity activity)
  {
    if (CONTROLLERS.containsKey(activity))
      return;
    final ViewGroup host = activity.findViewById(android.R.id.content);
    if (host == null)
      return;

    final NvRuntimeController controller = new NvRuntimeController(activity, host);
    CONTROLLERS.put(activity, controller);
    controller.attach();
  }

  private static NvRuntimeController require(MwmActivity activity)
  {
    NvRuntimeController c = CONTROLLERS.get(activity);
    if (c == null)
    {
      install(activity);
      c = CONTROLLERS.get(activity);
    }
    return c;
  }

  public static void openSearch(MwmActivity activity, String query)
  {
    final NvRuntimeController c = require(activity);
    if (c != null)
      c.showNvSearch(query == null ? "" : query);
  }

  public static void showCodeMenu(MwmActivity activity)
  {
    final NvRuntimeController c = require(activity);
    if (c != null)
      c.showNvCodeMenu();
  }

  public static void startCodePicker(MwmActivity activity)
  {
    final NvRuntimeController c = require(activity);
    if (c != null)
      c.showPointPicker();
  }

  public static void selectPointOnMap(MwmActivity activity,
                                      String title,
                                      String subtitle,
                                      String confirmLabel,
                                      boolean originPoint,
                                      MapPointSelectionListener listener)
  {
    final NvRuntimeController c = require(activity);
    if (c != null)
      c.showRoutePointPicker(title, subtitle, confirmLabel, originPoint, listener);
  }

  public static void showLocationStatus(MwmActivity activity)
  {
    final NvRuntimeController c = require(activity);
    if (c != null)
      c.showLocationSheet();
  }

  private void attach()
  {
    activity.getLifecycle().addObserver(this);
    locationHelper.addListener(this);
    lastLocation = locationHelper.getSavedLocation();

    final SearchPageViewModel searchVm = new ViewModelProvider(activity).get(SearchPageViewModel.class);
    searchVm.getSearchEnabled().observe(activity, enabled -> {
      nativeSearchActive = Boolean.TRUE.equals(enabled);
      updateBaseVisibility();
    });

    final PlacePageViewModel placeVm = new ViewModelProvider(activity).get(PlacePageViewModel.class);
    placeVm.getMapObject().observe(activity, object -> {
      selectedObject = object;
      updateBaseVisibility();
    });

    mainHandler.postDelayed(this::ensureTehranMap, 1600L);
    mainHandler.post(statePoll);
  }

  @Override
  public void onDestroy(LifecycleOwner owner)
  {
    destroyed = true;
    mainHandler.removeCallbacksAndMessages(null);
    try
    {
      locationHelper.removeListener(this);
    }
    catch (Throwable ignored) {}
    io.shutdownNow();
    CONTROLLERS.remove(activity);
  }

  @Override
  public void onLocationUpdated(Location location)
  {
    lastLocation = location;
    refreshLocationChip();
  }

  @Override
  public void onLocationUpdateTimeout()
  {
    refreshLocationChip();
  }

  private void updateBaseVisibility()
  {
    final View base = host.findViewWithTag("nv-map-overlay");
    final boolean routingBusy = RoutingController.get().isPlanning() || RoutingController.get().isNavigating();
    final boolean placeBusy = selectedObject != null;
    final boolean showBase = !customMode && !nativeSearchActive && !routingBusy && !placeBusy;
    if (base != null)
      base.setVisibility(showBase ? View.VISIBLE : View.GONE);

    final boolean showSelected = !customMode && !nativeSearchActive && !routingBusy && placeBusy;
    selectedCodeButton.setVisibility(showSelected ? View.VISIBLE : View.GONE);
  }

  private void ensureTehranMap()
  {
    if (destroyed)
      return;
    try
    {
      final String id = MapManager.nativeFindCountry(TEHRAN_LAT, TEHRAN_LON);
      if (TextUtils.isEmpty(id))
        return;
      final int status = MapManager.nativeGetStatus(id);
      if (status == CountryItem.STATUS_DOWNLOADABLE || status == CountryItem.STATUS_PARTLY)
        MapManager.startDownload(id);
      else if (status == CountryItem.STATUS_FAILED)
        MapManager.retryDownload(id);
      else if (status == CountryItem.STATUS_UPDATABLE)
        MapManager.startUpdate(id);
    }
    catch (Throwable ignored) {}
  }

  private void refreshLocationChip()
  {
    final View v = host.findViewWithTag("nv-location-quick");
    if (!(v instanceof TextView chip))
      return;

    final Location loc = lastLocation != null ? lastLocation : locationHelper.getSavedLocation();
    final String text;
    final int accent;
    if (!hasFineLocation())
    {
      text = "⚠  دقیق خاموش";
      accent = RED;
    }
    else if (!isGpsEnabled())
    {
      text = "⚠  GPS خاموش";
      accent = AMBER;
    }
    else if (loc == null)
    {
      text = "◎  یافتن مکان";
      accent = AMBER;
    }
    else
    {
      final long age = Math.max(0L, System.currentTimeMillis() - loc.getTime());
      final int accuracy = loc.hasAccuracy() ? Math.max(1, Math.round(loc.getAccuracy())) : 9999;
      if (age > FRESH_LOCATION_MS)
      {
        text = "⚠  GPS قدیمی";
        accent = AMBER;
      }
      else if (accuracy <= 10)
      {
        text = "◎  ±" + accuracy + "م";
        accent = GREEN;
      }
      else if (accuracy <= 25)
      {
        text = "◎  ±" + accuracy + "م";
        accent = AMBER;
      }
      else
      {
        text = "⚠  ±" + accuracy + "م";
        accent = RED;
      }
    }
    chip.setText(text);
    chip.setBackground(round(Color.argb(245, 8, 39, 64), accent, 14));
  }

  private void showLocationSheet()
  {
    beginSheet(true);
    final LinearLayout panel = bottomPanel();
    panel.addView(header("موقعیت من", "دقت واقعی GPS نمایش داده می‌شود", this::closeCustom));

    final Location loc = locationHelper.getSavedLocation();
    final TextView status;
    if (!hasFineLocation())
      status = body("مجوز «موقعیت دقیق» اندروید فعال نیست. تا زمانی که Precise Location روشن نشود NV مکان را دقیق اعلام نمی‌کند.", RED);
    else if (!isGpsEnabled())
      status = body("مجوز دقیق فعال است ولی GPS دستگاه خاموش است.", AMBER);
    else if (loc == null)
      status = body("هنوز GPS Fix دریافت نشده است. چند ثانیه در فضای باز یا کنار پنجره منتظر بمانید.", AMBER);
    else
    {
      final long ageSec = Math.max(0L, (System.currentTimeMillis() - loc.getTime()) / 1000L);
      final String accuracy = loc.hasAccuracy() ? "±" + Math.round(loc.getAccuracy()) + " متر" : "نامشخص";
      status = body("دقت فعلی: " + accuracy + "\nسن موقعیت: " + ageSec + " ثانیه\n"
                    + String.format(Locale.US, "%.6f, %.6f", loc.getLatitude(), loc.getLongitude()),
                    loc.hasAccuracy() && loc.getAccuracy() <= 10 ? GREEN : AMBER);
    }
    panel.addView(status);

    if (!hasFineLocation())
    {
      panel.addView(primary("فعال کردن موقعیت دقیق", BLUE, this::requestPreciseLocation));
      panel.addView(primary("مجوزهای برنامه", PANEL_2, this::openAppSettings));
    }
    else if (!isGpsEnabled())
      panel.addView(primary("روشن کردن GPS", BLUE, this::openLocationSettings));
    else
      panel.addView(primary("دریافت GPS بهتر و مرکز کردن نقشه", GREEN, this::focusMyLocation));

    panel.addView(primary("بستن", PANEL_2, this::closeCustom));
    addBottomPanel(panel, dp(390));
  }

  private void requestPreciseLocation()
  {
    try
    {
      activity.requestPermissions(new String[] {
          Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
      }, 9042);
      Toast.makeText(activity, "در پنجره مجوز، گزینه «موقعیت دقیق» را روشن کنید", Toast.LENGTH_LONG).show();
    }
    catch (Throwable ignored)
    {
      openAppSettings();
    }
  }

  private void focusMyLocation()
  {
    closeCustom();
    if (!hasFineLocation())
    {
      showLocationSheet();
      return;
    }
    if (!isGpsEnabled())
    {
      openLocationSettings();
      return;
    }
    try
    {
      final int mode = LocationState.getMode();
      if (mode == LocationState.NOT_FOLLOW || mode == LocationState.NOT_FOLLOW_NO_POSITION)
        LocationState.nativeSwitchToNextMode();
      locationHelper.restartWithNewMode();
      Toast.makeText(activity, "در حال دریافت GPS… دقت واقعی روی دکمه نمایش داده می‌شود", Toast.LENGTH_LONG).show();
    }
    catch (Throwable e)
    {
      Toast.makeText(activity, "فعال‌سازی GPS ناموفق بود", Toast.LENGTH_SHORT).show();
    }
  }

  private void showNvSearch(String initialQuery)
  {
    beginFullScreen();

    final LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    root.setPadding(dp(14), dp(36), dp(14), dp(14));
    root.setBackgroundColor(Color.rgb(8, 20, 32));

    root.addView(header("جستجوی NV", "جستجوی آنلاین فارسی + جستجوی محلی نقشه + کد NV", this::closeCustom));

    final LinearLayout searchRow = new LinearLayout(activity);
    searchRow.setOrientation(LinearLayout.HORIZONTAL);
    searchRow.setGravity(Gravity.CENTER_VERTICAL);
    searchRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

    final EditText input = new EditText(activity);
    input.setSingleLine(true);
    input.setText(initialQuery);
    input.setHint("نام مکان، آدرس یا کد NV");
    input.setTextColor(WHITE);
    input.setHintTextColor(MUTED);
    input.setTextSize(16);
    input.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    input.setPadding(dp(14), 0, dp(14), 0);
    input.setBackground(round(PANEL_2, OUTLINE, 14));
    searchRow.addView(input, new LinearLayout.LayoutParams(0, dp(54), 1f));

    final TextView go = label("جستجو", 14, WHITE, Typeface.BOLD, Gravity.CENTER);
    go.setBackground(round(BLUE, BLUE, 14));
    go.setClickable(true);
    LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(82), dp(54));
    gp.setMargins(dp(8), 0, 0, 0);
    searchRow.addView(go, gp);
    root.addView(searchRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

    final TextView status = body("عبارت را بنویسید و «جستجو» را بزنید. نتیجه‌ها اینجا نمایش داده می‌شوند.", MUTED);
    root.addView(status);

    final ScrollView scroll = new ScrollView(activity);
    final LinearLayout results = new LinearLayout(activity);
    results.setOrientation(LinearLayout.VERTICAL);
    results.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    results.setPadding(0, dp(4), 0, dp(12));
    scroll.addView(results, new ScrollView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    final LinearLayout footer = new LinearLayout(activity);
    footer.setOrientation(LinearLayout.HORIZONTAL);
    footer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    footer.addView(smallAction("انتخاب نقطه روی نقشه", GREEN, this::showPointPicker), weighted());
    footer.addView(smallAction("جستجوی محلی نقشه", PANEL_2,
                               () -> openNativeSearch(input.getText().toString())), weighted());
    root.addView(footer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

    customLayer.addView(root, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    final Runnable submit = () -> {
      final String q = input.getText().toString().trim();
      if (q.isEmpty())
      {
        status.setText("یک نام، آدرس یا کد NV وارد کنید.");
        status.setTextColor(AMBER);
        return;
      }
      hideKeyboard(input);
      if (NvCodeCodec.isNvCode(q))
      {
        openNvCode(q);
        return;
      }
      runOnlineSearch(q, status, results);
    };
    go.setOnClickListener(v -> submit.run());
    input.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_SEARCH)
      {
        submit.run();
        return true;
      }
      return false;
    });

    input.requestFocus();
    if (!initialQuery.trim().isEmpty())
      mainHandler.postDelayed(submit, 150L);
    else
      mainHandler.postDelayed(() -> showKeyboard(input), 180L);
  }

  private void runOnlineSearch(String query, TextView status, LinearLayout results)
  {
    status.setText("در حال جستجوی آنلاین «" + query + "»…");
    status.setTextColor(CYAN);
    results.removeAllViews();

    io.submit(() -> {
      try
      {
        final String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        final URL url = new URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=15&addressdetails=1&namedetails=1&accept-language=fa&q=" + q);
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "NV-Android/0.24 (https://github.com/hamednikravesh3-sys/NV-Android)");
        final int code = conn.getResponseCode();
        if (code < 200 || code >= 300)
          throw new IllegalStateException("HTTP " + code);
        final String raw = readAll(conn.getInputStream());
        final JSONArray array = new JSONArray(raw);
        final List<SearchResult> found = new ArrayList<>();
        for (int i = 0; i < array.length(); i++)
        {
          final JSONObject o = array.getJSONObject(i);
          final double lat = Double.parseDouble(o.getString("lat"));
          final double lon = Double.parseDouble(o.getString("lon"));
          final String display = o.optString("display_name", "مکان");
          String title = o.optString("name", "").trim();
          if (title.isEmpty())
          {
            final JSONObject names = o.optJSONObject("namedetails");
            if (names != null)
              title = names.optString("name:fa", names.optString("name", "")).trim();
          }
          if (title.isEmpty())
          {
            final int comma = display.indexOf(',');
            title = comma > 0 ? display.substring(0, comma).trim() : display;
          }
          found.add(new SearchResult(title, display, lat, lon));
        }
        conn.disconnect();
        activity.runOnUiThread(() -> renderSearchResults(query, found, status, results));
      }
      catch (Throwable e)
      {
        activity.runOnUiThread(() -> {
          status.setText("جستجوی آنلاین پاسخ نداد. اینترنت را بررسی کنید یا «جستجوی محلی نقشه» را بزنید.");
          status.setTextColor(AMBER);
          results.removeAllViews();
          results.addView(actionCard("جستجوی محلی: " + query, () -> openNativeSearch(query)));
        });
      }
    });
  }

  private void renderSearchResults(String query, List<SearchResult> found, TextView status, LinearLayout results)
  {
    if (destroyed || !customMode)
      return;
    results.removeAllViews();
    if (found.isEmpty())
    {
      status.setText("نتیجه‌ای برای «" + query + "» پیدا نشد.");
      status.setTextColor(AMBER);
      results.addView(actionCard("جستجوی محلی نقشه", () -> openNativeSearch(query)));
      return;
    }
    status.setText(found.size() + " نتیجه پیدا شد");
    status.setTextColor(GREEN);
    for (SearchResult item : found)
      results.addView(searchResultCard(item));
  }

  private View searchResultCard(SearchResult item)
  {
    final LinearLayout card = new LinearLayout(activity);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setGravity(Gravity.RIGHT);
    card.setPadding(dp(14), dp(10), dp(14), dp(10));
    card.setBackground(round(PANEL, OUTLINE, 16));
    card.setClickable(true);
    card.setOnClickListener(v -> showSearchResult(item));

    final TextView title = label(item.title(), 15, WHITE, Typeface.BOLD, Gravity.RIGHT);
    card.addView(title);
    final TextView address = label(item.address(), 12, MUTED, Typeface.NORMAL, Gravity.RIGHT);
    address.setMaxLines(2);
    card.addView(address);

    final LinearLayout buttons = new LinearLayout(activity);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    buttons.addView(smallAction("نمایش", CYAN, () -> showSearchResult(item)), weighted());
    buttons.addView(smallAction("مسیر", BLUE, () -> routeTo(item.title(), item.address(), item.lat(), item.lon())), weighted());
    buttons.addView(smallAction("کد/QR", GREEN, () -> generateAndShow(item.title(), item.lat(), item.lon())), weighted());
    card.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cp.setMargins(0, dp(5), 0, dp(5));
    card.setLayoutParams(cp);
    return card;
  }

  private void showSearchResult(SearchResult item)
  {
    hideKeyboard(customLayer);
    try
    {
      Framework.nativeSetViewportCenter(item.lat(), item.lon(), 17);
    }
    catch (Throwable ignored) {}
    beginSheet(true);
    final LinearLayout panel = bottomPanel();
    panel.addView(header(item.title(), item.address(), this::closeCustom));
    panel.addView(primary("مسیر از مکان من", BLUE, () -> routeTo(item.title(), item.address(), item.lat(), item.lon())));
    panel.addView(primary("ساخت کد NV + QR + بارکد", GREEN,
                          () -> generateAndShow(item.title(), item.lat(), item.lon())));
    panel.addView(primary("باز کردن نقطه آنلاین", PANEL_2,
                          () -> openOnlineMap(item.lat(), item.lon(), NvCodeCodec.encode(item.lat(), item.lon()))));
    addBottomPanel(panel, dp(360));
  }

  private void openNativeSearch(String query)
  {
    closeCustom();
    nativeSearchActive = true;
    updateBaseVisibility();
    activity.showSearch(query == null ? "" : query.trim());
  }

  private void showNvCodeMenu()
  {
    beginSheet(true);
    final LinearLayout panel = bottomPanel();
    panel.addView(header("کد NV و QR", "هر نقطه نقشه یک کد پایدار و قابل اشتراک دارد", this::closeCustom));

    final EditText codeInput = new EditText(activity);
    codeInput.setSingleLine(true);
    codeInput.setHint("مثال: NV-XXXXX-XXXXX-XX");
    codeInput.setTextColor(WHITE);
    codeInput.setHintTextColor(MUTED);
    codeInput.setTextSize(15);
    codeInput.setGravity(Gravity.CENTER);
    codeInput.setBackground(round(PANEL_2, OUTLINE, 14));
    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
    ip.setMargins(dp(12), dp(8), dp(12), dp(8));
    panel.addView(codeInput, ip);

    panel.addView(primary("باز کردن کد NV", BLUE, () -> openNvCode(codeInput.getText().toString())));
    panel.addView(primary("انتخاب نقطه روی نقشه و ساخت کد", GREEN, this::showPointPicker));
    if (selectedObject != null)
      panel.addView(primary("ساخت کد برای مکان انتخاب‌شده", CYAN,
                            () -> generateAndShow(selectedObject.getTitle(), selectedObject.getLat(), selectedObject.getLon())));
    panel.addView(primary("بستن", PANEL_2, this::closeCustom));
    addBottomPanel(panel, dp(430));
  }

  private void openNvCode(String raw)
  {
    try
    {
      final NvCodeCodec.Point point = NvCodeCodec.decode(raw);
      final String code = NvCodeCodec.encode(point.latitude(), point.longitude());
      Framework.nativeSetViewportCenter(point.latitude(), point.longitude(), 17);
      String name = "مکان NV";
      try
      {
        final String address = Framework.nativeGetAddress(point.latitude(), point.longitude());
        if (!TextUtils.isEmpty(address))
          name = address;
      }
      catch (Throwable ignored) {}
      showDecodedCode(name, code, point.latitude(), point.longitude());
    }
    catch (Throwable e)
    {
      Toast.makeText(activity, "کد NV معتبر نیست", Toast.LENGTH_LONG).show();
    }
  }

  private void showDecodedCode(String name, String code, double lat, double lon)
  {
    beginSheet(true);
    final LinearLayout panel = bottomPanel();
    panel.addView(header("کد NV باز شد", name, this::closeCustom));
    panel.addView(body(code + "\n" + String.format(Locale.US, "%.6f, %.6f", lat, lon), CYAN));
    panel.addView(primary("مسیر تا این نقطه", BLUE, () -> routeTo(name, "", lat, lon)));
    panel.addView(primary("ساخت QR و بارکد قابل ارسال", GREEN, () -> generateAndShow(name, lat, lon)));
    panel.addView(primary("نمایش نقطه آنلاین", PANEL_2, () -> openOnlineMap(lat, lon, code)));
    addBottomPanel(panel, dp(360));
  }

  private void showPointPicker()
  {
    beginTransparent();

    final TextView marker = label("▼", 42, RED, Typeface.BOLD, Gravity.CENTER);
    marker.setShadowLayer(9f, 0f, 3f, Color.argb(150, 0, 0, 0));
    FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(64), dp(76), Gravity.CENTER);
    mp.setMargins(0, 0, 0, dp(34));
    customLayer.addView(marker, mp);

    final LinearLayout panel = bottomPanel();
    panel.addView(header("انتخاب نقطه", "نقشه را حرکت دهید تا نوک نشانگر دقیقاً روی مکان موردنظر باشد", this::closeCustom));
    panel.addView(primary("ساخت کد NV + QR + بارکد برای این نقطه", GREEN, () -> {
      try
      {
        final double[] center = Framework.nativeGetScreenRectCenter();
        if (center == null || center.length < 2)
          throw new IllegalStateException("No map center");
        String name = "مکان انتخاب‌شده";
        try
        {
          final String address = Framework.nativeGetAddress(center[0], center[1]);
          if (!TextUtils.isEmpty(address))
            name = address;
        }
        catch (Throwable ignored) {}
        generateAndShow(name, center[0], center[1]);
      }
      catch (Throwable e)
      {
        Toast.makeText(activity, "مختصات نقطه خوانده نشد", Toast.LENGTH_SHORT).show();
      }
    }));
    panel.addView(primary("مسیر تا این نقطه", BLUE, () -> {
      final double[] center = Framework.nativeGetScreenRectCenter();
      if (center != null && center.length >= 2)
        routeTo("مکان انتخاب‌شده", "", center[0], center[1]);
    }));
    panel.addView(primary("انصراف", PANEL_2, this::closeCustom));

    FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(300), Gravity.BOTTOM);
    pp.setMargins(dp(10), 0, dp(10), dp(18));
    panel.setClickable(true);
    customLayer.addView(panel, pp);
  }

  private void showRoutePointPicker(String title,
                                    String subtitle,
                                    String confirmLabel,
                                    boolean originPoint,
                                    MapPointSelectionListener listener)
  {
    beginTransparent();

    final LinearLayout markerBox = new LinearLayout(activity);
    markerBox.setOrientation(LinearLayout.VERTICAL);
    markerBox.setGravity(Gravity.CENTER);
    markerBox.setClickable(false);

    final int markerColor = originPoint ? ORIGIN_PICKER : DESTINATION_PICKER;

    final TextView bubble = label(originPoint ? "مبدأ" : "مقصد", 13, WHITE, Typeface.BOLD, Gravity.CENTER);
    bubble.setBackground(round(Color.argb(245, 15, 23, 42), markerColor, 18));
    bubble.setPadding(dp(13), dp(5), dp(13), dp(5));
    bubble.setElevation(dp(6));
    markerBox.addView(bubble, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

    final TextView marker = label(originPoint ? "●" : "⚑", originPoint ? 38 : 44, markerColor, Typeface.BOLD, Gravity.CENTER);
    marker.setShadowLayer(10f, 0f, 3f, Color.argb(180, 0, 0, 0));
    markerBox.addView(marker, new LinearLayout.LayoutParams(dp(58), dp(54)));

    final FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(120), dp(88), Gravity.CENTER);
    mp.setMargins(0, 0, 0, dp(26));
    customLayer.addView(markerBox, mp);

    final LinearLayout panel = bottomPanel();
    panel.addView(header(
        TextUtils.isEmpty(title) ? (originPoint ? "انتخاب مبدأ" : "انتخاب مقصد") : title,
        TextUtils.isEmpty(subtitle)
            ? "نقشه را حرکت دهید تا نشانگر دقیقاً روی مکان موردنظر قرار بگیرد"
            : subtitle,
        this::closeCustom));

    panel.addView(body(
        originPoint
            ? "نشانگر بنفش، مبدأ سفر خواهد بود."
            : "پرچم نارنجی، مقصد سفر خواهد بود.",
        SHEET_MUTED));

    panel.addView(primary(
        TextUtils.isEmpty(confirmLabel)
            ? (originPoint ? "تأیید مبدأ" : "تأیید مقصد")
            : confirmLabel,
        originPoint ? ORIGIN_PICKER : DESTINATION_PICKER,
        () -> {
          try
          {
            final double[] center = Framework.nativeGetScreenRectCenter();
            if (center == null || center.length < 2)
              throw new IllegalStateException("No map center");

            String address = "";
            try
            {
              final String found = Framework.nativeGetAddress(center[0], center[1]);
              if (!TextUtils.isEmpty(found))
                address = found.trim();
            }
            catch (Throwable ignored) {}

            final double lat = center[0];
            final double lon = center[1];
            final String selectedAddress = address;
            closeCustom();
            if (listener != null)
              listener.onPointSelected(lat, lon, selectedAddress);
          }
          catch (Throwable e)
          {
            Toast.makeText(activity, "مختصات این نقطه خوانده نشد", Toast.LENGTH_SHORT).show();
          }
        }));

    panel.addView(primary("انصراف", PANEL_2, this::closeCustom));

    final FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(242), Gravity.BOTTOM);
    pp.setMargins(dp(10), 0, dp(10), dp(18));
    panel.setClickable(true);
    customLayer.addView(panel, pp);
  }

  private void generateAndShow(String name, double lat, double lon)
  {
    final String cleanName = TextUtils.isEmpty(name) ? "مکان NV" : name.trim();
    final String code;
    try
    {
      code = NvCodeCodec.encode(lat, lon);
    }
    catch (Throwable e)
    {
      Toast.makeText(activity, "ساخت کد NV ناموفق بود", Toast.LENGTH_SHORT).show();
      return;
    }

    beginSheet(true);
    final LinearLayout panel = bottomPanel();
    panel.addView(header("در حال ساخت کد NV", cleanName, this::closeCustom));
    final TextView progress = body("در حال ساخت QR آنلاین و بارکد…", CYAN);
    panel.addView(progress);
    addBottomPanel(panel, dp(260));

    io.submit(() -> {
      try
      {
        final ShareAsset asset = createShareAsset(code, cleanName, lat, lon);
        activity.runOnUiThread(() -> showShareAsset(asset));
      }
      catch (Throwable e)
      {
        activity.runOnUiThread(() -> {
          progress.setText("ساخت تصویر کد ناموفق بود: " + e.getClass().getSimpleName());
          progress.setTextColor(RED);
        });
      }
    });
  }

  private ShareAsset createShareAsset(String code, String name, double lat, double lon) throws Exception
  {
    final String onlineUrl = onlineUrl(lat, lon, code);
    final MultiFormatWriter writer = new MultiFormatWriter();
    final BitMatrix qrMatrix = writer.encode(onlineUrl, BarcodeFormat.QR_CODE, 620, 620);
    final BitMatrix barcodeMatrix = writer.encode(code, BarcodeFormat.CODE_128, 780, 170);
    final Bitmap qr = matrixToBitmap(qrMatrix);
    final Bitmap barcode = matrixToBitmap(barcodeMatrix);

    final int width = 900;
    final int height = 1160;
    final Bitmap combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(combined);
    canvas.drawColor(Color.WHITE);

    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.rgb(7, 33, 55));
    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(48f);
    canvas.drawText("NV LOCATION", width / 2f, 70f, paint);

    paint.setTextSize(42f);
    canvas.drawText(code, width / 2f, 125f, paint);
    canvas.drawBitmap(qr, 140f, 150f, paint);

    paint.setTextSize(28f);
    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
    final String shortName = name.length() > 42 ? name.substring(0, 42) + "…" : name;
    canvas.drawText(shortName, width / 2f, 815f, paint);

    canvas.drawBitmap(barcode, 60f, 840f, paint);
    paint.setTextSize(26f);
    canvas.drawText(String.format(Locale.US, "%.6f, %.6f", lat, lon), width / 2f, 1040f, paint);
    paint.setTextSize(22f);
    canvas.drawText("QR: online map   |   Barcode: NV code", width / 2f, 1090f, paint);

    final File dir = new File(activity.getCacheDir(), "nv_share");
    if (!dir.exists() && !dir.mkdirs())
      throw new IllegalStateException("Cannot create share directory");
    final File file = new File(dir, code.replace('-', '_') + ".png");
    try (FileOutputStream out = new FileOutputStream(file))
    {
      if (!combined.compress(Bitmap.CompressFormat.PNG, 100, out))
        throw new IllegalStateException("PNG save failed");
    }
    return new ShareAsset(code, name, lat, lon, onlineUrl, file, combined);
  }

  private void showShareAsset(ShareAsset asset)
  {
    if (destroyed)
      return;
    beginSheet(true);
    final LinearLayout panel = bottomPanel();
    panel.addView(header("کد NV آماده است", asset.name(), this::closeCustom));

    final ImageView preview = new ImageView(activity);
    preview.setImageBitmap(asset.bitmap());
    preview.setAdjustViewBounds(true);
    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    LinearLayout.LayoutParams pv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250));
    pv.setMargins(dp(14), dp(6), dp(14), dp(6));
    panel.addView(preview, pv);

    panel.addView(body(asset.code() + "\nQR → نقشه آنلاین\nبارکد → کد NV", CYAN));

    final LinearLayout actions = new LinearLayout(activity);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    actions.addView(smallAction("ارسال", GREEN, () -> shareAsset(asset)), weighted());
    actions.addView(smallAction("کپی کد", BLUE, () -> copyCode(asset.code())), weighted());
    actions.addView(smallAction("باز کردن آنلاین", PANEL_2,
                                () -> openOnlineMap(asset.lat(), asset.lon(), asset.code())), weighted());
    panel.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
    panel.addView(primary("بستن", PANEL_2, this::closeCustom));
    addBottomPanel(panel, dp(590));
  }

  private void shareAsset(ShareAsset asset)
  {
    try
    {
      final Uri uri = FileProvider.getUriForFile(activity, BuildConfig.FILE_PROVIDER_AUTHORITY, asset.file());
      final String text = "کد NV: " + asset.code() + "\n" + asset.name() + "\n"
          + String.format(Locale.US, "%.6f, %.6f", asset.lat(), asset.lon()) + "\n" + asset.onlineUrl();
      final Intent intent = new Intent(Intent.ACTION_SEND);
      intent.setType("image/png");
      intent.putExtra(Intent.EXTRA_STREAM, uri);
      intent.putExtra(Intent.EXTRA_TEXT, text);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      activity.startActivity(Intent.createChooser(intent, "ارسال کد و QR مکان NV"));
    }
    catch (Throwable e)
    {
      Toast.makeText(activity, "اشتراک‌گذاری ناموفق بود", Toast.LENGTH_SHORT).show();
    }
  }

  private void copyCode(String code)
  {
    final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard != null)
    {
      clipboard.setPrimaryClip(ClipData.newPlainText("NV Code", code));
      Toast.makeText(activity, "کد NV کپی شد", Toast.LENGTH_SHORT).show();
    }
  }

  private void openOnlineMap(double lat, double lon, String code)
  {
    try
    {
      activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(onlineUrl(lat, lon, code))));
    }
    catch (Throwable e)
    {
      Toast.makeText(activity, "مرورگر در دسترس نیست", Toast.LENGTH_SHORT).show();
    }
  }

  private void routeTo(String title, String subtitle, double lat, double lon)
  {
    hideKeyboard(customLayer);
    final MapObject start = locationHelper.getMyPosition();
    if (start == null)
    {
      Toast.makeText(activity, "ابتدا موقعیت فعلی را با GPS پیدا کنید", Toast.LENGTH_LONG).show();
      showLocationSheet();
      return;
    }
    final Location loc = locationHelper.getSavedLocation();
    if (loc == null || !loc.hasAccuracy() || loc.getAccuracy() > 50f
        || System.currentTimeMillis() - loc.getTime() > 60_000L)
    {
      Toast.makeText(activity, "GPS فعلی برای شروع مسیر دقت کافی ندارد", Toast.LENGTH_LONG).show();
      showLocationSheet();
      return;
    }
    final MapObject end = MapObject.createMapObject(MapObject.SEARCH,
        TextUtils.isEmpty(title) ? "مقصد NV" : title,
        subtitle == null ? "" : subtitle, lat, lon);
    closeCustom();
    RoutingController.get().prepare(start, end);
    updateBaseVisibility();
  }

  private void beginFullScreen()
  {
    customMode = true;
    customLayer.removeAllViews();
    customLayer.setBackgroundColor(Color.rgb(8, 20, 32));
    customLayer.setVisibility(View.VISIBLE);
    customLayer.setClickable(true);
    updateBaseVisibility();
  }

  private void beginSheet(boolean dim)
  {
    customMode = true;
    customLayer.removeAllViews();
    customLayer.setBackgroundColor(dim ? Color.argb(145, 0, 0, 0) : Color.TRANSPARENT);
    customLayer.setVisibility(View.VISIBLE);
    customLayer.setClickable(dim);
    if (dim)
      customLayer.setOnClickListener(v -> closeCustom());
    else
      customLayer.setOnClickListener(null);
    updateBaseVisibility();
  }

  private void beginTransparent()
  {
    customMode = true;
    customLayer.removeAllViews();
    customLayer.setBackgroundColor(Color.TRANSPARENT);
    customLayer.setVisibility(View.VISIBLE);
    customLayer.setClickable(false);
    customLayer.setOnClickListener(null);
    updateBaseVisibility();
  }

  private void closeCustom()
  {
    hideKeyboard(customLayer);
    customLayer.removeAllViews();
    customLayer.setVisibility(View.GONE);
    customLayer.setClickable(false);
    customMode = false;
    updateBaseVisibility();
  }

  private LinearLayout bottomPanel()
  {
    final LinearLayout panel = new LinearLayout(activity);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    panel.setPadding(dp(14), dp(12), dp(14), dp(16));
    panel.setBackground(round(Color.argb(252, 15, 23, 42), OUTLINE, 28));
    panel.setElevation(dp(22));
    panel.setClickable(true);
    panel.setOnClickListener(v -> {});
    return panel;
  }

  private void addBottomPanel(LinearLayout panel, int height)
  {
    final FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
    pp.setMargins(dp(10), 0, dp(10), dp(18));
    customLayer.addView(panel, pp);
  }

  private View header(String title, String subtitle, Runnable close)
  {
    final LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    row.setPadding(dp(4), dp(4), dp(4), dp(8));

    final LinearLayout texts = new LinearLayout(activity);
    texts.setOrientation(LinearLayout.VERTICAL);
    final TextView t = label(title, 20, SHEET_TEXT, Typeface.BOLD, Gravity.RIGHT);
    final TextView s = label(subtitle == null ? "" : subtitle, 12, SHEET_MUTED, Typeface.NORMAL, Gravity.RIGHT);
    s.setMaxLines(2);
    texts.addView(t);
    texts.addView(s);
    row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    final TextView x = label("×", 28, SHEET_TEXT, Typeface.NORMAL, Gravity.CENTER);
    x.setBackground(round(SHEET_SOFT, SHEET_BORDER, 22));
    x.setClickable(true);
    x.setOnClickListener(v -> close.run());
    row.addView(x, new LinearLayout.LayoutParams(dp(46), dp(46)));
    return row;
  }

  private TextView body(String text, int color)
  {
    final TextView v = label(text, 14, color, Typeface.NORMAL, Gravity.RIGHT);
    v.setPadding(dp(12), dp(10), dp(12), dp(10));
    v.setLineSpacing(0f, 1.18f);
    return v;
  }

  private TextView primary(String text, int color, Runnable action)
  {
    final TextView v = label(text, 14, WHITE, Typeface.BOLD, Gravity.CENTER);
    v.setBackground(round(color, color == PANEL_2 ? OUTLINE : color, 18));
    v.setClickable(true);
    v.setOnClickListener(x -> action.run());
    v.setElevation(dp(3));
    final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
    p.setMargins(dp(8), dp(5), dp(8), dp(5));
    v.setLayoutParams(p);
    return v;
  }

  private TextView smallAction(String text, int color, Runnable action)
  {
    final TextView v = label(text, 12, WHITE, Typeface.BOLD, Gravity.CENTER);
    v.setBackground(round(color, color == PANEL_2 ? OUTLINE : color, 12));
    v.setClickable(true);
    v.setOnClickListener(x -> action.run());
    return v;
  }

  private LinearLayout.LayoutParams weighted()
  {
    final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    p.setMargins(dp(3), dp(3), dp(3), dp(3));
    return p;
  }

  private View actionCard(String text, Runnable action)
  {
    final TextView v = label(text, 14, SHEET_TEXT, Typeface.BOLD, Gravity.CENTER);
    v.setBackground(round(SHEET_SOFT, SHEET_BORDER, 16));
    v.setClickable(true);
    v.setOnClickListener(x -> action.run());
    final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
    p.setMargins(dp(5), dp(5), dp(5), dp(5));
    v.setLayoutParams(p);
    return v;
  }

  private TextView label(String text, int sp, int color, int style, int gravity)
  {
    final TextView v = new TextView(activity);
    v.setText(text);
    v.setTextSize(sp);
    v.setTextColor(color);
    v.setTypeface(Typeface.create(Typeface.DEFAULT, style));
    v.setGravity(gravity);
    v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    return v;
  }

  private GradientDrawable round(int fill, int stroke, int radiusDp)
  {
    final GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(dp(radiusDp));
    d.setStroke(dp(1), stroke);
    return d;
  }

  private int dp(int value)
  {
    return value * density;
  }

  private boolean hasFineLocation()
  {
    return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED;
  }

  private boolean isGpsEnabled()
  {
    try
    {
      final LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
      return lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
    catch (Throwable ignored)
    {
      return false;
    }
  }

  private void openAppSettings()
  {
    try
    {
      activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:" + activity.getPackageName())));
    }
    catch (Throwable ignored) {}
  }

  private void openLocationSettings()
  {
    try
    {
      activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }
    catch (Throwable ignored) {}
  }

  private void showKeyboard(View v)
  {
    final InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null)
      imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
  }

  private void hideKeyboard(View v)
  {
    final InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null && v != null)
      imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
  }

  private static String readAll(InputStream in) throws Exception
  {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
    {
      final StringBuilder out = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null)
      {
        if (out.length() > 2_000_000)
          throw new IllegalStateException("Search response too large");
        out.append(line);
      }
      return out.toString();
    }
  }

  private static Bitmap matrixToBitmap(BitMatrix matrix)
  {
    final int width = matrix.getWidth();
    final int height = matrix.getHeight();
    final int[] pixels = new int[width * height];
    for (int y = 0; y < height; y++)
    {
      final int offset = y * width;
      for (int x = 0; x < width; x++)
        pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
    }
    final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    return bitmap;
  }

  private static String onlineUrl(double lat, double lon, String code)
  {
    return String.format(Locale.US,
        "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f&nv=%s#map=18/%.6f/%.6f",
        lat, lon, Uri.encode(code), lat, lon);
  }

  private record SearchResult(String title, String address, double lat, double lon) {}

  private record ShareAsset(String code, String name, double lat, double lon,
                            String onlineUrl, File file, Bitmap bitmap) {}
}
