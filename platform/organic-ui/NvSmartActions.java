package app.organicmaps;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
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

import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.routing.RoutingController;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Functional NV actions used by the Persian map overlay.
 * All POIs come from live OSM/Overpass data and all routes are delegated to Organic Maps.
 * No synthetic places, fake ETA values or fabricated route segments are generated.
 */
public final class NvSmartActions {
  private static final int BG = Color.rgb(8, 20, 32);
  private static final int PANEL = Color.rgb(7, 33, 55);
  private static final int PANEL_2 = Color.rgb(10, 48, 78);
  private static final int OUTLINE = Color.rgb(45, 126, 171);
  private static final int WHITE = Color.WHITE;
  private static final int MUTED = Color.rgb(205, 220, 231);
  private static final int CYAN = Color.rgb(40, 206, 255);
  private static final int BLUE = Color.rgb(45, 139, 255);
  private static final int GREEN = Color.rgb(42, 214, 113);
  private static final int AMBER = Color.rgb(255, 188, 54);
  private static final int RED = Color.rgb(255, 70, 89);
  private static final String TAG = "nv-smart-actions";

  private static final long MAX_NEARBY_FIX_AGE_MS = NvLocationPolicy.MAX_AGE_MS;
  private static final long MAX_ROUTE_FIX_AGE_MS = NvLocationPolicy.MAX_AGE_MS;
  private static final float MAX_NEARBY_ACCURACY_M = NvLocationPolicy.NEARBY_ACCURACY_M;
  private static final float MAX_ROUTE_ACCURACY_M = NvLocationPolicy.WARN_ACCURACY_M;
  private static final String[] OVERPASS_ENDPOINTS = {
      "https://overpass-api.de/api/interpreter",
      "https://overpass.kumi.systems/api/interpreter",
      "https://overpass.private.coffee/api/interpreter"
  };

  private NvSmartActions() {}

  public static void openNearby(MwmActivity activity, String rawCategory) {
    final NvNearbyCategory category = NvNearbyCategory.from(rawCategory);
    if (!onlineServicesEnabled(activity)) {
      Toast.makeText(activity, "حالت خصوصی فعال است؛ جستجوی آنلاین اطراف غیرفعال است.", Toast.LENGTH_LONG).show();
      NvRuntimeController.openSearch(activity, category.fallbackQuery);
      return;
    }
    final LocationHelper helper = MwmApplication.from(activity).getLocationHelper();
    final Location loc = helper.getSavedLocation();
    if (!isFresh(loc, MAX_NEARBY_FIX_AGE_MS)) {
      requestBetterLocation(activity, "برای پیدا کردن نزدیک‌ترین مکان، یک موقعیت تازه لازم است.");
      return;
    }
    if (!loc.hasAccuracy() || loc.getAccuracy() > MAX_NEARBY_ACCURACY_M) {
      final String a = loc.hasAccuracy() ? Math.round(loc.getAccuracy()) + " متر" : "نامشخص";
      requestBetterLocation(activity, "دقت فعلی GPS برای مرتب‌سازی نزدیک‌ترین‌ها کافی نیست: " + a);
      return;
    }

    final Screen ui = createScreen(activity, category.title,
        "فقط نتایج واقعی داخل شعاع همین موقعیت، مرتب‌شده از نزدیک به دور");
    final int accuracy = Math.max(1, Math.round(loc.getAccuracy()));
    ui.status.setText("در حال یافتن نزدیک‌ترین‌ها…  خطای GPS: " + accuracy + " متر");
    ui.status.setTextColor(accuracy <= 30 ? GREEN : CYAN);

    new Thread(() -> {
      try {
        final List<Place> places = fetchNearby(category, loc.getLatitude(), loc.getLongitude());
        activity.runOnUiThread(() -> renderNearby(activity, ui, category, loc, places));
      } catch (Throwable e) {
        activity.runOnUiThread(() -> {
          if (!isScreenAlive(activity, ui.root)) return;
          ui.status.setText("سرویس نزدیک‌ترین‌ها موقتاً پاسخ نداد. نتیجه دور یا ساختگی نمایش داده نمی‌شود.");
          ui.status.setTextColor(AMBER);
          ui.results.removeAllViews();
          ui.results.addView(button(activity, "تلاش دوباره", GREEN,
              () -> { removeScreen(activity); openNearby(activity, rawCategory); }));
          ui.results.addView(button(activity, "جستجوی عمومی «" + category.fallbackQuery + "»", BLUE,
              () -> { removeScreen(activity); NvRuntimeController.openSearch(activity, category.fallbackQuery); }));
        });
      }
    }, "nv-nearby-v032").start();
  }

