package app.organicmaps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NvV031LogicTest {
  @Test public void extractsPersianDestinationWithUrgency() {
    assertEquals("میدان تجریش",
        NvV031TextParser.extractDestination("من می‌خوام برم میدان تجریش، عجله دارم"));
  }

  @Test public void preservesMetroWhenItIsPartOfPlaceName() {
    assertEquals("ایستگاه مترو تجریش",
        NvV031TextParser.extractDestination("میخوام برم ایستگاه مترو تجریش"));
  }

  @Test public void removesTransportModifierNotDestinationName() {
    assertEquals("میدان آزادی",
        NvV031TextParser.extractDestination("لطفاً برو به میدان آزادی با مترو"));
  }

  @Test public void normalizesArabicAndPersianCharacters() {
    assertEquals("ایستگاه راه آهن یزد",
        NvV031TextParser.normalize("ايستگاه راه‌آهن يزد"));
  }

  @Test public void keepsCityQualifier() {
    assertEquals("راه آهن یزد",
        NvV031TextParser.extractDestination("می خواهم بروم راه آهن یزد سریع"));
  }
}
