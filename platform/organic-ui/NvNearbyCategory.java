package app.organicmaps;

/**
 * Canonical NV nearby categories used by the smart menu and runtime search.
 */
public final class NvNearbyCategory {
  public final String id;
  public final String title;
  public final String overpassFilter;
  public final String fallbackQuery;
  public final int radiusMeters;

  private NvNearbyCategory(String id, String title, String overpassFilter, String fallbackQuery, int radiusMeters) {
    this.id = id;
    this.title = title;
    this.overpassFilter = overpassFilter;
    this.fallbackQuery = fallbackQuery;
    this.radiusMeters = radiusMeters;
  }

  public static NvNearbyCategory from(String raw) {
    final String key = raw == null ? "" : raw.trim().toLowerCase();
    if (key.contains("اورژانس") || key.contains("emergency"))
      return new NvNearbyCategory("emergency", "اورژانس‌های نزدیک", "[\"emergency\"=\"ambulance_station\"]", "اورژانس", 20000);
    if (key.contains("بیمارستان") || key.contains("hospital"))
      return new NvNearbyCategory("hospital", "بیمارستان‌های نزدیک", "[\"amenity\"=\"hospital\"]", "بیمارستان", 20000);
    if (key.contains("درمانگاه") || key.contains("clinic"))
      return new NvNearbyCategory("clinic", "درمانگاه‌های نزدیک", "[\"amenity\"=\"clinic\"]", "درمانگاه", 15000);
    if (key.contains("داروخانه") || key.contains("pharmacy"))
      return new NvNearbyCategory("pharmacy", "داروخانه‌های نزدیک", "[\"amenity\"=\"pharmacy\"]", "داروخانه", 12000);
    if (key.contains("پلیس") || key.contains("police"))
      return new NvNearbyCategory("police", "پلیس‌های نزدیک", "[\"amenity\"=\"police\"]", "پلیس", 20000);
    if (key.contains("آتش") || key.contains("fire"))
      return new NvNearbyCategory("fire", "آتش‌نشانی‌های نزدیک", "[\"amenity\"=\"fire_station\"]", "آتش نشانی", 20000);
    if (key.contains("پارکینگ") || key.contains("parking"))
      return new NvNearbyCategory("parking", "پارکینگ‌های نزدیک", "[\"amenity\"=\"parking\"]", "پارکینگ", 10000);
    if (key.contains("بنزین") || key.contains("fuel"))
      return new NvNearbyCategory("fuel", "پمپ بنزین‌های نزدیک", "[\"amenity\"=\"fuel\"]", "پمپ بنزین", 20000);
    if (key.contains("رستوران") || key.contains("restaurant"))
      return new NvNearbyCategory("restaurant", "رستوران‌های نزدیک", "[\"amenity\"=\"restaurant\"]", "رستوران", 8000);
    if (key.contains("کافه") || key.contains("cafe"))
      return new NvNearbyCategory("cafe", "کافه‌های نزدیک", "[\"amenity\"=\"cafe\"]", "کافه", 8000);
    if (key.contains("پارک") || key.contains("park"))
      return new NvNearbyCategory("park", "پارک‌های نزدیک", "[\"leisure\"=\"park\"]", "پارک", 12000);
    if (key.contains("مترو") || key.contains("subway"))
      return new NvNearbyCategory("subway", "ایستگاه‌های مترو نزدیک", "[\"railway\"=\"station\"][\"station\"=\"subway\"]", "ایستگاه مترو", 15000);
    if (key.contains("تاکسی") || key.contains("taxi"))
      return new NvNearbyCategory("taxi", "ایستگاه‌های تاکسی نزدیک", "[\"amenity\"=\"taxi\"]", "ایستگاه تاکسی", 15000);
    if (key.contains("هتل") || key.contains("hotel"))
      return new NvNearbyCategory("hotel", "هتل‌های نزدیک", "[\"tourism\"=\"hotel\"]", "هتل", 15000);
    return new NvNearbyCategory("generic", "نتایج نزدیک", "", raw == null ? "" : raw.trim(), 12000);
  }
}
