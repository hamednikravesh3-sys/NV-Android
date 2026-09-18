package app.organicmaps;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.routing.RoutingOptions;
import app.organicmaps.sdk.settings.RoadType;
import app.organicmaps.sdk.util.Distance;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * NV v0.31 functional layer.
 *
 * Goals:
 * - rank Persian search results semantically instead of taking the nearest text match;
 * - separate railway-station search from metro/transit routing;
 * - gracefully fall back when a mixed/metro trip is not actually possible;
 * - expose a live NV ETA chip that blends the route engine ETA with real device speed;
 * - give every advanced menu entry a distinct, observable action;
 * - keep claims honest: no fabricated live metro positions or fake traffic data.
 */
public final class NvV031Actions implements DefaultLifecycleObserver {
  private static final int BG = Color.rgb(8, 20, 32);
  private static final int PANEL = Color.rgb(7, 33, 55);
  private static final int PANEL2 = Color.rgb(10, 48, 78);
  private static final int OUTLINE = Color.rgb(45, 126, 171);
  private static final int WHITE = Color.WHITE;
  private static final int MUTED = Color.rgb(205, 220, 231);
  private static final int CYAN = Color.rgb(40, 206, 255);
  private static final int BLUE = Color.rgb(45, 139, 255);
  private static final int PURPLE = Color.rgb(153, 102, 255);
  private static final int GREEN = Color.rgb(42, 214, 113);
  private static final int AMBER = Color.rgb(255, 188, 54);
  private static final int RED = Color.rgb(255, 70, 89);
  private static final String SCREEN_TAG = "nv-v031-screen";
  private static final String ETA_TAG = "nv-v031-eta-chip";
  private static final String PREFS = "nv_v031";
  private static final long FRESH_ROUTE_MS = 75_000L;
  private static final float MAX_ROUTE_ACCURACY = 60f;
  private static final Map<MwmActivity, NvV031Actions> INSTANCES = new WeakHashMap<>();

  private final MwmActivity activity;
  private final ViewGroup host;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final LocationHelper locationHelper;
  private final TextView etaChip;
  private long lastSpeedAlertMs;
  private long lastGpsAlertMs;
  private boolean destroyed;
  private final ArrayDeque<Float> speedSamples = new ArrayDeque<>();
  private long lastSpeedSampleTime;
  private volatile int onlineEtaSec = -1;
  private volatile double onlineEtaDistanceM = -1d;
  private volatile long onlineEtaUpdatedAt;
  private volatile double onlineTargetLat = Double.NaN;
  private volatile double onlineTargetLon = Double.NaN;
  private volatile boolean onlineEtaLoading;
  private volatile double onlineOriginLat = Double.NaN;
  private volatile double onlineOriginLon = Double.NaN;
  private volatile long lastOnlineEtaAttemptAt;
  private static final long ONLINE_ETA_TTL_MS = 45_000L;
  private static final double ONLINE_ETA_REFRESH_MOVE_M = 750d;

  private final Runnable poll = new Runnable() {
    @Override public void run() {
      if (destroyed) return;
      updateEtaAndAlerts();
      handler.postDelayed(this, 1000L);
    }
  };

  private NvV031Actions(MwmActivity a) {
    activity = a;
    host = a.findViewById(android.R.id.content);
    locationHelper = MwmApplication.from(a).getLocationHelper();
    etaChip = text(a, "", 13, WHITE, Typeface.BOLD);
    etaChip.setTag(ETA_TAG);
    etaChip.setGravity(Gravity.CENTER);
    etaChip.setPadding(dp(a, 10), dp(a, 6), dp(a, 10), dp(a, 6));
    etaChip.setBackground(round(a, Color.argb(245, 7, 33, 55), CYAN, 14));
    etaChip.setElevation(dp(a, 12));
    etaChip.setVisibility(View.GONE);
    if (host != null) {
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(a, 250), dp(a, 44), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      lp.setMargins(0, dp(a, 150), 0, 0);
      host.addView(etaChip, lp);
    }
    a.getLifecycle().addObserver(this);
    handler.post(poll);
  }

  public static void install(MwmActivity a) {
    if (INSTANCES.containsKey(a)) return;
    INSTANCES.put(a, new NvV031Actions(a));
  }

  @Override public void onDestroy(LifecycleOwner owner) {
    destroyed = true;
    handler.removeCallbacksAndMessages(null);
    INSTANCES.remove(activity);
  }

  private void updateEtaAndAlerts() {
    boolean busy = RoutingController.get().isPlanning() || RoutingController.get().isNavigating();
    if (!busy) {
      etaChip.setVisibility(View.GONE);
      speedSamples.clear();
      onlineEtaSec = -1;
      return;
    }
    try {
      RoutingInfo info = Framework.nativeGetRouteFollowingInfo();
      if (info == null || info.totalTimeInSeconds <= 0) {
        etaChip.setVisibility(View.GONE);
        return;
      }

      Location loc = locationHelper.getSavedLocation();
      recordSpeedSample(loc);

      int engine = info.totalTimeInSeconds;
      double meters = distanceMeters(info.distToTarget);
      MapObject endPoint = RoutingController.get().getEndPoint();
      requestOnlineEtaIfNeeded(loc, endPoint);

      int nv = correctedActiveEta(engine, meters);
      String source = onlineEtaFresh() ? "NV جاده‌ای" : "NV آفلاین";
      etaChip.setText(source + "  " + formatMinutes(nv) + (meters > 0 ? "  •  " + formatDistance(meters) : "") + "  • بدون ترافیک زنده");
      etaChip.setVisibility(View.VISIBLE);

      // Keep the native route-plan card consistent with NV's corrected ETA.
      // This removes the confusing situation where the top NV chip says one thing
      // while the Organic Maps vehicle card shows a clearly unrealistic value.
      if (RoutingController.get().isVehicleRouterType()) {
        TextView nativeVehicleTime = activity.findViewById(R.id.time_vehicle);
        if (nativeVehicleTime != null && meters > 0) {
          nativeVehicleTime.setText(formatMinutes(nv) + "  •  " + formatDistance(meters));
        }
      }

      SharedPreferences p = prefs(activity);
      long now = System.currentTimeMillis();
      if (p.getBoolean("speed_alert", true) && loc != null && loc.hasSpeed() && info.speedLimitMps > 0
          && loc.getSpeed() > info.speedLimitMps + 2.8 && now - lastSpeedAlertMs > 30_000L) {
        lastSpeedAlertMs = now;
        Toast.makeText(activity, "NV: سرعت فعلی از محدودیت این بخش بیشتر است", Toast.LENGTH_LONG).show();
      }
      if (p.getBoolean("gps_alert", true) && loc != null && loc.hasAccuracy() && loc.getAccuracy() > 60f
          && now - lastGpsAlertMs > 45_000L) {
        lastGpsAlertMs = now;
        Toast.makeText(activity, "NV: دقت GPS پایین است؛ برای مسیریابی دقیق‌تر منتظر Fix بهتر بمانید", Toast.LENGTH_LONG).show();
      }
    } catch (Throwable ignored) {
      etaChip.setVisibility(View.GONE);
    }
  }

  private void recordSpeedSample(Location loc) {
    if (loc == null || !loc.hasSpeed() || loc.getSpeed() < 0f) return;
    if (loc.hasAccuracy() && loc.getAccuracy() > 60f) return;
    long t = loc.getTime();
    if (t <= lastSpeedSampleTime) return;
    lastSpeedSampleTime = t;
    speedSamples.addLast(loc.getSpeed());
    while (speedSamples.size() > 20) speedSamples.removeFirst();
  }

  private double medianSpeedMps() {
    if (speedSamples.size() < 5) return -1d;
    List<Float> values = new ArrayList<>(speedSamples);
    Collections.sort(values);
    int n = values.size();
    if ((n & 1) == 1) return values.get(n / 2);
    return (values.get(n / 2 - 1) + values.get(n / 2)) / 2d;
  }

  private boolean onlineEtaFresh() {
    return onlineEtaSec > 0 && System.currentTimeMillis() - onlineEtaUpdatedAt <= ONLINE_ETA_TTL_MS;
  }

  private void requestOnlineEtaIfNeeded(Location loc, MapObject endPoint) {
    if (loc == null || endPoint == null || !RoutingController.get().isVehicleRouterType()) return;
    if (loc.hasAccuracy() && loc.getAccuracy() > 100f) return;

    final long now = System.currentTimeMillis();
    final double sLat = loc.getLatitude(), sLon = loc.getLongitude();
    final double tLat = endPoint.getLat(), tLon = endPoint.getLon();

    final boolean targetChanged = !Double.isFinite(onlineTargetLat)
        || haversine(onlineTargetLat, onlineTargetLon, tLat, tLon) > 100d;
    final boolean originMoved = !Double.isFinite(onlineOriginLat)
        || haversine(onlineOriginLat, onlineOriginLon, sLat, sLon) >= ONLINE_ETA_REFRESH_MOVE_M;
    final boolean expired = !onlineEtaFresh();

    if (!targetChanged && !originMoved && !expired) return;
    if (onlineEtaLoading || now - lastOnlineEtaAttemptAt < 8_000L) return;

    lastOnlineEtaAttemptAt = now;
    onlineEtaLoading = true;
    new Thread(() -> {
      try {
        RoadEstimate estimate = osrm(sLat, sLon, tLat, tLon);
        if (estimate.durationSec > 0 && estimate.distanceM > 0) {
          onlineEtaSec = estimate.durationSec;
          onlineEtaDistanceM = estimate.distanceM;
          onlineEtaUpdatedAt = System.currentTimeMillis();
          onlineTargetLat = tLat;
          onlineTargetLon = tLon;
          onlineOriginLat = sLat;
          onlineOriginLon = sLon;
        }
      } catch (Throwable ignored) {
        // Keep the native route-engine estimate when the online road estimator is unavailable.
      } finally {
        onlineEtaLoading = false;
      }
    }, "nv-v031-online-eta").start();
  }

  private int correctedActiveEta(int engineSec, double remainingMeters) {
    int base = engineSec;

    if (onlineEtaFresh() && onlineEtaSec > 0) {
      double ratio = engineSec / (double) onlineEtaSec;
      if (ratio > 1.45d || ratio < 0.70d)
        base = onlineEtaSec;
      else
        base = (int)Math.round(onlineEtaSec * 0.72d + engineSec * 0.28d);
    }

    // Instantaneous speed is too noisy for long routes. Only use a rolling median
    // during the final urban portion of a trip, and keep its influence deliberately small.
    if (remainingMeters > 0d && remainingMeters <= 15_000d) {
      double median = medianSpeedMps();
      if (median >= 2.5d) {
        double speedEta = remainingMeters / median;
        speedEta = Math.max(base * 0.78d, Math.min(base * 1.25d, speedEta));
        base = (int)Math.round(base * 0.85d + speedEta * 0.15d);
      }
    }

    return Math.max(60, base);
  }

