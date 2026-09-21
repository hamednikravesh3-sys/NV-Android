#!/usr/bin/env python3
from pathlib import Path
import base64
import io
import re
import sys
from PIL import Image

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'organicmaps')
repo_root = Path(__file__).resolve().parent.parent

# Copy NV sources into the cloned Organic Maps tree.
dst = root / 'android/app/src/main/java/app/organicmaps'
test_dst = root / 'android/app/src/test/java/app/organicmaps'
dst.mkdir(parents=True, exist_ok=True)
test_dst.mkdir(parents=True, exist_ok=True)
src = repo_root / 'platform/organic-ui'
for name in ['NvMapMenuOverlay.java', 'NvRuntimeController.java', 'NvCodeCodec.java', 'NvNearbyCategory.java',
             'NvSmartActions.java', 'NvV033Actions.java', 'NvV033TextParser.java', 'NvLocationPolicy.java', 'NvAnimatedBrand.java', 'NvSmartTravelUi.java']:
    (dst / name).write_bytes((src / name).read_bytes())
(test_dst / 'NvCodeCodecTest.java').write_bytes((src / 'NvCodeCodecTest.java').read_bytes())
(test_dst / 'NvV033LogicTest.java').write_bytes((src / 'NvV033LogicTest.java').read_bytes())

# Application id.
f = root / 'android/build.gradle'
t = f.read_text(encoding='utf-8')
t = t.replace("appId = 'app.organicmaps'", "appId = 'ir.nv.navigation'")
t = t.replace("versionCode = ver.V1", "versionCode = 33")
t = t.replace("versionName = ver.V2", "versionName = '0.33.0'")
if "appId = 'ir.nv.navigation'" not in t:
    raise SystemExit('app id patch failed')
f.write_text(t, encoding='utf-8')

# QR dependency + permission whitelist.
f = root / 'android/app/build.gradle'
t = f.read_text(encoding='utf-8')
old = r"~/name='app\.organicmaps(\.web)?(\.debug|\.beta|\.profileable)?\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'/"
new = r"~/name='ir\.nv\.navigation(\.web)?(\.debug|\.beta|\.profileable)?\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'/"
if old not in t:
    raise SystemExit('permission whitelist anchor missing')
t = t.replace(old, new)
anchor = "  implementation libs.google.material\n"
if anchor not in t:
    raise SystemExit('dependency anchor missing')
t = t.replace(anchor, anchor + "  implementation 'com.google.zxing:core:3.5.3'\n", 1)
world_anchor = "  webImplementation project(':sdk:location:gms:google')\n"
if world_anchor not in t:
    raise SystemExit('web flavor dependency anchor missing')
if "webImplementation project(':sdk:maps:world')" not in t:
    t = t.replace(world_anchor,
                  "  webImplementation project(':sdk:maps:world')\n" + world_anchor, 1)
f.write_text(t, encoding='utf-8')

# Distinct menu actions + semantic search from the home bar.
f = dst / 'NvMapMenuOverlay.java'
t = f.read_text(encoding='utf-8')
t = t.replace('bar.setOnClickListener(v -> NvRuntimeController.openSearch(activity, ""));',
              'bar.setOnClickListener(v -> NvV033Actions.openSmartSearch(activity));', 1)
