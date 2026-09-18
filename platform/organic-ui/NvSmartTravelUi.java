package app.organicmaps;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * NV Smart Travel visual shell based on the user's 13-22 reference panels.
 * The shell is intentionally visual; each action delegates to the existing functional NV v0.27 engines.
 */
public final class NvSmartTravelUi {
  private static final String TAG = "nv-smart-travel-ui";
  private static final int BG = Color.rgb(3, 18, 31);
  private static final int PANEL = Color.rgb(5, 34, 57);
  private static final int PANEL2 = Color.rgb(7, 48, 79);
  private static final int CYAN = Color.rgb(33, 202, 255);
  private static final int BLUE = Color.rgb(25, 115, 255);
  private static final int GREEN = Color.rgb(29, 208, 112);
  private static final int AMBER = Color.rgb(255, 185, 44);
  private static final int PURPLE = Color.rgb(142, 74, 255);
  private static final int RED = Color.rgb(255, 75, 88);
  private static final int WHITE = Color.WHITE;
  private static final int MUTED = Color.rgb(195, 219, 235);
  private static final int OUTLINE = Color.rgb(26, 145, 214);

  private static final String[] TITLES = {
      "چت هوشمند سفر", "حالت عجله دارم", "مسیر ترکیبی", "تعویض هوشمند ایستگاه", "حرکت زنده مترو",
      "هماهنگی تاکسی", "اطمینان زمان رسیدن", "مقایسه زمان و هزینه", "راهنمای پیاده", "ترجیحات سفر هوشمند"
  };

  private static final String[] SUBS = {
      "گفت‌وگوی طبیعی برای انتخاب مسیر",
      "انتخاب سریع‌ترین مسیر با درنظرگرفتن ترافیک",
      "ترکیب مترو، تاکسی و پیاده‌روی",
      "پیشنهاد بهترین خروجی و ادامه مسیر",
      "زمان‌بندی زنده و وضعیت خطوط",
      "هماهنگی زمان خروج با رسیدن تاکسی",
      "تحلیل تأخیر و اطمینان ETA",
      "انتخاب گزینه بر اساس زمان و هزینه",
      "مسیر پیاده با راهنمای تصویری",
      "شخصی‌سازی تجربه مسیریابی"
  };

  private NvSmartTravelUi() {}