  private static double distanceMeters(Distance d) {
    if (d == null || !d.isValid()) return -1;
    switch (d.mUnits) {
      case Kilometers: return d.mDistance * 1000d;
      case Miles: return d.mDistance * 1609.344d;
      case Feet: return d.mDistance * 0.3048d;
      default: return d.mDistance;
    }
  }

  private static String formatMinutes(int seconds) {
    int m = Math.max(1, (int)Math.round(seconds / 60d));
    if (m < 60) return m + " دقیقه";
    int h = m / 60, r = m % 60;
    return r == 0 ? h + " ساعت" : h + "س " + r + "د";
  }

  public static void goHome(MwmActivity a) {
    removeScreen(a);
    Location loc = MwmApplication.from(a).getLocationHelper().getSavedLocation();
    if (loc != null) {
      try { Framework.nativeSetViewportCenter(loc.getLatitude(), loc.getLongitude(), 16); } catch (Throwable ignored) {}
    }
    Toast.makeText(a, "صفحه اصلی NV", Toast.LENGTH_SHORT).show();
  }

  public static int getSearchRadius(MwmActivity a, int fallback) {
    return prefs(a).getInt("radius_m", fallback);
  }

  public static void openSmartSearch(MwmActivity a) { openSearch(a, false); }
  public static void openPlaceDetails(MwmActivity a) { openSearch(a, true); }