pattern = re.compile(r'    private void handleMenu\(int id\) \{.*?\n    \}\n\n    private void showNearby\(\)', re.S)
replacement = '''    private void handleMenu(int id) {
      switch (id) {
        case 1 -> NvV033Actions.goHome(activity);
        case 2 -> showNearby();
        case 3 -> NvSmartActions.openNearby(activity, "اورژانس");
        case 4 -> NvV033Actions.openPlaceDetails(activity);
        case 5 -> NvV033Actions.openRouteMode(activity);
        case 6 -> NvV033Actions.openRouteAlerts(activity);
        case 7 -> NvSmartActions.openNearby(activity, "داروخانه");
        case 8 -> NvSmartActions.openNearby(activity, "پارک");
        case 9 -> NvV033Actions.openSmartSearch(activity);
        case 10 -> NvV033Actions.openCompareRoutes(activity);
        case 11 -> NvV033Actions.openRadius(activity);
        case 12 -> showSOS();
        case 13 -> NvV033Actions.openChat(activity);
        case 14 -> NvV033Actions.openHurry(activity);
        case 15 -> NvV033Actions.openMixed(activity);
        case 16 -> NvV033Actions.openStationTransfer(activity);
        case 17 -> NvV033Actions.openMetroStatus(activity);
        case 18 -> NvV033Actions.openTaxi(activity);
        case 19 -> NvV033Actions.openEta(activity);
        case 20 -> NvV033Actions.openTimeCost(activity);
        case 21 -> NvV033Actions.openWalk(activity);
        case 22 -> NvV033Actions.openPreferences(activity);
        default -> NvV033Actions.openRouteMode(activity);
      }
    }

    private void showNearby()'''
t2, n = pattern.subn(replacement, t, count=1)
if n != 1:
    raise SystemExit('menu switch patch failed')
f.write_text(t2, encoding='utf-8')

# User-selected nearby radius applies to real nearby queries.
f = dst / 'NvSmartActions.java'
t = f.read_text(encoding='utf-8')
old_call = '''final List<Place> places = fetchNearby(category, loc.getLatitude(), loc.getLongitude());
        activity.runOnUiThread(() -> renderNearby(activity, ui, category, loc, places));'''
new_call = '''final int radiusMeters = NvV033Actions.getSearchRadius(activity, category.radiusMeters);
        final List<Place> places = fetchNearby(category, loc.getLatitude(), loc.getLongitude(), radiusMeters);
        activity.runOnUiThread(() -> renderNearby(activity, ui, category, loc, places, radiusMeters));'''
if old_call not in t:
    raise SystemExit('nearby call anchor missing')
t = t.replace(old_call, new_call, 1)
t = t.replace('Location origin, List<Place> places) {', 'Location origin, List<Place> places, int radiusMeters) {', 1)
t = t.replace('"در شعاع " + Math.round(category.radiusMeters / 1000f) + " کیلومتری موردی پیدا نشد."',
              '"در شعاع " + Math.round(radiusMeters / 1000f) + " کیلومتری موردی پیدا نشد."')
t = t.replace('private static List<Place> fetchNearby(NvNearbyCategory category, double lat, double lon) throws Exception {',
              'private static List<Place> fetchNearby(NvNearbyCategory category, double lat, double lon, int radiusMeters) throws Exception {')
t = t.replace('return geocodeNearby(category.fallbackQuery, lat, lon, category.radiusMeters);',
              'return geocodeNearby(category.fallbackQuery, lat, lon, radiusMeters);')
t = t.replace('return fetchNearbyFromEndpoint(endpoint, category, lat, lon);',
              'return fetchNearbyFromEndpoint(endpoint, category, lat, lon, radiusMeters);')
t = t.replace('double lat, double lon) throws Exception {\n    final String query = buildOverpassQuery(category, lat, lon);',
              'double lat, double lon, int radiusMeters) throws Exception {\n    final String query = buildOverpassQuery(category, lat, lon, radiusMeters);')
t = t.replace('if (distance > category.radiusMeters * 1.05d) continue;',
              'if (distance > radiusMeters * 1.05d) continue;')
t = t.replace('private static String buildOverpassQuery(NvNearbyCategory category, double lat, double lon) {\n    final String around = String.format(Locale.US, "(around:%d,%.7f,%.7f)", category.radiusMeters, lat, lon);',
              'private static String buildOverpassQuery(NvNearbyCategory category, double lat, double lon, int radiusMeters) {\n    final String around = String.format(Locale.US, "(around:%d,%.7f,%.7f)", radiusMeters, lat, lon);')
f.write_text(t, encoding='utf-8')

