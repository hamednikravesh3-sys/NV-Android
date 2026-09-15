from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected text not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Version bump.
build = Path("app/build.gradle.kts")
replace_once(build, 'versionCode = 24', 'versionCode = 25')
replace_once(build, 'versionName = "0.18.5"', 'versionName = "0.18.6"')

# Online map: make the normal/base map explicitly OpenStreetMap raster tiles.
map_file = Path("app/src/main/java/ir/nv/navigation/map/OnlineIranMap.kt")
replace_once(
    map_file,
    "/** Online vector map with genuine camera pitch, two-finger rotation and 3D buildings. */",
    "/** Online OpenStreetMap base map with NV routing/location overlays and optional satellite mode. */",
)
replace_once(
    map_file,
    "            setupThreeDimensionalBuildings(loadedStyle)\n",
    "            // The standard OpenStreetMap style is raster-only. 3D vector buildings\n"
    "            // are kept only for the optional satellite/vector-overlay mode.\n"
    "            if (satelliteMode) setupThreeDimensionalBuildings(loadedStyle)\n",
)
replace_once(
    map_file,
    "        } else {\n            readyMap.setStyle(if (darkMode) DARK_STYLE_URL else DAY_STYLE_URL, onStyleLoaded)\n        }\n",
    "        } else {\n"
    "            readyMap.setStyle(Style.Builder().fromJson(openStreetMapStyleJson(darkMode)), onStyleLoaded)\n"
    "        }\n",
)
marker = "    private fun satelliteStyleJson(night: Boolean): String {\n"
text = map_file.read_text(encoding="utf-8")
if marker not in text:
    raise SystemExit("satelliteStyleJson marker not found")
osm_fun = '''    private fun openStreetMapStyleJson(night: Boolean): String {\n        val rasterPaint = if (night) {\n            "\\\"raster-brightness-max\\\":0.62,\\\"raster-brightness-min\\\":0.05,\\\"raster-saturation\\\":-0.35,\\\"raster-contrast\\\":0.16,"\n        } else ""\n        return """{\n          "version": 8,\n          "name": "NV OpenStreetMap",\n          "sources": {\n            "osm-standard": {\n              "type": "raster",\n              "tiles": ["$OSM_TILE_URL"],\n              "tileSize": 256,\n              "minzoom": 0,\n              "maxzoom": 19,\n              "attribution": "$OSM_ATTRIBUTION"\n            }\n          },\n          "layers": [\n            {\n              "id": "osm-standard-layer",\n              "type": "raster",\n              "source": "osm-standard",\n              "paint": { $rasterPaint "raster-fade-duration": 100 }\n            }\n          ]\n        }""".trimIndent()\n    }\n\n'''
map_file.write_text(text.replace(marker, osm_fun + marker, 1), encoding="utf-8")
replace_once(
    map_file,
    '        const val DAY_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"\n        const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"\n',
    '        const val OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"\n'
    '        const val OSM_ATTRIBUTION = "© OpenStreetMap contributors"\n',
)

# Home UI: identify the provider and expose the province downloader as its own menu.
home = Path("app/src/main/java/ir/nv/navigation/ui/RahnamaHome.kt")
replace_once(home, '                    text = if (state.satelliteMode) "ماهواره‌ای" else "نقشه",',
             '                    text = if (state.satelliteMode) "ماهواره‌ای" else "OpenStreetMap",')
replace_once(home, '                Text("نقشه‌های آفلاین منطقه‌ای", color = NvColors.TextSecondaryDark)',
             '                Text("دانلود نقشه استان‌ها", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Black)\n'
             '                Text("منوی مستقل ۳۱ استان • داده‌های OpenStreetMap", color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)')
replace_once(home, '                    "از این بخش می‌توانید بسته هر استان را جداگانه دریافت کنید؛ شهرستان‌های هر استان داخل همان بسته استانی قرار می‌گیرند.",',
             '                    "هر استان را جداگانه دانلود، لغو یا حذف کنید. بسته‌ها شامل نقشه، جستجو و مسیریابی آفلاین همان استان هستند.",')
replace_once(home, '                    Text(if (state.satelliteMode) "بازگشت به نقشه عادی" else "نمای ماهواره‌ای")',
             '                    Text(if (state.satelliteMode) "بازگشت به OpenStreetMap" else "نمای ماهواره‌ای")')

# Province overlay: make it unmistakably a dedicated province menu and keep OSM provenance visible.
province = Path("app/src/main/java/ir/nv/navigation/ui/ProvinceDownloadOverlay.kt")
replace_once(province, '        Text("دانلود آفلاین")', '        Text("فهرست ۳۱ استان")')
replace_once(province, '            title = { Text("نقشه آفلاین", fontWeight = FontWeight.Black) },',
             '            title = { Text("دانلود نقشه استان‌ها", fontWeight = FontWeight.Black) },')
replace_once(
    province,
    '                    Text("بسته کامل ایران اکنون قابل دانلود است. بسته‌های ۳۱ استان نیز هر زمان روی سرور منتشر شوند به‌صورت مستقل فعال می‌شوند.")',
    '                    Text("بسته‌های استانی NV از داده‌های OpenStreetMap ساخته می‌شوند و هر استان مستقل دانلود، نصب و حذف می‌شود.")',
)
replace_once(
    province,
    '                        Text(\n                            "هنوز هیچ بسته استانی مستقلی روی سرور منتشر نشده است؛ به‌جای دکمه‌های غیرفعال می‌توانید همین حالا بسته کامل ایران را دانلود کنید.",\n                            fontWeight = FontWeight.Bold\n                        )',
    '                        Text(\n                            "در حال حاضر بسته استانی منتشرشده‌ای روی سرور پیدا نشد؛ اتصال را بررسی کنید و دوباره وارد این منو شوید.",\n                            fontWeight = FontWeight.Bold\n                        )',
)
# The full-Iran pack remains controlled by its own Settings switch; do not mix it into the province menu.
replace_once(
    province,
    '                    IranPackFallbackRow(\n                        status = iranPackStatus,\n                        onStart = onStartIranDownload,\n                        onRetry = onRetryIranDownload,\n                        onCancel = onCancelIranDownload\n                    )\n\n                    Text("بسته‌های استانی", fontWeight = FontWeight.Black)',
    '                    Text("استان‌ها", fontWeight = FontWeight.Black)',
)

print("v0.18.6 OpenStreetMap + province menu patch applied")
