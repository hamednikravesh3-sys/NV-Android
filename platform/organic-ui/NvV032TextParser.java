package app.organicmaps;

import java.util.Locale;

/** Pure-Java Persian trip text parser used by NV smart travel. */
final class NvV032TextParser {
  private NvV032TextParser() {}

  static String extractDestination(String raw) {
    String q = normalize(raw);
    if (q.isEmpty()) return "";

    q = q.replaceAll("^(لطفا|لطفاً)\\s+", "");
    q = q.replaceAll("^(من\\s+)?(می ?خوام|میخواهم|می خواهم)\\s+(برم|بروم|برم به|بروم به)?\\s*", "");
    q = q.replaceAll("^(برو|بریم|برویم)\\s+(به\\s+)?", "");

    int to = Math.max(q.lastIndexOf(" برم به "), q.lastIndexOf(" بروم به "));
    if (to >= 0)
      q = q.substring(to + (q.startsWith(" بروم به ", to) ? 8 : 7)).trim();
    else {
      int go = Math.max(q.lastIndexOf(" برم "), q.lastIndexOf(" بروم "));
      if (go >= 0) q = q.substring(go + (q.startsWith(" بروم ", go) ? 6 : 5)).trim();
    }

    if (q.startsWith("از ")) {
      int viaTo = q.lastIndexOf(" به ");
      int viaUntil = q.lastIndexOf(" تا ");
      int marker = Math.max(viaTo, viaUntil);
      if (marker > 2)
        q = q.substring(marker + 4).trim();
    }

    if (q.startsWith("به ")) q = q.substring(3).trim();

    String[] suffixes = {
        "عجله دارم", "خیلی عجله دارم", "عجله", "خیلی سریع", "سریع", "فوری", "زود",
        "مسیر ترکیبی", "ترکیبی", "با حمل و نقل عمومی", "با حمل‌ونقل عمومی",
        "با تاکسی", "با اسنپ", "با ماشین", "با خودرو", "با مترو", "پیاده"
    };

    boolean changed;
    do {
      changed = false;
      for (String suffix : suffixes) {
        String ns = normalize(suffix);
        if (q.endsWith(" " + ns)) {
          q = q.substring(0, q.length() - ns.length()).trim();
          changed = true;
        }
      }
    } while (changed);

    return q.replaceAll("[،,؛;:!؟?]+", " ").replaceAll("\\s+", " ").trim();
  }

  static String extractOrigin(String raw) {
    String q = normalize(raw);
    if (q.isEmpty()) return "";

    int from = q.indexOf("از ");
    if (from < 0) return "";
    String tail = q.substring(from + 3).trim();
    if (tail.isEmpty()) return "";

    String[] separators = {
        " برم به ", " بروم به ", " میخوام برم ", " می خواهم برم ", " می خواهم بروم ",
        " برم ", " بروم ", " به ", " تا "
    };
    int cut = -1;
    for (String separator : separators) {
      int i = tail.indexOf(separator);
      if (i > 0 && (cut < 0 || i < cut)) cut = i;
    }
    if (cut <= 0) return "";

    String origin = tail.substring(0, cut).trim();
    origin = origin.replaceAll("^(مبدا|مبدأ)\\s*[:：]?\\s*", "");
    return origin.replaceAll("[،,؛;:!؟?]+", " ").replaceAll("\\s+", " ").trim();
  }

  static String normalize(String s) {
    if (s == null) return "";
    String n = s.replace('ي','ی').replace('ى','ی').replace('ك','ک')
        .replace('ة','ه').replace('ۀ','ه').replace("\u200c"," ").replace("ـ","");
    n = n.replaceAll("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]", "");
    n = n.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
        .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
        .replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4')
        .replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
    return n.replaceAll("\\s+"," ").trim().toLowerCase(Locale.ROOT);
  }
}