# Runtime GPS wording and current-region map handling.
f = dst / 'NvRuntimeController.java'
t = f.read_text(encoding='utf-8')
t = t.replace('  private static final double TEHRAN_LAT = 35.6892;\n  private static final double TEHRAN_LON = 51.3890;\n', '')
t = t.replace('private static final long FRESH_LOCATION_MS = 30_000L;',
              'private static final long FRESH_LOCATION_MS = NvLocationPolicy.MAX_AGE_MS;')
t = t.replace('URLEncoder.encode(query, StandardCharsets.UTF_8)', 'URLEncoder.encode(query, "UTF-8")')
t = t.replace('text = "◎  ±" + accuracy + "م";', 'text = "◎  GPS " + accuracy + "م";')
t = t.replace('text = "⚠  ±" + accuracy + "م";', 'text = "⚠  GPS " + accuracy + "م";')
t = t.replace('else if (accuracy <= 10)', 'else if (accuracy <= NvLocationPolicy.GOOD_ACCURACY_M)')
t = t.replace('else if (accuracy <= 25)', 'else if (accuracy <= NvLocationPolicy.NEARBY_ACCURACY_M)')
t = t.replace('final String accuracy = loc.hasAccuracy() ? "±" + Math.round(loc.getAccuracy()) + " متر" : "نامشخص";',
              'final String accuracy = loc.hasAccuracy() ? "خطای GPS: " + Math.round(loc.getAccuracy()) + " متر" : "نامشخص";')
t = t.replace('loc.hasAccuracy() && loc.getAccuracy() <= 10 ? GREEN : AMBER);',
              'loc.hasAccuracy() && loc.getAccuracy() <= NvLocationPolicy.GOOD_ACCURACY_M ? GREEN : (loc.hasAccuracy() && loc.getAccuracy() <= NvLocationPolicy.NEARBY_ACCURACY_M ? AMBER : RED));')
t = t.replace('loc.getAccuracy() > 50f', 'loc.getAccuracy() > NvLocationPolicy.NEARBY_ACCURACY_M')
t = t.replace('mainHandler.postDelayed(this::ensureTehranMap, 1600L);', 'mainHandler.postDelayed(this::ensureCurrentRegionMap, 1800L);')
method_re = re.compile(r'  private void ensureTehranMap\(\)\n  \{.*?\n  \}\n\n  private void refreshLocationChip', re.S)
method_new = '''  private void ensureCurrentRegionMap()
  {
    // NV v0.33 is online-first: opening or using the app never forces or prompts
    // a regional map download. Offline maps remain an explicit user choice in
    // Organic Maps' downloader.
  }

  private void refreshLocationChip'''
t2, n = method_re.subn(method_new, t, count=1)
if n != 1:
    raise SystemExit('current-region map patch failed')
t = t2.replace(
    '    customLayer.setClickable(true);\n    updateBaseVisibility();\n  }\n\n  private void beginSheet',
    '    customLayer.setClickable(true);\n    customLayer.setOnClickListener(null);\n    updateBaseVisibility();\n  }\n\n  private void beginSheet', 1)
f.write_text(t, encoding='utf-8')

# Branding.
for rel in [
    'android/libs/branding/src/main/res/values/donottranslate.xml',
    'android/libs/branding/src/debug/res/values/donottranslate.xml',
    'android/libs/branding/src/beta/res/values/donottranslate.xml',
    'android/app/src/profileable/res/values/donottranslate.xml',
]:
    f = root / rel
    if f.exists():
        s = f.read_text(encoding='utf-8')
        s = re.sub(r'(<string name="app_name"[^>]*>).*?(</string>)', r'\1NV\2', s)
        f.write_text(s, encoding='utf-8')
icon = base64.b64decode((repo_root / 'platform/nv-logo-v030-user.b64').read_text(encoding='utf-8').strip())
img = Image.open(io.BytesIO(icon)).convert('RGBA')
px = img.load()
for y in range(img.height):
    for x in range(img.width):
        r, g, b, a = px[x, y]
        mn, mx = min(r, g, b), max(r, g, b)
        if mx - mn < 20 and mn >= 248:
            px[x, y] = (r, g, b, 0)
        elif mx - mn < 20 and mn >= 228:
            alpha = max(0, min(255, (248 - mn) * 13))
            px[x, y] = (r, g, b, alpha)
