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
 * NV v0.33 modern route and navigation layer.
 *
 * Goals:
 * - rank Persian search results semantically instead of taking the nearest text match;
 * - separate railway-station search from metro/transit routing;
 * - gracefully fall back when a mixed/metro trip is not actually possible;
 * - expose a live NV ETA chip that blends the route engine ETA with real device speed;
 * - give every advanced menu entry a distinct, observable action;
 * - keep claims honest: no fabricated live metro positions or fake traffic data.
 */
public final class NvV032Actions implements DefaultLifecycleObserver {
  private static final int BG = Color.rgb(15, 23, 42);
  private static final int PANEL = Color.rgb(22, 34, 53);
  private static final int PANEL2 = Color.rgb(30, 41, 59);
  private static final int OUTLINE = Color.rgb(71, 85, 105);
  private static final int WHITE = Color.WHITE;
  private static final int MUTED = Color.rgb(203, 213, 225);
  private static final int CYAN = Color.rgb(14, 165, 233);
  private static final int BLUE = Color.rgb(59, 130, 246);
  private static final int PURPLE = Color.rgb(139, 92, 246);
  private static final int GREEN = Color.rgb(34, 197, 94);
  private static final int AMBER = Color.rgb(249, 115, 22);
  private static final int RED = Color.rgb(239, 68, 68);
  private static final int PLANNER_BG = Color.rgb(246, 248, 251);
  private static final int PLANNER_CARD = Color.WHITE;
  private static final int PLANNER_SOFT = Color.rgb(239, 243, 248);
  private static final int PLANNER_TEXT = Color.rgb(20, 29, 43);
  private static final int PLANNER_MUTED = Color.rgb(100, 116, 139);
  private static final int PLANNER_BORDER = Color.rgb(224, 230, 238);
  private static final int PLANNER_BLUE = Color.rgb(45, 110, 245);
  private static final int PLANNER_ORANGE = Color.rgb(255, 111, 0);
  private static final String SCREEN_TAG = "nv-v032-screen";
  private static final String ETA_TAG = "nv-v032-eta-chip";
  private static final String PREFS = "nv_v032";
  private static final Map<MwmActivity, NvV032Actions> INSTANCES = new WeakHashMap<>();
  private static final Map<MwmActivity, RoutePlannerState> ROUTE_PLANNERS = new WeakHashMap<>();

  private final MwmActivity activity;
  private final ViewGroup host;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final LocationHelper locationHelper;
  private final TextView etaChip;
  private final TextView mixedTripChip;
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
  private int smoothedEtaSec = -1;
  private static final long ONLINE_ETA_TTL_MS = 30_000L;
  private static final double ONLINE_ETA_REFRESH_MOVE_M = 500d;

  private final Runnable poll = new Runnable() {
    @Override public void run() {
      if (destroyed) return;
      updateEtaAndAlerts();
      updateMixedTripChip();
      handler.postDelayed(this, 1000L);
    }
  };

  private NvV032Actions(MwmActivity a) {
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
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(a, 340), dp(a, 58), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      lp.setMargins(0, dp(a, 148), 0, 0);
      host.addView(etaChip, lp);
    }

    mixedTripChip = text(a, "", 12, WHITE, Typeface.BOLD);
    mixedTripChip.setGravity(Gravity.CENTER);
    mixedTripChip.setPadding(dp(a, 10), dp(a, 6), dp(a, 10), dp(a, 6));
    mixedTripChip.setBackground(round(a, Color.argb(245, 7, 45, 72), GREEN, 14));
    mixedTripChip.setElevation(dp(a, 12));
    mixedTripChip.setVisibility(View.GONE);
    mixedTripChip.setClickable(true);
    mixedTripChip.setOnClickListener(v -> continueMixedSession());
    if (host != null) {
      FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(a, 300), dp(a, 48), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      mp.setMargins(0, dp(a, 202), 0, 0);
      host.addView(mixedTripChip, mp);
    }

