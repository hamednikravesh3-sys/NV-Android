package app.organicmaps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NvV032LogicTest {
  @Test public void extractsPersianDestinationWithUrgency() {
    assertEquals("میدان تجریش",
        NvV032TextParser.extractDestination("من می‌خوام برم میدان تجریش، عجله دارم"));
  }

  @Test public void preservesMetroWhenItIsPartOfPlaceName() {
    assertEquals("ایستگاه مترو تجریش",
        NvV032TextParser.extractDestination("میخوام برم ایستگاه مترو تجریش"));
  }

  @Test public void removesTransportModifierNotDestinationName() {
    assertEquals("میدان آزادی",
        NvV032TextParser.extractDestination("لطفاً برو به میدان آزادی با مترو"));
  }

  @Test public void extractsExplicitOriginAndDestination() {
    assertEquals("میدان انقلاب",
        NvV032TextParser.extractOrigin("میخوام از میدان انقلاب برم میدان ونک"));
    assertEquals("میدان ونک",
        NvV032TextParser.extractDestination("میخوام از میدان انقلاب برم میدان ونک"));
  }

  @Test public void extractsCompactOriginExactlyLikeUserInput() {
    assertEquals("میدان انقلاب",
        NvV032TextParser.extractOrigin("میخوام ازمیدان انقلاب برم میدان ونک"));
    assertEquals("میدان ونک",
        NvV032TextParser.extractDestination("میخوام ازمیدان انقلاب برم میدان ونک"));
  }

  @Test public void extractsOriginAndDestinationWithBeMarker() {
    assertEquals("میدان انقلاب",
        NvV032TextParser.extractOrigin("از میدان انقلاب به میدان ونک"));
    assertEquals("میدان ونک",
        NvV032TextParser.extractDestination("از میدان انقلاب به میدان ونک"));
  }

  @Test public void noExplicitOriginFallsBackToGps() {
    assertEquals("", NvV032TextParser.extractOrigin("میخوام برم میدان ونک عجله دارم"));
  }

  @Test public void normalizesArabicAndPersianCharacters() {
    assertEquals("ایستگاه راه آهن یزد",
        NvV032TextParser.normalize("ايستگاه راه‌آهن يزد"));
  }

  @Test public void keepsCityQualifier() {
    assertEquals("راه آهن یزد",
        NvV032TextParser.extractDestination("می خواهم بروم راه آهن یزد سریع"));
  }
}