buf = io.BytesIO()
img.save(buf, format='WEBP', lossless=True, quality=96, method=6)
icon_transparent = buf.getvalue()
icon_dir = root / 'android/libs/branding/src/main/res/mipmap-nodpi'
icon_dir.mkdir(parents=True, exist_ok=True)
(icon_dir / 'nv_launcher.webp').write_bytes(icon_transparent)
splash_dir = root / 'android/app/src/main/res/drawable-nodpi'
splash_dir.mkdir(parents=True, exist_ok=True)
(splash_dir / 'nv_splash_logo.webp').write_bytes(icon_transparent)

# Launcher remains SplashActivity.
f = root / 'android/app/src/main/AndroidManifest.xml'
t = f.read_text(encoding='utf-8').replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@mipmap/nv_launcher"')
launcher = '      <intent-filter>\n        <action android:name="android.intent.action.MAIN"/>\n        <category android:name="android.intent.category.LAUNCHER"/>\n      </intent-filter>\n'
if launcher not in t:
    raise SystemExit('launcher filter missing')
t = t.replace(launcher, '', 1)
splash = '    <activity\n      android:name="app.organicmaps.SplashActivity"\n      android:theme="@style/MwmTheme.Splash"\n      android:configChanges="orientation|screenSize|smallestScreenSize|density|screenLayout|uiMode|keyboard|keyboardHidden|navigation"\n      android:screenOrientation="fullUser"\n      android:exported="true">\n'
if splash not in t:
    raise SystemExit('splash anchor missing')
t = t.replace(splash, splash + '\n' + launcher, 1)
f.write_text(t, encoding='utf-8')

# Install animated NV brand on the opening screen.
f = root / 'android/app/src/main/java/app/organicmaps/SplashActivity.java'
t = f.read_text(encoding='utf-8')
anchor = '    setContentView(R.layout.activity_splash);\n'
if anchor not in t:
    raise SystemExit('SplashActivity anchor missing')
if 'NvAnimatedBrand.install(this);' not in t:
    t = t.replace(anchor, anchor + '    NvAnimatedBrand.install(this);\n', 1)
t = t.replace('private static final long DELAY = 100;', 'private static final long DELAY = 1350;')
f.write_text(t, encoding='utf-8')

# Install layers on map activity.
f = root / 'android/app/src/main/java/app/organicmaps/MwmActivity.java'
t = f.read_text(encoding='utf-8')
anchor = '    setContentView(R.layout.activity_map);\n'
if anchor not in t:
    raise SystemExit('MwmActivity anchor missing')
t = t.replace(anchor, anchor + '    NvMapMenuOverlay.install(this);\n    NvRuntimeController.install(this);\n    NvV033Actions.install(this);\n', 1)
f.write_text(t, encoding='utf-8')

# Persian resource overrides.
routing_fa = root / 'android/libs/routing/src/main/res/values-fa'
routing_fa.mkdir(parents=True, exist_ok=True)
(routing_fa / 'nv_strings.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
  <string name="transit_not_found">مسیریابی مترو در این منطقه در دسترس نیست</string>
  <string name="dialog_pedestrian_route_is_long_header">مسیر مترو پیدا نشد</string>
  <string name="dialog_pedestrian_route_is_long_message">مبدا یا مقصد را به یک ایستگاه مترو نزدیک‌تر انتخاب کنید</string>
  <string name="dialog_routing_check_gps">سیگنال GPS را بررسی کنید</string>
  <string name="dialog_routing_cant_build_route">امکان ساخت مسیر وجود ندارد.</string>
  <string name="dialog_routing_change_start_or_end">مبدا یا مقصد را اصلاح کنید.</string>
  <string name="navigation_stop_button">توقف</string>
  <string name="ok">تأیید</string>
</resources>
''', encoding='utf-8')

sdk_fa = root / 'android/sdk/src/main/res/values-fa'
sdk_fa.mkdir(parents=True, exist_ok=True)
(sdk_fa / 'nv_strings.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
  <string name="core_my_position">موقعیت من</string>
  <string name="core_placepage_unknown_place">نقطه روی نقشه</string>
  <string name="subway_data_unavailable">اطلاعات مترو در دسترس نیست</string>
  <string name="m">متر</string>
  <string name="km">کیلومتر</string>
</resources>
''', encoding='utf-8')

