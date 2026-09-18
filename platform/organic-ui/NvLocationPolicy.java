package app.organicmaps;

/** Shared GPS quality policy for all NV routing/search features. */
final class NvLocationPolicy {
  static final long MAX_AGE_MS = 75_000L;
  static final float GOOD_ACCURACY_M = 60f;
  static final float WARN_ACCURACY_M = 120f;
  static final float NEARBY_ACCURACY_M = 100f;

  private NvLocationPolicy() {}
}
