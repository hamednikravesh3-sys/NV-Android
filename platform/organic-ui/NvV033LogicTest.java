package app.organicmaps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NvV033LogicTest {
  @Test public void extractsPersianDestinationWithUrgency() {
    assertEquals("میدان تجریش",
        NvV033TextParser.extractDestination("من می‌خوام برم میدان تجریش، عجله دارم"));
  }

  @Test public void preservesMetroWhenItIsPartOfPlaceName() {
    assertEquals("ایستگاه مترو تجریش",
        NvV033TextParser.extractDestination("میخوام برم ایستگاه مترو تجریش"));
  }

  @Test public void removesTransportModifierNotDestinationName() {
    assertEquals("میدان آزادی",
        NvV033TextParser.extractDestination("لطفاً برو به میدان آزادی با مترو"));
  }

  @Test public void normalizesArabicAndPersianCharacters() {
    assertEquals("ایستگاه راه آهن یزد",
        NvV033TextParser.normalize("ايستگاه راه‌آهن يزد"));
  }

  @Test public void keepsCityQualifier() {
    assertEquals("راه آهن یزد",
        NvV033TextParser.extractDestination("می خواهم بروم راه آهن یزد سریع"));
  }
}