  public static void openPlanner(MwmActivity activity, int menuId) {
    final String title = plannerTitle(menuId);
    final String subtitle = plannerSubtitle(menuId);
    final Screen ui = createScreen(activity, title, subtitle);

    final EditText input = new EditText(activity);
    input.setSingleLine(false);
    input.setMaxLines(3);
    input.setHint("مثال: می‌خوام برم میدان تجریش، عجله دارم، مسیر ترکیبی");
    input.setHintTextColor(MUTED);
    input.setTextColor(WHITE);
    input.setTextSize(16);
    input.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    input.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
    input.setBackground(round(activity, PANEL_2, OUTLINE, 14));
    input.setImeOptions(EditorInfo.IME_ACTION_GO);
    ui.controls.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 78)));

    final String preset = plannerPreset(menuId);
    if (!preset.isEmpty()) input.setText(preset);

    final Runnable submit = () -> {
      final String raw = input.getText().toString().trim();
      if (raw.isEmpty()) {
        ui.status.setText("مقصد را بنویسید.");
        ui.status.setTextColor(AMBER);
        return;
      }
      final TripIntent intent = parseTrip(raw, menuId);
      if (intent.destination.isEmpty()) {
        ui.status.setText("نام مقصد مشخص نیست؛ مثلاً بنویسید «می‌خوام برم میدان تجریش، عجله دارم».");
        ui.status.setTextColor(AMBER);
        return;
      }

      final Location loc = MwmApplication.from(activity).getLocationHelper().getSavedLocation();
      if (!isFresh(loc, MAX_ROUTE_FIX_AGE_MS)) {
        requestBetterLocation(activity, "برای شروع مسیر، موقعیت فعلی باید تازه شود.");
        return;
      }
      if (loc.hasAccuracy() && loc.getAccuracy() > MAX_ROUTE_ACCURACY_M) {
        requestBetterLocation(activity, "خطای GPS برای شروع مسیر زیاد است: " + Math.round(loc.getAccuracy()) + " متر");
        return;
      }

      final String priority = intent.hurry ? " • اولویت: رسیدن سریع‌تر" : "";
      ui.status.setText("در حال پیدا کردن «" + intent.destination + "» و آماده‌سازی " + routerName(intent.router) + priority + "…");
      ui.status.setTextColor(CYAN);
      ui.results.removeAllViews();
      geocodeAndRoute(activity, ui, intent, menuId);
    };

    ui.controls.addView(button(activity, "محاسبه و شروع مسیر", menuId == 14 ? RED : GREEN, submit));
    ui.controls.addView(button(activity, "فقط جستجوی مقصد", BLUE, () -> {
      final TripIntent intent = parseTrip(input.getText().toString(), menuId);
      removeScreen(activity);
      NvRuntimeController.openSearch(activity, intent.destination);
    }));
    input.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_GO) { submit.run(); return true; }
      return false;
    });
    input.requestFocus();
  }

  private static void geocodeAndRoute(MwmActivity activity, Screen ui, TripIntent intent, int menuId) {
    final Location loc = MwmApplication.from(activity).getLocationHelper().getSavedLocation();
    new Thread(() -> {
      try {
        final List<Place> found = geocode(intent.destination, loc, false);
        if (found.isEmpty()) throw new IllegalStateException("no result");
        final Place best = found.get(0);
        activity.runOnUiThread(() -> {
          if (!isScreenAlive(activity, ui.root)) return;
          ui.status.setText("مقصد پیدا شد: " + best.title + "\n" + routerName(intent.router)
              + (intent.hurry ? " • حالت عجله فعال" : ""));
          ui.status.setTextColor(GREEN);

          if (menuId == 10 || menuId == 20) {
            ui.results.removeAllViews();
            ui.results.addView(button(activity, "مسیر خودرو", BLUE,
                () -> routeTo(activity, best, Router.Vehicle)));
            ui.results.addView(button(activity, "مسیر ترکیبی مترو/حمل‌ونقل عمومی + پیاده", GREEN,
                () -> routeTo(activity, best, Router.Transit)));
            ui.results.addView(button(activity, "مسیر پیاده", CYAN,
                () -> routeTo(activity, best, Router.Pedestrian)));
            return;
          }

          // Explicit mixed/transit requests always stay transit. A hurry request without an
          // explicit transit keyword is routed as vehicle so the routing engine can select
          // the fastest road alternative available to it.
          routeTo(activity, best, intent.router);
        });
      } catch (Throwable e) {
        activity.runOnUiThread(() -> {
          if (!isScreenAlive(activity, ui.root)) return;
          ui.status.setText("مقصد به‌صورت قطعی پیدا نشد. نتیجه را از جستجوی نقشه انتخاب کنید.");
          ui.status.setTextColor(AMBER);
          ui.results.removeAllViews();
          ui.results.addView(button(activity, "باز کردن جستجوی مقصد", BLUE, () -> {
            removeScreen(activity);
            NvRuntimeController.openSearch(activity, intent.destination);
          }));
        });
      }
    }, "nv-trip-geocode-v032").start();
  }

  private static void renderNearby(MwmActivity activity, Screen ui, NvNearbyCategory category,
                                   Location origin, List<Place> places) {
    if (!isScreenAlive(activity, ui.root)) return;
    ui.results.removeAllViews();
    if (places.isEmpty()) {
      ui.status.setText("در شعاع " + Math.round(category.radiusMeters / 1000f) + " کیلومتری موردی پیدا نشد.");
      ui.status.setTextColor(AMBER);
      ui.results.addView(button(activity, "تلاش دوباره", GREEN,
          () -> { removeScreen(activity); openNearby(activity, category.fallbackQuery); }));
      ui.results.addView(button(activity, "جستجوی عمومی", BLUE,
          () -> { removeScreen(activity); NvRuntimeController.openSearch(activity, category.fallbackQuery); }));
      return;
    }

    final int acc = origin.hasAccuracy() ? Math.round(origin.getAccuracy()) : -1;
    ui.status.setText(places.size() + " نتیجه داخل محدوده پیدا شد — نزدیک‌ترین مورد اول است"
        + (acc > 0 ? " • خطای GPS: " + acc + " متر" : ""));
    ui.status.setTextColor(GREEN);

    int rank = 1;
    for (Place p : places) {
      final LinearLayout card = new LinearLayout(activity);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
      card.setBackground(round(activity, PANEL, OUTLINE, 16));
      final String distance = formatDistance(p.distanceMeters);
      card.addView(text(activity, rank + ".  " + p.title + "   •   " + distance, 15, WHITE, Typeface.BOLD));
      card.addView(text(activity, p.address, 12, MUTED, Typeface.NORMAL));
      final LinearLayout row = new LinearLayout(activity);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      row.addView(smallButton(activity, "مسیر", BLUE, () -> routeTo(activity, p, Router.Vehicle)), weight(activity));
      row.addView(smallButton(activity, "نمایش", CYAN, () -> {
        removeScreen(activity);
        Framework.nativeSetViewportCenter(p.lat, p.lon, 17);
      }), weight(activity));
      row.addView(smallButton(activity, "NV/QR", GREEN, () -> {
        removeScreen(activity);
        Framework.nativeSetViewportCenter(p.lat, p.lon, 17);
        NvRuntimeController.showCodeMenu(activity);
      }), weight(activity));
      card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 46)));
      final LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      cp.setMargins(0, dp(activity, 5), 0, dp(activity, 5));
      card.setLayoutParams(cp);
      ui.results.addView(card);
      rank++;
    }
  }

  private static List<Place> fetchNearby(NvNearbyCategory category, double lat, double lon) throws Exception {
    if (TextUtils.isEmpty(category.overpassFilter))
      return geocodeNearby(category.fallbackQuery, lat, lon, category.radiusMeters);

    Throwable lastError = null;
    for (String endpoint : OVERPASS_ENDPOINTS) {
      try {
        return fetchNearbyFromEndpoint(endpoint, category, lat, lon);
      } catch (Throwable e) {
        lastError = e;
      }
    }
    throw new IllegalStateException("all Overpass endpoints failed", lastError);
  }

  private static List<Place> fetchNearbyFromEndpoint(String endpoint, NvNearbyCategory category,
                                                      double lat, double lon) throws Exception {
    final String query = buildOverpassQuery(category, lat, lon);
    final HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
    conn.setConnectTimeout(7000);
    conn.setReadTimeout(18000);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    conn.setRequestProperty("Accept", "application/json");
    conn.setRequestProperty("User-Agent", "NV-Android/0.32");
    final byte[] payload = ("data=" + URLEncoder.encode(query, "UTF-8")).getBytes(StandardCharsets.UTF_8);
    try (OutputStream out = conn.getOutputStream()) { out.write(payload); }
    final int code = conn.getResponseCode();
    if (code < 200 || code >= 300) {
      conn.disconnect();
      throw new IllegalStateException("HTTP " + code);
    }

    final JSONObject root = new JSONObject(readAll(conn.getInputStream()));
    conn.disconnect();
    final JSONArray elements = root.optJSONArray("elements");
    final List<Place> out = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    if (elements == null) return out;

    for (int i = 0; i < elements.length(); i++) {
      final JSONObject e = elements.optJSONObject(i);
      if (e == null) continue;
      double plat = e.optDouble("lat", Double.NaN);
      double plon = e.optDouble("lon", Double.NaN);
      if (!Double.isFinite(plat) || !Double.isFinite(plon)) {
        final JSONObject center = e.optJSONObject("center");
        if (center != null) {
          plat = center.optDouble("lat", Double.NaN);
          plon = center.optDouble("lon", Double.NaN);
        }
      }
      if (!Double.isFinite(plat) || !Double.isFinite(plon)) continue;

      final double distance = haversine(lat, lon, plat, plon);
      // Defense in depth: never label a remote result as nearby even if an upstream
      // service returns data outside the requested around() radius.
      if (distance > category.radiusMeters * 1.05d) continue;

      final JSONObject tags = e.optJSONObject("tags");
      String title = tag(tags, "name:fa");
      if (title.isEmpty()) title = tag(tags, "name");
      if (title.isEmpty()) title = singularTitle(category.title);
      final String address = buildAddress(tags);
      final String key = title + "|" + Math.round(plat * 100000d) + "|" + Math.round(plon * 100000d);
      if (seen.add(key)) out.add(new Place(title, address, plat, plon, distance));
    }

    out.sort(Comparator.comparingDouble(p -> p.distanceMeters));
    return out.size() > 20 ? new ArrayList<>(out.subList(0, 20)) : out;
  }

  private static String buildOverpassQuery(NvNearbyCategory category, double lat, double lon) {
    final String around = String.format(Locale.US, "(around:%d,%.7f,%.7f)", category.radiusMeters, lat, lon);
    final StringBuilder q = new StringBuilder("[out:json][timeout:18];(");
    if ("emergency".equals(category.id)) {
      final String[] filters = {
          "[\"emergency\"=\"ambulance_station\"]",
          "[\"amenity\"=\"hospital\"]",
          "[\"amenity\"=\"clinic\"]"
      };
      for (String f : filters) {
        q.append("node").append(around).append(f).append(';');
        q.append("way").append(around).append(f).append(';');
        q.append("relation").append(around).append(f).append(';');
      }
    } else {
      final String f = category.overpassFilter;
      q.append("node").append(around).append(f).append(';');
      q.append("way").append(around).append(f).append(';');
      q.append("relation").append(around).append(f).append(';');
    }
    q.append(");out center tags 80;");
    return q.toString();
  }

  private static List<Place> geocodeNearby(String query, double lat, double lon, int radiusMeters) throws Exception {
    final Location fake = new Location("nv");
    fake.setLatitude(lat);
    fake.setLongitude(lon);
    final List<Place> candidates = geocode(query, fake, true);
    final List<Place> out = new ArrayList<>();
    for (Place p : candidates) {
      if (p.distanceMeters <= radiusMeters * 1.05d) out.add(p);
    }
    out.sort(Comparator.comparingDouble(p -> p.distanceMeters));
    return out;
  }

  private static List<Place> geocode(String query, Location bias, boolean bounded) throws Exception {
    final StringBuilder url = new StringBuilder(
        "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&addressdetails=1&namedetails=1&accept-language=fa&q=")
        .append(URLEncoder.encode(query, "UTF-8"));
    if (bias != null) {
      final double dLat = bounded ? 0.45 : 1.6;
      final double dLon = bounded ? 0.55 : 1.9;
      url.append(String.format(Locale.US, "&viewbox=%.6f,%.6f,%.6f,%.6f",
          bias.getLongitude() - dLon, bias.getLatitude() + dLat,
          bias.getLongitude() + dLon, bias.getLatitude() - dLat));
      if (bounded) url.append("&bounded=1");
    }

    final HttpURLConnection conn = (HttpURLConnection) new URL(url.toString()).openConnection();
    conn.setConnectTimeout(8000);
    conn.setReadTimeout(12000);
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Accept", "application/json");
    conn.setRequestProperty("User-Agent", "NV-Android/0.32");
    final int code = conn.getResponseCode();
    if (code < 200 || code >= 300) {
      conn.disconnect();
      throw new IllegalStateException("HTTP " + code);
    }

    final JSONArray array = new JSONArray(readAll(conn.getInputStream()));
    conn.disconnect();
    final List<Place> out = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      final JSONObject o = array.getJSONObject(i);
      final double lat = Double.parseDouble(o.getString("lat"));
      final double lon = Double.parseDouble(o.getString("lon"));
      final String display = o.optString("display_name", "مکان");
      String title = o.optString("name", "").trim();
      if (title.isEmpty()) {
        final int comma = display.indexOf(',');
        title = comma > 0 ? display.substring(0, comma).trim() : display;
      }
      final double dist = bias == null ? 0d : haversine(bias.getLatitude(), bias.getLongitude(), lat, lon);
      out.add(new Place(title, display, lat, lon, dist));
    }
    if (bias != null) out.sort(Comparator.comparingDouble(p -> p.distanceMeters));
    return out;
  }

  private static void routeTo(MwmActivity activity, Place place, Router router) {
    final LocationHelper helper = MwmApplication.from(activity).getLocationHelper();
    final Location loc = helper.getSavedLocation();
    if (!isFresh(loc, MAX_ROUTE_FIX_AGE_MS)) {
      requestBetterLocation(activity, "موقعیت فعلی برای شروع مسیر تازه نیست.");
      return;
    }
    if (loc.hasAccuracy() && loc.getAccuracy() > MAX_ROUTE_ACCURACY_M) {
      requestBetterLocation(activity, "خطای GPS برای شروع مسیر زیاد است: " + Math.round(loc.getAccuracy()) + " متر");
      return;
    }

    final MapObject start = MapObject.createMapObject(
        MapObject.MY_POSITION, "موقعیت من", "", loc.getLatitude(), loc.getLongitude());
    final MapObject end = MapObject.createMapObject(
        MapObject.SEARCH, place.title, place.address, place.lat, place.lon);
    removeScreen(activity);
    RoutingController.get().prepare(start, end, router);
  }

  private static TripIntent parseTrip(String raw, int menuId) {
    String q = raw == null ? "" : raw.trim();
    final boolean saysTransit = menuId == 15 || containsAny(q,
        "ترکیبی", "مترو", "حمل و نقل", "حمل‌ونقل", "اتوبوس", "عمومی");
    final boolean saysWalk = menuId == 21 || containsAny(q, "پیاده", "قدم");
    final boolean saysVehicle = containsAny(q, "ماشین", "خودرو", "تاکسی", "اسنپ");
    final boolean hurry = menuId == 14 || containsAny(q,
        "عجله دارم", "عجله", "سریع", "زود", "دیرم شده", "فوری");

    final Router router;
    if (saysTransit) router = Router.Transit;
    else if (saysWalk) router = Router.Pedestrian;
    else if (saysVehicle || hurry) router = Router.Vehicle;
    else router = Router.Vehicle;

    final String[] noise = {
        "می‌خوام برم", "میخوام برم", "می خوام برم", "می خواهم بروم", "می‌خواهم بروم",
        "میخوام بروم", "می‌خواهم برم", "می خواهم برم", "میخام برم", "بریم", "برو",
        "لطفا", "لطفاً", "از اینجا", "برای رسیدن به", "عجله دارم", "عجله", "خیلی سریع",
        "سریع", "زود", "دیرم شده", "فوری", "مسیر ترکیبی", "ترکیبی", "کم هزینه", "کم‌هزینه",
        "با مترو", "مترو", "با اتوبوس", "اتوبوس", "حمل و نقل عمومی", "حمل‌ونقل عمومی",
        "با تاکسی", "تاکسی", "با اسنپ", "اسنپ", "با ماشین", "ماشین", "با خودرو", "خودرو", "پیاده"
    };
    for (String n : noise) q = q.replace(n, " ");
    q = q.replace('،', ' ').replace(',', ' ').replace('؛', ' ').replace(':', ' ')
        .replace('!', ' ').replace('؟', ' ').replace('?', ' ');
    q = q.replaceAll("\\s+", " ").trim();
    if (q.startsWith("به ")) q = q.substring(3).trim();
    if (q.startsWith("برم ")) q = q.substring(4).trim();
    if (q.startsWith("سمت ")) q = q.substring(4).trim();
    return new TripIntent(q, router, hurry);
  }

  private static String plannerTitle(int id) {
    return switch (id) {
      case 5 -> "حالت مسیریابی";
      case 6 -> "هشدارهای مسیر";
      case 10 -> "مقایسه مسیرها";
      case 13 -> "چت هوشمند سفر";
      case 14 -> "حالت عجله دارم";
      case 15 -> "مسیر ترکیبی";
      case 19 -> "اطمینان زمان رسیدن";
      case 20 -> "مقایسه زمان و هزینه";
      case 21 -> "راهنمای پیاده";
      default -> "برنامه‌ریز سفر NV";
    };
  }

  private static String plannerSubtitle(int id) {
    if (id == 15) return "مقصد را بنویسید؛ مسیر واقعی Transit شامل حمل‌ونقل عمومی و بخش‌های پیاده محاسبه می‌شود";
    if (id == 14) return "مقصد را بنویسید؛ اولویت روی شروع سریع مسیر واقعی خودرو است مگر خودتان مترو/ترکیبی بخواهید";
    if (id == 10 || id == 20) return "مقصد را بنویسید؛ بعد از پیدا شدن مقصد، خودرو، Transit و پیاده را می‌توانید مقایسه کنید";
    if (id == 21) return "مقصد را بنویسید؛ مسیر پیاده واقعی محاسبه می‌شود";
    if (id == 13) return "فارسی طبیعی بنویسید؛ مقصد، عجله و نوع مسیر از جمله شما استخراج می‌شود";
    return "عبارت طبیعی فارسی را بنویسید؛ مقصد و نوع مسیر از متن استخراج می‌شود";
  }

  private static String plannerPreset(int id) {
    return switch (id) {
      case 14 -> "می‌خوام برم ";
      case 15 -> "می‌خوام برم  مسیر ترکیبی";
      case 21 -> "می‌خوام برم  پیاده";
      default -> "";
    };
  }

  private static String routerName(Router router) {
    if (router == Router.Transit) return "مسیر ترکیبی / حمل‌ونقل عمومی + پیاده";
    if (router == Router.Pedestrian) return "مسیر پیاده";
    if (router == Router.Bicycle) return "مسیر دوچرخه";
    return "مسیر خودرو / تاکسی";
  }

  private static void requestBetterLocation(MwmActivity activity, String message) {
    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    try {
      MwmApplication.from(activity).getLocationHelper().restartWithNewMode();
    } catch (Throwable ignored) {}
    NvRuntimeController.showLocationStatus(activity);
  }

  private static boolean onlineServicesEnabled(Context c) {
    return c.getSharedPreferences("nv_v032", Context.MODE_PRIVATE)
        .getBoolean("online_services", true);
  }

  private static boolean isFresh(Location loc, long maxAgeMs) {
    if (loc == null) return false;
    final long age = Math.max(0L, System.currentTimeMillis() - loc.getTime());
    return age <= maxAgeMs;
  }

  private static Screen createScreen(MwmActivity activity, String title, String subtitle) {
    removeScreen(activity);
    final ViewGroup host = activity.findViewById(android.R.id.content);
    final FrameLayout root = new FrameLayout(activity);
    root.setTag(TAG);
    root.setBackgroundColor(BG);
    root.setClickable(true);
    root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    host.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    final LinearLayout column = new LinearLayout(activity);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(dp(activity, 14), dp(activity, 36), dp(activity, 14), dp(activity, 14));
    final LinearLayout head = new LinearLayout(activity);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    final TextView close = text(activity, "×", 30, WHITE, Typeface.NORMAL);
    close.setGravity(Gravity.CENTER);
    close.setOnClickListener(v -> removeScreen(activity));
    head.addView(close, new LinearLayout.LayoutParams(dp(activity, 50), dp(activity, 54)));
    final LinearLayout titles = new LinearLayout(activity);
    titles.setOrientation(LinearLayout.VERTICAL);
    titles.addView(text(activity, title, 20, WHITE, Typeface.BOLD));
    titles.addView(text(activity, subtitle, 12, MUTED, Typeface.NORMAL));
    head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    column.addView(head);

    final TextView status = text(activity, "", 14, CYAN, Typeface.NORMAL);
    status.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
    column.addView(status);
    final LinearLayout controls = new LinearLayout(activity);
    controls.setOrientation(LinearLayout.VERTICAL);
    column.addView(controls);
    final ScrollView scroll = new ScrollView(activity);
    final LinearLayout results = new LinearLayout(activity);
    results.setOrientation(LinearLayout.VERTICAL);
    results.setPadding(0, dp(activity, 5), 0, dp(activity, 20));
    scroll.addView(results);
    column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return new Screen(root, status, controls, results);
  }

  public static void removeScreen(MwmActivity activity) {
    final ViewGroup host = activity.findViewById(android.R.id.content);
    if (host == null) return;
    final View old = host.findViewWithTag(TAG);
    if (old != null) host.removeView(old);
  }

  private static boolean isScreenAlive(MwmActivity activity, View root) {
    final ViewGroup host = activity.findViewById(android.R.id.content);
    return host != null && host.findViewWithTag(TAG) == root;
  }

  private static TextView button(MwmActivity a, String s, int color, Runnable r) {
    final TextView v = text(a, s, 14, WHITE, Typeface.BOLD);
    v.setGravity(Gravity.CENTER);
    v.setBackground(round(a, color, color == PANEL_2 ? OUTLINE : color, 14));
    v.setOnClickListener(x -> r.run());
    final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 52));
    p.setMargins(dp(a, 4), dp(a, 5), dp(a, 4), dp(a, 5));
    v.setLayoutParams(p);
    return v;
  }

  private static TextView smallButton(MwmActivity a, String s, int color, Runnable r) {
    final TextView v = text(a, s, 12, WHITE, Typeface.BOLD);
    v.setGravity(Gravity.CENTER);
    v.setBackground(round(a, color, color, 12));
    v.setOnClickListener(x -> r.run());
    return v;
  }

  private static TextView text(MwmActivity a, String s, int sp, int color, int style) {
    final TextView v = new TextView(a);
    v.setText(s);
    v.setTextSize(sp);
    v.setTextColor(color);
    v.setTypeface(Typeface.DEFAULT, style);
    v.setGravity(Gravity.RIGHT);
    v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    return v;
  }

  private static GradientDrawable round(MwmActivity a, int fill, int stroke, int r) {
    final GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(dp(a, r));
    d.setStroke(dp(a, 1), stroke);
    return d;
  }

  private static LinearLayout.LayoutParams weight(MwmActivity a) {
    final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    p.setMargins(dp(a, 3), dp(a, 3), dp(a, 3), dp(a, 3));
    return p;
  }

  private static int dp(Context c, int v) {
    return Math.max(1, Math.round(c.getResources().getDisplayMetrics().density)) * v;
  }

  private static String tag(JSONObject tags, String key) {
    return tags == null ? "" : tags.optString(key, "").trim();
  }

  private static String buildAddress(JSONObject tags) {
    if (tags == null) return "";
    final List<String> parts = new ArrayList<>();
    final String house = tag(tags, "addr:housenumber");
    final String street = tag(tags, "addr:street");
    final String district = tag(tags, "addr:district");
    final String city = tag(tags, "addr:city");
    final String phone = tag(tags, "phone");
    if (!street.isEmpty()) parts.add((house.isEmpty() ? "" : house + "، ") + street);
    if (!district.isEmpty()) parts.add(district);
    if (!city.isEmpty()) parts.add(city);
    if (!phone.isEmpty()) parts.add("تلفن: " + phone);
    return parts.isEmpty() ? "اطلاعات آدرس کامل در OpenStreetMap ثبت نشده" : String.join("، ", parts);
  }

  private static String singularTitle(String s) {
    return s.replace("های نزدیک", "").replace("‌های نزدیک", "").replace("نزدیک", "").trim();
  }

  private static String formatDistance(double m) {
    return m < 1000 ? Math.round(m) + " متر" : String.format(Locale.US, "%.1f کیلومتر", m / 1000d);
  }

  private static double haversine(double lat1, double lon1, double lat2, double lon2) {
    final double r = 6371000d;
    final double p1 = Math.toRadians(lat1);
    final double p2 = Math.toRadians(lat2);
    final double dp = Math.toRadians(lat2 - lat1);
    final double dl = Math.toRadians(lon2 - lon1);
    final double a = Math.sin(dp / 2) * Math.sin(dp / 2)
        + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private static boolean containsAny(String s, String... values) {
    for (String v : values) if (s.contains(v)) return true;
    return false;
  }

  private static String readAll(InputStream in) throws Exception {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      final StringBuilder b = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        if (b.length() > 2_000_000) throw new IllegalStateException("response too large");
        b.append(line);
      }
      return b.toString();
    }
  }

  private static final class Screen {
    final FrameLayout root;
    final TextView status;
    final LinearLayout controls;
    final LinearLayout results;
    Screen(FrameLayout r, TextView s, LinearLayout c, LinearLayout o) {
      root = r; status = s; controls = c; results = o;
    }
  }

  private static final class Place {
    final String title;
    final String address;
    final double lat;
    final double lon;
    final double distanceMeters;
    Place(String t, String a, double la, double lo, double d) {
      title = t; address = a; lat = la; lon = lo; distanceMeters = d;
    }
  }

  private static final class TripIntent {
    final String destination;
    final Router router;
    final boolean hurry;
    TripIntent(String d, Router r, boolean h) {
      destination = d; router = r; hurry = h;
    }
  }
}