app_fa = root / 'android/app/src/main/res/values-fa'
app_fa.mkdir(parents=True, exist_ok=True)
(app_fa / 'nv_strings.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
  <string name="search">جستجو</string>
  <string name="search_map">جستجو روی نقشه</string>
  <string name="download">دانلود</string>
  <string name="download_resources">برای شروع، نقشه کلی جهان را دانلود کنید.\\nاین فایل %s از حافظه را استفاده می‌کند.</string>
  <string name="download_resources_continue">رفتن به نقشه</string>
  <string name="download_country_ask">نقشه %s دانلود شود؟</string>
  <string name="update_country_ask">نقشه %s به‌روزرسانی شود؟</string>
  <string name="placepage_add_stop">افزودن توقف</string>
  <string name="p2p_from_here">انتخاب مبدا</string>
  <string name="p2p_to_here">انتخاب مقصد</string>
</resources>
''', encoding='utf-8')

(root / 'NV_ENGINE_ATTRIBUTION.txt').write_text(
    'NV v0.33 is online-first on Organic Maps/OpenStreetMap. Core world assets are bundled, regional map downloads are optional, search queries Nominatim and Photon in parallel, route comparison includes connected mixed metro options, and cost/fuel estimates are not shown.\n',
    encoding='utf-8')

# Build-time assertions.
assert 'NvV033Actions.openSmartSearch' in (dst / 'NvMapMenuOverlay.java').read_text(encoding='utf-8')
assert 'NvV033Actions.openRouteAlerts' in (dst / 'NvMapMenuOverlay.java').read_text(encoding='utf-8')
assert 'NvV033Actions.openHurry(activity)' in (dst / 'NvMapMenuOverlay.java').read_text(encoding='utf-8')
assert 'case 1 -> NvV033Actions.goHome(activity);' in (dst / 'NvMapMenuOverlay.java').read_text(encoding='utf-8')
assert 'addSmartTravelPill' in (dst / 'NvMapMenuOverlay.java').read_text(encoding='utf-8')
assert (dst / 'NvAnimatedBrand.java').exists()
assert (dst / 'NvSmartTravelUi.java').exists()
assert 'getSearchRadius' in (dst / 'NvSmartActions.java').read_text(encoding='utf-8')
assert 'online_services' in (dst / 'NvV033Actions.java').read_text(encoding='utf-8')
assert (dst / 'NvLocationPolicy.java').exists()
assert (dst / 'NvV033TextParser.java').exists()
assert 'ensureCurrentRegionMap' in (dst / 'NvRuntimeController.java').read_text(encoding='utf-8')
assert 'map_prompted_region' not in (dst / 'NvRuntimeController.java').read_text(encoding='utf-8')
assert 'ensureTehranMap' not in (dst / 'NvRuntimeController.java').read_text(encoding='utf-8')
assert (icon_dir / 'nv_launcher.webp').stat().st_size > 0
assert (splash_dir / 'nv_splash_logo.webp').stat().st_size > 0
app_gradle = (root / 'android/app/build.gradle').read_text(encoding='utf-8')
assert "webImplementation project(':sdk:maps:world')" in app_gradle
actions_text = (dst / 'NvV033Actions.java').read_text(encoding='utf-8')
assert 'fuel_l100' not in actions_text
assert 'fuel_price_toman' not in actions_text
assert 'estimateMixedCostToman' not in actions_text
print('NV v0.33 online-first integration applied')
