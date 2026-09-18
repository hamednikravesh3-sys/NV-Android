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
import android.text.InputType;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  private static final long FRESH_ROUTE_MS = 120_000L;
  private static final float MAX_ROUTE_ACCURACY = 75f;
  private static final String[] OVERPASS_ENDPOINTS = {
      "https://overpass-api.de/api/interpreter",
      "https://overpass.kumi.systems/api/interpreter",
      "https://overpass.private.coffee/api/interpreter"
  };
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
  private final ArrayDeque<Location> locationSamples = new ArrayDeque<>();
  private long lastSpeedSampleTime;
  private long lastLocationSampleTime;
  private volatile int onlineEtaSec = -1;
  private volatile double onlineEtaDistanceM = -1d;
  private volatile long onlineEtaUpdatedAt;
  private volatile double onlineTargetLat = Double.NaN;
  private volatile double onlineTargetLon = Double.NaN;
  private volatile double onlineSourceLat = Double.NaN;
  private volatile double onlineSourceLon = Double.NaN;
  private volatile boolean onlineEtaLoading;

  private final Runnable poll = new Runnable() {
    @Override public void run() {
      if (destroyed) return;
      recordLocationSample(locationHelper.getSavedLocation());
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

  public static void returnHome(MwmActivity a) {
    removeScreen(a);
    NvSmartTravelUi.close(a);
    try {
      Location loc=bestRecentLocation(a);
      if(loc!=null) Framework.nativeSetViewportCenter(loc.getLatitude(),loc.getLongitude(),16);
    } catch(Throwable ignored) {}
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

      Location loc = bestRecentLocationInstance();
      recordSpeedSample(loc);

      int engine = info.totalTimeInSeconds;
      double meters = distanceMeters(info.distToTarget);
      MapObject endPoint = RoutingController.get().getEndPoint();
      requestOnlineEtaIfNeeded(loc, endPoint);

      int nv = correctedActiveEta(engine, meters);
      String source = onlineEtaFresh() ? "NV آنلاین" : "NV";
      etaChip.setText(source + "  " + formatMinutes(nv) + (meters > 0 ? "  •  " + formatDistance(meters) : ""));
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

  private synchronized void recordLocationSample(Location loc) {
    if (loc == null || loc.getTime() <= lastLocationSampleTime) return;
    lastLocationSampleTime = loc.getTime();
    locationSamples.addLast(new Location(loc));
    while (locationSamples.size() > 20) locationSamples.removeFirst();
  }

  private synchronized Location bestRecentLocationInstance() {
    long now = System.currentTimeMillis();
    Location best = null;
    double bestScore = Double.POSITIVE_INFINITY;
    for (Location l : locationSamples) {
      long age = Math.max(0L, now - l.getTime());
      if (age > 30_000L) continue;
      double acc = l.hasAccuracy() ? Math.max(1d, l.getAccuracy()) : 500d;
      double speed = l.hasSpeed() ? Math.max(0d, l.getSpeed()) : 0d;
      double ageWeight = speed > 4d ? 5d : 2d;
      double score = acc + (age / 1000d) * ageWeight;
      if (score < bestScore) { bestScore = score; best = l; }
    }
    if (best != null) return new Location(best);
    Location current = locationHelper.getSavedLocation();
    return current == null ? null : new Location(current);
  }

  private static Location bestRecentLocation(MwmActivity a) {
    NvV031Actions instance = INSTANCES.get(a);
    if (instance != null) return instance.bestRecentLocationInstance();
    Location current = MwmApplication.from(a).getLocationHelper().getSavedLocation();
    return current == null ? null : new Location(current);
  }

  private void recordSpeedSample(Location loc) {
    if (loc == null || !loc.hasSpeed() || loc.getSpeed() < 0f) return;
    if (loc.hasAccuracy() && loc.getAccuracy() > 75f) return;
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
    return onlineEtaSec > 0 && System.currentTimeMillis() - onlineEtaUpdatedAt <= 35_000L;
  }

  private void requestOnlineEtaIfNeeded(Location loc, MapObject endPoint) {
    if (loc == null || endPoint == null || !RoutingController.get().isVehicleRouterType()) return;
    if (loc.hasAccuracy() && loc.getAccuracy() > 100f) return;

    double tLat = endPoint.getLat(), tLon = endPoint.getLon();
    boolean targetChanged = !Double.isFinite(onlineTargetLat)
        || haversine(onlineTargetLat, onlineTargetLon, tLat, tLon) > 100d;
    boolean moved = !Double.isFinite(onlineSourceLat)
        || haversine(onlineSourceLat, onlineSourceLon, loc.getLatitude(), loc.getLongitude()) > 250d;
    boolean stale = System.currentTimeMillis() - onlineEtaUpdatedAt > 35_000L;
    if (!targetChanged && !moved && !stale && onlineEtaFresh()) return;
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
        onlineSourceLat = sLat;
        onlineSourceLon = sLon;
      } catch (Throwable ignored) {
        // Fall back to the offline route engine when the public estimator is unavailable.
      } finally {
        onlineEtaLoading = false;
      }
    }, "nv-v031-online-eta").start();
  }

  private int correctedActiveEta(int engineSec, double remainingMeters) {
    int base = Math.max(60, engineSec);

    if (onlineEtaFresh() && onlineEtaSec > 0) {
      int online = onlineEtaSec;
      // If the online estimate was computed slightly earlier, scale it by remaining distance.
      if (remainingMeters > 0d && onlineEtaDistanceM > 100d) {
        double fraction = Math.max(0.10d, Math.min(1.05d, remainingMeters / onlineEtaDistanceM));
        online = Math.max(60, (int)Math.round(onlineEtaSec * fraction));
      }

      double ratio = base / (double) online;
      if (ratio > 1.60d || ratio < 0.62d)
        base = online;
      else
        base = (int)Math.round(online * 0.78d + base * 0.22d);
    }

    // Never extrapolate a long trip from instantaneous speed. For the last 12 km,
    // a rolling median may make a small correction only when enough clean samples exist.
    if (remainingMeters > 0d && remainingMeters <= 12_000d) {
      double median = medianSpeedMps();
      if (median >= 2.8d) {
        double speedEta = remainingMeters / median;
        speedEta = Math.max(base * 0.82d, Math.min(base * 1.18d, speedEta));
        base = (int)Math.round(base * 0.90d + speedEta * 0.10d);
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
    Location origin = bestRecentLocation(a);
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
    Location origin = bestRecentLocation(a);
    if (!freshEnough(origin)) {
      setStatus(s, "برای شروع مسیر، GPS تازه با خطای حداکثر ۷۵ متر لازم است.", RED);
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
              + finalMixed.fromStation.title + " → " + finalMixed.toStation.title;
          s.results.addView(optionCard(a,
              "🚇 ترکیبی" + ("ترکیبی".equals(bestName) ? "  ✓ سریع‌ترین" : ""),
              details,
              GREEN,
              () -> buildMixedPlan(a, s, origin, dest)));
          s.results.addView(text(a, "زمان مترو تقریبی است؛ داده زنده قطار سراسری متصل نیست.", 11, MUTED, Typeface.NORMAL));
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
            formatMinutes(result.metroSec) + " • " + result.stationCount + " ایستگاه • " + result.transfers + " تعویض • " + result.lineSummary, BLUE));
        s.results.addView(stepCard(a, "۳", result.egressMode + " تا مقصد",
            formatMinutes(result.egressSec) + " • " + formatDistance(result.egressDistanceM), PURPLE));
        Router accessRouter = "تاکسی".equals(result.accessMode) ? Router.Vehicle : Router.Pedestrian;
        Router egressRouter = "تاکسی".equals(result.egressMode) ? Router.Vehicle : Router.Pedestrian;
        s.results.addView(button(a, "مرحله ۱: رفتن به " + result.fromStation.title, GREEN,
            () -> routeTo(a, result.fromStation, accessRouter)));
        s.results.addView(button(a, "مرحله ۲: مسیر مترو بین دو ایستگاه", BLUE,
            () -> routeBetween(a, result.fromStation, result.toStation, Router.Transit)));
        s.results.addView(button(a, "مرحله ۳: از " + result.toStation.title + " تا مقصد", PURPLE,
            () -> routeBetween(a, result.toStation, dest, egressRouter)));
        s.results.addView(button(a, "مقایسه دوباره با خودرو", PANEL2, () -> compareHurryOptions(a, s, origin, dest)));
      });
    }, "nv-v031-mixed").start();
  }

  private static MixedEstimate estimateMixed(MwmActivity a, Location origin, Place dest) throws Exception {
    if (!prefs(a).getBoolean("use_metro", true)) return null;

    MetroNetwork network = fetchMetroNetwork(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon);
    if (network == null || network.nodes.isEmpty() || network.edges.isEmpty()) return null;

    List<MetroNode> from = nearestMetroNodes(network, origin.getLatitude(), origin.getLongitude(), 4_500d, 5);
    List<MetroNode> to = nearestMetroNodes(network, dest.lat, dest.lon, 4_500d, 5);
    if (from.isEmpty() || to.isEmpty()) return null;

    MetroPath bestPath = null;
    MetroNode bestFrom = null, bestTo = null;
    double bestScore = Double.POSITIVE_INFINITY;

    for (MetroNode f : from) {
      for (MetroNode t : to) {
        MetroPath p = shortestMetroPath(network, f.id, t.id);
        if (p == null) continue;
        double accessDirect = haversine(origin.getLatitude(), origin.getLongitude(), f.lat, f.lon);
        double egressDirect = haversine(t.lat, t.lon, dest.lat, dest.lon);
        double score = p.seconds + walkingSeconds(accessDirect) + walkingSeconds(egressDirect);
        if (score < bestScore) {
          bestScore = score;
          bestPath = p;
          bestFrom = f;
          bestTo = t;
        }
      }
    }
    if (bestPath == null || bestFrom == null || bestTo == null) return null;

    boolean minCost = prefs(a).getBoolean("min_cost", false);
    boolean lessWalking = prefs(a).getBoolean("less_walking", false);
    boolean taxi = prefs(a).getBoolean("use_taxi", true) && (!minCost || lessWalking);

    RoadEstimate access;
    RoadEstimate egress;
    String accessMode, egressMode;
    double accessDirect = haversine(origin.getLatitude(), origin.getLongitude(), bestFrom.lat, bestFrom.lon);
    double egressDirect = haversine(bestTo.lat, bestTo.lon, dest.lat, dest.lon);

    if (taxi && accessDirect > 900d) {
      RoadEstimate tmp=null;
      try{tmp=osrm(origin.getLatitude(), origin.getLongitude(), bestFrom.lat, bestFrom.lon);}catch(Throwable ignored){}
      if(tmp!=null){access=tmp;accessMode="تاکسی";}
      else{
        double d=accessDirect*1.20d;access=new RoadEstimate(d,walkingSeconds(accessDirect));accessMode="پیاده";
      }
    } else {
      double d = accessDirect * 1.20d;
      access = new RoadEstimate(d, walkingSeconds(accessDirect));
      accessMode = "پیاده";
    }

    if (taxi && egressDirect > 900d) {
      RoadEstimate tmp=null;
      try{tmp=osrm(bestTo.lat,bestTo.lon,dest.lat,dest.lon);}catch(Throwable ignored){}
      if(tmp!=null){egress=tmp;egressMode="تاکسی";}
      else{
        double d=egressDirect*1.20d;egress=new RoadEstimate(d,walkingSeconds(egressDirect));egressMode="پیاده";
      }
    } else {
      double d = egressDirect * 1.20d;
      egress = new RoadEstimate(d, walkingSeconds(egressDirect));
      egressMode = "پیاده";
    }

    Place aStation = new Place(bestFrom.name, "ایستگاه مترو", bestFrom.lat, bestFrom.lon,
        accessDirect, "railway", "station", 0);
    Place bStation = new Place(bestTo.name, "ایستگاه مترو", bestTo.lat, bestTo.lon,
        egressDirect, "railway", "station", 0);

    int total = access.durationSec + bestPath.seconds + egress.durationSec;
    return new MixedEstimate(
        aStation, bStation,
        access.distanceM, access.durationSec,
        bestPath.distanceM, bestPath.seconds,
        egress.distanceM, egress.durationSec,
        total, accessMode, egressMode,
        bestPath.lineSummary, bestPath.stationCount, bestPath.transfers);
  }

  private static JSONObject overpassJson(String query,int readTimeoutMs) throws Exception {
    Throwable last=null;
    for(String endpoint:OVERPASS_ENDPOINTS){
      HttpURLConnection conn=null;
      try{
        conn=(HttpURLConnection)new URL(endpoint).openConnection();
        conn.setConnectTimeout(7000);conn.setReadTimeout(readTimeoutMs);conn.setRequestMethod("POST");conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("User-Agent","NV-Android/0.31");
        byte[] body=("data="+URLEncoder.encode(query,"UTF-8")).getBytes(StandardCharsets.UTF_8);
        try(java.io.OutputStream os=conn.getOutputStream()){os.write(body);}
        int code=conn.getResponseCode();
        if(code<200||code>=300)throw new IllegalStateException("Overpass HTTP "+code);
        return new JSONObject(readAll(conn.getInputStream()));
      }catch(Throwable e){last=e;}
      finally{if(conn!=null)conn.disconnect();}
    }
    throw new IllegalStateException("all Overpass endpoints failed",last);
  }

  private static MetroNetwork fetchMetroNetwork(double lat1,double lon1,double lat2,double lon2) throws Exception {
    double midLat=(lat1+lat2)/2d, midLon=(lon1+lon2)/2d;
    double direct=haversine(lat1,lon1,lat2,lon2);
    int radius=(int)Math.max(12_000d,Math.min(45_000d,direct/2d+12_000d));
    String around=String.format(Locale.US,"(around:%d,%.7f,%.7f)",radius,midLat,midLon);
    String q="[out:json][timeout:22];relation"+around+"[\"route\"=\"subway\"];(._;>;);out body;";
    JSONArray els=overpassJson(q,22000).optJSONArray("elements");
    if(els==null)return null;

    Map<Long,MetroNode> nodes=new HashMap<>();
    List<JSONObject> relations=new ArrayList<>();
    for(int i=0;i<els.length();i++){
      JSONObject e=els.optJSONObject(i); if(e==null)continue;
      String type=e.optString("type","");
      if("relation".equals(type)){relations.add(e);continue;}
      if(!"node".equals(type))continue;
      long id=e.optLong("id",-1); double la=e.optDouble("lat",Double.NaN),lo=e.optDouble("lon",Double.NaN);
      if(id<0||!Double.isFinite(la)||!Double.isFinite(lo))continue;
      JSONObject tags=e.optJSONObject("tags");
      String name="";
      boolean stationLike=false;
      if(tags!=null){
        name=tags.optString("name:fa",tags.optString("name",""));
        String railway=tags.optString("railway","");
        String pt=tags.optString("public_transport","");
        String subway=tags.optString("subway","");
        stationLike="station".equals(railway)||"halt".equals(railway)||"tram_stop".equals(railway)
            ||"station".equals(pt)||"platform".equals(pt)||"stop_position".equals(pt)
            ||"yes".equals(subway);
      }
      nodes.put(id,new MetroNode(id,name,la,lo,stationLike));
    }

    MetroNetwork net=new MetroNetwork();
    net.nodes.putAll(nodes);
    for(JSONObject rel:relations){
      JSONObject tags=rel.optJSONObject("tags");
      String line=tags==null?"":tags.optString("ref",tags.optString("name:fa",tags.optString("name","مترو")));
      JSONArray members=rel.optJSONArray("members"); if(members==null)continue;
      List<Long> ordered=new ArrayList<>();
      for(int j=0;j<members.length();j++){
        JSONObject m=members.optJSONObject(j); if(m==null||!"node".equals(m.optString("type")))continue;
        long ref=m.optLong("ref",-1); if(ref<0||!nodes.containsKey(ref))continue;
        String role=m.optString("role","");
        MetroNode n=nodes.get(ref);
        if(role.contains("stop")||role.contains("platform")||n.stationLike){
          n.stationLike=true;
          if(TextUtils.isEmpty(n.name))n.name="ایستگاه مترو";
          ordered.add(ref);
        }
      }
      Long prev=null;
      for(Long id:ordered){
        if(prev!=null && !prev.equals(id)){
          MetroNode a=nodes.get(prev),b=nodes.get(id);
          double d=haversine(a.lat,a.lon,b.lat,b.lon);
          int sec=Math.max(75,(int)Math.round(d/10.5d+35d));
          net.addEdge(prev,id,d,sec,line);
          net.addEdge(id,prev,d,sec,line);
        }
        prev=id;
      }
    }
    return net;
  }

  private static List<MetroNode> nearestMetroNodes(MetroNetwork n,double lat,double lon,double max,int limit){
    List<MetroNode> out=new ArrayList<>();
    for(MetroNode m:n.nodes.values()){
      if(!m.stationLike)continue;
      m.tempDistance=haversine(lat,lon,m.lat,m.lon);
      if(m.tempDistance<=max)out.add(m);
    }
    out.sort(Comparator.comparingDouble(x->x.tempDistance));
    if(out.size()>limit)return new ArrayList<>(out.subList(0,limit));
    return out;
  }

  private static MetroPath shortestMetroPath(MetroNetwork net,long start,long goal){
    if(start==goal)return new MetroPath(0,0d,1,0,"همان ایستگاه");
    PriorityQueue<MetroState> pq=new PriorityQueue<>(Comparator.comparingInt(x->x.seconds));
    Map<String,Integer> best=new HashMap<>();
    pq.add(new MetroState(start,"",0,0d,1,0,new ArrayList<>()));
    while(!pq.isEmpty()){
      MetroState s=pq.poll();
      String key=s.node+"|"+s.line;
      Integer old=best.get(key); if(old!=null&&old<=s.seconds)continue;
      best.put(key,s.seconds);
      if(s.node==goal){
        String summary=s.lines.isEmpty()?"مترو":TextUtils.join(" → ",s.lines);
        return new MetroPath(s.seconds,s.distanceM,s.stationCount,s.transfers,summary);
      }
      List<MetroEdge> edges=net.edges.get(s.node); if(edges==null)continue;
      for(MetroEdge e:edges){
        boolean transfer=!s.line.isEmpty()&&!s.line.equals(e.line);
        int add=e.seconds+(transfer?240:0);
        List<String> lines=new ArrayList<>(s.lines);
        if(lines.isEmpty()||!lines.get(lines.size()-1).equals(e.line))lines.add(e.line);
        pq.add(new MetroState(e.to,e.line,s.seconds+add,s.distanceM+e.distanceM,
            s.stationCount+1,s.transfers+(transfer?1:0),lines));
      }
    }
    return null;
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
          String quality = origin.hasAccuracy() && origin.getAccuracy() <= 25f ? "کیفیت GPS: خوب" :
                           origin.hasAccuracy() && origin.getAccuracy() <= 50f ? "کیفیت GPS: متوسط" : "کیفیت GPS: محدود";
          s.results.addView(text(a, quality + " • ترافیک زنده در این برآورد وجود ندارد.", 12, MUTED, Typeface.NORMAL));
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
    setStatus(s, "در حال مقایسه زمان، فاصله و هزینه تقریبی…", CYAN);
    new Thread(() -> {
      RoadEstimate car = null;
      MixedEstimate mixed = null;
      try { car = osrm(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon); } catch (Throwable ignored) {}
      if (prefs(a).getBoolean("use_metro", true)) {
        try { mixed = estimateMixed(a, origin, dest); } catch (Throwable ignored) {}
      }

      double direct = haversine(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon);
      int walkSec = direct <= 15_000d ? walkingSeconds(direct) : Integer.MAX_VALUE;
      RoadEstimate finalCar = car;
      MixedEstimate finalMixed = mixed;

      a.runOnUiThread(() -> {
        if (!alive(a, s)) return;
        s.results.removeAllViews();

        int fuelL100 = prefs(a).getInt("fuel_l100", 8);
        int fuelPrice = prefs(a).getInt("fuel_price_toman", 3000);
        int taxiBase = prefs(a).getInt("taxi_base_toman", 25000);
        int taxiPerKm = prefs(a).getInt("taxi_per_km_toman", 9000);
        int metroFare = prefs(a).getInt("metro_fare_toman", 6000);

        if (finalCar != null) {
          double liters = finalCar.distanceM / 100000d * fuelL100;
          long fuelCost = Math.round(liters * fuelPrice);
          s.results.addView(optionCard(a,
              "🚗 خودرو",
              formatMinutes(finalCar.durationSec) + " • " + formatDistance(finalCar.distanceM)
                  + " • سوخت ≈ " + formatToman(fuelCost),
              BLUE, () -> routeTo(a, dest, Router.Vehicle)));
          s.results.addView(text(a,
              "هزینه خودرو فقط سوخت است؛ عوارض، پارکینگ و ترافیک زنده در این عدد لحاظ نشده‌اند.",
              10, MUTED, Typeface.NORMAL));
        }

        if (finalMixed != null) {
          long cost = metroFare;
          if ("تاکسی".equals(finalMixed.accessMode))
            cost += taxiBase + Math.round((finalMixed.accessDistanceM / 1000d) * taxiPerKm);
          if ("تاکسی".equals(finalMixed.egressMode))
            cost += taxiBase + Math.round((finalMixed.egressDistanceM / 1000d) * taxiPerKm);
          s.results.addView(optionCard(a,
              "🚇 ترکیبی",
              formatMinutes(finalMixed.totalSec) + " • " + finalMixed.stationCount + " ایستگاه • "
                  + finalMixed.transfers + " تعویض • هزینه تنظیم‌شده ≈ " + formatToman(cost),
              GREEN, () -> buildMixedPlan(a, s, origin, dest)));
        }

        if (walkSec < Integer.MAX_VALUE) {
          s.results.addView(optionCard(a,
              "🚶 پیاده",
              "حدود " + formatMinutes(walkSec) + " • " + formatDistance(direct * 1.20d) + " • هزینه ۰",
              CYAN, () -> routeTo(a, dest, Router.Pedestrian)));
        }

        if (finalCar == null && finalMixed == null && walkSec == Integer.MAX_VALUE)
          setStatus(s, "هیچ گزینه قابل محاسبه‌ای آماده نشد.", AMBER);
        else
          setStatus(s, "مقایسه برای «" + dest.title + "» آماده است", GREEN);
      });
    }, "nv-v031-compare").start();
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
    Location loc = bestRecentLocation(a);
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
    Location loc = bestRecentLocation(a);
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
    Location loc = bestRecentLocation(a);
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
    Screen s = screen(a, "ترجیحات سفر هوشمند", "هزینه‌ها قابل ویرایش‌اند و فقط برای مقایسه تقریبی استفاده می‌شوند");
    SharedPreferences p = prefs(a);

    s.results.addView(text(a, "مصرف سوخت:", 14, WHITE, Typeface.BOLD));
    int[] fuel = {6, 8, 10, 12};
    LinearLayout fuelRow = new LinearLayout(a);
    fuelRow.setOrientation(LinearLayout.HORIZONTAL);
    fuelRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    for (int f : fuel) fuelRow.addView(smallButton(a, f + " L/100", p.getInt("fuel_l100",8)==f?GREEN:PANEL2,
        () -> p.edit().putInt("fuel_l100", f).apply()), weight(a));
    s.results.addView(fuelRow,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(a,44)));

    EditText fuelPrice = numberInput(a, "قیمت هر لیتر سوخت (تومان)", p.getInt("fuel_price_toman",3000));
    EditText taxiBase = numberInput(a, "هزینه پایه تاکسی (تومان)", p.getInt("taxi_base_toman",25000));
    EditText taxiKm = numberInput(a, "هزینه تاکسی به ازای هر km (تومان)", p.getInt("taxi_per_km_toman",9000));
    EditText metroFare = numberInput(a, "کرایه مترو (تومان)", p.getInt("metro_fare_toman",6000));
    s.results.addView(fuelPrice); s.results.addView(taxiBase); s.results.addView(taxiKm); s.results.addView(metroFare);

    s.results.addView(button(a, "ذخیره هزینه‌های تقریبی", GREEN, () -> {
      p.edit()
        .putInt("fuel_price_toman", parsePositiveInt(fuelPrice,3000))
        .putInt("taxi_base_toman", parsePositiveInt(taxiBase,25000))
        .putInt("taxi_per_km_toman", parsePositiveInt(taxiKm,9000))
        .putInt("metro_fare_toman", parsePositiveInt(metroFare,6000))
        .apply();
      Toast.makeText(a,"تنظیمات هزینه ذخیره شد",Toast.LENGTH_SHORT).show();
    }));
    s.results.addView(button(a, "تنظیم هشدارهای مسیر", BLUE, () -> openRouteAlerts(a)));
  }

  private static EditText numberInput(MwmActivity a,String hint,int value){
    EditText e=input(a,hint);
    e.setSingleLine(true);
    e.setInputType(InputType.TYPE_CLASS_NUMBER);
    e.setText(String.valueOf(value));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(a,54));
    lp.setMargins(0,dp(a,4),0,dp(a,4));
    e.setLayoutParams(lp);
    return e;
  }

  private static int parsePositiveInt(EditText e,int fallback){
    try{return Math.max(0,Integer.parseInt(e.getText().toString().trim()));}
    catch(Throwable ignored){return fallback;}
  }

  private static String formatToman(long value){
    return String.format(Locale.US,"%,d تومان",Math.max(0,value));
  }

  private static List<Place> geocodeRanked(String query, Location origin) throws Exception {
    Throwable first=null;
    try {
      List<Place> n=geocodeNominatim(query,origin);
      if(!n.isEmpty())return rankPlaces(query,n);
    } catch(Throwable e){first=e;}

    try {
      List<Place> p=geocodePhoton(query,origin);
      if(!p.isEmpty())return rankPlaces(query,p);
    } catch(Throwable e){
      if(first!=null)e.addSuppressed(first);
      throw e;
    }
    if(first instanceof Exception)throw (Exception)first;
    return new ArrayList<>();
  }

  private static List<Place> rankPlaces(String query,List<Place> out){
    String normalized=normalize(query);
    for(Place p:out)p.score=scorePlace(normalized,p);
    out.sort((p1,p2)->{
      int s=Integer.compare(p2.score,p1.score);
      if(s!=0)return s;
      return Double.compare(p1.distanceMeters<0?Double.MAX_VALUE:p1.distanceMeters,
                            p2.distanceMeters<0?Double.MAX_VALUE:p2.distanceMeters);
    });
    if(out.size()>10)return new ArrayList<>(out.subList(0,10));
    return out;
  }

  private static List<Place> geocodeNominatim(String query,Location origin) throws Exception {
    String normalized=normalize(query);
    String searchQ=query;
    if(containsAny(normalized,"راه آهن","راه اهن","راه‌آهن")&&!containsAny(normalized,"ایستگاه"))
      searchQ="ایستگاه "+query;

    StringBuilder u=new StringBuilder(
      "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=20&addressdetails=1&namedetails=1&extratags=1&accept-language=fa&q=")
      .append(URLEncoder.encode(searchQ,"UTF-8"));
    if(origin!=null){
      double dLat=2.2,dLon=2.5;
      u.append(String.format(Locale.US,"&viewbox=%.6f,%.6f,%.6f,%.6f",
          origin.getLongitude()-dLon,origin.getLatitude()+dLat,
          origin.getLongitude()+dLon,origin.getLatitude()-dLat));
    }

    HttpURLConnection conn=(HttpURLConnection)new URL(u.toString()).openConnection();
    conn.setConnectTimeout(8000);conn.setReadTimeout(12000);conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent","NV-Android/0.31");conn.setRequestProperty("Accept","application/json");
    int code=conn.getResponseCode();
    if(code<200||code>=300)throw new IllegalStateException("Nominatim HTTP "+code);
    JSONArray a=new JSONArray(readAll(conn.getInputStream()));conn.disconnect();

    List<Place> out=new ArrayList<>();
    for(int i=0;i<a.length();i++){
      JSONObject o=a.optJSONObject(i);if(o==null)continue;
      double lat=Double.parseDouble(o.getString("lat")),lon=Double.parseDouble(o.getString("lon"));
      String display=o.optString("display_name",""),title=o.optString("name","").trim();
      if(title.isEmpty()){
        JSONObject names=o.optJSONObject("namedetails");
        if(names!=null)title=names.optString("name:fa",names.optString("name","")).trim();
      }
      if(title.isEmpty()){int comma=display.indexOf(',');title=comma>0?display.substring(0,comma).trim():display;}
      String category=o.optString("category",""),type=o.optString("type","");
      double dist=origin==null?-1:haversine(origin.getLatitude(),origin.getLongitude(),lat,lon);
      out.add(new Place(title,display,lat,lon,dist,category,type,0));
    }
    return out;
  }

  private static List<Place> geocodePhoton(String query,Location origin) throws Exception {
    StringBuilder u=new StringBuilder("https://photon.komoot.io/api/?limit=15&q=")
        .append(URLEncoder.encode(query,"UTF-8"));
    if(origin!=null)u.append(String.format(Locale.US,"&lat=%.6f&lon=%.6f",origin.getLatitude(),origin.getLongitude()));

    HttpURLConnection conn=(HttpURLConnection)new URL(u.toString()).openConnection();
    conn.setConnectTimeout(8000);conn.setReadTimeout(12000);conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent","NV-Android/0.31");conn.setRequestProperty("Accept","application/json");
    int code=conn.getResponseCode();
    if(code<200||code>=300)throw new IllegalStateException("Photon HTTP "+code);
    JSONObject root=new JSONObject(readAll(conn.getInputStream()));conn.disconnect();
    JSONArray features=root.optJSONArray("features");
    List<Place> out=new ArrayList<>();
    if(features==null)return out;

    for(int i=0;i<features.length();i++){
      JSONObject f=features.optJSONObject(i);if(f==null)continue;
      JSONObject g=f.optJSONObject("geometry"),p=f.optJSONObject("properties");
      if(g==null||p==null)continue;
      JSONArray coords=g.optJSONArray("coordinates");
      if(coords==null||coords.length()<2)continue;
      double lon=coords.optDouble(0,Double.NaN),lat=coords.optDouble(1,Double.NaN);
      if(!Double.isFinite(lat)||!Double.isFinite(lon))continue;
      String title=p.optString("name","");
      String city=p.optString("city",p.optString("county",""));
      String state=p.optString("state","");
      String country=p.optString("country","");
      StringBuilder addr=new StringBuilder(title);
      if(!TextUtils.isEmpty(city))addr.append(", ").append(city);
      if(!TextUtils.isEmpty(state))addr.append(", ").append(state);
      if(!TextUtils.isEmpty(country))addr.append(", ").append(country);
      String category=p.optString("osm_key",""),type=p.optString("osm_value","");
      double dist=origin==null?-1:haversine(origin.getLatitude(),origin.getLongitude(),lat,lon);
      out.add(new Place(title,addr.toString(),lat,lon,dist,category,type,0));
    }
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
    String u=String.format(Locale.US,
        "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false&alternatives=true&steps=false",
        lon1,lat1,lon2,lat2);
    HttpURLConnection conn=(HttpURLConnection)new URL(u).openConnection();
    conn.setConnectTimeout(8000);conn.setReadTimeout(12000);conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent","NV-Android/0.31");
    int code=conn.getResponseCode();
    if(code<200||code>=300)throw new IllegalStateException("OSRM HTTP "+code);
    JSONObject root=new JSONObject(readAll(conn.getInputStream()));conn.disconnect();
    JSONArray routes=root.optJSONArray("routes");
    if(routes==null||routes.length()==0)throw new IllegalStateException("no route");
    JSONObject best=null;double bestDuration=Double.POSITIVE_INFINITY;
    for(int i=0;i<routes.length();i++){
      JSONObject rr=routes.optJSONObject(i);if(rr==null)continue;
      double d=rr.optDouble("duration",Double.POSITIVE_INFINITY);
      if(d>0&&d<bestDuration){bestDuration=d;best=rr;}
    }
    if(best==null)throw new IllegalStateException("invalid route");
    return new RoadEstimate(best.optDouble("distance",0),Math.max(1,(int)Math.round(bestDuration)));
  }

  private static boolean hasMetroNear(double lat,double lon,int radius) throws Exception { return !metroStations(lat,lon,radius).isEmpty(); }

  private static List<Place> metroStations(double lat,double lon,int radius) throws Exception {
    String around=String.format(Locale.US,"(around:%d,%.7f,%.7f)",radius,lat,lon);
    String q="[out:json][timeout:12];(node"+around+"[\"railway\"=\"station\"][\"station\"=\"subway\"];"
        +"node"+around+"[\"railway\"=\"station\"][\"subway\"=\"yes\"];"
        +"node"+around+"[\"public_transport\"=\"station\"][\"subway\"=\"yes\"];);out tags;";
    JSONArray els=overpassJson(q,14000).optJSONArray("elements");
    List<Place> out=new ArrayList<>(); if(els==null)return out;
    Set<String> seen=new HashSet<>();
    for(int i=0;i<els.length();i++){
      JSONObject e=els.optJSONObject(i);if(e==null)continue;
      double la=e.optDouble("lat",Double.NaN),lo=e.optDouble("lon",Double.NaN);
      if(!Double.isFinite(la)||!Double.isFinite(lo))continue;
      String key=String.format(Locale.US,"%.6f,%.6f",la,lo);if(!seen.add(key))continue;
      JSONObject tags=e.optJSONObject("tags");
      String name=tags==null?"":tags.optString("name:fa",tags.optString("name","ایستگاه مترو"));
      if(TextUtils.isEmpty(name))name="ایستگاه مترو";
      double d=haversine(lat,lon,la,lo);
      out.add(new Place(name,"ایستگاه مترو",la,lo,d,"railway","station",0));
    }
    out.sort(Comparator.comparingDouble(p->p.distanceMeters));
    if(out.size()>12)return new ArrayList<>(out.subList(0,12));
    return out;
  }

  private static List<Place> subwayEntrances(Place station, MapObject target) throws Exception {
    String around = String.format(Locale.US, "(around:%d,%.7f,%.7f)", 700, station.lat, station.lon);
    String q = "[out:json][timeout:12];(node" + around + "[\"railway\"=\"subway_entrance\"];);out tags;";
    JSONArray els = overpassJson(q, 14000).optJSONArray("elements");

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
    JSONArray els = overpassJson(q, 14000).optJSONArray("elements");

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

  private static void routeBetween(MwmActivity a, Place from, Place to, Router router) {
    if (from == null || to == null) return;
    MapObject start=MapObject.createMapObject(MapObject.SEARCH,from.title,from.address,from.lat,from.lon);
    MapObject end=MapObject.createMapObject(MapObject.SEARCH,to.title,to.address,to.lat,to.lon);
    removeScreen(a);
    RoutingController.get().prepare(start,end,router);
  }

  private static void routeTo(MwmActivity a, Place p, Router router) {
    Location loc=bestRecentLocation(a);
    if(!freshEnough(loc)){Toast.makeText(a,"GPS برای شروع مسیر کافی نیست",Toast.LENGTH_LONG).show();NvRuntimeController.showLocationStatus(a);return;}

    if (router == Router.Vehicle) {
      boolean avoidHighways = prefs(a).getBoolean("avoid_highways", false);
      boolean safer = prefs(a).getBoolean("safer_route", true);
      if (avoidHighways) RoutingOptions.addOption(RoadType.Motorway);
      else RoutingOptions.removeOption(RoadType.Motorway);
      // Organic Maps has no generic "safe route" score. The concrete supported
      // safety-related option we can apply is avoiding dirty/unpaved roads.
      if (safer) RoutingOptions.addOption(RoadType.Dirty);
      else RoutingOptions.removeOption(RoadType.Dirty);
    }

    MapObject start=MapObject.createMapObject(MapObject.MY_POSITION,"موقعیت من","",loc.getLatitude(),loc.getLongitude());
    MapObject end=MapObject.createMapObject(MapObject.SEARCH,p.title,p.address,p.lat,p.lon);
    removeScreen(a);
    RoutingController.get().prepare(start,end,router);
  }

  private static boolean freshEnough(Location l){return l!=null&&System.currentTimeMillis()-l.getTime()<=FRESH_ROUTE_MS&&l.hasAccuracy()&&l.getAccuracy()<=MAX_ROUTE_ACCURACY;}
  private static boolean looksLikeTrip(String s){
    String q=normalize(s);
    return containsAny(q,"میخوام","می خوام","می‌خوام","برم","برو","حرکت کن","مسیریابی","عجله",
        "مسیر ترکیبی","پیاده","با مترو","با تاکسی","از "," به ");
  }

  private static String extractDestination(String raw){
    String q=normalize(raw);

    // Prefer an explicit destination after "به".
    Matcher m=Pattern.compile("(?:^|\\s)به\\s+(.+)$").matcher(q);
    if(m.find()){
      String tail=cleanDestinationNoise(m.group(1));
      if(tail.length()>=2)return tail;
    }

    // Common Persian trip forms: "برم X", "برو X", "مقصد X".
    m=Pattern.compile("(?:برم|برو|مقصد(?:م)?|سمت)\\s+(.+)$").matcher(q);
    if(m.find()){
      String tail=cleanDestinationNoise(m.group(1));
      if(tail.length()>=2)return tail;
    }

    return cleanDestinationNoise(q);
  }

  private static String cleanDestinationNoise(String input){
    String q=normalize(input);
    String[] noise={
      "می‌خوام برم","میخوام برم","می خوام برم","می خواهم بروم","میخواهم بروم",
      "لطفا","لطفاً","خواهش میکنم","خواهش می‌کنم",
      "عجله دارم","عجله","خیلی سریع","سریع","زود","فوری",
      "مسیر ترکیبی","ترکیبی","با مترو","مترو","حمل و نقل عمومی","حمل‌ونقل عمومی",
      "با تاکسی","تاکسی","با اسنپ","اسنپ","با ماشین","ماشین","با خودرو","خودرو",
      "پیاده","راه برو","مسیریابی کن","مسیر بده","راهنمایی کن"
    };
    for(String n:noise) q=q.replace(n," ");
    q=q.replaceAll("\\s+"," ").trim();
    return q;
  }
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
  private static final class MixedEstimate {
    final Place fromStation, toStation;
    final double accessDistanceM, metroDistanceM, egressDistanceM;
    final int accessSec, metroSec, egressSec, totalSec, stationCount, transfers;
    final String accessMode, egressMode, lineSummary;
    MixedEstimate(Place f, Place t, double ad, int as, double md, int ms, double ed, int es,
                  int total, String am, String em, String lines, int stations, int transferCount) {
      fromStation=f; toStation=t; accessDistanceM=ad; accessSec=as;
      metroDistanceM=md; metroSec=ms; egressDistanceM=ed; egressSec=es;
      totalSec=total; accessMode=am; egressMode=em; lineSummary=lines;
      stationCount=stations; transfers=transferCount;
    }
  }
  private static final class MetroNode {
    final long id; String name; final double lat,lon; boolean stationLike; double tempDistance;
    MetroNode(long i,String n,double a,double o,boolean s){id=i;name=n;lat=a;lon=o;stationLike=s;}
  }
  private static final class MetroEdge {
    final long to; final double distanceM; final int seconds; final String line;
    MetroEdge(long t,double d,int s,String l){to=t;distanceM=d;seconds=s;line=TextUtils.isEmpty(l)?"مترو":l;}
  }
  private static final class MetroNetwork {
    final Map<Long,MetroNode> nodes=new HashMap<>();
    final Map<Long,List<MetroEdge>> edges=new HashMap<>();
    void addEdge(long f,long t,double d,int s,String l){
      edges.computeIfAbsent(f,k->new ArrayList<>()).add(new MetroEdge(t,d,s,l));
    }
  }
  private static final class MetroState {
    final long node; final String line; final int seconds,stationCount,transfers;
    final double distanceM; final List<String> lines;
    MetroState(long n,String l,int s,double d,int st,int tr,List<String> ls){
      node=n;line=l;seconds=s;distanceM=d;stationCount=st;transfers=tr;lines=ls;
    }
  }
  private static final class MetroPath {
    final int seconds,stationCount,transfers; final double distanceM; final String lineSummary;
    MetroPath(int s,double d,int st,int tr,String l){seconds=s;distanceM=d;stationCount=st;transfers=tr;lineSummary=l;}
  }
}
