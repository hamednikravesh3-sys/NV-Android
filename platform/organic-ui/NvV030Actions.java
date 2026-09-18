package app.organicmaps;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * NV v0.30 functional layer.
 *
 * Goals:
 * - rank Persian search results semantically instead of taking the nearest text match;
 * - separate railway-station search from metro/transit routing;
 * - gracefully fall back when a mixed/metro trip is not actually possible;
 * - expose a live NV ETA chip that blends the route engine ETA with real device speed;
 * - give every advanced menu entry a distinct, observable action;
 * - keep claims honest: no fabricated live metro positions or fake traffic data.
 */
public final class NvV030Actions implements DefaultLifecycleObserver {
  private static final int BG = Color.rgb(8, 20, 32);
  private static final int PANEL = Color.rgb(7, 33, 55);
  private static final int PANEL2 = Color.rgb(10, 48, 78);
  private static final int OUTLINE = Color.rgb(45, 126, 171);
  private static final int WHITE = Color.WHITE;
  private static final int MUTED = Color.rgb(205, 220, 231);
  private static final int CYAN = Color.rgb(40, 206, 255);
  private static final int BLUE = Color.rgb(45, 139, 255);
  private static final int GREEN = Color.rgb(42, 214, 113);
  private static final int AMBER = Color.rgb(255, 188, 54);
  private static final int RED = Color.rgb(255, 70, 89);
  private static final String SCREEN_TAG = "nv-v030-screen";
  private static final String ETA_TAG = "nv-v030-eta-chip";
  private static final String PREFS = "nv_v030";
  private static final long FRESH_ROUTE_MS = 120_000L;
  private static final float MAX_ROUTE_ACCURACY = 60f;
  private static final Map<MwmActivity, NvV030Actions> INSTANCES = new WeakHashMap<>();

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

  private final Runnable poll = new Runnable() {
    @Override public void run() {
      if (destroyed) return;
      updateEtaAndAlerts();
      handler.postDelayed(this, 1000L);
    }
  };

  private NvV030Actions(MwmActivity a) {
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
    INSTANCES.put(a, new NvV030Actions(a));
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
      String source = onlineEtaFresh() ? "NV آنلاین" : "NV";
      etaChip.setText(source + "  " + formatMinutes(nv) + (meters > 0 ? "  •  " + formatDistance(meters) : ""));
      etaChip.setVisibility(View.VISIBLE);

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
    return onlineEtaSec > 0 && System.currentTimeMillis() - onlineEtaUpdatedAt <= 90_000L;
  }

