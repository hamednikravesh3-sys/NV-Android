package app.organicmaps;

import java.util.Locale;

/**
 * Stateless, reversible NV location code.
 *
 * A code encodes latitude/longitude at 1e-5 degree resolution (roughly one metre)
 * and includes a two-character checksum. It does not require a central registry,
 * so a shared NV code keeps working even when the registry backend is unavailable.
 */
public final class NvCodeCodec
{
  private static final double SCALE = 100000.0;
  private static final int LON_BITS = 26;
  private static final long LON_MASK = (1L << LON_BITS) - 1L;
  private static final long MAX_LAT_Q = 18_000_000L;
  private static final long MAX_LON_Q = 36_000_000L;
  private static final int CHECK_MOD = 36 * 36;

  private NvCodeCodec() {}

  public record Point(double latitude, double longitude) {}

  public static String encode(double latitude, double longitude)
  {
    if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
        || latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0)
      throw new IllegalArgumentException("Invalid latitude/longitude");

    final long latQ = Math.round((latitude + 90.0) * SCALE);
    final long lonQ = Math.round((longitude + 180.0) * SCALE);
    if (latQ < 0 || latQ > MAX_LAT_Q || lonQ < 0 || lonQ > MAX_LON_Q)
      throw new IllegalArgumentException("Location is outside supported range");

    final long packed = (latQ << LON_BITS) | lonQ;
    final String body = leftPad(Long.toString(packed, 36).toUpperCase(Locale.US), 10, '0');
    final String check = leftPad(Integer.toString(checksum(packed), 36).toUpperCase(Locale.US), 2, '0');
    return "NV-" + body.substring(0, 5) + "-" + body.substring(5) + "-" + check;
  }

  public static Point decode(String input)
  {
    if (input == null)
      throw new IllegalArgumentException("NV code is empty");

    String normalized = input.trim().toUpperCase(Locale.US);
    if (normalized.startsWith("NV:"))
      normalized = normalized.substring(3);
    else if (normalized.startsWith("NV"))
      normalized = normalized.substring(2);

    normalized = normalized.replaceAll("[^0-9A-Z]", "");
    if (normalized.length() != 12)
      throw new IllegalArgumentException("NV code must contain 12 code characters");

    final String body = normalized.substring(0, 10);
    final String check = normalized.substring(10);
    final long packed;
    final int expected;
    try
    {
      packed = Long.parseLong(body, 36);
      expected = Integer.parseInt(check, 36);
    }
    catch (NumberFormatException e)
    {
      throw new IllegalArgumentException("NV code contains invalid characters", e);
    }

    if (expected != checksum(packed))
      throw new IllegalArgumentException("NV code checksum is invalid");

    final long latQ = packed >>> LON_BITS;
    final long lonQ = packed & LON_MASK;
    if (latQ < 0 || latQ > MAX_LAT_Q || lonQ < 0 || lonQ > MAX_LON_Q)
      throw new IllegalArgumentException("NV code location is invalid");

    final double lat = (latQ / SCALE) - 90.0;
    final double lon = (lonQ / SCALE) - 180.0;
    return new Point(lat, lon);
  }

  public static boolean isNvCode(String input)
  {
    if (input == null)
      return false;
    final String s = input.trim().toUpperCase(Locale.US);
    return s.startsWith("NV-") || s.startsWith("NV:");
  }

  private static int checksum(long packed)
  {
    final long mixed = packed ^ (packed >>> 17) ^ (packed >>> 34) ^ 0x4E56L;
    return (int) Math.floorMod(mixed, CHECK_MOD);
  }

  private static String leftPad(String value, int length, char c)
  {
    if (value.length() >= length)
      return value;
    final StringBuilder out = new StringBuilder(length);
    for (int i = value.length(); i < length; i++)
      out.append(c);
    out.append(value);
    return out.toString();
  }
}