  public static void openHub(MwmActivity a) {
    final Screen s = screen(a, "هوشمند سفر", "طراحی بخش ۱۳ تا ۲۲ مطابق مرجع ارسالی");
    final LinearLayout grid = new LinearLayout(a);
    grid.setOrientation(LinearLayout.VERTICAL);
    for (int i = 0; i < 10; i += 2) {
      final LinearLayout row = new LinearLayout(a);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      final int idA = 13 + i;
      row.addView(featureCard(a, idA, iconFor(idA), TITLES[i], SUBS[i]), weight(a));
      if (i + 1 < 10) {
        final int idB = 14 + i;
        row.addView(featureCard(a, idB, iconFor(idB), TITLES[i + 1], SUBS[i + 1]), weight(a));
      }
      grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 118)));
    }
    s.body.addView(grid);
  }

  public static void open(MwmActivity a, int id) {
    if (id < 13 || id > 22) { openHub(a); return; }
    switch (id) {
      case 13 -> chat(a);
      case 14 -> hurry(a);
      case 15 -> mixed(a);
      case 16 -> transfer(a);
      case 17 -> metro(a);
      case 18 -> taxi(a);
      case 19 -> eta(a);
      case 20 -> compare(a);
      case 21 -> walk(a);
      case 22 -> preferences(a);
      default -> openHub(a);
    }
  }

  private static void chat(MwmActivity a) {
    Screen s = screen(a, "چت هوشمند سفر ✦", SUBS[0]);
    s.body.addView(chatBubble(a, "من عجله دارم!", true));
    s.body.addView(chatBubble(a, "مقصد را بنویسید؛ NV موقعیت فعلی، زمان، نوع سفر و گزینه‌های ممکن را بررسی می‌کند.", false));
    s.body.addView(sectionTitle(a, "سریع‌ترین مسیر پیشنهادی", GREEN));
    s.body.addView(routeStrip(a, "🚶", "🚇", "🚕", "۲۸ دقیقه"));
    s.body.addView(mapCard(a, "موقعیت فعلی  •  مترو  •  مقصد", BLUE));
    s.body.addView(primary(a, "شروع گفت‌وگوی هوشمند", BLUE, () -> NvV027Actions.openChat(a)));
  }

  private static void hurry(MwmActivity a) {
    Screen s = screen(a, "حالت عجله دارم", SUBS[1]);
    s.body.addView(locationPair(a, "موقعیت فعلی من", "مقصد را از گفتگو یا جستجو انتخاب کنید"));
    s.body.addView(mapCard(a, "سریع‌ترین مسیر  •  ETA زنده", BLUE));
    s.body.addView(optionRow(a, "🚗  مستقیم", "زمان و فاصله جاده‌ای", "انتخاب این مسیر", BLUE));
    s.body.addView(optionRow(a, "🚇  مترو + تاکسی", "در صورت دسترسی واقعی مترو", "بررسی", PANEL2));
    s.body.addView(optionRow(a, "🚶  پیاده + مترو", "ترکیب چندمرحله‌ای", "بررسی", PANEL2));
    s.body.addView(primary(a, "محاسبه سریع‌ترین مسیر", BLUE, () -> NvV027Actions.openHurry(a)));
  }

  private static void mixed(MwmActivity a) {
    Screen s = screen(a, "مسیر ترکیبی", SUBS[2]);
    s.body.addView(locationPair(a, "مبدأ: موقعیت فعلی", "مقصد: انتخاب مقصد"));
    s.body.addView(chipRow(a, new String[]{"۴۲ دقیقه", "۱۴ km", "تغییر مسیر"}));
    s.body.addView(step(a, "۱", "🚕", "تاکسی تا ایستگاه مترو", "زمان و فاصله بر اساس موقعیت واقعی", GREEN));
    s.body.addView(step(a, "۲", "🚇", "مترو", "فقط پس از بررسی وجود ایستگاه نزدیک", BLUE));
    s.body.addView(step(a, "۳", "🚶", "پیاده‌روی و تعویض خط", "هدایت مرحله‌ای", PURPLE));
    s.body.addView(step(a, "۴", "🚕", "تاکسی تا مقصد", "در صورت نیاز", GREEN));
    s.body.addView(primary(a, "شروع سفر", BLUE, () -> NvV027Actions.openMixed(a)));
  }

  private static void transfer(MwmActivity a) {
    Screen s = screen(a, "تعویض هوشمند ایستگاه", SUBS[3]);
    s.body.addView(mapCard(a, "ایستگاه فعلی  ●━━━━●  خروجی پیشنهادی", BLUE));
    s.body.addView(successCard(a, "خروجی پیشنهادی NV", "کمترین پیاده‌روی • دسترسی بهتر • مسیر ساده‌تر"));
    s.body.addView(step(a, "۱", "🚇", "در ایستگاه پیاده شوید", "خروجی مناسب روی نقشه مشخص می‌شود", BLUE));
    s.body.addView(step(a, "۲", "↗", "از خروجی پیشنهادی خارج شوید", "با توجه به ادامه مسیر", CYAN));
    s.body.addView(step(a, "۳", "🚶", "ادامه مسیر تا مقصد", "راهنمای قدم‌به‌قدم", GREEN));
    s.body.addView(primary(a, "بررسی ایستگاه و خروجی‌ها", BLUE, () -> NvV027Actions.openStationTransfer(a)));
  }

  private static void metro(MwmActivity a) {
    Screen s = screen(a, "حرکت زنده مترو", SUBS[4]);
    s.body.addView(mapCard(a, "خطوط مترو  ●  ●  ●  وضعیت سرویس", PURPLE));
    s.body.addView(successCard(a, "وضعیت کلی مترو", "اطلاعات فقط در صورت وجود داده معتبر نمایش داده می‌شود"));
    s.body.addView(metric(a, "قطار بعدی", "بررسی بر اساس داده موجود", BLUE));
    s.body.addView(metric(a, "وضعیت خط", "فعال / نامشخص", GREEN));
    s.body.addView(primary(a, "مشاهده ایستگاه‌های مترو", BLUE, () -> NvV027Actions.openMetroStatus(a)));
  }

  private static void taxi(MwmActivity a) {
    Screen s = screen(a, "هماهنگی تاکسی", SUBS[5]);
    s.body.addView(mapCard(a, "ایستگاه / خروجی  ───  محل سوار شدن تاکسی", AMBER));
    s.body.addView(metric(a, "زمان هماهنگ‌شده", "پس از محاسبه مسیر", BLUE));
    s.body.addView(metric(a, "فاصله تا محل سوار شدن", "بر اساس موقعیت فعلی", GREEN));
    s.body.addView(primary(a, "هماهنگی و پیدا کردن تاکسی", BLUE, () -> NvV027Actions.openTaxi(a)));
  }

  private static void eta(MwmActivity a) {
    Screen s = screen(a, "اطمینان زمان رسیدن", SUBS[6]);
    final LinearLayout hero = card(a, BLUE);
    final TextView big = text(a, "ETA\n۱۰:۰۳", 30, WHITE, Typeface.BOLD);
    big.setGravity(Gravity.CENTER);
    big.setPadding(dp(a, 10), dp(a, 14), dp(a, 10), dp(a, 14));
    hero.addView(big);
    hero.addView(text(a, "اطمینان پویا بر اساس موتور مسیر، فاصله و سرعت واقعی دستگاه", 13, MUTED, Typeface.NORMAL));
    s.body.addView(hero);
    s.body.addView(metric(a, "ریسک ترافیک یا تأخیر", "در صورت وجود داده معتبر", RED));
    s.body.addView(metric(a, "عملیات عمرانی در مسیر", "در صورت وجود هشدار نقشه", AMBER));
    s.body.addView(metric(a, "احتمال بارش", "وابسته به منبع داده", CYAN));
    s.body.addView(primary(a, "محاسبه ETA واقعی", BLUE, () -> NvV027Actions.openEta(a)));
  }

  private static void compare(MwmActivity a) {
    Screen s = screen(a, "مقایسه زمان و هزینه", SUBS[7]);
    s.body.addView(optionRow(a, "🚗  سریع‌ترین", "خودرو • زمان و فاصله", "بررسی", BLUE));
    s.body.addView(optionRow(a, "🚶🚇🚕  متعادل", "پیاده + حمل‌ونقل عمومی + تاکسی", "بررسی", GREEN));
    s.body.addView(optionRow(a, "🚗  اقتصادی", "مصرف تقریبی سوخت بر اساس تنظیمات", "بررسی", AMBER));
    s.body.addView(primary(a, "مشاهده مقایسه واقعی", BLUE, () -> NvV027Actions.openTimeCost(a)));
  }

  private static void walk(MwmActivity a) {
    Screen s = screen(a, "راهنمای پیاده", SUBS[8]);
    s.body.addView(mapCard(a, "↱  ۱۵۰ متر  •  سپس به سمت راست بپیچید", CYAN));
    s.body.addView(metric(a, "فاصله باقی‌مانده", "از موتور مسیریابی پیاده", BLUE));
    s.body.addView(metric(a, "زمان باقی‌مانده", "برآورد مسیر پیاده", GREEN));
    s.body.addView(primary(a, "شروع راهنمای پیاده", BLUE, () -> NvV027Actions.openWalk(a)));
  }

  private static void preferences(MwmActivity a) {
    Screen s = screen(a, "ترجیحات سفر هوشمند", SUBS[9]);
    s.body.addView(toggleLike(a, "استفاده از مترو (اولویت بالا)", true, "🚇"));
    s.body.addView(toggleLike(a, "استفاده از تاکسی", true, "🚕"));
    s.body.addView(toggleLike(a, "به حداقل رساندن هزینه", false, "♙"));
    s.body.addView(toggleLike(a, "سریع‌ترین مسیر را پیشنهاد بده", true, "⚡"));
    s.body.addView(toggleLike(a, "اجتناب از بزرگراه‌ها", false, "⊘"));
    s.body.addView(toggleLike(a, "مسیرهای پیاده‌روی کمتر", false, "🚶"));
    s.body.addView(toggleLike(a, "ترجیح مسیرهای امن‌تر", true, "🛡"));
    s.body.addView(primary(a, "ذخیره و تنظیم ترجیحات واقعی", BLUE, () -> NvV027Actions.openPreferences(a)));
  }

  private static View featureCard(MwmActivity a, int id, String icon, String title, String sub) {
    final LinearLayout c = card(a, id == 14 ? RED : (id == 15 ? GREEN : BLUE));
    final TextView ic = text(a, icon, 23, id == 14 ? RED : CYAN, Typeface.BOLD);
    ic.setGravity(Gravity.CENTER);
    c.addView(ic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 34)));
    final TextView t = text(a, title, 14, WHITE, Typeface.BOLD);
    t.setGravity(Gravity.CENTER);
    c.addView(t);
    final TextView st = text(a, sub, 10, MUTED, Typeface.NORMAL);
    st.setGravity(Gravity.CENTER);
    st.setMaxLines(2);
    c.addView(st);
    c.setClickable(true);
    c.setOnClickListener(v -> open(a, id));
    return c;
  }

  private static Screen screen(MwmActivity a, String title, String subtitle) {
    remove(a);
    final ViewGroup host = a.findViewById(android.R.id.content);
    final FrameLayout root = new FrameLayout(a);
    root.setTag(TAG);
    root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    root.setBackgroundColor(BG);
    root.setClickable(true);
    root.setElevation(dp(a, 40));
    host.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    final LinearLayout column = new LinearLayout(a);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    column.setPadding(dp(a, 12), dp(a, 28), dp(a, 12), dp(a, 12));

    final LinearLayout header = new LinearLayout(a);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    final TextView close = text(a, "‹", 34, WHITE, Typeface.NORMAL);
    close.setGravity(Gravity.CENTER);
    close.setClickable(true);
    close.setOnClickListener(v -> remove(a));
    header.addView(close, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 52)));

    final LinearLayout titleBox = new LinearLayout(a);
    titleBox.setOrientation(LinearLayout.VERTICAL);
    final TextView t = text(a, title, 19, WHITE, Typeface.BOLD);
    final TextView st = text(a, subtitle, 11, MUTED, Typeface.NORMAL);
    st.setMaxLines(2);
    titleBox.addView(t);
    titleBox.addView(st);
    header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    final View nv = NvAnimatedBrand.createLogo(a, 13, () -> NvRuntimeController.showCodeMenu(a));
    header.addView(nv, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 40)));
    column.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 64)));

    final ScrollView scroll = new ScrollView(a);
    scroll.setFillViewport(true);
    final LinearLayout body = new LinearLayout(a);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    body.setPadding(dp(a, 2), dp(a, 4), dp(a, 2), dp(a, 18));
    scroll.addView(body);
    column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return new Screen(root, body);
  }

  private static void remove(MwmActivity a) {
    final ViewGroup host = a.findViewById(android.R.id.content);
    if (host == null) return;
    final View v = host.findViewWithTag(TAG);
    if (v != null) host.removeView(v);
  }

  private static View chatBubble(MwmActivity a, String msg, boolean mine) {
    final TextView v = text(a, msg, 14, WHITE, mine ? Typeface.BOLD : Typeface.NORMAL);
    v.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
    v.setBackground(round(a, mine ? BLUE : PANEL2, mine ? CYAN : OUTLINE, 18, 1));
    final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(a, mine ? 235 : 300), ViewGroup.LayoutParams.WRAP_CONTENT);
    p.gravity = mine ? Gravity.RIGHT : Gravity.LEFT;
    p.setMargins(dp(a, 6), dp(a, 5), dp(a, 6), dp(a, 5));
    v.setLayoutParams(p);
    return v;
  }

  private static View locationPair(MwmActivity a, String from, String to) {
    LinearLayout c = card(a, BLUE);
    c.addView(metric(a, "●  " + from, "موقعیت فعلی", CYAN));
    c.addView(metric(a, "●  " + to, "مقصد", RED));
    return c;
  }

  private static View mapCard(MwmActivity a, String label, int accent) {
    final FrameLayout map = new FrameLayout(a);
    map.setBackground(round(a, Color.rgb(7, 39, 60), accent, 18, 2));
    final TextView grid = text(a, "╲  ╱   ╲══╱   ╲  ╱\n  ●━━━━━━◉━━━━━━●\n╱   ╲   ╱  ╲   ╱", 17, Color.argb(180, 65, 175, 220), Typeface.BOLD);
    grid.setGravity(Gravity.CENTER);
    map.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    final TextView cap = text(a, label, 13, WHITE, Typeface.BOLD);
    cap.setGravity(Gravity.CENTER);
    cap.setBackground(round(a, Color.argb(210, 4, 24, 40), accent, 12, 1));
    FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44), Gravity.BOTTOM);
    cp.setMargins(dp(a, 8), 0, dp(a, 8), dp(a, 8));
    map.addView(cap, cp);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 176));
    p.setMargins(dp(a, 3), dp(a, 7), dp(a, 3), dp(a, 7));
    map.setLayoutParams(p);
    return map;
  }

  private static View routeStrip(MwmActivity a, String a1, String a2, String a3, String total) {
    final LinearLayout c = card(a, GREEN);
    c.setOrientation(LinearLayout.HORIZONTAL);
    c.setGravity(Gravity.CENTER);
    c.addView(node(a, a1, "۷ دقیقه", AMBER), weight(a));
    c.addView(node(a, a2, "۱۴ دقیقه", BLUE), weight(a));
    c.addView(node(a, a3, "۷ دقیقه", GREEN), weight(a));
    c.addView(node(a, total, "کل سفر", CYAN), weight(a));
    return c;
  }

  private static View node(MwmActivity a, String icon, String sub, int color) {
    LinearLayout box = new LinearLayout(a);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setGravity(Gravity.CENTER);
    TextView t = text(a, icon, 18, color, Typeface.BOLD); t.setGravity(Gravity.CENTER); box.addView(t);
    TextView s = text(a, sub, 10, MUTED, Typeface.NORMAL); s.setGravity(Gravity.CENTER); box.addView(s);
    return box;
  }

  private static View optionRow(MwmActivity a, String title, String sub, String action, int accent) {
    final LinearLayout c = card(a, accent);
    final LinearLayout texts = new LinearLayout(a);
    texts.setOrientation(LinearLayout.VERTICAL);
    texts.addView(text(a, title, 15, WHITE, Typeface.BOLD));
    texts.addView(text(a, sub, 11, MUTED, Typeface.NORMAL));
    c.addView(texts);
    final TextView aText = text(a, action, 11, accent == PANEL2 ? CYAN : accent, Typeface.BOLD);
    aText.setPadding(0, dp(a, 5), 0, 0);
    c.addView(aText);
    return c;
  }

  private static View step(MwmActivity a, String n, String icon, String title, String sub, int color) {
    final LinearLayout row = new LinearLayout(a);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    row.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8));
    row.setBackground(round(a, PANEL2, OUTLINE, 16, 1));
    final TextView num = text(a, n, 13, WHITE, Typeface.BOLD); num.setGravity(Gravity.CENTER); num.setBackground(round(a, color, color, 20, 1));
    row.addView(num, new LinearLayout.LayoutParams(dp(a, 34), dp(a, 34)));
    final TextView ic = text(a, icon, 20, color, Typeface.BOLD); ic.setGravity(Gravity.CENTER); row.addView(ic, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 42)));
    final LinearLayout tx = new LinearLayout(a); tx.setOrientation(LinearLayout.VERTICAL); tx.addView(text(a, title, 14, WHITE, Typeface.BOLD)); tx.addView(text(a, sub, 10, MUTED, Typeface.NORMAL));
    row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    p.setMargins(dp(a, 3), dp(a, 4), dp(a, 3), dp(a, 4)); row.setLayoutParams(p);
    return row;
  }

  private static View chipRow(MwmActivity a, String[] labels) {
    LinearLayout row = new LinearLayout(a); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    for (String s : labels) { TextView t = text(a, s, 11, WHITE, Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setBackground(round(a, PANEL2, OUTLINE, 14, 1)); row.addView(t, weight(a)); }
    row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44))); return row;
  }

  private static View successCard(MwmActivity a, String title, String sub) {
    final LinearLayout c = card(a, GREEN);
    c.addView(text(a, "✓  " + title, 17, WHITE, Typeface.BOLD));
    c.addView(text(a, sub, 11, Color.rgb(201, 243, 217), Typeface.NORMAL));
    return c;
  }

  private static View metric(MwmActivity a, String title, String value, int color) {
    final LinearLayout row = new LinearLayout(a);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8));
    final TextView v = text(a, value, 12, color, Typeface.BOLD); v.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
    row.addView(v, new LinearLayout.LayoutParams(dp(a, 135), ViewGroup.LayoutParams.WRAP_CONTENT));
    final TextView t = text(a, title, 13, WHITE, Typeface.BOLD);
    row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    return row;
  }

  private static View sectionTitle(MwmActivity a, String title, int color) {
    TextView t = text(a, title, 15, color, Typeface.BOLD); t.setPadding(dp(a, 4), dp(a, 9), dp(a, 4), dp(a, 4)); return t;
  }

  private static View toggleLike(MwmActivity a, String title, boolean on, String icon) {
    final LinearLayout row = new LinearLayout(a);
    row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    row.setPadding(dp(a, 10), dp(a, 7), dp(a, 10), dp(a, 7)); row.setBackground(round(a, PANEL, OUTLINE, 14, 1));
    TextView i = text(a, icon, 18, on ? CYAN : MUTED, Typeface.BOLD); i.setGravity(Gravity.CENTER); row.addView(i, new LinearLayout.LayoutParams(dp(a, 40), dp(a, 38)));
    TextView t = text(a, title, 13, WHITE, Typeface.BOLD); row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    TextView sw = text(a, on ? "●━━" : "━━●", 15, on ? BLUE : Color.GRAY, Typeface.BOLD); sw.setGravity(Gravity.CENTER); row.addView(sw, new LinearLayout.LayoutParams(dp(a, 62), dp(a, 36)));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 52)); p.setMargins(0, dp(a, 3), 0, dp(a, 3)); row.setLayoutParams(p); return row;
  }

  private static LinearLayout card(MwmActivity a, int accent) {
    final LinearLayout c = new LinearLayout(a);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    c.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
    c.setBackground(round(a, PANEL, accent, 18, 1));
    c.setElevation(dp(a, 4));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    p.setMargins(dp(a, 3), dp(a, 5), dp(a, 3), dp(a, 5));
    c.setLayoutParams(p);
    return c;
  }

  private static TextView primary(MwmActivity a, String title, int color, Runnable action) {
    final TextView b = text(a, title, 15, WHITE, Typeface.BOLD);
    b.setGravity(Gravity.CENTER);
    b.setBackground(round(a, color, CYAN, 16, 1));
    b.setClickable(true);
    b.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 54));
    p.setMargins(dp(a, 3), dp(a, 10), dp(a, 3), dp(a, 5));
    b.setLayoutParams(p);
    return b;
  }

  private static TextView text(Context c, String s, int sp, int color, int style) {
    final TextView v = new TextView(c);
    v.setText(s);
    v.setTextSize(sp);
    v.setTextColor(color);
    v.setTypeface(Typeface.DEFAULT, style);
    v.setGravity(Gravity.RIGHT);
    v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    v.setTextDirection(View.TEXT_DIRECTION_RTL);
    return v;
  }

  private static GradientDrawable round(Context c, int fill, int stroke, int radius, int strokeDp) {
    final GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(dp(c, radius));
    d.setStroke(dp(c, strokeDp), stroke);
    return d;
  }

  private static LinearLayout.LayoutParams weight(Context c) {
    final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    p.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
    return p;
  }

  private static String iconFor(int id) {
    switch (id) {
      case 13: return "✦";
      case 14: return "⚡";
      case 15: return "↝";
      case 16: return "M";
      case 17: return "🚇";
      case 18: return "🚕";
      case 19: return "◷";
      case 20: return "⇄";
      case 21: return "🚶";
      case 22: return "⚙";
      default: return "NV";
    }
  }

  private static int dp(Context c, int v) {
    return Math.max(1, Math.round(c.getResources().getDisplayMetrics().density)) * v;
  }

  private static final class Screen {
    final FrameLayout root;
    final LinearLayout body;
    Screen(FrameLayout root, LinearLayout body) { this.root = root; this.body = body; }
  }
}