  private void requestOnlineEtaIfNeeded(Location loc, MapObject endPoint) {
    if (loc == null || endPoint == null || !RoutingController.get().isVehicleRouterType()) return;
    if (loc.hasAccuracy() && loc.getAccuracy() > 100f) return;

    double tLat = endPoint.getLat(), tLon = endPoint.getLon();
    boolean targetChanged = !Double.isFinite(onlineTargetLat)
        || haversine(onlineTargetLat, onlineTargetLon, tLat, tLon) > 100d;
    if (!targetChanged && onlineEtaFresh()) return;
    if (onlineEtaLoading) return;

    onlineEtaLoading = true;
    final double sLat = loc.getLatitude(), sLon = loc.getLongitude();
    new Thread(() -> {
      try {
        RoadEstimate estimate = osrm(sLat, sLon, tLat, tLon);
        onlineEtaSec = estimate.durationSec;
        onlineEtaDistanceM = estimate.distanceM;
        onlineEtaUpdatedAt = System.currentTimeMillis();
        onlineTargetLat = tLat;
        onlineTargetLon = tLon;
      } catch (Throwable ignored) {
        // Keep route-engine ETA if the online estimator is unavailable.
      } finally {
        onlineEtaLoading = false;
      }
    }, "nv-v030-online-eta").start();
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
    }, "nv-v030-search").start();
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
    if (!freshEnough(origin)) {
      setStatus(s, "موقعیت فعلی برای شروع مسیر کافی نیست؛ ابتدا GPS را تازه کنید.", RED);
      s.results.removeAllViews();
      s.results.addView(button(a, "بررسی GPS", GREEN, () -> NvRuntimeController.showLocationStatus(a)));
      return;
    }
    setStatus(s, "در حال تشخیص مقصد «" + destination + "»…", CYAN);
    s.results.removeAllViews();
    final String dest = destination;
    new Thread(() -> {
      try {
        List<Place> list = geocodeRanked(dest, origin);
        if (list.isEmpty()) throw new IllegalStateException("no destination");
        Place best = list.get(0);
        a.runOnUiThread(() -> {
          if (!alive(a, s)) return;
          if (mode == Mode.WALK || containsAny(raw, "پیاده", "قدم")) {
            setStatus(s, "مقصد: " + best.title + " • مسیر پیاده", GREEN);
            routeTo(a, best, Router.Pedestrian); return;
          }
          boolean explicitMixed = mode == Mode.MIXED || containsAny(raw, "ترکیبی", "مترو", "حمل و نقل عمومی", "حمل‌ونقل عمومی", "اتوبوس");
          // Railway stations are destinations, not a request for subway routing.
          boolean railDestination = containsAny(dest, "راه آهن", "راه‌آهن", "ایستگاه قطار", "راه اهن");
          if (explicitMixed && !railDestination) {
            verifyMixedThenRoute(a, s, origin, best); return;
          }
          if (mode == Mode.COMPARE) {
            compareModes(a, s, origin, best); return;
          }
          previewVehicle(a, s, origin, best, mode == Mode.HURRY || containsAny(raw, "عجله", "سریع", "زود", "فوری"));
        });
      } catch (Throwable e) {
        a.runOnUiThread(() -> {
          if (!alive(a, s)) return;
          setStatus(s, "مقصد با اطمینان کافی پیدا نشد.", AMBER);
          s.results.addView(button(a, "جستجوی هوشمند مقصد", BLUE, () -> openSmartSearch(a)));
        });
      }
    }, "nv-v030-trip").start();
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
    }, "nv-v030-eta").start();
  }

  private static int adjustedEtaSeconds(int engineSec, double distanceM, Location origin) {
    // For pre-route comparisons, OSRM's duration is already the estimate. Do not
    // extrapolate the entire trip from one instantaneous GPS speed sample.
    return Math.max(60, engineSec);
  }

  private static void compareModes(MwmActivity a, Screen s, Location origin, Place dest) {
    setStatus(s, "در حال مقایسه گزینه‌ها…", CYAN);
    new Thread(() -> {
      RoadEstimate car = null;
      boolean metro = false;
      try { car = osrm(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon); } catch (Throwable ignored) {}
      try { metro = hasMetroNear(origin.getLatitude(), origin.getLongitude(), 3500) && hasMetroNear(dest.lat, dest.lon, 3500); } catch (Throwable ignored) {}
      RoadEstimate finalCar = car; boolean finalMetro = metro;
      a.runOnUiThread(() -> {
        if (!alive(a, s)) return;
        s.results.removeAllViews();
        double direct = haversine(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon);
        int walkSec = (int)Math.round((direct * 1.20) / 1.35);
        if (finalCar != null) {
          int adj = adjustedEtaSeconds(finalCar.durationSec, finalCar.distanceM, origin);
          double consumption = finalCar.distanceM / 100000d * prefs(a).getInt("fuel_l100", 8);
          s.results.addView(text(a, "خودرو: " + formatMinutes(adj) + " • " + formatDistance(finalCar.distanceM), 16, WHITE, Typeface.BOLD));
          s.results.addView(text(a, String.format(Locale.US, "مصرف تقریبی با تنظیم فعلی: %.1f لیتر", consumption), 12, MUTED, Typeface.NORMAL));
        }
        s.results.addView(text(a, "پیاده: حدود " + formatMinutes(walkSec) + " • " + formatDistance(direct * 1.20), 15, WHITE, Typeface.NORMAL));
        s.results.addView(text(a, finalMetro ? "مترو/ترکیبی: ایستگاه مناسب در هر دو سمت پیدا شد" : "مترو/ترکیبی: ایستگاه مناسب در هر دو سمت پیدا نشد", 14, finalMetro ? GREEN : AMBER, Typeface.BOLD));
        s.results.addView(button(a, "مسیر خودرو", BLUE, () -> routeTo(a, dest, Router.Vehicle)));
        if (finalMetro) s.results.addView(button(a, "مسیر مترو/ترکیبی", GREEN, () -> routeTo(a, dest, Router.Transit)));
        s.results.addView(button(a, "مسیر پیاده", CYAN, () -> routeTo(a, dest, Router.Pedestrian)));
        setStatus(s, "مقایسه برای «" + dest.title + "» آماده است", GREEN);
      });
    }, "nv-v030-compare").start();
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
    }, "nv-v030-mixed").start();
  }

  public static void openStationTransfer(MwmActivity a) { showMetroStations(a, "تعویض هوشمند ایستگاه", "ایستگاه‌های نزدیک را از نزدیک به دور نمایش می‌دهد تا ورودی مناسب مسیر را انتخاب کنید"); }
  public static void openMetroStatus(MwmActivity a) { showMetroStations(a, "مترو و ایستگاه‌ها", "ایستگاه‌های واقعی اطراف؛ چون منبع GTFS-Realtime سراسری برای ایران متصل نیست، موقعیت زنده قطار جعل نمی‌شود"); }

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
    }, "nv-v030-metro").start();
  }

  public static void openTaxi(MwmActivity a) { NvSmartActions.openNearby(a, "ایستگاه تاکسی"); }

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
    Screen s = screen(a, "ترجیحات سفر هوشمند", "تنظیمات واقعی برای ETA، مصرف و هشدارها");
    s.results.addView(text(a, "مصرف سوخت مبنا برای مقایسه زمان/مصرف:", 14, WHITE, Typeface.BOLD));
    int[] fuel = {6, 8, 10, 12};
    for (int f : fuel) s.results.addView(button(a, f + " لیتر در ۱۰۰ کیلومتر", prefs(a).getInt("fuel_l100", 8) == f ? GREEN : PANEL2, () -> {
      prefs(a).edit().putInt("fuel_l100", f).apply(); Toast.makeText(a, "مصرف مبنا ذخیره شد", Toast.LENGTH_SHORT).show(); removeScreen(a);
    }));
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
    c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setRequestMethod("GET"); c.setRequestProperty("User-Agent", "NV-Android/0.30"); c.setRequestProperty("Accept", "application/json");
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
    String u=String.format(Locale.US,"https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false&alternatives=true&steps=false",lon1,lat1,lon2,lat2);
    HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setRequestMethod("GET");c.setRequestProperty("User-Agent","NV-Android/0.30");
    if(c.getResponseCode()<200||c.getResponseCode()>=300)throw new IllegalStateException("HTTP"); JSONObject root=new JSONObject(readAll(c.getInputStream()));c.disconnect();
    JSONArray routes=root.optJSONArray("routes"); if(routes==null||routes.length()==0)throw new IllegalStateException("no route"); JSONObject best=routes.getJSONObject(0);
    return new RoadEstimate(best.optDouble("distance",0),Math.max(1,(int)Math.round(best.optDouble("duration",0))));
  }

  private static boolean hasMetroNear(double lat,double lon,int radius) throws Exception { return !metroStations(lat,lon,radius).isEmpty(); }

  private static List<Place> metroStations(double lat,double lon,int radius) throws Exception {
    String around=String.format(Locale.US,"(around:%d,%.7f,%.7f)",radius,lat,lon);
    String q="[out:json][timeout:12];(node"+around+"[\"railway\"=\"station\"][\"station\"=\"subway\"];node"+around+"[\"railway\"=\"station\"][\"subway\"=\"yes\"];);out tags;";
    HttpURLConnection c=(HttpURLConnection)new URL("https://overpass-api.de/api/interpreter").openConnection();c.setConnectTimeout(7000);c.setReadTimeout(14000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");c.setRequestProperty("User-Agent","NV-Android/0.30");
    byte[] body=("data="+URLEncoder.encode(q,"UTF-8")).getBytes(StandardCharsets.UTF_8);try(java.io.OutputStream os=c.getOutputStream()){os.write(body);} if(c.getResponseCode()<200||c.getResponseCode()>=300)throw new IllegalStateException("HTTP");
    JSONArray els=new JSONObject(readAll(c.getInputStream())).optJSONArray("elements");c.disconnect();List<Place> out=new ArrayList<>(); if(els==null)return out;
    for(int i=0;i<els.length();i++){JSONObject e=els.optJSONObject(i);if(e==null)continue;double la=e.optDouble("lat",Double.NaN),lo=e.optDouble("lon",Double.NaN);if(!Double.isFinite(la)||!Double.isFinite(lo))continue;JSONObject tags=e.optJSONObject("tags");String name=tags==null?"":tags.optString("name:fa",tags.optString("name","ایستگاه مترو"));double d=haversine(lat,lon,la,lo);out.add(new Place(name,"ایستگاه مترو",la,lo,d,"railway","station",0));}
    out.sort(Comparator.comparingDouble(p->p.distanceMeters)); if(out.size()>12)return new ArrayList<>(out.subList(0,12));return out;
  }

  private static void routeTo(MwmActivity a, Place p, Router router) {
    Location loc=MwmApplication.from(a).getLocationHelper().getSavedLocation();
    if(!freshEnough(loc)){Toast.makeText(a,"GPS برای شروع مسیر کافی نیست",Toast.LENGTH_LONG).show();NvRuntimeController.showLocationStatus(a);return;}
    MapObject start=MapObject.createMapObject(MapObject.MY_POSITION,"موقعیت من","",loc.getLatitude(),loc.getLongitude());
    MapObject end=MapObject.createMapObject(MapObject.SEARCH,p.title,p.address,p.lat,p.lon); removeScreen(a); RoutingController.get().prepare(start,end,router);
  }

  private static boolean freshEnough(Location l){return l!=null&&System.currentTimeMillis()-l.getTime()<=FRESH_ROUTE_MS&&(!l.hasAccuracy()||l.getAccuracy()<=MAX_ROUTE_ACCURACY);}
  private static boolean looksLikeTrip(String s){return containsAny(normalize(s),"میخوام","می خوام","می‌خوام","برم","برو","عجله","مسیر ترکیبی","پیاده","با مترو");}
  private static String extractDestination(String raw){String q=normalize(raw);String[] noise={"می‌خوام برم","میخوام برم","می خوام برم","می خواهم بروم","میخواهم بروم","لطفا","لطفاً","عجله دارم","عجله","خیلی سریع","سریع","زود","فوری","مسیر ترکیبی","ترکیبی","با مترو","مترو","حمل و نقل عمومی","حمل‌ونقل عمومی","با تاکسی","تاکسی","با اسنپ","اسنپ","با ماشین","ماشین","با خودرو","خودرو","پیاده"};for(String n:noise)q=q.replace(normalize(n)," ");q=q.replace('،',' ').replace(',',' ').replace('؛',' ').replace(':',' ').replace('!',' ').replace('؟',' ');q=q.replaceAll("\\s+"," ").trim();if(q.startsWith("به "))q=q.substring(3).trim();if(q.startsWith("برم "))q=q.substring(4).trim();return q;}
  private static String normalize(String s){return s==null?"":s.replace('ي','ی').replace('ك','ک').replace("\u200c"," ").replace("ـ","").replaceAll("\\s+"," ").trim().toLowerCase(Locale.ROOT);}
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
}
