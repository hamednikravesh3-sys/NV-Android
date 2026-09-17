package app.organicmaps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NvCodeCodecTest
{
  @Test
  public void roundTripTehranIsWithinOneMeterCell()
  {
    double lat = 35.6892;
    double lon = 51.3890;
    String code = NvCodeCodec.encode(lat, lon);
    assertTrue(code.startsWith("NV-"));
    NvCodeCodec.Point point = NvCodeCodec.decode(code);
    assertEquals(lat, point.latitude(), 0.00001);
    assertEquals(lon, point.longitude(), 0.00001);
  }

  @Test
  public void tolerantFormattingStillDecodes()
  {
    String code = NvCodeCodec.encode(35.7448, 51.3753);
    String compact = code.replace("NV-", "NV:").replace("-", " ");
    NvCodeCodec.Point point = NvCodeCodec.decode(compact);
    assertEquals(35.7448, point.latitude(), 0.00001);
    assertEquals(51.3753, point.longitude(), 0.00001);
  }

  @Test
  public void badChecksumIsRejected()
  {
    String code = NvCodeCodec.encode(35.6892, 51.3890);
    String bad = code.substring(0, code.length() - 1) + (code.endsWith("0") ? "1" : "0");
    assertThrows(IllegalArgumentException.class, () -> NvCodeCodec.decode(bad));
  }
}