    a.getLifecycle().addObserver(this);
    handler.post(poll);
  }

  public static void install(MwmActivity a) {
    if (INSTANCES.containsKey(a)) return;
    migratePreferences(a);
    INSTANCES.put(a, new NvV032Actions(a));
  }

  private static void migratePreferences(Context c) {
    SharedPreferences current = prefs(c);
    if (current.getBoolean("_migrated_v031", false)) return;
    SharedPreferences old = c.getSharedPreferences("nv_v031", Context.MODE_PRIVATE);
    SharedPreferences.Editor e = current.edit();
    for (Map.Entry<String, ?> item : old.getAll().entrySet()) {
      String k = item.getKey();
      Object v = item.getValue();
      if (v instanceof Boolean) e.putBoolean(k, (Boolean)v);
      else if (v instanceof Integer) e.putInt(k, (Integer)v);
      else if (v instanceof Long) e.putLong(k, (Long)v);
      else if (v instanceof Float) e.putFloat(k, (Float)v);
      else if (v instanceof String) e.putString(k, (String)v);
    }
    e.putBoolean("_migrated_v031", true).apply();
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
      smoothedEtaSec = -1;
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
      boolean onlineAllowed = onlineServicesEnabled(activity);
      String source = onlineEtaFresh() ? "NV جاده‌ای" : "NV آفلاین";
      int low = Math.max(60, (int)Math.round(nv * (onlineEtaFresh() ? 0.86d : 0.80d)));
      int high = Math.max(low + 60, (int)Math.round(nv * (onlineEtaFresh() ? 1.22d : 1.35d)));
      String line1 = source + "  " + formatMinutes(nv) + (meters > 0 ? "  •  " + formatDistance(meters) : "");
      String line2 = onlineAllowed
          ? "بازه بدون ترافیک زنده: " + formatMinutes(low) + " تا " + formatMinutes(high)
          : "حالت خصوصی • تخمین آفلاین";
      etaChip.setText(line1 + "\n" + line2);
      etaChip.setMaxLines(2);
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
      if (p.getBoolean("gps_alert", true) && loc != null && loc.hasAccuracy() && loc.getAccuracy() > NvLocationPolicy.GOOD_ACCURACY_M
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
    if (loc.hasAccuracy() && loc.getAccuracy() > NvLocationPolicy.GOOD_ACCURACY_M) return;
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
    if (!onlineServicesEnabled(activity)) return;
    if (loc == null || endPoint == null || !RoutingController.get().isVehicleRouterType()) return;
    if (loc.hasAccuracy() && loc.getAccuracy() > NvLocationPolicy.NEARBY_ACCURACY_M) return;

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
    }, "nv-v032-online-eta").start();
  }

  private int correctedActiveEta(int engineSec, double remainingMeters) {
    int base = Math.max(60, engineSec);

    if (onlineEtaFresh() && onlineEtaSec > 0) {
      int onlineRemaining = onlineEtaSec;
      if (remainingMeters > 0d && onlineEtaDistanceM > 500d) {
        double progressRatio = remainingMeters / onlineEtaDistanceM;
        progressRatio = Math.max(0.04d, Math.min(1.12d, progressRatio));
        onlineRemaining = Math.max(60, (int)Math.round(onlineEtaSec * progressRatio));
      }

      double ratio = engineSec / (double) onlineRemaining;
      if (ratio > 1.55d || ratio < 0.64d)
        base = onlineRemaining;
      else
        base = (int)Math.round(onlineRemaining * 0.68d + engineSec * 0.32d);
    }

    // GPS speed affects only the final urban portion, using a rolling median rather than one sample.
    if (remainingMeters > 0d && remainingMeters <= 12_000d) {
      double median = medianSpeedMps();
      if (median >= 2.5d) {
        double speedEta = remainingMeters / median;
        speedEta = Math.max(base * 0.82d, Math.min(base * 1.20d, speedEta));
        base = (int)Math.round(base * 0.88d + speedEta * 0.12d);
      }
    }

    base = Math.max(60, base);
    if (smoothedEtaSec <= 0) {
      smoothedEtaSec = base;
    } else {
      int jump = Math.abs(base - smoothedEtaSec);
      int resetThreshold = Math.max(8 * 60, (int)Math.round(smoothedEtaSec * 0.35d));
      if (jump >= resetThreshold)
        smoothedEtaSec = base;
      else
        smoothedEtaSec = (int)Math.round(smoothedEtaSec * 0.72d + base * 0.28d);
    }
    return Math.max(60, smoothedEtaSec);
  }

  private void updateMixedTripChip() {
    SharedPreferences p = prefs(activity);
    if (!p.getBoolean("mixed_active", false)) {
      mixedTripChip.setVisibility(View.GONE);
      return;
    }

    Location loc = locationHelper.getSavedLocation();
    if (loc == null) {
      mixedTripChip.setText("NV سفر ترکیبی • GPS لازم است");
      mixedTripChip.setVisibility(View.VISIBLE);
      return;
    }

    int stage = p.getInt("mixed_stage", 1);
    if (stage == 1 && p.getBoolean("mixed_origin_explicit", false)) {
      Place plannedOrigin = placeFromSession(p, "mixed_origin", "مبدأ انتخابی");
      if (Double.isFinite(plannedOrigin.lat)) {
        double fromPhone = haversine(loc.getLatitude(), loc.getLongitude(), plannedOrigin.lat, plannedOrigin.lon);
        if (fromPhone > 1000d) {
          mixedTripChip.setText("NV سفر ترکیبی • برنامه از " + plannedOrigin.title + " • برای نمایش مرحله ۱ بزنید");
          mixedTripChip.setVisibility(View.VISIBLE);
          return;
        }
      }
    }
    Place from = placeFromSession(p, "mixed_from", "ایستگاه مترو");
    Place to = placeFromSession(p, "mixed_to", "ایستگاه مقصد");
    Place dest = placeFromSession(p, "mixed_dest", "مقصد");
    if (!Double.isFinite(from.lat) || !Double.isFinite(to.lat) || !Double.isFinite(dest.lat)) {
      clearMixedSession();
      return;
    }

    double dFrom = haversine(loc.getLatitude(), loc.getLongitude(), from.lat, from.lon);
    double dTo = haversine(loc.getLatitude(), loc.getLongitude(), to.lat, to.lon);
    double dDest = haversine(loc.getLatitude(), loc.getLongitude(), dest.lat, dest.lon);

    if (stage == 3 && dDest <= 150d) {
      clearMixedSession();
      Toast.makeText(activity, "NV: به مقصد سفر ترکیبی رسیدید", Toast.LENGTH_LONG).show();
      return;
    }

    String label;
    if (stage == 1) {
      label = dFrom <= 300d
          ? "به " + from.title + " رسیدید • برای مترو بزنید"
          : "مرحله ۱ • تا " + from.title + " • " + formatDistance(dFrom);
    } else if (stage == 2) {
      label = dTo <= 400d
          ? "به " + to.title + " رسیدید • برای ادامه بزنید"
          : "مرحله ۲ • مترو تا " + to.title;
    } else {
      label = "مرحله ۳ • تا مقصد • " + formatDistance(dDest);
    }

    mixedTripChip.setText("NV سفر ترکیبی • " + label);
    mixedTripChip.setVisibility(View.VISIBLE);
  }

  private void continueMixedSession() {
    SharedPreferences p = prefs(activity);
    if (!p.getBoolean("mixed_active", false)) return;
    Location loc = locationHelper.getSavedLocation();
    if (loc == null) {
      Toast.makeText(activity, "GPS برای ادامه سفر لازم است", Toast.LENGTH_LONG).show();
      return;
    }

    int stage = p.getInt("mixed_stage", 1);
    Place from = placeFromSession(p, "mixed_from", "ایستگاه مترو");
    Place to = placeFromSession(p, "mixed_to", "ایستگاه مقصد");
    Place dest = placeFromSession(p, "mixed_dest", "مقصد");

    if (stage == 1) {
      if (p.getBoolean("mixed_origin_explicit", false)) {
        Place plannedOrigin = placeFromSession(p, "mixed_origin", "مبدأ انتخابی");
        if (Double.isFinite(plannedOrigin.lat)) {
          double fromPhone = haversine(loc.getLatitude(), loc.getLongitude(), plannedOrigin.lat, plannedOrigin.lon);
          if (fromPhone > 1000d) {
            Router first = "تاکسی".equals(p.getString("mixed_access_mode", "پیاده"))
                ? Router.Vehicle : Router.Pedestrian;
            routeBetween(activity, locationFromPlace(plannedOrigin), from, first);
            return;
          }
        }
      }
      double d = haversine(loc.getLatitude(), loc.getLongitude(), from.lat, from.lon);
      if (d <= 300d) {
        p.edit().putInt("mixed_stage", 2).apply();
        routeTo(activity, to, Router.Transit);
      } else {
        String mode = p.getString("mixed_access_mode", "پیاده");
        routeTo(activity, from, "تاکسی".equals(mode) ? Router.Vehicle : Router.Pedestrian);
      }
    } else if (stage == 2) {
      double d = haversine(loc.getLatitude(), loc.getLongitude(), to.lat, to.lon);
      if (d <= 400d) {
        p.edit().putInt("mixed_stage", 3).apply();
        String mode = p.getString("mixed_egress_mode", "پیاده");
        routeTo(activity, dest, "تاکسی".equals(mode) ? Router.Vehicle : Router.Pedestrian);
      } else {
        routeTo(activity, to, Router.Transit);
      }
    } else {
      String mode = p.getString("mixed_egress_mode", "پیاده");
      routeTo(activity, dest, "تاکسی".equals(mode) ? Router.Vehicle : Router.Pedestrian);
    }
  }

  private static Place placeFromSession(SharedPreferences p, String prefix, String fallback) {
    double lat = Double.longBitsToDouble(p.getLong(prefix + "_lat", Double.doubleToLongBits(Double.NaN)));
    double lon = Double.longBitsToDouble(p.getLong(prefix + "_lon", Double.doubleToLongBits(Double.NaN)));
    String name = p.getString(prefix + "_name", fallback);
    String address = p.getString(prefix + "_address", "");
    return new Place(name, address, lat, lon, -1d, "", "", 0);
  }

  private void clearMixedSession() {
    prefs(activity).edit()
        .remove("mixed_active").remove("mixed_stage")
        .remove("mixed_from_lat").remove("mixed_from_lon").remove("mixed_from_name").remove("mixed_from_address")
        .remove("mixed_to_lat").remove("mixed_to_lon").remove("mixed_to_name").remove("mixed_to_address")
        .remove("mixed_dest_lat").remove("mixed_dest_lon").remove("mixed_dest_name").remove("mixed_dest_address")
        .remove("mixed_access_mode").remove("mixed_egress_mode")
        .remove("mixed_origin_explicit")
        .remove("mixed_origin_lat").remove("mixed_origin_lon").remove("mixed_origin_name").remove("mixed_origin_address")
        .apply();
    mixedTripChip.setVisibility(View.GONE);
  }

  private static void startMixedSession(MwmActivity a, Location origin, MixedEstimate m, Place dest) {
    SharedPreferences.Editor e = prefs(a).edit();
    e.putBoolean("mixed_active", true).putInt("mixed_stage", 1);
    putSessionPlace(e, "mixed_from", m.fromStation);
    putSessionPlace(e, "mixed_to", m.toStation);
    putSessionPlace(e, "mixed_dest", dest);
    e.putString("mixed_access_mode", m.accessMode);
    e.putString("mixed_egress_mode", m.egressMode);
    e.putBoolean("mixed_origin_explicit", isExplicitOrigin(origin));
    if (isExplicitOrigin(origin)) {
      Place plannedOrigin = new Place(originTitle(origin), "مبدأ جستجوشده",
          origin.getLatitude(), origin.getLongitude(), -1d, "", "", 0);
      putSessionPlace(e, "mixed_origin", plannedOrigin);
    }
    e.apply();

    Router first = "تاکسی".equals(m.accessMode) ? Router.Vehicle : Router.Pedestrian;
    routeBetween(a, origin, m.fromStation, first);
  }

  private static void putSessionPlace(SharedPreferences.Editor e, String prefix, Place p) {
    e.putLong(prefix + "_lat", Double.doubleToRawLongBits(p.lat));
    e.putLong(prefix + "_lon", Double.doubleToRawLongBits(p.lon));
    e.putString(prefix + "_name", p.title);
    e.putString(prefix + "_address", p.address);
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

  public static void openSmartSearch(MwmActivity a) { openRoutePlanner(a); }
  public static void openPlaceDetails(MwmActivity a) { openSearch(a, true); }

  private static void openSearch(MwmActivity a, boolean detailMode) {
    Screen s = screen(a, detailMode ? "جزئیات مکان" : "جستجوی هوشمند NV",
        detailMode ? "مکان را جستجو کنید؛ نتیجه بر اساس نام، نوع مکان، شهر و فاصله رتبه‌بندی می‌شود"
                   : "مبدأ و مقصد را از متن می‌فهمد؛ سپس خودرو و مسیر ترکیبی را مقایسه می‌کند");
    EditText input = input(a, detailMode ? "مثال: ایستگاه راه‌آهن یزد" : "مثال: از میدان انقلاب برم میدان ونک");
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
    if (!onlineServicesEnabled(a)) {
      setStatus(s, "حالت خصوصی فعال است؛ جستجوی آنلاین غیرفعال است.", AMBER);
      s.results.removeAllViews();
      s.results.addView(button(a, "جستجو با موتور داخلی نقشه", BLUE,
          () -> { removeScreen(a); NvRuntimeController.openSearch(a, query); }));
      return;
    }
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
    }, "nv-v032-search").start();
  }

  private static void showPlace(MwmActivity a, Place p) {
    Screen s = screen(a, p.title, "جزئیات مکان انتخاب‌شده");
    s.results.addView(text(a, p.address, 14, WHITE, Typeface.NORMAL));
    s.results.addView(text(a, String.format(Locale.US, "مختصات: %.6f, %.6f", p.lat, p.lon), 13, CYAN, Typeface.NORMAL));
    s.results.addView(button(a, "مسیر خودرو", BLUE, () -> routeTo(a, p, Router.Vehicle)));
    s.results.addView(button(a, "مسیر پیاده", CYAN, () -> routeTo(a, p, Router.Pedestrian)));
    s.results.addView(button(a, "نمایش روی نقشه", GREEN, () -> { removeScreen(a); Framework.nativeSetViewportCenter(p.lat, p.lon, 17); }));
  }

  public static void openRoutePlanner(MwmActivity a) {
    RoutePlannerState state = new RoutePlannerState();
    ROUTE_PLANNERS.put(a, state);
    renderRoutePlanner(a, state);
  }

  private static void renderRoutePlanner(MwmActivity a, RoutePlannerState state) {
    Screen s = plannerScreen(a, "مسیریابی", "دو نقطه را مشخص کنید؛ NV بقیه مسیرها را مقایسه می‌کند");
    s.results.removeAllViews();

    s.results.addView(endpointCard(
        a,
        "مبدأ",
        state.origin == null ? "هنوز انتخاب نشده" : state.originLabel,
        PURPLE,
        () -> pickPlannerPointOnMap(a, state, true)));

    s.results.addView(endpointCard(
        a,
        "مقصد",
        state.destination == null ? "هنوز انتخاب نشده" : state.destination.title,
        PLANNER_ORANGE,
        () -> pickPlannerPointOnMap(a, state, false)));

    if (state.origin == null || state.destination == null) {
      setStatus(s, "مبدأ و مقصد را در همان صفحه نقشه جستجو یا با حرکت نقشه انتخاب کنید.", CYAN);
      return;
    }

    setStatus(s, "مبدأ و مقصد مشخص شدند؛ همه پیشنهادهای مسیر در حال محاسبه‌اند…", GREEN);
    compareModes(a, s, state.origin, state.destination);
  }

  private static View endpointCard(MwmActivity a, String title, String value, int accent,
                                   Runnable action) {
    LinearLayout card = new LinearLayout(a);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(a, 14), dp(a, 13), dp(a, 14), dp(a, 12));
    card.setBackground(round(a, PLANNER_CARD, PLANNER_BORDER, 22));
    card.setElevation(dp(a, 2));

    LinearLayout head = new LinearLayout(a);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

    String icon = "مبدأ".equals(title) ? "●" : "⚑";
    TextView badge = text(a, icon, "مبدأ".equals(title) ? 25 : 30, accent, Typeface.BOLD);
    badge.setGravity(Gravity.CENTER);
    badge.setBackground(round(a,
        Color.argb(18, Color.red(accent), Color.green(accent), Color.blue(accent)),
        Color.TRANSPARENT, 24));
    head.addView(badge, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));

    LinearLayout copy = new LinearLayout(a);
    copy.setOrientation(LinearLayout.VERTICAL);
    copy.setPadding(dp(a, 10), 0, dp(a, 10), 0);
    copy.addView(text(a, title, 12, PLANNER_MUTED, Typeface.BOLD));
    TextView valueText = text(a, value, 16, PLANNER_TEXT, Typeface.BOLD);
    valueText.setMaxLines(2);
    copy.addView(valueText);
    head.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    TextView state = text(a,
        ("هنوز انتخاب نشده".equals(value) ? "انتخاب" : "تغییر"),
        11, accent, Typeface.BOLD);
    state.setGravity(Gravity.CENTER);
    head.addView(state, new LinearLayout.LayoutParams(dp(a, 54), dp(a, 44)));
    card.addView(head);

    TextView choose = plannerMiniButton(
        a,
        "⌕  جستجو یا انتخاب روی نقشه",
        accent,
        true,
        action);
    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 48));
    rp.setMargins(0, dp(a, 8), 0, 0);
    card.addView(choose, rp);

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(dp(a, 2), dp(a, 7), dp(a, 2), dp(a, 7));
    card.setLayoutParams(lp);
    return card;
  }

  private static void openEndpointSearch(MwmActivity a, RoutePlannerState state, boolean originPoint) {
    pickPlannerPointOnMap(a, state, originPoint);
  }

  private static void jumpSearchResultToMap(MwmActivity a, RoutePlannerState state,
                                            boolean originPoint, Place p) {
    removeScreen(a);
    try {
      Framework.nativeSetViewportCenter(p.lat, p.lon, 17);
    } catch (Throwable ignored) {}

    // Give the map one frame to move before drawing the fixed center marker/flag.
    new Handler(Looper.getMainLooper()).postDelayed(() ->
        NvRuntimeController.selectPointOnMap(
            a,
            originPoint ? "تأیید مبدأ" : "تأیید مقصد",
            (originPoint ? "نشانگر بنفش روی «" : "پرچم نارنجی روی «")
                + p.title + "» قرار گرفت؛ در صورت نیاز نقشه را کمی جابه‌جا کنید",
            originPoint ? "تأیید این مبدأ" : "تأیید این مقصد",
            originPoint,
            plannerMapSearch(a, state, originPoint),
            (lat, lon, address) -> {
              String fallback = !TextUtils.isEmpty(p.title)
                  ? p.title
                  : (originPoint ? "مبدأ جستجوشده" : "مقصد جستجوشده");
              String label = TextUtils.isEmpty(address) ? fallback : address;

              if (originPoint) {
                state.origin = mapSelectionLocation(lat, lon, label);
                state.originLabel = fallback;
              } else {
                double d = state.origin == null ? -1d
                    : haversine(state.origin.getLatitude(), state.origin.getLongitude(), lat, lon);
                state.destination = new Place(
                    fallback,
                    TextUtils.isEmpty(address) ? p.address : address,
                    lat, lon, d, "search-map", "confirmed", 1000);
              }
              renderRoutePlanner(a, state);
            }),
        120L);
  }

  private static NvRuntimeController.MapPointSearchListener plannerMapSearch(
      MwmActivity a, RoutePlannerState state, boolean originPoint) {
    return (query, callback) -> {
      if (!onlineServicesEnabled(a)) {
        callback.onResults(new ArrayList<>(), "برای جستجو، خدمات آنلاین باید روشن باشد");
        return;
      }

      new Thread(() -> {
        try {
          Location bias = originPoint ? null : state.origin;
          List<Place> found = geocodeRanked(query, bias);
          List<NvRuntimeController.MapPointSearchResult> out = new ArrayList<>();
          int count = Math.min(6, found.size());
          for (int i = 0; i < count; i++) {
            Place p = found.get(i);
            String subtitle = p.address;
            if (p.distanceMeters >= 0 && Double.isFinite(p.distanceMeters)) {
              subtitle += (subtitle.isEmpty() ? "" : " • ") + formatDistance(p.distanceMeters);
            }
            out.add(new NvRuntimeController.MapPointSearchResult(
                p.title, subtitle, p.lat, p.lon));
          }
          callback.onResults(out, out.isEmpty() ? "نتیجه‌ای پیدا نشد" : "");
        } catch (Throwable e) {
          callback.onResults(new ArrayList<>(), "جستجو پاسخ نداد؛ دوباره تلاش کنید");
        }
      }, originPoint ? "nv-map-origin-search" : "nv-map-destination-search").start();
    };
  }

  private static void pickPlannerPointOnMap(MwmActivity a, RoutePlannerState state, boolean originPoint) {
    removeScreen(a);
    NvRuntimeController.selectPointOnMap(
        a,
        originPoint ? "انتخاب مبدأ" : "انتخاب مقصد",
        "نقشه را جابه‌جا کنید؛ نشانگر وسط ثابت می‌ماند",
        originPoint ? "تأیید مبدأ" : "تأیید مقصد",
        originPoint,
        plannerMapSearch(a, state, originPoint),
        (lat, lon, address) -> {
          String label = TextUtils.isEmpty(address)
              ? (originPoint ? "مبدأ انتخاب‌شده روی نقشه" : "مقصد انتخاب‌شده روی نقشه")
              : address;
          if (originPoint) {
            state.origin = mapSelectionLocation(lat, lon, label);
            state.originLabel = label;
          } else {
            double d = state.origin == null ? -1d
                : haversine(state.origin.getLatitude(), state.origin.getLongitude(), lat, lon);
            state.destination = new Place(label, address == null ? "" : address,
                lat, lon, d, "map", "selected", 1000);
          }
          renderRoutePlanner(a, state);
        });
  }

  // Legacy smart-route entry points now open the same simple origin/destination planner.
  public static void openRouteMode(MwmActivity a) { openRoutePlanner(a); }
  public static void openChat(MwmActivity a) { openRoutePlanner(a); }
  public static void openHurry(MwmActivity a) { openRoutePlanner(a); }
  public static void openMixed(MwmActivity a) { openRoutePlanner(a); }
  public static void openEta(MwmActivity a) { openRoutePlanner(a); }
  public static void openTimeCost(MwmActivity a) { openRoutePlanner(a); }
  public static void openWalk(MwmActivity a) { openRoutePlanner(a); }
  public static void openCompareRoutes(MwmActivity a) { openRoutePlanner(a); }

  private static void openTripInput(MwmActivity a, Mode mode, String title, String subtitle) {
    Screen s = screen(a, title, subtitle);
    EditText input = input(a, "مثال: از میدان انقلاب برم میدان ونک");
    s.controls.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 72)));
    Runnable go = () -> {
      String q = input.getText().toString().trim();
      if (q.isEmpty()) { setStatus(s, "مقصد را بنویسید.", AMBER); return; }
      performTrip(a, s, q, mode);
    };
    s.controls.addView(button(a, mode == Mode.HURRY ? "پیدا کردن سریع‌ترین مسیر" : "محاسبه", mode == Mode.HURRY ? RED : GREEN, go));

    LinearLayout mapRow = new LinearLayout(a);
    mapRow.setOrientation(LinearLayout.HORIZONTAL);
    mapRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    mapRow.addView(smallButton(a, "مبدأ + مقصد روی نقشه", BLUE,
        () -> startMapEndpointFlow(a, mode, true)), weight(a));
    mapRow.addView(smallButton(a, "GPS من + مقصد روی نقشه", PANEL2,
        () -> startMapEndpointFlow(a, mode, false)), weight(a));
    s.controls.addView(mapRow, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 48)));

    input.setOnEditorActionListener((v, actionId, event) -> { if (actionId == EditorInfo.IME_ACTION_GO) { go.run(); return true; } return false; });
    input.requestFocus();
  }

  private static void startMapEndpointFlow(MwmActivity a, Mode mode, boolean chooseOriginOnMap) {
    removeScreen(a);

    if (!chooseOriginOnMap) {
      Location origin = MwmApplication.from(a).getLocationHelper().getSavedLocation();
      if (!planningLocationOkay(origin)) {
        Screen s = screen(a, "انتخاب مقصد روی نقشه",
            "برای استفاده از موقعیت فعلی، GPS تازه و دقیق لازم است");
        setStatus(s, "GPS مناسب در دسترس نیست. مبدأ را روی نقشه انتخاب کنید.", AMBER);
        s.results.addView(button(a, "انتخاب مبدأ روی نقشه", BLUE,
            () -> startMapEndpointFlow(a, mode, true)));
        s.results.addView(button(a, "بررسی وضعیت GPS", GREEN,
            () -> { removeScreen(a); NvRuntimeController.showLocationStatus(a); }));
        return;
      }
      pickDestinationOnMap(a, mode, origin);
      return;
    }

    NvRuntimeController.selectPointOnMap(
        a,
        "انتخاب مبدأ",
        "نقشه را حرکت دهید؛ نشانگر بنفش دقیقاً مبدأ سفر خواهد بود",
        "تأیید مبدأ و انتخاب مقصد",
        true,
        (lat, lon, address) -> {
          Location origin = mapSelectionLocation(lat, lon,
              TextUtils.isEmpty(address) ? "مبدأ انتخاب‌شده روی نقشه" : address);
          try { Framework.nativeSetViewportCenter(lat, lon, 16); } catch (Throwable ignored) {}
          pickDestinationOnMap(a, mode, origin);
        });
  }

  private static void pickDestinationOnMap(MwmActivity a, Mode mode, Location origin) {
    NvRuntimeController.selectPointOnMap(
        a,
        "انتخاب مقصد",
        "نقشه را حرکت دهید؛ پرچم نارنجی دقیقاً مقصد سفر خواهد بود",
        "تأیید مقصد و بررسی مسیرها",
        false,
        (lat, lon, address) -> {
          String title = TextUtils.isEmpty(address) ? "مقصد انتخاب‌شده روی نقشه" : address;
          double distance = haversine(origin.getLatitude(), origin.getLongitude(), lat, lon);
          Place destination = new Place(title, address == null ? "" : address,
              lat, lon, distance, "map", "selected", 1000);

          Screen s = screen(a, "مسیر انتخاب‌شده روی نقشه",
              "مبدأ و مقصد تأیید شدند؛ گزینه‌های سفر در حال بررسی هستند");
          setStatus(s, "مبدأ: " + originTitle(origin) + " • مقصد: " + title, GREEN);
          dispatchResolvedTrip(a, s, "", mode, origin, destination);
        });
  }

  private static Location mapSelectionLocation(double lat, double lon, String label) {
    Location l = new Location("NV_MAP:" + (TextUtils.isEmpty(label) ? "مبدأ روی نقشه" : label));
    l.setLatitude(lat);
    l.setLongitude(lon);
    l.setAccuracy(5f);
    l.setTime(System.currentTimeMillis());
    return l;
  }

  private static void performTrip(MwmActivity a, Screen s, String raw, Mode mode) {
    String destination = extractDestination(raw);
    String originQuery = extractOrigin(raw);
    if (destination.isEmpty()) { setStatus(s, "نام مقصد از جمله مشخص نشد.", AMBER); return; }

    if (!onlineServicesEnabled(a)) {
      setStatus(s, originQuery.isEmpty()
          ? "حالت خصوصی فعال است؛ جستجوی آنلاین مقصد غیرفعال است."
          : "برای تبدیل مبدأ و مقصد جستجوشده به مختصات، خدمات آنلاین باید فعال باشد.", AMBER);
      s.results.removeAllViews();
      s.results.addView(button(a, "جستجو با موتور داخلی نقشه", BLUE,
          () -> { removeScreen(a); NvRuntimeController.openSearch(a, destination); }));
      return;
    }

    if (!originQuery.isEmpty()) {
      setStatus(s, "در حال تشخیص مبدأ «" + originQuery + "» و مقصد «" + destination + "»…", CYAN);
      s.results.removeAllViews();
      new Thread(() -> {
        try {
          // An explicitly typed origin must not be biased toward the phone's current GPS.
          List<Place> origins = geocodeRanked(originQuery, null);
          if (origins.isEmpty()) throw new IllegalStateException("no origin");
          if (!destinationConfident(origins, originQuery)) {
            a.runOnUiThread(() -> {
              if (!alive(a, s)) return;
              renderOriginChoices(a, s, raw, mode, destination, origins);
            });
            return;
          }
          Location resolvedOrigin = locationFromPlace(origins.get(0));
          resolveDestinationForTrip(a, s, raw, mode, resolvedOrigin, destination);
        } catch (Throwable e) {
          a.runOnUiThread(() -> {
            if (!alive(a, s)) return;
            setStatus(s, "مبدأ جستجوشده با اطمینان کافی پیدا نشد.", AMBER);
            s.results.removeAllViews();
            s.results.addView(button(a, "جستجوی هوشمند مبدأ", BLUE, () -> openSmartSearch(a)));
          });
        }
      }, "nv-v032-origin").start();
      return;
    }

    Location origin = MwmApplication.from(a).getLocationHelper().getSavedLocation();
    if (!planningLocationOkay(origin)) {
      setStatus(s, "برای مسیریابی از موقعیت فعلی، GPS تازه با خطای حداکثر ۱۲۰ متر لازم است؛ یا مبدأ را در متن بنویسید.", RED);
      s.results.removeAllViews();
      s.results.addView(button(a, "دریافت GPS بهتر", GREEN, () -> NvRuntimeController.showLocationStatus(a)));
      return;
    }
    if (!freshEnough(origin)) {
      setStatus(s, "هشدار: دقت GPS فعلی " + Math.round(origin.getAccuracy()) + " متر است؛ محاسبه ممکن است از خیابان مجاور شروع شود.", AMBER);
    }
    resolveDestinationForTrip(a, s, raw, mode, origin, destination);
  }

  private static void resolveDestinationForTrip(MwmActivity a, Screen s, String raw, Mode mode,
                                                Location origin, String destination) {
    setStatus(s, (isExplicitOrigin(origin) ? "مبدأ جستجوشده تأیید شد • " : "")
        + "در حال تشخیص مقصد «" + destination + "»…", CYAN);
    s.results.removeAllViews();

    new Thread(() -> {
      try {
        // Once the origin is known, use it as the geographic bias for destination ranking.
        List<Place> list = geocodeRanked(destination, origin);
        if (list.isEmpty()) throw new IllegalStateException("no destination");

        if (!destinationConfident(list, destination)) {
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
          s.results.removeAllViews();
          s.results.addView(button(a, "جستجوی هوشمند مقصد", BLUE, () -> openSmartSearch(a)));
        });
      }
    }, "nv-v032-destination").start();
  }

  private static void renderOriginChoices(MwmActivity a, Screen s, String raw, Mode mode,
                                          String destination, List<Place> list) {
    s.results.removeAllViews();
    setStatus(s, "چند مبدأ مشابه پیدا شد؛ مبدأ درست را انتخاب کنید.", AMBER);
    int count = Math.min(5, list.size());
    for (int i = 0; i < count; i++) {
      Place p = list.get(i);
      LinearLayout card = new LinearLayout(a);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(dp(a, 12), dp(a, 9), dp(a, 12), dp(a, 9));
      card.setBackground(round(a, PANEL, OUTLINE, 14));
      card.addView(text(a, (i + 1) + ". " + p.title, 15, WHITE, Typeface.BOLD));
      card.addView(text(a, p.address, 11, MUTED, Typeface.NORMAL));
      card.addView(button(a, "انتخاب این مبدأ", BLUE,
          () -> resolveDestinationForTrip(a, s, raw, mode, locationFromPlace(p), destination)));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.setMargins(0, dp(a, 5), 0, dp(a, 5));
      s.results.addView(card, lp);
    }
  }

  private static Location locationFromPlace(Place p) {
    Location l = new Location("NV_SEARCH:" + p.title);
    l.setLatitude(p.lat);
    l.setLongitude(p.lon);
    l.setAccuracy(10f);
    l.setTime(System.currentTimeMillis());
    return l;
  }

  private static boolean isExplicitOrigin(Location l) {
    if (l == null || l.getProvider() == null) return false;
    String provider = l.getProvider();
    return provider.startsWith("NV_SEARCH:") || provider.startsWith("NV_MAP:");
  }

  private static String originTitle(Location l) {
    if (!isExplicitOrigin(l)) return "موقعیت فعلی";
    String p = l.getProvider();
    String prefix = p.startsWith("NV_MAP:") ? "NV_MAP:" : "NV_SEARCH:";
    String name = p.substring(prefix.length()).trim();
    return name.isEmpty() ? "مبدأ انتخابی" : name;
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
      routeBetween(a, origin, best, Router.Pedestrian);
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

    if (mode == Mode.AUTO) {
      setStatus(s, "مبدأ: " + originTitle(origin) + " • مقصد: " + best.title + " • بررسی گزینه‌های هوشمند…", CYAN);
      compareHurryOptions(a, s, origin, best);
      return;
    }

    previewVehicle(a, s, origin, best, false);
  }

  private static void compareHurryOptions(MwmActivity a, Screen s, Location origin, Place dest) {
    if (!onlineServicesEnabled(a)) {
      setStatus(s, "حالت خصوصی فعال است؛ سریع‌ترین مسیر آنلاین مقایسه نمی‌شود.", AMBER);
      s.results.removeAllViews();
      s.results.addView(button(a, "شروع مسیر خودرو با موتور آفلاین", BLUE,
          () -> routeBetween(a, origin, dest, Router.Vehicle)));
      s.results.addView(button(a, "مسیر پیاده با موتور آفلاین", CYAN,
          () -> routeBetween(a, origin, dest, Router.Pedestrian)));
      return;
    }
    setStatus(s, "در حال مقایسه همزمان گزینه‌های قابل استفاده…", CYAN);
    new Thread(() -> {
      RoadEstimate car = null;
      MixedEstimate mixed = null;
      try { car = osrm(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon); } catch (Throwable ignored) {}
      if (prefs(a).getBoolean("use_metro", true)) {
        try { mixed = estimateMixed(a, origin, dest, true); } catch (Throwable ignored) {}
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
          s.results.addView(button(a, "مسیر خودرو با موتور آفلاین", BLUE, () -> routeBetween(a, origin, dest, Router.Vehicle)));
          return;
        }

        setStatus(s, "مبدأ: " + originTitle(origin) + " • مقصد: " + dest.title + "\nسریع‌ترین برآورد فعلی: " + bestName + " • " + formatMinutes(best), GREEN);

        if (finalCar != null) {
          s.results.addView(optionCard(a,
              "🚗 خودرو" + ("خودرو".equals(bestName) ? "  ✓ سریع‌ترین" : ""),
              formatMinutes(finalCar.durationSec) + " • " + formatDistance(finalCar.distanceM),
              BLUE,
              () -> routeBetween(a, origin, dest, Router.Vehicle)));
        }

        if (finalMixed != null) {
          String details = formatMinutes(finalMixed.totalSec) + " • "
              + mixedModeLabel(finalMixed.accessMode) + " → مترو → " + mixedModeLabel(finalMixed.egressMode)
              + " • " + finalMixed.fromStation.title + " → " + finalMixed.toStation.title
              + " • حدود " + formatToman(estimateMixedCostToman(a, finalMixed));
          s.results.addView(optionCard(a,
              "🚇 ترکیبی" + ("ترکیبی".equals(bestName) ? "  ✓ سریع‌ترین" : ""),
              details,
              GREEN,
              () -> startMixedSession(a, origin, finalMixed, dest)));
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
              () -> routeBetween(a, origin, dest, Router.Pedestrian)));
        }
      });
    }, "nv-v032-hurry").start();
  }

  private static void buildMixedPlan(MwmActivity a, Screen s, Location origin, Place dest) {
    if (!onlineServicesEnabled(a)) {
      setStatus(s, "برای ساخت سفر ترکیبی، دسترسی به داده آنلاین ایستگاه‌ها لازم است.", AMBER);
      s.results.removeAllViews();
      s.results.addView(button(a, "مسیر پیاده آفلاین", CYAN, () -> routeBetween(a, origin, dest, Router.Pedestrian)));
      s.results.addView(button(a, "مسیر خودرو آفلاین", BLUE, () -> routeBetween(a, origin, dest, Router.Vehicle)));
      return;
    }
    setStatus(s, "در حال ساخت سفر چندمرحله‌ای واقعی با ایستگاه‌های نزدیک…", CYAN);
    new Thread(() -> {
      MixedEstimate mixed = null;
      try { mixed = estimateMixed(a, origin, dest, true); } catch (Throwable ignored) {}
      MixedEstimate result = mixed;

      a.runOnUiThread(() -> {
        if (!alive(a, s)) return;
        s.results.removeAllViews();

        if (result == null) {
          setStatus(s, "مترو مناسب نزدیک مبدأ یا مقصد پیدا نشد؛ گزینه‌های عملی جایگزین نمایش داده شدند.", AMBER);
          s.results.addView(button(a, "سریع‌ترین مسیر خودرو", BLUE, () -> routeBetween(a, origin, dest, Router.Vehicle)));
          s.results.addView(button(a, "مسیر پیاده", CYAN, () -> routeBetween(a, origin, dest, Router.Pedestrian)));
          return;
        }

        setStatus(s, "برآورد سفر ترکیبی برای «" + dest.title + "» آماده است", GREEN);
        s.results.addView(text(a, "کل برآورد: " + formatMinutes(result.totalSec), 18, WHITE, Typeface.BOLD));
        s.results.addView(stepCard(a, "۱", mixedModeLabel(result.accessMode) + " تا " + result.fromStation.title,
            formatMinutes(result.accessSec) + " • " + formatDistance(result.accessDistanceM), GREEN));
        s.results.addView(stepCard(a, "۲", "مترو: " + result.fromStation.title + " → " + result.toStation.title,
            formatMinutes(result.metroSec)
                + " • " + result.metroSource
                + (result.metroStops >= 0 ? " • " + result.metroStops + " ایستگاه" : "")
                + (result.metroTransfers >= 0 ? " • " + result.metroTransfers + " تعویض" : "")
                + " • بدون داده زنده قطار", BLUE));
        s.results.addView(stepCard(a, "۳", mixedModeLabel(result.egressMode) + " تا مقصد",
            formatMinutes(result.egressSec) + " • " + formatDistance(result.egressDistanceM), PURPLE));
        s.results.addView(text(a, "هزینه تقریبی کل: " + formatToman(estimateMixedCostToman(a, result)),
            13, CYAN, Typeface.BOLD));
        s.results.addView(button(a, "شروع سفر ترکیبی مرحله‌به‌مرحله", GREEN, () -> startMixedSession(a, origin, result, dest)));
        s.results.addView(button(a, "مقایسه با خودرو", BLUE, () -> compareHurryOptions(a, s, origin, dest)));
      });
    }, "nv-v032-mixed").start();
  }

  private static MixedEstimate estimateMixed(MwmActivity a, Location origin, Place dest) throws Exception {
    return estimateMixed(a, origin, dest, false);
  }

  private static MixedEstimate estimateMixed(MwmActivity a, Location origin, Place dest,
                                             boolean fastestPriority) throws Exception {
    if (!prefs(a).getBoolean("use_metro", true)) return null;

    boolean minCost = !fastestPriority && prefs(a).getBoolean("min_cost", false);
    boolean lessWalking = !fastestPriority && prefs(a).getBoolean("less_walking", false);
    boolean allowVehicle = prefs(a).getBoolean("use_taxi", true);

    int stationRadius = allowVehicle && !minCost ? 8_000 : 5_000;
    List<Place> from = metroStations(origin.getLatitude(), origin.getLongitude(), stationRadius);
    List<Place> to = metroStations(dest.lat, dest.lon, stationRadius);
    if (from.isEmpty() || to.isEmpty()) return null;

    Place aStation = null, bStation = null;
    MetroRouteEstimate metro = null;
    double bestPreliminary = Double.MAX_VALUE;
    int evaluated = 0;
    int fromCount = Math.min(4, from.size());
    int toCount = Math.min(4, to.size());

    List<int[]> pairs = new ArrayList<>();
    for (int i = 0; i < fromCount; i++) {
      for (int j = 0; j < toCount; j++) pairs.add(new int[]{i,j});
    }
    pairs.sort((x,y) -> Double.compare(
        from.get(x[0]).distanceMeters + to.get(x[1]).distanceMeters,
        from.get(y[0]).distanceMeters + to.get(y[1]).distanceMeters));

    for (int[] pair : pairs) {
      if (evaluated >= 7) break;
      Place f = from.get(pair[0]), t = to.get(pair[1]);
      MetroRouteEstimate candidate = null;
      try { candidate = metroNetworkEstimate(f, t); } catch (Throwable ignored) {}
      evaluated++;
      if (candidate == null) continue;

      double accessWalkApprox = f.distanceMeters / 1.35d;
      double egressWalkApprox = t.distanceMeters / 1.35d;
      double accessVehicleApprox = f.distanceMeters / 8.0d + 3 * 60d;
      double egressVehicleApprox = t.distanceMeters / 8.0d + 5 * 60d;

      double accessApprox = preliminaryLegSeconds(
          accessWalkApprox, accessVehicleApprox, allowVehicle, minCost, lessWalking);
      double egressApprox = preliminaryLegSeconds(
          egressWalkApprox, egressVehicleApprox, allowVehicle, minCost, lessWalking);

      double preliminary = accessApprox + candidate.seconds + egressApprox
          + candidate.transfers * 90d;
      if (preliminary < bestPreliminary) {
        bestPreliminary = preliminary;
        aStation = f;
        bStation = t;
        metro = candidate;
      }
    }

    if (aStation == null || bStation == null) {
      aStation = from.get(0);
      bStation = to.get(0);
    }

    double accessDirect = haversine(origin.getLatitude(), origin.getLongitude(), aStation.lat, aStation.lon);
    double egressDirect = haversine(bStation.lat, bStation.lon, dest.lat, dest.lon);
    double accessWalkDistance = accessDirect * 1.20d;
    double egressWalkDistance = egressDirect * 1.20d;
    RoadEstimate accessWalk = new RoadEstimate(accessWalkDistance, walkingSeconds(accessDirect));
    RoadEstimate egressWalk = new RoadEstimate(egressWalkDistance, walkingSeconds(egressDirect));

    RoadEstimate accessVehicle = null;
    RoadEstimate egressVehicle = null;
    if (allowVehicle && !minCost) {
      try {
        RoadEstimate raw = osrm(origin.getLatitude(), origin.getLongitude(), aStation.lat, aStation.lon);
        if (raw != null)
          accessVehicle = new RoadEstimate(raw.distanceM, raw.durationSec + 3 * 60);
      } catch (Throwable ignored) {}
      try {
        RoadEstimate raw = osrm(bStation.lat, bStation.lon, dest.lat, dest.lon);
        if (raw != null)
          egressVehicle = new RoadEstimate(raw.distanceM, raw.durationSec + 5 * 60);
      } catch (Throwable ignored) {}
    }

    MixedLeg access = chooseMixedLeg(accessWalk, accessVehicle, allowVehicle, minCost, lessWalking);
    MixedLeg egress = chooseMixedLeg(egressWalk, egressVehicle, allowVehicle, minCost, lessWalking);

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

    int total = access.estimate.durationSec + metroSec + egress.estimate.durationSec;
    return new MixedEstimate(
        aStation, bStation,
        access.estimate.distanceM, access.estimate.durationSec,
        metroDistance, metroSec,
        egress.estimate.distanceM, egress.estimate.durationSec,
        total,
        access.mode,
        egress.mode,
        metroStops, metroTransfers, metroSource);
  }

  private static double preliminaryLegSeconds(double walkSec, double vehicleSec,
                                              boolean allowVehicle, boolean minCost,
                                              boolean lessWalking) {
    if (!allowVehicle || minCost) return walkSec;
    if (lessWalking) return vehicleSec;
    return vehicleSec + 90d < walkSec ? vehicleSec : walkSec;
  }

  private static MixedLeg chooseMixedLeg(RoadEstimate walk, RoadEstimate vehicle,
                                         boolean allowVehicle, boolean minCost,
                                         boolean lessWalking) {
    if (!allowVehicle || minCost || vehicle == null) return new MixedLeg(walk, "پیاده");
    if (lessWalking) return new MixedLeg(vehicle, "تاکسی");
    if (vehicle.durationSec + 90 < walk.durationSec) return new MixedLeg(vehicle, "تاکسی");
    return new MixedLeg(walk, "پیاده");
  }

  private static String mixedModeLabel(String mode) {
    return "تاکسی".equals(mode) ? "خودرو/تاکسی" : "پیاده";
  }

  private static MetroRouteEstimate metroNetworkEstimate(Place from, Place to) throws Exception {
    double direct = haversine(from.lat, from.lon, to.lat, to.lon);
    double midLat = (from.lat + to.lat) / 2d;
    double midLon = (from.lon + to.lon) / 2d;
    int radius = (int)Math.max(15_000d, Math.min(55_000d, direct / 2d + 12_000d));

    String around = String.format(Locale.US, "(around:%d,%.7f,%.7f)", radius, midLat, midLon);
    String q = "[out:json][timeout:20];relation" + around
        + "[\"type\"=\"route\"][\"route\"=\"subway\"];out body;>;out tags;";
    JSONArray elements = overpassElements(q);

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
    card.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
    card.setBackground(round(a, PANEL, OUTLINE, 20));
    card.setElevation(dp(a, 3));

    LinearLayout titleRow = new LinearLayout(a);
    titleRow.setOrientation(LinearLayout.HORIZONTAL);
    titleRow.setGravity(Gravity.CENTER_VERTICAL);
    titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

    View accent = new View(a);
    accent.setBackground(round(a, color, color, 6));
    titleRow.addView(accent, new LinearLayout.LayoutParams(dp(a, 5), dp(a, 34)));

    LinearLayout copy = new LinearLayout(a);
    copy.setOrientation(LinearLayout.VERTICAL);
    copy.setPadding(dp(a, 10), 0, dp(a, 10), 0);
    copy.addView(text(a, title, 16, WHITE, Typeface.BOLD));
    TextView sub = text(a, subtitle, 12, MUTED, Typeface.NORMAL);
    sub.setMaxLines(3);
    copy.addView(sub);
    titleRow.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    card.addView(titleRow);

    TextView choose = smallButton(a, "انتخاب این مسیر", color, action);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 46));
    cp.setMargins(0, dp(a, 10), 0, 0);
    card.addView(choose, cp);

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(0, dp(a, 6), 0, dp(a, 6));
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
          int low = Math.max(60, (int)Math.round(adjusted * 0.86d));
          int high = Math.max(low + 60, (int)Math.round(adjusted * 1.22d));
          setStatus(s, "مقصد: " + dest.title + (hurry ? " • اولویت سرعت" : ""), GREEN);
          s.results.addView(text(a, "برآورد جاده‌ای NV: " + formatMinutes(adjusted) + " • " + formatDistance(result.distanceM), 17, WHITE, Typeface.BOLD));
          s.results.addView(text(a, "بازه بدون ترافیک زنده: " + formatMinutes(low) + " تا " + formatMinutes(high), 12, CYAN, Typeface.BOLD));
          s.results.addView(text(a, "این زمان بر پایه مسیر جاده‌ای است و ترافیک لحظه‌ای را جعل نمی‌کند.", 11, MUTED, Typeface.NORMAL));
        } else {
          setStatus(s, "برآورد آنلاین در دسترس نیست؛ مسیر آفلاین موتور نقشه استفاده می‌شود.", AMBER);
        }
        s.results.addView(button(a, "شروع مسیر خودرو", BLUE, () -> routeBetween(a, origin, dest, Router.Vehicle)));
        s.results.addView(button(a, "مسیر پیاده", CYAN, () -> routeBetween(a, origin, dest, Router.Pedestrian)));
      });
    }, "nv-v032-eta").start();
  }

  private static int adjustedEtaSeconds(int engineSec, double distanceM, Location origin) {
    // For pre-route comparisons, OSRM's duration is already the estimate. Do not
    // extrapolate the entire trip from one instantaneous GPS speed sample.
    return Math.max(60, engineSec);
  }

  private static void compareModes(MwmActivity a, Screen s, Location origin, Place dest) {
    if (!onlineServicesEnabled(a)) {
      setStatus(s, "حالت خصوصی فعال است؛ مقایسه آنلاین مسیرها انجام نمی‌شود.", AMBER);
      s.results.removeAllViews();
      s.results.addView(routeOptionCard(a, "🚗 خودرو آفلاین", "محاسبه با موتور داخلی نقشه",
          PLANNER_BLUE, true, () -> routeBetween(a, origin, dest, Router.Vehicle)));
      s.results.addView(routeOptionCard(a, "🚶 پیاده آفلاین", "محاسبه با موتور داخلی نقشه",
          GREEN, false, () -> routeBetween(a, origin, dest, Router.Pedestrian)));
      return;
    }
    setStatus(s, "در حال محاسبه مسیرهای جایگزین، زمان و هزینه…", CYAN);
    new Thread(() -> {
      List<RoadEstimate> carRoutes = new ArrayList<>();
      MixedEstimate mixed = null;
      try { carRoutes = osrmAlternatives(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon); } catch (Throwable ignored) {}
      try { mixed = estimateMixed(a, origin, dest, true); } catch (Throwable ignored) {}

      double direct = haversine(origin.getLatitude(), origin.getLongitude(), dest.lat, dest.lon);
      int walkSec = direct <= 15_000d ? walkingSeconds(direct) : Integer.MAX_VALUE;
      List<RoadEstimate> finalCars = carRoutes;
      MixedEstimate finalMixed = mixed;

      a.runOnUiThread(() -> {
        if (!alive(a, s)) return;
        s.results.removeAllViews();

        int bestSec = Integer.MAX_VALUE;
        for (RoadEstimate car : finalCars) bestSec = Math.min(bestSec, car.durationSec);
        if (finalMixed != null) bestSec = Math.min(bestSec, finalMixed.totalSec);
        if (walkSec < Integer.MAX_VALUE) bestSec = Math.min(bestSec, walkSec);

        int idx = 1;
        for (RoadEstimate car : finalCars) {
          double liters = car.distanceM / 100000d * prefs(a).getInt("fuel_l100", 8);
          long cost = estimateCarCostToman(a, car.distanceM);
          String tag = idx == 1 ? "مسیر اصلی" : "جایگزین " + idx;
          boolean recommended = car.durationSec == bestSec;
          s.results.addView(routeOptionCard(a,
              "🚗 خودرو • " + tag,
              formatMinutes(car.durationSec) + "  •  " + formatDistance(car.distanceM)
                  + "  •  " + String.format(Locale.US, "%.1f لیتر", liters)
                  + "  •  حدود " + formatToman(cost),
              PLANNER_BLUE,
              recommended,
              () -> routeBetween(a, origin, dest, Router.Vehicle)));
          idx++;
        }

        if (finalMixed != null) {
          long cost = estimateMixedCostToman(a, finalMixed);
          boolean recommended = finalMixed.totalSec == bestSec;
          s.results.addView(routeOptionCard(a,
              "🚇 سفر ترکیبی",
              formatMinutes(finalMixed.totalSec) + "  •  "
                  + mixedModeLabel(finalMixed.accessMode) + " → مترو → " + mixedModeLabel(finalMixed.egressMode)
                  + "\n" + finalMixed.fromStation.title + " → " + finalMixed.toStation.title
                  + "  •  حدود " + formatToman(cost),
              GREEN,
              recommended,
              () -> startMixedSession(a, origin, finalMixed, dest)));
          TextView metroInfo = text(a,
              "مترو: " + finalMixed.metroSource
                  + (finalMixed.metroStops >= 0 ? " • " + finalMixed.metroStops + " ایستگاه" : "")
                  + (finalMixed.metroTransfers >= 0 ? " • " + finalMixed.metroTransfers + " تعویض خط" : "")
                  + " • بدون داده زنده قطار",
              11, PLANNER_MUTED, Typeface.NORMAL);
          metroInfo.setPadding(dp(a, 8), 0, dp(a, 8), dp(a, 4));
          s.results.addView(metroInfo);
        }

        if (walkSec < Integer.MAX_VALUE) {
          boolean recommended = walkSec == bestSec;
          s.results.addView(routeOptionCard(a,
              "🚶 پیاده",
              "حدود " + formatMinutes(walkSec) + "  •  " + formatDistance(direct * 1.20d) + "  •  بدون هزینه",
              Color.rgb(14, 165, 233),
              recommended,
              () -> routeBetween(a, origin, dest, Router.Pedestrian)));
        }

        if (finalCars.isEmpty() && finalMixed == null && walkSec == Integer.MAX_VALUE) {
          setStatus(s, "مقایسه آنلاین در دسترس نیست؛ از موتور آفلاین نقشه استفاده کنید.", AMBER);
          s.results.addView(routeOptionCard(a, "🚗 خودرو آفلاین", "محاسبه با موتور داخلی نقشه",
              PLANNER_BLUE, true, () -> routeBetween(a, origin, dest, Router.Vehicle)));
          return;
        }
        s.results.addView(plannerSecondaryButton(a, "تغییر مبدأ یا مقصد", () -> {
          RoutePlannerState current = ROUTE_PLANNERS.get(a);
          if (current == null) {
            current = new RoutePlannerState();
            current.origin = origin;
            current.originLabel = originTitle(origin);
            current.destination = dest;
            ROUTE_PLANNERS.put(a, current);
          }
          renderRoutePlanner(a, current);
        }));
        setStatus(s, "همه پیشنهادهای قابل استفاده برای «" + dest.title + "» آماده است", GREEN);
      });
    }, "nv-v032-compare").start();
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
          s.results.addView(button(a, "شروع مسیر مترو/پیاده", GREEN, () -> routeBetween(a, origin, dest, Router.Transit)));
          s.results.addView(button(a, "مقایسه با خودرو", BLUE, () -> previewVehicle(a, s, origin, dest, true)));
        } else {
          setStatus(s, "مترو مناسب نزدیک مبدا یا مقصد وجود ندارد؛ به‌جای خطای «No metro route» گزینه‌های عملی نمایش داده شد.", AMBER);
          s.results.addView(button(a, "سریع‌ترین مسیر خودرو", BLUE, () -> routeBetween(a, origin, dest, Router.Vehicle)));
          s.results.addView(button(a, "مسیر پیاده", CYAN, () -> routeBetween(a, origin, dest, Router.Pedestrian)));
        }
      });
    }, "nv-v032-mixed").start();
  }

  public static void openStationTransfer(MwmActivity a) {
    Screen s = screen(a, "تعویض هوشمند ایستگاه", "ایستگاه، خروجی و ادامه مسیر بر اساس مقصد فعال");
    if (!onlineServicesEnabled(a)) {
      setStatus(s, "برای بررسی خروجی‌های مترو، خدمات آنلاین باید فعال باشد.", AMBER);
      s.results.addView(button(a, "باز کردن تنظیمات", BLUE, () -> openPreferences(a)));
      return;
    }
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
    }, "nv-v032-station-transfer").start();
  }

  public static void openMetroStatus(MwmActivity a) {
    showMetroStations(a, "مترو و ایستگاه‌ها",
        "ایستگاه‌های واقعی اطراف؛ موقعیت زنده قطار فقط در صورت اتصال منبع رسمی قابل نمایش است");
  }

  private static void showMetroStations(MwmActivity a, String title, String subtitle) {
    Screen s = screen(a, title, subtitle);
    if (!onlineServicesEnabled(a)) {
      setStatus(s, "حالت خصوصی فعال است؛ داده آنلاین ایستگاه‌ها دریافت نمی‌شود.", AMBER);
      s.results.addView(button(a, "باز کردن تنظیمات", BLUE, () -> openPreferences(a)));
      return;
    }
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
    }, "nv-v032-metro").start();
  }

  public static void openTaxi(MwmActivity a) {
    Screen s = screen(a, "تاکسی و محل سوارشدن", "نزدیک‌ترین ایستگاه‌ها و نقاط تاکسی ثبت‌شده اطراف موقعیت فعلی");
    if (!onlineServicesEnabled(a)) {
      setStatus(s, "حالت خصوصی فعال است؛ جستجوی آنلاین نقاط تاکسی انجام نمی‌شود.", AMBER);
      s.results.addView(button(a, "باز کردن تنظیمات", BLUE, () -> openPreferences(a)));
      return;
    }
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
    }, "nv-v032-taxi").start();
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
    Screen s = screen(a, "تنظیمات سفر", "رفتار مسیریابی، حریم خصوصی، هزینه و مصرف را اینجا تنظیم کنید");
    SharedPreferences p = prefs(a);

    s.results.addView(sectionText(a, "حریم خصوصی و اینترنت"));
    s.results.addView(togglePreference(a, p, "online_services", true,
        "خدمات هوشمند آنلاین",
        "در صورت خاموش بودن، مقصد/مختصات برای OSRM، Nominatim، Photon و Overpass ارسال نمی‌شود."));
    s.results.addView(text(a,
        p.getBoolean("online_services", true)
            ? "حالت آنلاین: ETA جاده‌ای، جستجوی هوشمند و داده ایستگاه‌ها فعال است."
            : "حالت خصوصی: فقط قابلیت‌های محلی/آفلاین نقشه استفاده می‌شوند.",
        11, p.getBoolean("online_services", true) ? CYAN : GREEN, Typeface.NORMAL));

    s.results.addView(sectionText(a, "شیوه سفر"));
    s.results.addView(togglePreference(a, p, "use_metro", true, "استفاده از مترو", "در سفر ترکیبی مترو بررسی شود."));
    s.results.addView(togglePreference(a, p, "use_taxi", true, "استفاده از تاکسی", "برای دسترسی به ایستگاه یا مقصد امکان تاکسی در نظر گرفته شود."));
    s.results.addView(togglePreference(a, p, "min_cost", false, "اولویت هزینه کمتر", "در سفر ترکیبی، تا حد امکان گزینه ارزان‌تر ترجیح داده شود."));
    s.results.addView(togglePreference(a, p, "less_walking", false, "پیاده‌روی کمتر", "در صورت امکان تاکسی برای بخش‌های دسترسی ترجیح داده شود."));
    s.results.addView(togglePreference(a, p, "avoid_highways", false, "اجتناب از بزرگراه", "در مسیریابی خودرو از بزرگراه‌ها اجتناب شود."));
    s.results.addView(togglePreference(a, p, "safer_route", true, "اجتناب از جاده خاکی/نامناسب", "گزینه قابل پشتیبانی موتور نقشه برای جاده‌های خاکی فعال می‌شود."));

    s.results.addView(sectionText(a, "مصرف و هزینه تقریبی"));
    int[] fuel = {6, 8, 10, 12};
    LinearLayout fuelRow = new LinearLayout(a);
    fuelRow.setOrientation(LinearLayout.HORIZONTAL);
    fuelRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    for (int f : fuel) {
      fuelRow.addView(smallButton(a, f + "L/100", p.getInt("fuel_l100", 8) == f ? GREEN : PANEL2,
          () -> { p.edit().putInt("fuel_l100", f).apply(); openPreferences(a); }), weight(a));
    }
    s.results.addView(fuelRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 48)));

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
        "هزینه تاکسی و مترو برآوردی است و فقط بر اساس مقادیر شما محاسبه می‌شود؛ قیمت لحظه‌ای جعل نمی‌شود.",
        11, MUTED, Typeface.NORMAL));

    s.results.addView(sectionText(a, "هشدارها"));
    s.results.addView(button(a, "تنظیم هشدار سرعت و GPS", BLUE, () -> openRouteAlerts(a)));
  }

  private static TextView sectionText(MwmActivity a, String title) {
    TextView v = text(a, title, 15, CYAN, Typeface.BOLD);
    v.setPadding(dp(a, 4), dp(a, 12), dp(a, 4), dp(a, 4));
    return v;
  }

  private static View togglePreference(MwmActivity a, SharedPreferences p, String key, boolean def,
                                       String title, String subtitle) {
    boolean value = p.getBoolean(key, def);
    LinearLayout card = new LinearLayout(a);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(a, 12), dp(a, 9), dp(a, 12), dp(a, 9));
    card.setBackground(round(a, PANEL, value ? GREEN : OUTLINE, 14));
    TextView name = text(a, (value ? "●  " : "○  ") + title, 14, WHITE, Typeface.BOLD);
    TextView desc = text(a, subtitle, 11, MUTED, Typeface.NORMAL);
    card.addView(name);
    card.addView(desc);
    card.setClickable(true);
    card.setOnClickListener(v -> {
      p.edit().putBoolean(key, !value).apply();
      openPreferences(a);
    });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(0, dp(a, 4), 0, dp(a, 4));
    card.setLayoutParams(lp);
    return card;
  }

  private static List<Place> geocodeRanked(String query, Location origin) throws Exception {
    List<Place> out = new ArrayList<>();
    Exception firstError = null;
    try { out.addAll(geocodeNominatim(query, origin)); }
    catch (Exception e) { firstError = e; }

    if (out.size() < 4) {
      try {
        List<Place> fallback = geocodePhoton(query, origin);
        Set<String> seen = new HashSet<>();
        for (Place p : out) seen.add(String.format(Locale.US, "%.5f,%.5f", p.lat, p.lon));
        for (Place p : fallback) {
          String key = String.format(Locale.US, "%.5f,%.5f", p.lat, p.lon);
          if (seen.add(key)) out.add(p);
        }
      } catch (Exception ignored) {}
    }

    if (out.isEmpty() && firstError != null) throw firstError;
    String normalized = normalize(query);
    for (Place p : out) p.score = scorePlace(normalized, p);
    out.sort((p1,p2)-> {
      int s1=Integer.compare(p2.score,p1.score);
      if(s1!=0)return s1;
      return Double.compare(p1.distanceMeters<0?Double.MAX_VALUE:p1.distanceMeters,
                            p2.distanceMeters<0?Double.MAX_VALUE:p2.distanceMeters);
    });
    if (out.size()>10) return new ArrayList<>(out.subList(0,10));
    return out;
  }

  private static List<Place> geocodeNominatim(String query, Location origin) throws Exception {
    String normalized = normalize(query);
    String searchQ = query;
    if (containsAny(normalized, "راه آهن", "راه اهن", "راه‌آهن") && !containsAny(normalized, "ایستگاه"))
      searchQ = "ایستگاه " + query;

    StringBuilder u = new StringBuilder(
        "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=20&addressdetails=1&namedetails=1&extratags=1&accept-language=fa&q=")
        .append(URLEncoder.encode(searchQ, "UTF-8"));
    if (origin != null) {
      double dLat = 2.2, dLon = 2.5;
      u.append(String.format(Locale.US, "&viewbox=%.6f,%.6f,%.6f,%.6f",
          origin.getLongitude()-dLon, origin.getLatitude()+dLat,
          origin.getLongitude()+dLon, origin.getLatitude()-dLat));
    }

    HttpURLConnection conn = (HttpURLConnection)new URL(u.toString()).openConnection();
    conn.setConnectTimeout(8000); conn.setReadTimeout(12000); conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent", "NV-Android/0.33");
    conn.setRequestProperty("Accept", "application/json");
    if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300)
      throw new IllegalStateException("Nominatim HTTP " + conn.getResponseCode());
    JSONArray arr = new JSONArray(readAll(conn.getInputStream()));
    conn.disconnect();

    List<Place> out = new ArrayList<>();
    for (int i=0;i<arr.length();i++) {
      JSONObject o=arr.optJSONObject(i); if(o==null)continue;
      double lat=Double.parseDouble(o.getString("lat")), lon=Double.parseDouble(o.getString("lon"));
      String display=o.optString("display_name", "");
      String title=o.optString("name", "").trim();
      if (title.isEmpty()) {
        JSONObject names=o.optJSONObject("namedetails");
        if (names!=null) title=names.optString("name:fa", names.optString("name", "")).trim();
      }
      if (title.isEmpty()) {
        int comma=display.indexOf(',');
        title=comma>0?display.substring(0,comma).trim():display;
      }
      String category=o.optString("category", ""), type=o.optString("type", "");
      double dist=origin==null?-1:haversine(origin.getLatitude(),origin.getLongitude(),lat,lon);
      out.add(new Place(title,display,lat,lon,dist,category,type,0));
    }
    return out;
  }

  private static List<Place> geocodePhoton(String query, Location origin) throws Exception {
    StringBuilder u = new StringBuilder("https://photon.komoot.io/api/?limit=20&lang=fa&q=")
        .append(URLEncoder.encode(query, "UTF-8"));
    if (origin != null) {
      u.append(String.format(Locale.US, "&lat=%.6f&lon=%.6f",
          origin.getLatitude(), origin.getLongitude()));
    }

    HttpURLConnection conn=(HttpURLConnection)new URL(u.toString()).openConnection();
    conn.setConnectTimeout(8000); conn.setReadTimeout(12000); conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent","NV-Android/0.33");
    conn.setRequestProperty("Accept","application/json");
    if(conn.getResponseCode()<200||conn.getResponseCode()>=300)
      throw new IllegalStateException("Photon HTTP " + conn.getResponseCode());

    JSONObject root=new JSONObject(readAll(conn.getInputStream()));
    conn.disconnect();
    JSONArray features=root.optJSONArray("features");
    List<Place> out=new ArrayList<>();
    if(features==null)return out;

    for(int i=0;i<features.length();i++){
      JSONObject f=features.optJSONObject(i); if(f==null)continue;
      JSONObject geom=f.optJSONObject("geometry");
      JSONObject prop=f.optJSONObject("properties");
      if(geom==null||prop==null)continue;
      JSONArray xy=geom.optJSONArray("coordinates");
      if(xy==null||xy.length()<2)continue;
      double lon=xy.optDouble(0,Double.NaN), lat=xy.optDouble(1,Double.NaN);
      if(!Double.isFinite(lat)||!Double.isFinite(lon))continue;
      String title=prop.optString("name", "").trim();
      if(title.isEmpty())title=prop.optString("street", "مکان");
      StringBuilder address=new StringBuilder();
      for(String key:new String[]{"street","district","city","county","state","country"}){
        String v=prop.optString(key,"").trim();
        if(!v.isEmpty() && address.indexOf(v)<0){ if(address.length()>0)address.append("، "); address.append(v); }
      }
      String category=prop.optString("osm_key","");
      String type=prop.optString("osm_value","");
      double dist=origin==null?-1:haversine(origin.getLatitude(),origin.getLongitude(),lat,lon);
      out.add(new Place(title,address.toString(),lat,lon,dist,category,type,0));
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
    boolean metro=containsAny(query,"مترو","زیرزمینی");
    if(metro){
      if(t.contains("مترو")||a.contains("مترو"))score+=130;
      if(cat.contains("railway")||type.contains("station"))score+=35;
      if((t.contains("راه آهن")||t.contains("راه‌آهن")||a.contains("راه آهن")||a.contains("راه‌آهن"))
          && !t.contains("مترو"))score-=100;
    }
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
    conn.setRequestProperty("User-Agent","NV-Android/0.33");
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

  private static JSONArray overpassElements(String query) throws Exception {
    String[] endpoints = {
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    };
    Exception last = null;
    for (String endpoint : endpoints) {
      HttpURLConnection conn = null;
      try {
        conn = (HttpURLConnection)new URL(endpoint).openConnection();
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(18000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("User-Agent", "NV-Android/0.33");
        byte[] body=("data="+URLEncoder.encode(query,"UTF-8")).getBytes(StandardCharsets.UTF_8);
        try(java.io.OutputStream os=conn.getOutputStream()){os.write(body);}
        int code=conn.getResponseCode();
        if(code<200||code>=300) throw new IllegalStateException("Overpass HTTP "+code);
        JSONObject root=new JSONObject(readAll(conn.getInputStream()));
        JSONArray els=root.optJSONArray("elements");
        return els==null?new JSONArray():els;
      } catch (Exception e) {
        last=e;
      } finally {
        if(conn!=null) conn.disconnect();
      }
    }
    if(last!=null) throw last;
    return new JSONArray();
  }

  private static boolean hasMetroNear(double lat,double lon,int radius) throws Exception { return !metroStations(lat,lon,radius).isEmpty(); }

  private static List<Place> metroStations(double lat,double lon,int radius) throws Exception {
    String around=String.format(Locale.US,"(around:%d,%.7f,%.7f)",radius,lat,lon);
    String q="[out:json][timeout:12];("
        +"node"+around+"[\"railway\"=\"station\"][\"station\"=\"subway\"];"
        +"node"+around+"[\"railway\"=\"station\"][\"subway\"=\"yes\"];"
        +"node"+around+"[\"public_transport\"=\"station\"][\"subway\"=\"yes\"];"
        +");out tags;";
    JSONArray els=overpassElements(q);
    List<Place> out=new ArrayList<>();
    for(int i=0;i<els.length();i++){
      JSONObject obj=els.optJSONObject(i);if(obj==null)continue;
      double la=obj.optDouble("lat",Double.NaN),lo=obj.optDouble("lon",Double.NaN);
      if(!Double.isFinite(la)||!Double.isFinite(lo))continue;
      JSONObject tags=obj.optJSONObject("tags");
      String name=tags==null?"":tags.optString("name:fa",tags.optString("name","ایستگاه مترو"));
      if(name.isEmpty()) name="ایستگاه مترو";
      double d=haversine(lat,lon,la,lo);
      out.add(new Place(name,"ایستگاه مترو",la,lo,d,"railway","station",0));
    }
    out.sort(Comparator.comparingDouble(p->p.distanceMeters));
    if(out.size()>12)return new ArrayList<>(out.subList(0,12));
    return out;
  }

  private static List<Place> subwayEntrances(Place station, MapObject target) throws Exception {
    String around=String.format(Locale.US,"(around:%d,%.7f,%.7f)",550,station.lat,station.lon);
    String q="[out:json][timeout:12];(node"+around+"[\"railway\"=\"subway_entrance\"];);out tags;";
    JSONArray els=overpassElements(q);
    List<Place> out=new ArrayList<>();
    for(int i=0;i<els.length();i++){
      JSONObject obj=els.optJSONObject(i);if(obj==null)continue;
      double la=obj.optDouble("lat",Double.NaN),lo=obj.optDouble("lon",Double.NaN);
      if(!Double.isFinite(la)||!Double.isFinite(lo))continue;
      JSONObject tags=obj.optJSONObject("tags");
      String name="";
      if(tags!=null){
        name=tags.optString("name:fa",tags.optString("name",""));
        if(name.isEmpty()){
          String ref=tags.optString("ref","");
          name=ref.isEmpty()?"خروجی مترو":"خروجی "+ref;
        }
      }
      double stationDistance=haversine(station.lat,station.lon,la,lo);
      if(stationDistance>550d) continue;
      double d=target!=null?haversine(la,lo,target.getLat(),target.getLon())
          :stationDistance;
      out.add(new Place(name,"خروجی مترو",la,lo,d,"railway","subway_entrance",0));
    }
    out.sort(Comparator.comparingDouble(p->p.distanceMeters));
    return out;
  }

  private static List<Place> taxiStands(double lat,double lon,int radius) throws Exception {
    String around=String.format(Locale.US,"(around:%d,%.7f,%.7f)",radius,lat,lon);
    String q="[out:json][timeout:12];("
        +"node"+around+"[\"amenity\"=\"taxi\"];"
        +"way"+around+"[\"amenity\"=\"taxi\"];"
        +");out center tags;";
    JSONArray els=overpassElements(q);
    List<Place> out=new ArrayList<>();
    for(int i=0;i<els.length();i++){
      JSONObject obj=els.optJSONObject(i);if(obj==null)continue;
      double la=obj.optDouble("lat",Double.NaN),lo=obj.optDouble("lon",Double.NaN);
      JSONObject center=obj.optJSONObject("center");
      if((!Double.isFinite(la)||!Double.isFinite(lo))&&center!=null){
        la=center.optDouble("lat",Double.NaN);lo=center.optDouble("lon",Double.NaN);
      }
      if(!Double.isFinite(la)||!Double.isFinite(lo))continue;
      JSONObject tags=obj.optJSONObject("tags");
      String name=tags==null?"":tags.optString("name:fa",tags.optString("name",""));
      if(name.isEmpty())name="ایستگاه تاکسی";
      double d=haversine(lat,lon,la,lo);
      out.add(new Place(name,"ایستگاه تاکسی",la,lo,d,"amenity","taxi",0));
    }
    out.sort(Comparator.comparingDouble(p->p.distanceMeters));
    if(out.size()>10)return new ArrayList<>(out.subList(0,10));
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

  private static void routeBetween(MwmActivity a, Location origin, Place p, Router router) {
    if (!isExplicitOrigin(origin)) {
      routeTo(a, p, router);
      return;
    }
    if (router == Router.Vehicle) {
      boolean avoidHighways = prefs(a).getBoolean("avoid_highways", false);
      boolean safer = prefs(a).getBoolean("safer_route", true);
      if (avoidHighways) RoutingOptions.addOption(RoadType.Motorway);
      else RoutingOptions.removeOption(RoadType.Motorway);
      if (safer) RoutingOptions.addOption(RoadType.Dirty);
      else RoutingOptions.removeOption(RoadType.Dirty);
    }
    MapObject start = MapObject.createMapObject(MapObject.SEARCH, originTitle(origin), "مبدأ جستجوشده",
        origin.getLatitude(), origin.getLongitude());
    MapObject end = MapObject.createMapObject(MapObject.SEARCH, p.title, p.address, p.lat, p.lon);
    removeScreen(a);
    RoutingController.get().prepare(start, end, router);
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
    return l!=null && System.currentTimeMillis()-l.getTime()<=NvLocationPolicy.MAX_AGE_MS
        && (!l.hasAccuracy() || l.getAccuracy()<=NvLocationPolicy.GOOD_ACCURACY_M);
  }
  private static boolean planningLocationOkay(Location l){
    return l!=null && System.currentTimeMillis()-l.getTime()<=NvLocationPolicy.MAX_AGE_MS
        && (!l.hasAccuracy() || l.getAccuracy()<=NvLocationPolicy.WARN_ACCURACY_M);
  }
  private static boolean looksLikeTrip(String s){return (!extractOrigin(s).isEmpty() && !extractDestination(s).isEmpty()) || containsAny(normalize(s),"میخوام","می خوام","می‌خوام","برم","برو","عجله","مسیر ترکیبی","پیاده","با مترو");}
  static String extractDestination(String raw) { return NvV032TextParser.extractDestination(raw); }
  static String extractOrigin(String raw) { return NvV032TextParser.extractOrigin(raw); }

  static String normalize(String s) { return NvV032TextParser.normalize(s); }

  private static boolean containsAny(String s,String...v){String n=normalize(s);for(String x:v)if(n.contains(normalize(x)))return true;return false;}
  private static String formatDistance(double m){return m<1000?Math.round(m)+" متر":String.format(Locale.US,"%.1f کیلومتر",m/1000d);}
  private static double haversine(double a,double b,double c,double d){double r=6371000,p1=Math.toRadians(a),p2=Math.toRadians(c),dp=Math.toRadians(c-a),dl=Math.toRadians(d-b),x=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return r*2*Math.atan2(Math.sqrt(x),Math.sqrt(1-x));}
  private static String readAll(InputStream in)throws Exception{try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String line;while((line=br.readLine())!=null){if(b.length()>2_000_000)throw new IllegalStateException("large response");b.append(line);}return b.toString();}}
  private static boolean onlineServicesEnabled(Context c) {
    return prefs(c).getBoolean("online_services", true);
  }

  private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

  private static Screen plannerScreen(MwmActivity a, String title, String subtitle) {
    removeScreen(a);
    ViewGroup host = a.findViewById(android.R.id.content);

    FrameLayout root = new FrameLayout(a);
    root.setTag(SCREEN_TAG);
    root.setBackgroundColor(PLANNER_BG);
    root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    host.addView(root, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout col = new LinearLayout(a);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setPadding(dp(a, 16), dp(a, 32), dp(a, 16), dp(a, 16));

    LinearLayout head = new LinearLayout(a);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

    LinearLayout titles = new LinearLayout(a);
    titles.setOrientation(LinearLayout.VERTICAL);
    titles.addView(text(a, title, 24, PLANNER_TEXT, Typeface.BOLD));
    TextView sub = text(a, subtitle, 12, PLANNER_MUTED, Typeface.NORMAL);
    sub.setMaxLines(2);
    titles.addView(sub);
    head.addView(titles, new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    TextView close = text(a, "×", 28, PLANNER_TEXT, Typeface.NORMAL);
    close.setGravity(Gravity.CENTER);
    close.setBackground(round(a, PLANNER_SOFT, PLANNER_BORDER, 24));
    close.setOnClickListener(v -> removeScreen(a));
    head.addView(close, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 46)));
    col.addView(head);

    TextView status = text(a, "", 13, PLANNER_BLUE, Typeface.BOLD);
    status.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
    status.setBackground(round(a, PLANNER_SOFT, Color.TRANSPARENT, 14));
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    sp.setMargins(0, dp(a, 12), 0, dp(a, 6));
    col.addView(status, sp);

    LinearLayout controls = new LinearLayout(a);
    controls.setOrientation(LinearLayout.VERTICAL);
    col.addView(controls);

    ScrollView sv = new ScrollView(a);
    sv.setVerticalScrollBarEnabled(false);
    LinearLayout results = new LinearLayout(a);
    results.setOrientation(LinearLayout.VERTICAL);
    results.setPadding(0, dp(a, 6), 0, dp(a, 28));
    sv.addView(results);
    col.addView(sv, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    root.addView(col, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return new Screen(root, status, controls, results);
  }

  private static EditText plannerInput(MwmActivity a, String hint) {
    EditText v = new EditText(a);
    v.setSingleLine(true);
    v.setHint(hint);
    v.setHintTextColor(Color.rgb(148, 163, 184));
    v.setTextColor(PLANNER_TEXT);
    v.setTextSize(17);
    v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    v.setPadding(dp(a, 16), 0, dp(a, 16), 0);
    v.setBackground(round(a, PLANNER_CARD, PLANNER_BORDER, 18));
    v.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    v.setElevation(dp(a, 1));
    return v;
  }

  private static TextView plannerPrimaryButton(MwmActivity a, String label, int color, Runnable action) {
    TextView v = text(a, label, 14, Color.WHITE, Typeface.BOLD);
    v.setGravity(Gravity.CENTER);
    v.setBackground(round(a, color, color, 17));
    v.setOnClickListener(x -> action.run());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 54));
    p.setMargins(dp(a, 2), dp(a, 6), dp(a, 2), dp(a, 4));
    v.setLayoutParams(p);
    return v;
  }

  private static TextView plannerSecondaryButton(MwmActivity a, String label, Runnable action) {
    TextView v = text(a, label, 14, PLANNER_TEXT, Typeface.BOLD);
    v.setGravity(Gravity.CENTER);
    v.setBackground(round(a, PLANNER_SOFT, PLANNER_BORDER, 17));
    v.setOnClickListener(x -> action.run());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 52));
    p.setMargins(dp(a, 2), dp(a, 4), dp(a, 2), dp(a, 4));
    v.setLayoutParams(p);
    return v;
  }

  private static TextView plannerMiniButton(MwmActivity a, String label, int color,
                                            boolean filled, Runnable action) {
    TextView v = text(a, label, 12, filled ? Color.WHITE : color, Typeface.BOLD);
    v.setGravity(Gravity.CENTER);
    v.setBackground(round(a, filled ? color : PLANNER_SOFT,
        filled ? color : PLANNER_BORDER, 14));
    v.setOnClickListener(x -> action.run());
    return v;
  }

  private static View plannerOptionCard(MwmActivity a, String title, String subtitle,
                                        int accent, Runnable action) {
    LinearLayout card = new LinearLayout(a);
    card.setOrientation(LinearLayout.HORIZONTAL);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    card.setPadding(dp(a, 14), dp(a, 11), dp(a, 14), dp(a, 11));
    card.setBackground(round(a, PLANNER_CARD, PLANNER_BORDER, 18));
    card.setElevation(dp(a, 1));
    card.setClickable(true);
    card.setOnClickListener(v -> action.run());

    TextView dot = text(a, "●", 19, accent, Typeface.BOLD);
    dot.setGravity(Gravity.CENTER);
    card.addView(dot, new LinearLayout.LayoutParams(dp(a, 36), dp(a, 44)));

    LinearLayout copy = new LinearLayout(a);
    copy.setOrientation(LinearLayout.VERTICAL);
    copy.setPadding(dp(a, 8), 0, dp(a, 8), 0);
    copy.addView(text(a, title, 15, PLANNER_TEXT, Typeface.BOLD));
    TextView sub = text(a, subtitle, 11, PLANNER_MUTED, Typeface.NORMAL);
    sub.setMaxLines(2);
    copy.addView(sub);
    card.addView(copy, new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    TextView arrow = text(a, "‹", 26, PLANNER_MUTED, Typeface.NORMAL);
    arrow.setGravity(Gravity.CENTER);
    card.addView(arrow, new LinearLayout.LayoutParams(dp(a, 34), dp(a, 44)));

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(dp(a, 2), dp(a, 5), dp(a, 2), dp(a, 5));
    card.setLayoutParams(lp);
    return card;
  }

  private static View routeOptionCard(MwmActivity a, String title, String subtitle,
                                      int accent, boolean recommended, Runnable action) {
    LinearLayout card = new LinearLayout(a);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(a, 15), dp(a, 13), dp(a, 15), dp(a, 12));
    card.setBackground(round(a, PLANNER_CARD, recommended ? accent : PLANNER_BORDER, 20));
    card.setElevation(dp(a, recommended ? 4 : 1));

    LinearLayout top = new LinearLayout(a);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

    TextView t = text(a, title, 16, PLANNER_TEXT, Typeface.BOLD);
    top.addView(t, new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    if (recommended) {
      TextView badge = text(a, "پیشنهاد NV", 10, Color.WHITE, Typeface.BOLD);
      badge.setGravity(Gravity.CENTER);
      badge.setBackground(round(a, accent, accent, 12));
      top.addView(badge, new LinearLayout.LayoutParams(dp(a, 82), dp(a, 30)));
    }
    card.addView(top);

    TextView sub = text(a, subtitle, 12, PLANNER_MUTED, Typeface.NORMAL);
    sub.setPadding(0, dp(a, 6), 0, dp(a, 8));
    sub.setLineSpacing(0f, 1.15f);
    card.addView(sub);

    TextView choose = text(a, "انتخاب این مسیر", 13, accent, Typeface.BOLD);
    choose.setGravity(Gravity.CENTER);
    choose.setBackground(round(a,
        Color.argb(16, Color.red(accent), Color.green(accent), Color.blue(accent)),
        Color.TRANSPARENT, 14));
    choose.setOnClickListener(v -> action.run());
    card.addView(choose, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44)));

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(dp(a, 2), dp(a, 6), dp(a, 2), dp(a, 6));
    card.setLayoutParams(lp);
    return card;
  }

  private static Screen screen(MwmActivity a, String title, String subtitle) {
    removeScreen(a);
    ViewGroup host = a.findViewById(android.R.id.content);
    FrameLayout root = new FrameLayout(a);
    root.setTag(SCREEN_TAG);
    root.setBackgroundColor(BG);
    root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    host.addView(root, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout col = new LinearLayout(a);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setPadding(dp(a, 16), dp(a, 28), dp(a, 16), dp(a, 14));

    LinearLayout head = new LinearLayout(a);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

    LinearLayout titles = new LinearLayout(a);
    titles.setOrientation(LinearLayout.VERTICAL);
    TextView titleView = text(a, title, 22, WHITE, Typeface.BOLD);
    TextView subtitleView = text(a, subtitle, 12, MUTED, Typeface.NORMAL);
    subtitleView.setMaxLines(2);
    titles.addView(titleView);
    titles.addView(subtitleView);
    head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    TextView x = text(a, "×", 27, WHITE, Typeface.NORMAL);
    x.setGravity(Gravity.CENTER);
    x.setBackground(round(a, PANEL2, OUTLINE, 18));
    x.setOnClickListener(v -> removeScreen(a));
    head.addView(x, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 46)));
    col.addView(head);

    TextView status = text(a, "", 13, CYAN, Typeface.BOLD);
    status.setPadding(dp(a, 12), dp(a, 9), dp(a, 12), dp(a, 9));
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    sp.setMargins(0, dp(a, 10), 0, dp(a, 4));
    col.addView(status, sp);

    LinearLayout controls = new LinearLayout(a);
    controls.setOrientation(LinearLayout.VERTICAL);
    col.addView(controls);

    ScrollView sv = new ScrollView(a);
    sv.setFillViewport(false);
    sv.setClipToPadding(false);
    LinearLayout results = new LinearLayout(a);
    results.setOrientation(LinearLayout.VERTICAL);
    results.setPadding(0, dp(a, 8), 0, dp(a, 24));
    sv.addView(results);
    col.addView(sv, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    root.addView(col, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return new Screen(root, status, controls, results);
  }

  private static EditText input(MwmActivity a,String hint){EditText v=new EditText(a);v.setSingleLine(true);v.setHint(hint);v.setHintTextColor(MUTED);v.setTextColor(WHITE);v.setTextSize(16);v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);v.setPadding(dp(a,16),0,dp(a,16),0);v.setBackground(round(a,PANEL2,OUTLINE,18));v.setImeOptions(EditorInfo.IME_ACTION_SEARCH);v.setElevation(dp(a,2));return v;}
  private static void setStatus(Screen s,String t,int c){s.status.setText(t);s.status.setTextColor(c);s.status.setBackground(round(s.status.getContext(),Color.argb(28,Color.red(c),Color.green(c),Color.blue(c)),Color.argb(75,Color.red(c),Color.green(c),Color.blue(c)),16));}
  private static void removeScreen(MwmActivity a){ViewGroup h=a.findViewById(android.R.id.content);if(h==null)return;View v=h.findViewWithTag(SCREEN_TAG);if(v!=null)h.removeView(v);}
  private static boolean alive(MwmActivity a,Screen s){ViewGroup h=a.findViewById(android.R.id.content);return h!=null&&h.findViewWithTag(SCREEN_TAG)==s.root;}
  private static TextView button(MwmActivity a,String label,int color,Runnable r){TextView v=text(a,label,14,WHITE,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setBackground(round(a,color,color==PANEL2?OUTLINE:color,18));v.setElevation(dp(a,3));v.setOnClickListener(x->r.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(a,56));p.setMargins(0,dp(a,6),0,dp(a,6));v.setLayoutParams(p);return v;}
  private static TextView smallButton(MwmActivity a,String label,int color,Runnable r){TextView v=text(a,label,12,WHITE,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setBackground(round(a,color,color==PANEL2?OUTLINE:color,16));v.setOnClickListener(x->r.run());return v;}
  private static TextView text(MwmActivity a,String s,int sp,int color,int style){TextView v=new TextView(a);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setTypeface(Typeface.DEFAULT,style);v.setGravity(Gravity.RIGHT);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v;}
  private static LinearLayout.LayoutParams weight(MwmActivity a){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f);p.setMargins(dp(a,3),dp(a,3),dp(a,3),dp(a,3));return p;}
  private static GradientDrawable round(Context c,int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));d.setStroke(dp(c,1),stroke);return d;}
  private static int dp(Context c,int v){return Math.max(1,Math.round(c.getResources().getDisplayMetrics().density))*v;}

  private enum Mode { AUTO, CHAT, HURRY, MIXED, ETA, COMPARE, WALK }
  private static final class RoutePlannerState {
    Location origin;
    String originLabel = "";
    Place destination;
  }

  private static final class Screen { final FrameLayout root;final TextView status;final LinearLayout controls,results;Screen(FrameLayout r,TextView s,LinearLayout c,LinearLayout o){root=r;status=s;controls=c;results=o;} }
  private static final class Place { final String title,address;final double lat,lon,distanceMeters;final String category,type;int score;Place(String t,String a,double la,double lo,double d,String c,String ty,int sc){title=t;address=a;lat=la;lon=lo;distanceMeters=d;category=c;type=ty;score=sc;} }
  private static final class RoadEstimate { final double distanceM;final int durationSec;RoadEstimate(double d,int t){distanceM=d;durationSec=t;} }
  private static final class MixedLeg {
    final RoadEstimate estimate; final String mode;
    MixedLeg(RoadEstimate estimate, String mode) { this.estimate=estimate; this.mode=mode; }
  }
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