  private static void openSearch(MwmActivity a, boolean detailMode) {
    Screen s = screen(a, detailMode ? "جزئیات مکان" : "جستجوی هوشمند NV",
        detailMode ? "مکان را جستجو کنید؛ نتیجه بر اساس نام، نوع مکان، شهر و فاصله رتبه‌بندی می‌شود"
                   : "فارسی طبیعی، نام مکان، آدرس، راه‌آهن، مترو و عبارت سفر را می‌فهمد");
    EditText input = input(a, detailMode ? "مثال: ایستگاه راه‌آهن یزد" : "مثال: می‌خوام برم میدان تجریش، عجله دارم");
    s.controls.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 64)));
    Runnable go = () -> {
      String q = input.getText().toString().trim();
      if (q.isEmpty()) { setStatus(s, "عبارت جستجو را بنویسید.", AMBER); return; }
      if (!detailMode && looksLikeTrip(q)) {
        performTrip(a, s, q, Mode.AUTO);
      } else {
        smartSearch(a, s, q, detailMode);
      }
    };
    s.controls.addView(button(a, detailMode ? "پیدا کردن و نمایش جزئیات" : "جستجوی هوشمند", GREEN, go));
    s.controls.addView(button(a, "جستجوی محلی نقشه", BLUE, () -> {
      removeScreen(a); NvRuntimeController.openSearch(a, input.getText().toString());
    }));
    input.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) { go.run(); return true; }
      return false;
    });
    input.requestFocus();
  }

  private static void smartSearch(MwmActivity a, Screen s, String query, boolean detailMode) {
    setStatus(s, "در حال جستجو و رتبه‌بندی «" + query + "»…", CYAN);
    s.results.removeAllViews();
    Location origin = MwmApplication.from(a).getLocationHelper().getSavedLocation();
    new Thread(() -> {
      try {
        List<Place> found = geocodeRanked(query, origin);
        a.runOnUiThread(() -> {
          if (!alive(a, s)) return;
          s.results.removeAllViews();
          if (found.isEmpty()) {
            setStatus(s, "نتیجه مطمئنی پیدا نشد.", AMBER);
            s.results.addView(button(a, "جستجوی محلی نقشه", BLUE,
                () -> { removeScreen(a); NvRuntimeController.openSearch(a, query); }));
            return;
          }
          setStatus(s, found.size() + " نتیجه رتبه‌بندی شد — بهترین تطابق اول است", GREEN);
          int n = 1;
          for (Place p : found) {
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(a, 12), dp(a, 9), dp(a, 12), dp(a, 9));
            card.setBackground(round(a, PANEL, OUTLINE, 14));
            String dist = p.distanceMeters >= 0 ? " • " + formatDistance(p.distanceMeters) : "";
            card.addView(text(a, n + ". " + p.title + dist, 15, WHITE, Typeface.BOLD));
            card.addView(text(a, p.address, 12, MUTED, Typeface.NORMAL));
            if (!TextUtils.isEmpty(p.category) || !TextUtils.isEmpty(p.type))
              card.addView(text(a, "نوع: " + p.category + "/" + p.type, 11, CYAN, Typeface.NORMAL));
            LinearLayout row = new LinearLayout(a); row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.addView(smallButton(a, detailMode ? "نمایش" : "مسیر", BLUE, () -> {
              if (detailMode) showPlace(a, p); else routeTo(a, p, Router.Vehicle);
            }), weight(a));
            row.addView(smallButton(a, "پیاده", CYAN, () -> routeTo(a, p, Router.Pedestrian)), weight(a));
            row.addView(smallButton(a, "NV/QR", GREEN, () -> {
              removeScreen(a); Framework.nativeSetViewportCenter(p.lat, p.lon, 17); NvRuntimeController.showCodeMenu(a);
            }), weight(a));
            card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44)));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, dp(a, 5), 0, dp(a, 5)); card.setLayoutParams(cp); s.results.addView(card); n++;
          }
        });
      } catch (Throwable e) {
        a.runOnUiThread(() -> {
          if (!alive(a, s)) return;
          setStatus(s, "جستجوی آنلاین پاسخ نداد؛ جستجوی محلی نقشه را امتحان کنید.", AMBER);
          s.results.removeAllViews();
          s.results.addView(button(a, "جستجوی محلی نقشه", BLUE,
              () -> { removeScreen(a); NvRuntimeController.openSearch(a, query); }));
        });
      }
    }, "nv-v031-search").start();
  }

  private static void showPlace(MwmActivity a, Place p) {
    Screen s = screen(a, p.title, "جزئیات مکان انتخاب‌شده");
    s.results.addView(text(a, p.address, 14, WHITE, Typeface.NORMAL));
    s.results.addView(text(a, String.format(Locale.US, "مختصات: %.6f, %.6f", p.lat, p.lon), 13, CYAN, Typeface.NORMAL));
    s.results.addView(button(a, "مسیر خودرو", BLUE, () -> routeTo(a, p, Router.Vehicle)));
    s.results.addView(button(a, "مسیر پیاده", CYAN, () -> routeTo(a, p, Router.Pedestrian)));
    s.results.addView(button(a, "نمایش روی نقشه", GREEN, () -> { removeScreen(a); Framework.nativeSetViewportCenter(p.lat, p.lon, 17); }));
  }

  public static void openRouteMode(MwmActivity a) { openTripInput(a, Mode.AUTO, "حالت مسیریابی", "نوع مسیر از جمله شما تشخیص داده می‌شود"); }
  public static void openChat(MwmActivity a) { openTripInput(a, Mode.CHAT, "چت هوشمند سفر", "طبیعی بنویسید؛ مقصد، عجله و نوع جابه‌جایی استخراج می‌شود"); }
  public static void openHurry(MwmActivity a) { openTripInput(a, Mode.HURRY, "حالت عجله دارم", "سریع‌ترین گزینه عملی بررسی می‌شود و ETA پیش از شروع نمایش داده می‌شود"); }
  public static void openMixed(MwmActivity a) { openTripInput(a, Mode.MIXED, "مسیر ترکیبی", "NV ابتدا وجود مترو نزدیک مبدا و مقصد را بررسی می‌کند؛ در نبود مترو خطای ساختگی نشان نمی‌دهد"); }
  public static void openEta(MwmActivity a) { openTripInput(a, Mode.ETA, "اطمینان زمان رسیدن", "برآورد موتور مسیر با فاصله و سرعت واقعی دستگاه تطبیق داده می‌شود"); }
  public static void openTimeCost(MwmActivity a) { openTripInput(a, Mode.COMPARE, "مقایسه زمان و مصرف", "زمان خودرو و پیاده و مصرف تقریبی سوخت را قبل از شروع مقایسه کنید"); }
  public static void openWalk(MwmActivity a) { openTripInput(a, Mode.WALK, "راهنمای پیاده", "مسیر پیاده مستقل محاسبه می‌شود"); }
  public static void openCompareRoutes(MwmActivity a) { openTripInput(a, Mode.COMPARE, "مقایسه مسیرها", "خودرو، پیاده و امکان مترو جداگانه بررسی می‌شوند"); }

  private static void openTripInput(MwmActivity a, Mode mode, String title, String subtitle) {
    Screen s = screen(a, title, subtitle);
    EditText input = input(a, "مثال: می‌خوام برم میدان تجریش، عجله دارم");
    s.controls.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 72)));
    Runnable go = () -> {
      String q = input.getText().toString().trim();
      if (q.isEmpty()) { setStatus(s, "مقصد را بنویسید.", AMBER); return; }
      performTrip(a, s, q, mode);
    };
    s.controls.addView(button(a, mode == Mode.HURRY ? "پیدا کردن سریع‌ترین مسیر" : "محاسبه", mode == Mode.HURRY ? RED : GREEN, go));
    input.setOnEditorActionListener((v, actionId, event) -> { if (actionId == EditorInfo.IME_ACTION_GO) { go.run(); return true; } return false; });
    input.requestFocus();
  }

  private static void performTrip(MwmActivity a, Screen s, String raw, Mode mode) {
    String destination = extractDestination(raw);
    if (destination.isEmpty()) { setStatus(s, "نام مقصد از جمله مشخص نشد.", AMBER); return; }
    Location origin = MwmApplication.from(a).getLocationHelper().getSavedLocation();
    if (!planningLocationOkay(origin)) {
      setStatus(s, "برای محاسبه مسیر، موقعیت تازه با خطای حداکثر ۱۲۰ متر لازم است.", RED);
      s.results.removeAllViews();
      s.results.addView(button(a, "دریافت GPS بهتر", GREEN, () -> NvRuntimeController.showLocationStatus(a)));
      return;
    }
    if (!freshEnough(origin)) {
      setStatus(s, "هشدار: دقت GPS فعلی " + Math.round(origin.getAccuracy()) + " متر است؛ محاسبه ممکن است از خیابان مجاور شروع شود.", AMBER);
    }

    setStatus(s, "در حال تشخیص مقصد «" + destination + "»…", CYAN);
    s.results.removeAllViews();
    final String dest = destination;

    new Thread(() -> {
      try {
        List<Place> list = geocodeRanked(dest, origin);
        if (list.isEmpty()) throw new IllegalStateException("no destination");

        if (!destinationConfident(list, dest)) {
          a.runOnUiThread(() -> {
            if (!alive(a, s)) return;
            renderDestinationChoices(a, s, raw, mode, origin, list);
          });
          return;
        }

        Place best = list.get(0);
        a.runOnUiThread(() -> {
          if (!alive(a, s)) return;
          dispatchResolvedTrip(a, s, raw, mode, origin, best);
        });
      } catch (Throwable e) {
        a.runOnUiThread(() -> {
          if (!alive(a, s)) return;
          setStatus(s, "مقصد با اطمینان کافی پیدا نشد.", AMBER);
          s.results.addView(button(a, "جستجوی هوشمند مقصد", BLUE, () -> openSmartSearch(a)));
        });
      }
    }, "nv-v031-trip").start();
  }

  private static boolean destinationConfident(List<Place> list, String query) {
    if (list == null || list.isEmpty()) return false;
    Place first = list.get(0);
    String q = normalize(query), title = normalize(first.title);
    if (title.equals(q) || (q.length() >= 4 && title.contains(q))) return true;
    if (first.score < 70) return false;
    if (list.size() == 1) return true;
    Place second = list.get(1);
    return first.score - second.score >= 24;
  }

  private static void renderDestinationChoices(MwmActivity a, Screen s, String raw, Mode mode,
                                               Location origin, List<Place> list) {
    s.results.removeAllViews();
    setStatus(s, "چند مقصد مشابه پیدا شد؛ برای جلوگیری از مسیر اشتباه، مقصد را انتخاب کنید.", AMBER);
    int count = Math.min(5, list.size());
    for (int i = 0; i < count; i++) {
      Place p = list.get(i);
      String dist = p.distanceMeters >= 0 ? " • " + formatDistance(p.distanceMeters) : "";
      LinearLayout card = new LinearLayout(a);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(dp(a, 12), dp(a, 9), dp(a, 12), dp(a, 9));
      card.setBackground(round(a, PANEL, OUTLINE, 14));
      card.addView(text(a, (i + 1) + ". " + p.title + dist, 15, WHITE, Typeface.BOLD));
      card.addView(text(a, p.address, 11, MUTED, Typeface.NORMAL));
      card.addView(button(a, "انتخاب این مقصد", BLUE, () -> dispatchResolvedTrip(a, s, raw, mode, origin, p)));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.setMargins(0, dp(a, 5), 0, dp(a, 5));
      s.results.addView(card, lp);
    }
  }

  private static void dispatchResolvedTrip(MwmActivity a, Screen s, String raw, Mode mode,
                                           Location origin, Place best) {
    s.results.removeAllViews();

    if (mode == Mode.WALK || containsAny(raw, "پیاده", "قدم")) {
      setStatus(s, "مقصد: " + best.title + " • مسیر پیاده", GREEN);
      routeTo(a, best, Router.Pedestrian);
      return;
    }

    boolean explicitMixed = mode == Mode.MIXED
        || containsAny(raw, "ترکیبی", "مترو", "حمل و نقل عمومی", "حمل‌ونقل عمومی", "اتوبوس");
    boolean railDestination = containsAny(best.title + " " + best.address,
        "راه آهن", "راه‌آهن", "ایستگاه قطار", "راه اهن");

    if (mode == Mode.HURRY || containsAny(raw, "عجله", "سریع", "زود", "فوری")) {
      compareHurryOptions(a, s, origin, best);
      return;
    }

    if (explicitMixed && !railDestination) {
      buildMixedPlan(a, s, origin, best);
      return;
    }

    if (mode == Mode.COMPARE) {
      compareModes(a, s, origin, best);
      return;
    }

    previewVehicle(a, s, origin, best, false);
  }

  private static void compareHurryOptions(MwmActivity a, Screen s, Location origin, Place dest) {
    setStatus(s, "در حال مقایسه همزمان گزینه‌های قابل استفاده…", CYAN);
    new Thread(() -> {
      RoadEstimate car = null;
      MixedEstimate mixed = null;
      try { car = osrm(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon); } catch (Throwable ignored) {}
      if (prefs(a).getBoolean("use_metro", true)) {
        try { mixed = estimateMixed(a, origin, dest); } catch (Throwable ignored) {}
      }

      double direct = haversine(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon);
      int walkSec = direct <= 8_000d ? walkingSeconds(direct) : Integer.MAX_VALUE;
      RoadEstimate finalCar = car;
      MixedEstimate finalMixed = mixed;

      a.runOnUiThread(() -> {
        if (!alive(a, s)) return;
        s.results.removeAllViews();

        int best = Integer.MAX_VALUE;
        String bestName = "";
        if (finalCar != null && finalCar.durationSec < best) { best = finalCar.durationSec; bestName = "خودرو"; }
        if (finalMixed != null && finalMixed.totalSec < best) { best = finalMixed.totalSec; bestName = "ترکیبی"; }
        if (walkSec < best) { best = walkSec; bestName = "پیاده"; }

        if (best == Integer.MAX_VALUE) {
          setStatus(s, "هیچ برآورد قابل اعتمادی آماده نشد؛ مسیر آفلاین نقشه را امتحان کنید.", AMBER);
          s.results.addView(button(a, "مسیر خودرو با موتور آفلاین", BLUE, () -> routeTo(a, dest, Router.Vehicle)));
          return;
        }

        setStatus(s, "سریع‌ترین برآورد فعلی: " + bestName + " • " + formatMinutes(best), GREEN);

        if (finalCar != null) {
          s.results.addView(optionCard(a,
              "🚗 خودرو" + ("خودرو".equals(bestName) ? "  ✓ سریع‌ترین" : ""),
              formatMinutes(finalCar.durationSec) + " • " + formatDistance(finalCar.distanceM),
              BLUE,
              () -> routeTo(a, dest, Router.Vehicle)));
        }

        if (finalMixed != null) {
          String details = formatMinutes(finalMixed.totalSec) + " • "
              + finalMixed.fromStation.title + " → " + finalMixed.toStation.title
              + " • حدود " + formatToman(estimateMixedCostToman(a, finalMixed));
          s.results.addView(optionCard(a,
              "🚇 ترکیبی" + ("ترکیبی".equals(bestName) ? "  ✓ سریع‌ترین" : ""),
              details,
              GREEN,
              () -> routeTo(a, dest, Router.Transit)));
          s.results.addView(text(a,
              "مترو: " + finalMixed.metroSource
                  + (finalMixed.metroStops >= 0 ? " • " + finalMixed.metroStops + " ایستگاه" : "")
                  + (finalMixed.metroTransfers >= 0 ? " • " + finalMixed.metroTransfers + " تعویض خط" : "")
                  + " • داده زنده قطار متصل نیست.",
              11, MUTED, Typeface.NORMAL));
        }

        if (walkSec < Integer.MAX_VALUE) {
          s.results.addView(optionCard(a,
              "🚶 پیاده" + ("پیاده".equals(bestName) ? "  ✓ سریع‌ترین" : ""),
              "حدود " + formatMinutes(walkSec) + " • " + formatDistance(direct * 1.20d),
              CYAN,
              () -> routeTo(a, dest, Router.Pedestrian)));
        }
      });
    }, "nv-v031-hurry").start();
  }

  private static void buildMixedPlan(MwmActivity a, Screen s, Location origin, Place dest) {
    setStatus(s, "در حال ساخت سفر چندمرحله‌ای واقعی با ایستگاه‌های نزدیک…", CYAN);
    new Thread(() -> {
      MixedEstimate mixed = null;
      try { mixed = estimateMixed(a, origin, dest); } catch (Throwable ignored) {}
      MixedEstimate result = mixed;

      a.runOnUiThread(() -> {
        if (!alive(a, s)) return;
        s.results.removeAllViews();

        if (result == null) {
          setStatus(s, "مترو مناسب نزدیک مبدأ یا مقصد پیدا نشد؛ گزینه‌های عملی جایگزین نمایش داده شدند.", AMBER);
          s.results.addView(button(a, "سریع‌ترین مسیر خودرو", BLUE, () -> routeTo(a, dest, Router.Vehicle)));
          s.results.addView(button(a, "مسیر پیاده", CYAN, () -> routeTo(a, dest, Router.Pedestrian)));
          return;
        }

        setStatus(s, "برآورد سفر ترکیبی برای «" + dest.title + "» آماده است", GREEN);
        s.results.addView(text(a, "کل برآورد: " + formatMinutes(result.totalSec), 18, WHITE, Typeface.BOLD));
        s.results.addView(stepCard(a, "۱", result.accessMode + " تا " + result.fromStation.title,
            formatMinutes(result.accessSec) + " • " + formatDistance(result.accessDistanceM), GREEN));
        s.results.addView(stepCard(a, "۲", "مترو: " + result.fromStation.title + " → " + result.toStation.title,
            formatMinutes(result.metroSec)
                + " • " + result.metroSource
                + (result.metroStops >= 0 ? " • " + result.metroStops + " ایستگاه" : "")
                + (result.metroTransfers >= 0 ? " • " + result.metroTransfers + " تعویض" : "")
                + " • بدون داده زنده قطار", BLUE));
        s.results.addView(stepCard(a, "۳", result.egressMode + " تا مقصد",
            formatMinutes(result.egressSec) + " • " + formatDistance(result.egressDistanceM), PURPLE));
        s.results.addView(text(a, "هزینه تقریبی کل: " + formatToman(estimateMixedCostToman(a, result)),
            13, CYAN, Typeface.BOLD));
        s.results.addView(button(a, "شروع مسیر مترو/پیاده", GREEN, () -> routeTo(a, dest, Router.Transit)));
        s.results.addView(button(a, "مقایسه با خودرو", BLUE, () -> compareHurryOptions(a, s, origin, dest)));
      });
    }, "nv-v031-mixed").start();
  }

  private static MixedEstimate estimateMixed(MwmActivity a, Location origin, Place dest) throws Exception {
    if (!prefs(a).getBoolean("use_metro", true)) return null;
    List<Place> from = metroStations(origin.getLatitude(), origin.getLongitude(), 5_000);
    List<Place> to = metroStations(dest.lat, dest.lon, 5_000);
    if (from.isEmpty() || to.isEmpty()) return null;

    Place aStation = from.get(0), bStation = to.get(0);
    boolean minCost = prefs(a).getBoolean("min_cost", false);
    boolean lessWalking = prefs(a).getBoolean("less_walking", false);
    boolean taxi = prefs(a).getBoolean("use_taxi", true) && (!minCost || lessWalking);

    RoadEstimate access;
    RoadEstimate egress;
    if (taxi) {
      access = osrm(origin.getLatitude(), origin.getLongitude(), aStation.lat, aStation.lon);
      egress = osrm(bStation.lat, bStation.lon, dest.lat, dest.lon);
    } else {
      double ad = haversine(origin.getLatitude(), origin.getLongitude(), aStation.lat, aStation.lon) * 1.20d;
      double ed = haversine(bStation.lat, bStation.lon, dest.lat, dest.lon) * 1.20d;
      access = new RoadEstimate(ad, walkingSeconds(ad / 1.20d));
      egress = new RoadEstimate(ed, walkingSeconds(ed / 1.20d));
    }

    MetroRouteEstimate metro = null;
    try { metro = metroNetworkEstimate(aStation, bStation); } catch (Throwable ignored) {}

    double metroDistance;
    int metroSec;
    int metroStops;
    int metroTransfers;
    String metroSource;
    if (metro != null) {
      metroDistance = metro.distanceM;
      metroSec = metro.seconds;
      metroStops = metro.stops;
      metroTransfers = metro.transfers;
      metroSource = "شبکه خطوط OSM";
    } else {
      metroDistance = haversine(aStation.lat, aStation.lon, bStation.lat, bStation.lon) * 1.15d;
      metroSec = Math.max(8 * 60, (int)Math.round(metroDistance / 8.0d + 5 * 60d));
      metroStops = -1;
      metroTransfers = -1;
      metroSource = "برآورد فاصله‌ای";
    }

    int total = access.durationSec + metroSec + egress.durationSec;
    return new MixedEstimate(
        aStation, bStation,
        access.distanceM, access.durationSec,
        metroDistance, metroSec,
        egress.distanceM, egress.durationSec,
        total,
        taxi ? "تاکسی" : "پیاده",
        taxi ? "تاکسی" : "پیاده",
        metroStops, metroTransfers, metroSource);
  }

  private static MetroRouteEstimate metroNetworkEstimate(Place from, Place to) throws Exception {
    double direct = haversine(from.lat, from.lon, to.lat, to.lon);
    double midLat = (from.lat + to.lat) / 2d;
    double midLon = (from.lon + to.lon) / 2d;
    int radius = (int)Math.max(15_000d, Math.min(55_000d, direct / 2d + 12_000d));

    String around = String.format(Locale.US, "(around:%d,%.7f,%.7f)", radius, midLat, midLon);
    String q = "[out:json][timeout:20];relation" + around
        + "[\"type\"=\"route\"][\"route\"=\"subway\"];out body;>;out tags;";
    HttpURLConnection conn = (HttpURLConnection)new URL("https://overpass-api.de/api/interpreter").openConnection();
    conn.setConnectTimeout(8_000);
    conn.setReadTimeout(22_000);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    conn.setRequestProperty("User-Agent", "NV-Android/0.31");
    byte[] body = ("data=" + URLEncoder.encode(q, "UTF-8")).getBytes(StandardCharsets.UTF_8);
    try (java.io.OutputStream os = conn.getOutputStream()) { os.write(body); }
    if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300)
      throw new IllegalStateException("metro graph HTTP " + conn.getResponseCode());

    JSONArray elements = new JSONObject(readAll(conn.getInputStream())).optJSONArray("elements");
    conn.disconnect();
    if (elements == null) return null;

    Map<Long, MetroNode> nodes = new HashMap<>();
    List<JSONObject> relations = new ArrayList<>();

    for (int i = 0; i < elements.length(); i++) {
      JSONObject el = elements.optJSONObject(i);
      if (el == null) continue;
      String type = el.optString("type", "");
      if ("node".equals(type)) {
        long id = el.optLong("id", -1L);
        double lat = el.optDouble("lat", Double.NaN);
        double lon = el.optDouble("lon", Double.NaN);
        if (id <= 0 || !Double.isFinite(lat) || !Double.isFinite(lon)) continue;
        JSONObject tags = el.optJSONObject("tags");
        String name = tags == null ? "" : tags.optString("name:fa", tags.optString("name", ""));
        boolean stationLike = false;
        if (tags != null) {
          stationLike = "station".equals(tags.optString("railway"))
              || "subway".equals(tags.optString("station"))
              || "yes".equals(tags.optString("subway"))
              || "stop_position".equals(tags.optString("public_transport"))
              || "platform".equals(tags.optString("public_transport"));
        }
        nodes.put(id, new MetroNode(id, lat, lon, name, stationLike));
      } else if ("relation".equals(type)) {
        relations.add(el);
      }
    }

    Map<Long, List<MetroEdge>> graph = new HashMap<>();
    for (JSONObject rel : relations) {
      JSONObject tags = rel.optJSONObject("tags");
      String line = tags == null ? "" : tags.optString("ref", tags.optString("name:fa", tags.optString("name", "مترو")));
      if (line == null || line.trim().isEmpty()) line = "مترو";
      JSONArray members = rel.optJSONArray("members");
      if (members == null) continue;

      List<MetroNode> seq = new ArrayList<>();
      Set<Long> seenConsecutive = new HashSet<>();
      long previous = -1L;
      for (int i = 0; i < members.length(); i++) {
        JSONObject m = members.optJSONObject(i);
        if (m == null || !"node".equals(m.optString("type"))) continue;
        long ref = m.optLong("ref", -1L);
        MetroNode node = nodes.get(ref);
        if (node == null) continue;
        String role = m.optString("role", "");
        boolean roleStop = role.contains("stop") || role.contains("platform");
        if (!node.stationLike && !roleStop) continue;
        if (ref == previous) continue;
        previous = ref;
        if (!seq.isEmpty() && seenConsecutive.contains(ref)) continue;
        seq.add(node);
        seenConsecutive.add(ref);
      }

      for (int i = 1; i < seq.size(); i++) {
        MetroNode a = seq.get(i - 1), b = seq.get(i);
        double d = haversine(a.lat, a.lon, b.lat, b.lon);
        if (d < 50d || d > 8_000d) continue;
        int sec = Math.max(60, (int)Math.round(d / 9.0d + 35d));
        graph.computeIfAbsent(a.id, k -> new ArrayList<>()).add(new MetroEdge(b.id, line, d, sec));
        graph.computeIfAbsent(b.id, k -> new ArrayList<>()).add(new MetroEdge(a.id, line, d, sec));
      }
    }

    if (graph.isEmpty()) return null;
    MetroNode start = nearestMetroNode(nodes, graph, from.lat, from.lon);
    MetroNode goal = nearestMetroNode(nodes, graph, to.lat, to.lon);
    if (start == null || goal == null) return null;

    PriorityQueue<MetroState> pq = new PriorityQueue<>(Comparator.comparingInt(x -> x.seconds));
    Map<String, Integer> best = new HashMap<>();
    MetroState init = new MetroState(start.id, "", 0, 0d, 0, 0);
    pq.add(init);
    best.put(start.id + "|", 0);

    while (!pq.isEmpty()) {
      MetroState cur = pq.poll();
      String curKey = cur.nodeId + "|" + cur.line;
      Integer known = best.get(curKey);
      if (known != null && cur.seconds > known) continue;
      if (cur.nodeId == goal.id)
        return new MetroRouteEstimate(cur.seconds + 2 * 60, cur.distanceM, cur.stops, cur.transfers);

      List<MetroEdge> edges = graph.get(cur.nodeId);
      if (edges == null) continue;
      for (MetroEdge edge : edges) {
        boolean transfer = !cur.line.isEmpty() && !cur.line.equals(edge.line);
        int nextSec = cur.seconds + edge.seconds + (transfer ? 4 * 60 : 0);
        int nextTransfers = cur.transfers + (transfer ? 1 : 0);
        String key = edge.to + "|" + edge.line;
        int old = best.getOrDefault(key, Integer.MAX_VALUE);
        if (nextSec >= old) continue;
        best.put(key, nextSec);
        pq.add(new MetroState(edge.to, edge.line, nextSec,
            cur.distanceM + edge.distanceM, cur.stops + 1, nextTransfers));
      }
    }
    return null;
  }

  private static MetroNode nearestMetroNode(Map<Long, MetroNode> nodes,
                                            Map<Long, List<MetroEdge>> graph,
                                            double lat, double lon) {
    MetroNode best = null;
    double bestD = Double.MAX_VALUE;
    for (MetroNode n : nodes.values()) {
      if (!graph.containsKey(n.id)) continue;
      double d = haversine(lat, lon, n.lat, n.lon);
      if (d < bestD) { bestD = d; best = n; }
    }
    return bestD <= 1_500d ? best : null;
  }

  private static int walkingSeconds(double directMeters) {
    double networkMeters = directMeters * 1.20d;
    return Math.max(60, (int)Math.round(networkMeters / 1.35d));
  }

  private static View optionCard(MwmActivity a, String title, String subtitle, int color, Runnable action) {
    LinearLayout card = new LinearLayout(a);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
    card.setBackground(round(a, PANEL, color, 14));
    card.addView(text(a, title, 15, WHITE, Typeface.BOLD));
    card.addView(text(a, subtitle, 12, MUTED, Typeface.NORMAL));
    card.addView(button(a, "انتخاب", color, action));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(0, dp(a, 5), 0, dp(a, 5));
    card.setLayoutParams(lp);
    return card;
  }

  private static View stepCard(MwmActivity a, String n, String title, String subtitle, int color) {
    LinearLayout row = new LinearLayout(a);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    row.setPadding(dp(a, 10), dp(a, 9), dp(a, 10), dp(a, 9));
    row.setBackground(round(a, PANEL, color, 14));
    TextView num = text(a, n, 14, WHITE, Typeface.BOLD);
    num.setGravity(Gravity.CENTER);
    num.setBackground(round(a, color, color, 20));
    row.addView(num, new LinearLayout.LayoutParams(dp(a, 38), dp(a, 38)));
    LinearLayout tx = new LinearLayout(a);
    tx.setOrientation(LinearLayout.VERTICAL);
    tx.addView(text(a, title, 14, WHITE, Typeface.BOLD));
    tx.addView(text(a, subtitle, 11, MUTED, Typeface.NORMAL));
    row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(0, dp(a, 4), 0, dp(a, 4));
    row.setLayoutParams(lp);
    return row;
  }

  private static void previewVehicle(MwmActivity a, Screen s, Location origin, Place dest, boolean hurry) {
    setStatus(s, "در حال محاسبه زمان و فاصله جاده‌ای…", CYAN);
    new Thread(() -> {
      RoadEstimate est = null;
      try { est = osrm(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon); } catch (Throwable ignored) {}
      RoadEstimate result = est;
      a.runOnUiThread(() -> {
        if (!alive(a, s)) return;
        s.results.removeAllViews();
        if (result != null) {
          int adjusted = result.durationSec;
          setStatus(s, "مقصد: " + dest.title + (hurry ? " • اولویت سرعت" : ""), GREEN);
          s.results.addView(text(a, "برآورد NV آنلاین: " + formatMinutes(adjusted) + " • " + formatDistance(result.distanceM), 17, WHITE, Typeface.BOLD));
          s.results.addView(text(a, "این زمان بر پایه مسیر جاده‌ای آنلاین است؛ ترافیک زنده در دسترس نیست.", 12, MUTED, Typeface.NORMAL));
        } else {
          setStatus(s, "برآورد آنلاین در دسترس نیست؛ مسیر آفلاین موتور نقشه استفاده می‌شود.", AMBER);
        }
        s.results.addView(button(a, "شروع مسیر خودرو", BLUE, () -> routeTo(a, dest, Router.Vehicle)));
        s.results.addView(button(a, "مسیر پیاده", CYAN, () -> routeTo(a, dest, Router.Pedestrian)));
      });
    }, "nv-v031-eta").start();
  }

  private static int adjustedEtaSeconds(int engineSec, double distanceM, Location origin) {
    // For pre-route comparisons, OSRM's duration is already the estimate. Do not
    // extrapolate the entire trip from one instantaneous GPS speed sample.
    return Math.max(60, engineSec);
  }

  private static void compareModes(MwmActivity a, Screen s, Location origin, Place dest) {
    setStatus(s, "در حال محاسبه مسیرهای جایگزین، زمان و هزینه…", CYAN);
    new Thread(() -> {
      List<RoadEstimate> carRoutes = new ArrayList<>();
      MixedEstimate mixed = null;
      try { carRoutes = osrmAlternatives(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon); } catch (Throwable ignored) {}
      try { mixed = estimateMixed(a, origin, dest); } catch (Throwable ignored) {}

      double direct = haversine(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon);
      int walkSec = direct <= 15_000d ? walkingSeconds(direct) : Integer.MAX_VALUE;
      List<RoadEstimate> finalCars = carRoutes;
      MixedEstimate finalMixed = mixed;

      a.runOnUiThread(() -> {
        if (!alive(a, s)) return;
        s.results.removeAllViews();

        int idx = 1;
        for (RoadEstimate car : finalCars) {
          double liters = car.distanceM / 100000d * prefs(a).getInt("fuel_l100", 8);
          long cost = estimateCarCostToman(a, car.distanceM);
          String tag = idx == 1 ? "سریع‌ترین" : "جایگزین " + idx;
          s.results.addView(optionCard(a,
              "🚗 خودرو • " + tag,
              formatMinutes(car.durationSec) + " • " + formatDistance(car.distanceM)
                  + " • " + String.format(Locale.US, "%.1f لیتر", liters)
                  + " • حدود " + formatToman(cost),
              idx == 1 ? BLUE : PANEL2,
              () -> routeTo(a, dest, Router.Vehicle)));
          idx++;
        }

        if (finalMixed != null) {
          long cost = estimateMixedCostToman(a, finalMixed);
          s.results.addView(optionCard(a,
              "🚇 سفر ترکیبی",
              formatMinutes(finalMixed.totalSec) + " • "
                  + finalMixed.fromStation.title + " → " + finalMixed.toStation.title
                  + " • حدود " + formatToman(cost),
              GREEN,
              () -> routeTo(a, dest, Router.Transit)));
          s.results.addView(text(a,
              "مترو: " + finalMixed.metroSource
                  + (finalMixed.metroStops >= 0 ? " • " + finalMixed.metroStops + " ایستگاه" : "")
                  + (finalMixed.metroTransfers >= 0 ? " • " + finalMixed.metroTransfers + " تعویض خط" : "")
                  + " • بدون داده زنده قطار.",
              11, MUTED, Typeface.NORMAL));
        }

        if (walkSec < Integer.MAX_VALUE) {
          s.results.addView(optionCard(a,
              "🚶 پیاده",
              "حدود " + formatMinutes(walkSec) + " • " + formatDistance(direct * 1.20d) + " • بدون هزینه",
              CYAN,
              () -> routeTo(a, dest, Router.Pedestrian)));
        }

        if (finalCars.isEmpty() && finalMixed == null && walkSec == Integer.MAX_VALUE) {
          setStatus(s, "مقایسه آنلاین در دسترس نیست؛ از موتور آفلاین نقشه استفاده کنید.", AMBER);
          s.results.addView(button(a, "مسیر خودرو آفلاین", BLUE, () -> routeTo(a, dest, Router.Vehicle)));
          return;
        }
        setStatus(s, "گزینه‌های قابل استفاده برای «" + dest.title + "» آماده است", GREEN);
      });
    }, "nv-v031-compare").start();
  }

  private static long estimateCarCostToman(MwmActivity a, double distanceM) {
    SharedPreferences p = prefs(a);
    double liters = distanceM / 100000d * p.getInt("fuel_l100", 8);
    return Math.max(0L, Math.round(liters * p.getInt("fuel_price_toman", 3000)));
  }

  private static long estimateTaxiCostToman(MwmActivity a, double distanceM) {
    SharedPreferences p = prefs(a);
    return Math.max(0L, p.getInt("taxi_base_toman", 30000)
        + Math.round((distanceM / 1000d) * p.getInt("taxi_km_toman", 10000)));
  }

  private static long estimateMixedCostToman(MwmActivity a, MixedEstimate m) {
    long total = prefs(a).getInt("metro_fare_toman", 6000);
    if ("تاکسی".equals(m.accessMode)) total += estimateTaxiCostToman(a, m.accessDistanceM);
    if ("تاکسی".equals(m.egressMode)) total += estimateTaxiCostToman(a, m.egressDistanceM);
    return total;
  }

  private static String formatToman(long value) {
    return String.format(Locale.US, "%,d تومان", value);
  }

  private static void verifyMixedThenRoute(MwmActivity a, Screen s, Location origin, Place dest) {
    setStatus(s, "در حال بررسی ایستگاه مترو نزدیک مبدا و مقصد…", CYAN);
    new Thread(() -> {
      boolean ok = false;
      try { ok = hasMetroNear(origin.getLatitude(), origin.getLongitude(), 3500) && hasMetroNear(dest.lat, dest.lon, 3500); } catch (Throwable ignored) {}
      boolean metroOk = ok;
      a.runOnUiThread(() -> {
        if (!alive(a, s)) return;
        s.results.removeAllViews();
        if (metroOk) {
          setStatus(s, "ایستگاه مترو در هر دو سمت پیدا شد. مسیر ترکیبی قابل بررسی است.", GREEN);
          s.results.addView(button(a, "شروع مسیر مترو/پیاده", GREEN, () -> routeTo(a, dest, Router.Transit)));
          s.results.addView(button(a, "مقایسه با خودرو", BLUE, () -> previewVehicle(a, s, origin, dest, true)));
        } else {
          setStatus(s, "مترو مناسب نزدیک مبدا یا مقصد وجود ندارد؛ به‌جای خطای «No metro route» گزینه‌های عملی نمایش داده شد.", AMBER);
          s.results.addView(button(a, "سریع‌ترین مسیر خودرو", BLUE, () -> routeTo(a, dest, Router.Vehicle)));
          s.results.addView(button(a, "مسیر پیاده", CYAN, () -> routeTo(a, dest, Router.Pedestrian)));
        }
      });
    }, "nv-v031-mixed").start();
  }

  public static void openStationTransfer(MwmActivity a) {
    Screen s = screen(a, "تعویض هوشمند ایستگاه", "ایستگاه، خروجی و ادامه مسیر بر اساس مقصد فعال");
    Location loc = MwmApplication.from(a).getLocationHelper().getSavedLocation();
    if (!freshEnough(loc)) { setStatus(s, "GPS تازه با دقت مناسب لازم است.", RED); return; }

    MapObject target = RoutingController.get().getEndPoint();
    setStatus(s, "در حال بررسی نزدیک‌ترین ایستگاه و خروجی‌های ثبت‌شده…", CYAN);

    new Thread(() -> {
      try {
        List<Place> stations = metroStations(loc.getLatitude(), loc.getLongitude(), 6_000);
        if (stations.isEmpty()) {
          a.runOnUiThread(() -> setStatus(s, "در شعاع ۶ کیلومتر ایستگاه مترو پیدا نشد.", AMBER));
          return;
        }

        Place station = stations.get(0);
        List<Place> entrances = subwayEntrances(station, target);
        a.runOnUiThread(() -> {
          if (!alive(a, s)) return;
          s.results.removeAllViews();
          setStatus(s, "ایستگاه پیشنهادی: " + station.title + " • " + formatDistance(station.distanceMeters), GREEN);

          s.results.addView(optionCard(a, "🚇 " + station.title,
              "پیاده تا ایستگاه: " + formatDistance(station.distanceMeters),
              BLUE, () -> routeTo(a, station, Router.Pedestrian)));

          if (entrances.isEmpty()) {
            s.results.addView(text(a, "برای این ایستگاه خروجی مجزا در OpenStreetMap ثبت نشده است.", 12, MUTED, Typeface.NORMAL));
          } else {
            Place bestExit = entrances.get(0);
            String reason = target != null
                ? "نزدیک‌ترین خروجی ثبت‌شده به ادامه مسیر"
                : "نزدیک‌ترین خروجی ثبت‌شده";
            s.results.addView(optionCard(a, "✓ خروجی پیشنهادی: " + bestExit.title,
                reason + " • " + formatDistance(bestExit.distanceMeters),
                GREEN, () -> routeTo(a, bestExit, Router.Pedestrian)));

            int max = Math.min(5, entrances.size());
            for (int i = 1; i < max; i++) {
              Place e = entrances.get(i);
              s.results.addView(optionCard(a, "خروجی دیگر: " + e.title,
                  formatDistance(e.distanceMeters),
                  PANEL2, () -> routeTo(a, e, Router.Pedestrian)));
            }
          }
        });
      } catch (Throwable e) {
        a.runOnUiThread(() -> setStatus(s, "داده خروجی‌های ایستگاه در دسترس نیست.", AMBER));
      }
    }, "nv-v031-station-transfer").start();
  }

  public static void openMetroStatus(MwmActivity a) {
    showMetroStations(a, "مترو و ایستگاه‌ها",
        "ایستگاه‌های واقعی اطراف؛ موقعیت زنده قطار فقط در صورت اتصال منبع رسمی قابل نمایش است");
  }

  private static void showMetroStations(MwmActivity a, String title, String subtitle) {
    Screen s = screen(a, title, subtitle);
    Location loc = MwmApplication.from(a).getLocationHelper().getSavedLocation();
    if (!freshEnough(loc)) { setStatus(s, "GPS تازه لازم است.", RED); return; }
    setStatus(s, "در حال یافتن ایستگاه‌های مترو نزدیک…", CYAN);
    new Thread(() -> {
      try {
        List<Place> stations = metroStations(loc.getLatitude(), loc.getLongitude(), 10_000);
        a.runOnUiThread(() -> {
          if (!alive(a, s)) return;
          s.results.removeAllViews();
          if (stations.isEmpty()) { setStatus(s, "در شعاع ۱۰ کیلومتر ایستگاه مترو ثبت‌شده‌ای پیدا نشد.", AMBER); return; }
          setStatus(s, stations.size() + " ایستگاه پیدا شد", GREEN);
          for (Place p : stations) {
            LinearLayout card = new LinearLayout(a); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8)); card.setBackground(round(a, PANEL, OUTLINE, 14));
            card.addView(text(a, p.title + " • " + formatDistance(p.distanceMeters), 15, WHITE, Typeface.BOLD));
            LinearLayout row = new LinearLayout(a); row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(smallButton(a, "خودرو تا ایستگاه", BLUE, () -> routeTo(a, p, Router.Vehicle)), weight(a));
            row.addView(smallButton(a, "پیاده تا ایستگاه", CYAN, () -> routeTo(a, p, Router.Pedestrian)), weight(a));
            card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44))); s.results.addView(card);
          }
        });
      } catch (Throwable e) { a.runOnUiThread(() -> setStatus(s, "سرویس ایستگاه‌های مترو پاسخ نداد.", AMBER)); }
    }, "nv-v031-metro").start();
  }

  public static void openTaxi(MwmActivity a) {
    Screen s = screen(a, "تاکسی و محل سوارشدن", "نزدیک‌ترین ایستگاه‌ها و نقاط تاکسی ثبت‌شده اطراف موقعیت فعلی");
    Location loc = MwmApplication.from(a).getLocationHelper().getSavedLocation();
    if (!freshEnough(loc)) { setStatus(s, "GPS تازه با دقت مناسب لازم است.", RED); return; }

    setStatus(s, "در حال پیدا کردن نقاط تاکسی نزدیک…", CYAN);
    new Thread(() -> {
      try {
        List<Place> places = taxiStands(loc.getLatitude(), loc.getLongitude(), 5_000);
        a.runOnUiThread(() -> {
          if (!alive(a, s)) return;
          s.results.removeAllViews();
          if (places.isEmpty()) {
            setStatus(s, "در شعاع ۵ کیلومتر نقطه تاکسی ثبت‌شده‌ای پیدا نشد.", AMBER);
            return;
          }
          setStatus(s, places.size() + " نقطه تاکسی پیدا شد", GREEN);
          for (Place p : places) {
            s.results.addView(optionCard(a, "🚕 " + p.title,
                formatDistance(p.distanceMeters) + " از موقعیت فعلی",
                GREEN, () -> routeTo(a, p, Router.Pedestrian)));
          }
          s.results.addView(text(a,
              "هماهنگی زمان رسیدن خودروی اسنپ/تپسی نیازمند API رسمی سرویس‌دهنده است و بدون آن زمان ساختگی نمایش داده نمی‌شود.",
              11, MUTED, Typeface.NORMAL));
        });
      } catch (Throwable e) {
        a.runOnUiThread(() -> setStatus(s, "سرویس جستجوی نقاط تاکسی پاسخ نداد.", AMBER));
      }
    }, "nv-v031-taxi").start();
  }

  public static void openRouteAlerts(MwmActivity a) {
    Screen s = screen(a, "هشدارهای مسیر", "این تنظیمات در زمان مسیریابی واقعاً توسط NV بررسی می‌شوند");
    SharedPreferences p = prefs(a);
    addToggle(a, s, "هشدار عبور از محدودیت سرعت", "speed_alert", p.getBoolean("speed_alert", true));
    addToggle(a, s, "هشدار افت دقت GPS", "gps_alert", p.getBoolean("gps_alert", true));
    setStatus(s, "هشدارها روی مسیر فعال اعمال می‌شوند", GREEN);
  }

  private static void addToggle(MwmActivity a, Screen s, String label, String key, boolean initial) {
    TextView b = button(a, label + ": " + (initial ? "روشن" : "خاموش"), initial ? GREEN : PANEL2, () -> {});
    final boolean[] state = {initial};
    b.setOnClickListener(v -> {
      state[0] = !state[0]; prefs(a).edit().putBoolean(key, state[0]).apply();
      b.setText(label + ": " + (state[0] ? "روشن" : "خاموش")); b.setBackground(round(a, state[0] ? GREEN : PANEL2, state[0] ? GREEN : OUTLINE, 14));
    });
    s.results.addView(b);
  }

  public static void openRadius(MwmActivity a) {
    Screen s = screen(a, "محدوده جستجو", "شعاع انتخابی روی جستجوی «اطراف من» اعمال می‌شود");
    int[] km = {5, 10, 25, 50, 100};
    for (int x : km) s.results.addView(button(a, x + " کیلومتر", prefs(a).getInt("radius_m", 10000) == x * 1000 ? GREEN : PANEL2, () -> {
      prefs(a).edit().putInt("radius_m", x * 1000).apply(); Toast.makeText(a, "شعاع جستجو روی " + x + " کیلومتر تنظیم شد", Toast.LENGTH_SHORT).show(); removeScreen(a);
    }));
  }

  public static void openPreferences(MwmActivity a) {
    Screen s = screen(a, "ترجیحات سفر هوشمند", "تنظیمات واقعی برای زمان، هزینه، مصرف و انتخاب شیوه سفر");
    SharedPreferences p = prefs(a);

    s.results.addView(text(a, "مصرف سوخت مبنا:", 14, WHITE, Typeface.BOLD));
    int[] fuel = {6, 8, 10, 12};
    for (int f : fuel) {
      s.results.addView(button(a, f + " لیتر در ۱۰۰ کیلومتر",
          p.getInt("fuel_l100", 8) == f ? GREEN : PANEL2,
          () -> { p.edit().putInt("fuel_l100", f).apply(); Toast.makeText(a, "مصرف مبنا ذخیره شد", Toast.LENGTH_SHORT).show(); }));
    }

    EditText fuelPrice = input(a, "قیمت هر لیتر سوخت (تومان)");
    fuelPrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    fuelPrice.setText(String.valueOf(p.getInt("fuel_price_toman", 3000)));
    s.results.addView(fuelPrice, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 58)));

    EditText taxiBase = input(a, "هزینه پایه تاکسی (تومان)");
    taxiBase.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    taxiBase.setText(String.valueOf(p.getInt("taxi_base_toman", 30000)));
    s.results.addView(taxiBase, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 58)));

    EditText taxiKm = input(a, "هزینه تقریبی تاکسی به ازای هر کیلومتر (تومان)");
    taxiKm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    taxiKm.setText(String.valueOf(p.getInt("taxi_km_toman", 10000)));
    s.results.addView(taxiKm, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 58)));

    EditText metroFare = input(a, "کرایه تقریبی مترو (تومان)");
    metroFare.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    metroFare.setText(String.valueOf(p.getInt("metro_fare_toman", 6000)));
    s.results.addView(metroFare, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 58)));

    s.results.addView(button(a, "ذخیره هزینه‌ها", GREEN, () -> {
      try {
        p.edit()
            .putInt("fuel_price_toman", Integer.parseInt(fuelPrice.getText().toString().trim()))
            .putInt("taxi_base_toman", Integer.parseInt(taxiBase.getText().toString().trim()))
            .putInt("taxi_km_toman", Integer.parseInt(taxiKm.getText().toString().trim()))
            .putInt("metro_fare_toman", Integer.parseInt(metroFare.getText().toString().trim()))
            .apply();
        Toast.makeText(a, "تنظیمات هزینه ذخیره شد", Toast.LENGTH_SHORT).show();
      } catch (Throwable e1) {
        Toast.makeText(a, "مقادیر هزینه باید عدد صحیح باشند", Toast.LENGTH_LONG).show();
      }
    }));

    s.results.addView(text(a,
        "هزینه تاکسی/مترو برآوردی و قابل تنظیم است؛ قیمت زنده بدون API رسمی سرویس‌دهنده نمایش داده نمی‌شود.",
        11, MUTED, Typeface.NORMAL));
    s.results.addView(button(a, "تنظیم هشدارهای مسیر", BLUE, () -> openRouteAlerts(a)));
  }

  private static List<Place> geocodeRanked(String query, Location origin) throws Exception {
    String normalized = normalize(query);
    String searchQ = query;
    if (containsAny(normalized, "راه آهن", "راه اهن", "راه‌آهن") && !containsAny(normalized, "ایستگاه"))
      searchQ = "ایستگاه " + query;
    StringBuilder u = new StringBuilder("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=20&addressdetails=1&namedetails=1&extratags=1&accept-language=fa&q=")
        .append(URLEncoder.encode(searchQ, "UTF-8"));
    if (origin != null) {
      double dLat = 2.2, dLon = 2.5;
      u.append(String.format(Locale.US, "&viewbox=%.6f,%.6f,%.6f,%.6f", origin.getLongitude()-dLon, origin.getLatitude()+dLat, origin.getLongitude()+dLon, origin.getLatitude()-dLat));
    }
    HttpURLConnection c = (HttpURLConnection)new URL(u.toString()).openConnection();
    c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setRequestMethod("GET"); c.setRequestProperty("User-Agent", "NV-Android/0.31"); c.setRequestProperty("Accept", "application/json");
    if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) throw new IllegalStateException("HTTP " + c.getResponseCode());
    JSONArray a = new JSONArray(readAll(c.getInputStream())); c.disconnect();
    List<Place> out = new ArrayList<>();
    for (int i=0;i<a.length();i++) {
      JSONObject o=a.getJSONObject(i); double lat=Double.parseDouble(o.getString("lat")), lon=Double.parseDouble(o.getString("lon"));
      String display=o.optString("display_name", ""); String title=o.optString("name", "").trim();
      if (title.isEmpty()) { JSONObject names=o.optJSONObject("namedetails"); if (names!=null) title=names.optString("name:fa", names.optString("name", "")).trim(); }
      if (title.isEmpty()) { int comma=display.indexOf(','); title=comma>0?display.substring(0,comma).trim():display; }
      String category=o.optString("category", ""), type=o.optString("type", ""); double dist=origin==null?-1:haversine(origin.getLatitude(),origin.getLongitude(),lat,lon);
      Place p=new Place(title,display,lat,lon,dist,category,type,0); p.score=scorePlace(normalized,p); out.add(p);
    }
    out.sort((p1,p2)-> { int s=Integer.compare(p2.score,p1.score); if(s!=0)return s; return Double.compare(p1.distanceMeters<0?Double.MAX_VALUE:p1.distanceMeters,p2.distanceMeters<0?Double.MAX_VALUE:p2.distanceMeters); });
    if (out.size()>10) return new ArrayList<>(out.subList(0,10));
    return out;
  }

  private static int scorePlace(String query, Place p) {
    String t=normalize(p.title), a=normalize(p.address), cat=normalize(p.category), type=normalize(p.type); int score=0;
    if (t.equals(query)) score+=180;
    String[] tokens=query.split("\\s+"); int titleHits=0;
    for(String x:tokens){ if(x.length()<2)continue; if(t.contains(x)){score+=24;titleHits++;} else if(a.contains(x))score+=7; }
    if (titleHits>=Math.max(1,tokens.length-1)) score+=45;
    boolean rail=containsAny(query,"راه آهن","راه اهن","راه‌آهن","ایستگاه قطار");
    if(rail){ if(cat.contains("railway")||type.contains("station"))score+=120; if(t.contains("ایستگاه"))score+=45; if(t.contains("راه آهن")||t.contains("راه‌آهن"))score+=55; if(a.contains("کوی راه آهن")&&!t.contains("ایستگاه"))score-=45; }
    boolean metro=containsAny(query,"مترو","زیرزمینی"); if(metro&&(cat.contains("railway")||type.contains("station")))score+=80;
    if(p.distanceMeters>=0){ if(p.distanceMeters<20_000)score+=20; else if(p.distanceMeters<80_000)score+=10; }
    return score;
  }

  private static RoadEstimate osrm(double lat1,double lon1,double lat2,double lon2) throws Exception {
    List<RoadEstimate> routes = osrmAlternatives(lat1, lon1, lat2, lon2);
    if (routes.isEmpty()) throw new IllegalStateException("no route");
    routes.sort(Comparator.comparingInt(x -> x.durationSec));
    return routes.get(0);
  }

  private static List<RoadEstimate> osrmAlternatives(double lat1,double lon1,double lat2,double lon2) throws Exception {
    String u=String.format(Locale.US,
        "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false&alternatives=3&steps=false",
        lon1,lat1,lon2,lat2);
    HttpURLConnection conn=(HttpURLConnection)new URL(u).openConnection();
    conn.setConnectTimeout(8000);
    conn.setReadTimeout(12000);
    conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent","NV-Android/0.31");
    if(conn.getResponseCode()<200||conn.getResponseCode()>=300)
      throw new IllegalStateException("HTTP " + conn.getResponseCode());
    JSONObject root=new JSONObject(readAll(conn.getInputStream()));
    conn.disconnect();
    JSONArray routes=root.optJSONArray("routes");
    if(routes==null||routes.length()==0) throw new IllegalStateException("no route");

    List<RoadEstimate> out=new ArrayList<>();
    for(int i=0;i<routes.length();i++){
      JSONObject rr=routes.optJSONObject(i);
      if(rr==null) continue;
      double d=rr.optDouble("distance",0d);
      int sec=Math.max(1,(int)Math.round(rr.optDouble("duration",0d)));
      if(d>0d && sec>0) out.add(new RoadEstimate(d,sec));
    }
    out.sort(Comparator.comparingInt(x -> x.durationSec));
    if(out.size()>3) return new ArrayList<>(out.subList(0,3));
    return out;
  }

  private static boolean hasMetroNear(double lat,double lon,int radius) throws Exception { return !metroStations(lat,lon,radius).isEmpty(); }

  private static List<Place> metroStations(double lat,double lon,int radius) throws Exception {
    String around=String.format(Locale.US,"(around:%d,%.7f,%.7f)",radius,lat,lon);
    String q="[out:json][timeout:12];(node"+around+"[\"railway\"=\"station\"][\"station\"=\"subway\"];node"+around+"[\"railway\"=\"station\"][\"subway\"=\"yes\"];);out tags;";
    HttpURLConnection c=(HttpURLConnection)new URL("https://overpass-api.de/api/interpreter").openConnection();c.setConnectTimeout(7000);c.setReadTimeout(14000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");c.setRequestProperty("User-Agent","NV-Android/0.31");
    byte[] body=("data="+URLEncoder.encode(q,"UTF-8")).getBytes(StandardCharsets.UTF_8);try(java.io.OutputStream os=c.getOutputStream()){os.write(body);} if(c.getResponseCode()<200||c.getResponseCode()>=300)throw new IllegalStateException("HTTP");
    JSONArray els=new JSONObject(readAll(c.getInputStream())).optJSONArray("elements");c.disconnect();List<Place> out=new ArrayList<>(); if(els==null)return out;
    for(int i=0;i<els.length();i++){JSONObject e=els.optJSONObject(i);if(e==null)continue;double la=e.optDouble("lat",Double.NaN),lo=e.optDouble("lon",Double.NaN);if(!Double.isFinite(la)||!Double.isFinite(lo))continue;JSONObject tags=e.optJSONObject("tags");String name=tags==null?"":tags.optString("name:fa",tags.optString("name","ایستگاه مترو"));double d=haversine(lat,lon,la,lo);out.add(new Place(name,"ایستگاه مترو",la,lo,d,"railway","station",0));}
    out.sort(Comparator.comparingDouble(p->p.distanceMeters)); if(out.size()>12)return new ArrayList<>(out.subList(0,12));return out;
  }

  private static List<Place> subwayEntrances(Place station, MapObject target) throws Exception {
    String around = String.format(Locale.US, "(around:%d,%.7f,%.7f)", 700, station.lat, station.lon);
    String q = "[out:json][timeout:12];(node" + around + "[\"railway\"=\"subway_entrance\"];);out tags;";
    HttpURLConnection conn = (HttpURLConnection)new URL("https://overpass-api.de/api/interpreter").openConnection();
    conn.setConnectTimeout(7000); conn.setReadTimeout(14000); conn.setRequestMethod("POST"); conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    conn.setRequestProperty("User-Agent", "NV-Android/0.31");
    byte[] body = ("data=" + URLEncoder.encode(q, "UTF-8")).getBytes(StandardCharsets.UTF_8);
    try (java.io.OutputStream os = conn.getOutputStream()) { os.write(body); }
    if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) throw new IllegalStateException("HTTP");
    JSONArray els = new JSONObject(readAll(conn.getInputStream())).optJSONArray("elements");
    conn.disconnect();

    List<Place> out = new ArrayList<>();
    if (els == null) return out;
    for (int i = 0; i < els.length(); i++) {
      JSONObject e = els.optJSONObject(i); if (e == null) continue;
      double la = e.optDouble("lat", Double.NaN), lo = e.optDouble("lon", Double.NaN);
      if (!Double.isFinite(la) || !Double.isFinite(lo)) continue;
      JSONObject tags = e.optJSONObject("tags");
      String name = "";
      if (tags != null) {
        name = tags.optString("name:fa", tags.optString("name", ""));
        if (name.isEmpty()) {
          String ref = tags.optString("ref", "");
          name = ref.isEmpty() ? "خروجی مترو" : "خروجی " + ref;
        }
      }
      double d = target != null ? haversine(la, lo, target.getLat(), target.getLon())
                                : haversine(station.lat, station.lon, la, lo);
      out.add(new Place(name, "خروجی مترو", la, lo, d, "railway", "subway_entrance", 0));
    }
    out.sort(Comparator.comparingDouble(p -> p.distanceMeters));
    return out;
  }

  private static List<Place> taxiStands(double lat, double lon, int radius) throws Exception {
    String around = String.format(Locale.US, "(around:%d,%.7f,%.7f)", radius, lat, lon);
    String q = "[out:json][timeout:12];(node" + around + "[\"amenity\"=\"taxi\"];way" + around + "[\"amenity\"=\"taxi\"];);out center tags;";
    HttpURLConnection conn = (HttpURLConnection)new URL("https://overpass-api.de/api/interpreter").openConnection();
    conn.setConnectTimeout(7000); conn.setReadTimeout(14000); conn.setRequestMethod("POST"); conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    conn.setRequestProperty("User-Agent", "NV-Android/0.31");
    byte[] body = ("data=" + URLEncoder.encode(q, "UTF-8")).getBytes(StandardCharsets.UTF_8);
    try (java.io.OutputStream os = conn.getOutputStream()) { os.write(body); }
    if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) throw new IllegalStateException("HTTP");
    JSONArray els = new JSONObject(readAll(conn.getInputStream())).optJSONArray("elements");
    conn.disconnect();

    List<Place> out = new ArrayList<>();
    if (els == null) return out;
    for (int i = 0; i < els.length(); i++) {
      JSONObject e = els.optJSONObject(i); if (e == null) continue;
      double la = e.optDouble("lat", Double.NaN), lo = e.optDouble("lon", Double.NaN);
      JSONObject center = e.optJSONObject("center");
      if ((!Double.isFinite(la) || !Double.isFinite(lo)) && center != null) {
        la = center.optDouble("lat", Double.NaN);
        lo = center.optDouble("lon", Double.NaN);
      }
      if (!Double.isFinite(la) || !Double.isFinite(lo)) continue;
      JSONObject tags = e.optJSONObject("tags");
      String name = tags == null ? "" : tags.optString("name:fa", tags.optString("name", ""));
      if (name.isEmpty()) name = "ایستگاه تاکسی";
      double d = haversine(lat, lon, la, lo);
      out.add(new Place(name, "ایستگاه تاکسی", la, lo, d, "amenity", "taxi", 0));
    }
    out.sort(Comparator.comparingDouble(p -> p.distanceMeters));
    if (out.size() > 10) return new ArrayList<>(out.subList(0, 10));
    return out;
  }

  private static void routeTo(MwmActivity a, Place p, Router router) {
    Location loc=MwmApplication.from(a).getLocationHelper().getSavedLocation();
    if(!planningLocationOkay(loc)){
      Toast.makeText(a,"GPS برای شروع مسیر کافی نیست",Toast.LENGTH_LONG).show();
      NvRuntimeController.showLocationStatus(a);
      return;
    }

    if (!freshEnough(loc)) {
      final Location weak = loc;
      new AlertDialog.Builder(a)
          .setTitle("دقت GPS پایین است")
          .setMessage("خطای موقعیت فعلی حدود " + Math.round(weak.getAccuracy())
              + " متر است. می‌توانید منتظر GPS بهتر بمانید یا با هشدار ادامه دهید.")
          .setNegativeButton("دریافت GPS بهتر", (d,w) -> NvRuntimeController.showLocationStatus(a))
          .setPositiveButton("ادامه با هشدار", (d,w) -> startRoute(a, p, router, weak))
          .show();
      return;
    }
    startRoute(a, p, router, loc);
  }

  private static void startRoute(MwmActivity a, Place p, Router router, Location loc) {
    if (router == Router.Vehicle) {
      boolean avoidHighways = prefs(a).getBoolean("avoid_highways", false);
      boolean safer = prefs(a).getBoolean("safer_route", true);
      if (avoidHighways) RoutingOptions.addOption(RoadType.Motorway);
      else RoutingOptions.removeOption(RoadType.Motorway);
      if (safer) RoutingOptions.addOption(RoadType.Dirty);
      else RoutingOptions.removeOption(RoadType.Dirty);
    }

    MapObject start=MapObject.createMapObject(MapObject.MY_POSITION,"موقعیت من","",loc.getLatitude(),loc.getLongitude());
    MapObject end=MapObject.createMapObject(MapObject.SEARCH,p.title,p.address,p.lat,p.lon);
    removeScreen(a);
    RoutingController.get().prepare(start,end,router);
  }

  private static boolean freshEnough(Location l){
    return l!=null && System.currentTimeMillis()-l.getTime()<=FRESH_ROUTE_MS
        && (!l.hasAccuracy() || l.getAccuracy()<=MAX_ROUTE_ACCURACY);
  }
  private static boolean planningLocationOkay(Location l){
    return l!=null && System.currentTimeMillis()-l.getTime()<=FRESH_ROUTE_MS
        && (!l.hasAccuracy() || l.getAccuracy()<=120f);
  }
  private static boolean looksLikeTrip(String s){return containsAny(normalize(s),"میخوام","می خوام","می‌خوام","برم","برو","عجله","مسیر ترکیبی","پیاده","با مترو");}
  private static String extractDestination(String raw) {
    String q = normalize(raw);
    if (q.isEmpty()) return "";

    q = q.replaceAll("^(لطفا|لطفاً)\\s+", "");
    q = q.replaceAll("^(من\\s+)?(می ?خوام|میخواهم|می خواهم)\\s+(برم|بروم|برم به|بروم به)?\\s*", "");
    q = q.replaceAll("^(برو|بریم|برویم)\\s+(به\\s+)?", "");

    int to = Math.max(q.lastIndexOf(" برم به "), q.lastIndexOf(" بروم به "));
    if (to >= 0) q = q.substring(to + (q.startsWith(" بروم به ", to) ? 8 : 7)).trim();
    else {
      int go = Math.max(q.lastIndexOf(" برم "), q.lastIndexOf(" بروم "));
      if (go >= 0) q = q.substring(go + (q.startsWith(" بروم ", go) ? 6 : 5)).trim();
    }

    if (q.startsWith("به ")) q = q.substring(3).trim();

    // Strip travel-mode/urgency clauses only when they are modifiers, not part of a place name.
    String[] suffixes = {
        "عجله دارم", "خیلی عجله دارم", "عجله", "خیلی سریع", "سریع", "فوری", "زود",
        "مسیر ترکیبی", "ترکیبی", "با حمل و نقل عمومی", "با حمل‌ونقل عمومی",
        "با تاکسی", "با اسنپ", "با ماشین", "با خودرو", "با مترو", "پیاده"
    };
    boolean changed;
    do {
      changed = false;
      for (String s : suffixes) {
        String ns = normalize(s);
        if (q.endsWith(" " + ns)) {
          q = q.substring(0, q.length() - ns.length()).trim();
          changed = true;
        }
      }
    } while (changed);

    q = q.replaceAll("[،,؛;:!؟?]+", " ").replaceAll("\\s+", " ").trim();
    return q;
  }

  private static String normalize(String s) {
    if (s == null) return "";
    String n = s.replace('ي','ی').replace('ى','ی').replace('ك','ک')
        .replace('ة','ه').replace('ۀ','ه').replace("\u200c"," ").replace("ـ","");
    n = n.replaceAll("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]", "");
    n = n.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
        .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
        .replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4')
        .replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
    return n.replaceAll("\\s+"," ").trim().toLowerCase(Locale.ROOT);
  }

  private static boolean containsAny(String s,String...v){String n=normalize(s);for(String x:v)if(n.contains(normalize(x)))return true;return false;}
  private static String formatDistance(double m){return m<1000?Math.round(m)+" متر":String.format(Locale.US,"%.1f کیلومتر",m/1000d);}
  private static double haversine(double a,double b,double c,double d){double r=6371000,p1=Math.toRadians(a),p2=Math.toRadians(c),dp=Math.toRadians(c-a),dl=Math.toRadians(d-b),x=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return r*2*Math.atan2(Math.sqrt(x),Math.sqrt(1-x));}
  private static String readAll(InputStream in)throws Exception{try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String line;while((line=br.readLine())!=null){if(b.length()>2_000_000)throw new IllegalStateException("large response");b.append(line);}return b.toString();}}
  private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

  private static Screen screen(MwmActivity a,String title,String subtitle){removeScreen(a);ViewGroup host=a.findViewById(android.R.id.content);FrameLayout root=new FrameLayout(a);root.setTag(SCREEN_TAG);root.setBackgroundColor(BG);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);host.addView(root,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(dp(a,14),dp(a,36),dp(a,14),dp(a,14));LinearLayout head=new LinearLayout(a);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);TextView x=text(a,"×",30,WHITE,Typeface.NORMAL);x.setGravity(Gravity.CENTER);x.setOnClickListener(v->removeScreen(a));head.addView(x,new LinearLayout.LayoutParams(dp(a,50),dp(a,54)));LinearLayout titles=new LinearLayout(a);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text(a,title,20,WHITE,Typeface.BOLD));titles.addView(text(a,subtitle,12,MUTED,Typeface.NORMAL));head.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));col.addView(head);TextView status=text(a,"",14,CYAN,Typeface.NORMAL);status.setPadding(dp(a,8),dp(a,8),dp(a,8),dp(a,8));col.addView(status);LinearLayout controls=new LinearLayout(a);controls.setOrientation(LinearLayout.VERTICAL);col.addView(controls);ScrollView sv=new ScrollView(a);LinearLayout results=new LinearLayout(a);results.setOrientation(LinearLayout.VERTICAL);results.setPadding(0,dp(a,5),0,dp(a,20));sv.addView(results);col.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));root.addView(col,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));return new Screen(root,status,controls,results);}
  private static EditText input(MwmActivity a,String hint){EditText v=new EditText(a);v.setSingleLine(false);v.setMaxLines(3);v.setHint(hint);v.setHintTextColor(MUTED);v.setTextColor(WHITE);v.setTextSize(16);v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);v.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));v.setBackground(round(a,PANEL2,OUTLINE,14));v.setImeOptions(EditorInfo.IME_ACTION_GO);return v;}
  private static void setStatus(Screen s,String t,int c){s.status.setText(t);s.status.setTextColor(c);}
  private static void removeScreen(MwmActivity a){ViewGroup h=a.findViewById(android.R.id.content);if(h==null)return;View v=h.findViewWithTag(SCREEN_TAG);if(v!=null)h.removeView(v);}
  private static boolean alive(MwmActivity a,Screen s){ViewGroup h=a.findViewById(android.R.id.content);return h!=null&&h.findViewWithTag(SCREEN_TAG)==s.root;}
  private static TextView button(MwmActivity a,String label,int color,Runnable r){TextView v=text(a,label,14,WHITE,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setBackground(round(a,color,color==PANEL2?OUTLINE:color,14));v.setOnClickListener(x->r.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(a,52));p.setMargins(dp(a,4),dp(a,5),dp(a,4),dp(a,5));v.setLayoutParams(p);return v;}
  private static TextView smallButton(MwmActivity a,String label,int color,Runnable r){TextView v=text(a,label,11,WHITE,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setBackground(round(a,color,color,12));v.setOnClickListener(x->r.run());return v;}
  private static TextView text(MwmActivity a,String s,int sp,int color,int style){TextView v=new TextView(a);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setTypeface(Typeface.DEFAULT,style);v.setGravity(Gravity.RIGHT);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v;}
  private static LinearLayout.LayoutParams weight(MwmActivity a){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f);p.setMargins(dp(a,3),dp(a,3),dp(a,3),dp(a,3));return p;}
  private static GradientDrawable round(Context c,int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));d.setStroke(dp(c,1),stroke);return d;}
  private static int dp(Context c,int v){return Math.max(1,Math.round(c.getResources().getDisplayMetrics().density))*v;}

  private enum Mode { AUTO, CHAT, HURRY, MIXED, ETA, COMPARE, WALK }
  private static final class Screen { final FrameLayout root;final TextView status;final LinearLayout controls,results;Screen(FrameLayout r,TextView s,LinearLayout c,LinearLayout o){root=r;status=s;controls=c;results=o;} }
  private static final class Place { final String title,address;final double lat,lon,distanceMeters;final String category,type;int score;Place(String t,String a,double la,double lo,double d,String c,String ty,int sc){title=t;address=a;lat=la;lon=lo;distanceMeters=d;category=c;type=ty;score=sc;} }
  private static final class RoadEstimate { final double distanceM;final int durationSec;RoadEstimate(double d,int t){distanceM=d;durationSec=t;} }
  private static final class MixedEstimate {
    final Place fromStation, toStation;
    final double accessDistanceM, metroDistanceM, egressDistanceM;
    final int accessSec, metroSec, egressSec, totalSec;
    final String accessMode, egressMode;
    final int metroStops, metroTransfers;
    final String metroSource;
    MixedEstimate(Place f, Place t, double ad, int as, double md, int ms, double ed, int es,
                  int total, String am, String em, int stops, int transfers, String source) {
      fromStation=f; toStation=t; accessDistanceM=ad; accessSec=as;
      metroDistanceM=md; metroSec=ms; egressDistanceM=ed; egressSec=es;
      totalSec=total; accessMode=am; egressMode=em;
      metroStops=stops; metroTransfers=transfers; metroSource=source;
    }
  }

  private static final class MetroNode {
    final long id; final double lat, lon; final String name; final boolean stationLike;
    MetroNode(long id, double lat, double lon, String name, boolean stationLike) {
      this.id=id; this.lat=lat; this.lon=lon; this.name=name; this.stationLike=stationLike;
    }
  }
  private static final class MetroEdge {
    final long to; final String line; final double distanceM; final int seconds;
    MetroEdge(long to, String line, double distanceM, int seconds) {
      this.to=to; this.line=line; this.distanceM=distanceM; this.seconds=seconds;
    }
  }
  private static final class MetroState {
    final long nodeId; final String line; final int seconds; final double distanceM;
    final int stops, transfers;
    MetroState(long nodeId, String line, int seconds, double distanceM, int stops, int transfers) {
      this.nodeId=nodeId; this.line=line; this.seconds=seconds; this.distanceM=distanceM;
      this.stops=stops; this.transfers=transfers;
    }
  }
  private static final class MetroRouteEstimate {
    final int seconds; final double distanceM; final int stops, transfers;
    MetroRouteEstimate(int seconds, double distanceM, int stops, int transfers) {
      this.seconds=seconds; this.distanceM=distanceM; this.stops=stops; this.transfers=transfers;
    }
  }}
