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
 * Native NV Smart Travel UI for menu items 13-22.
 * The layout follows the supplied dark-neon reference: top title, real map visible through the
 * center on map-centric screens, rounded cards, large blue primary actions and RTL Persian text.
 */
public final class NvSmartTravelUi {
  private static final String TAG = "nv-smart-travel-ui-v029";
  private static final int BG = Color.rgb(2, 17, 30);
  private static final int PANEL = Color.rgb(5, 32, 55);
  private static final int PANEL2 = Color.rgb(7, 48, 79);
  private static final int CYAN = Color.rgb(35, 205, 255);
  private static final int BLUE = Color.rgb(25, 116, 255);
  private static final int GREEN = Color.rgb(26, 207, 109);
  private static final int AMBER = Color.rgb(255, 183, 42);
  private static final int PURPLE = Color.rgb(139, 78, 255);
  private static final int RED = Color.rgb(255, 76, 90);
  private static final int WHITE = Color.WHITE;
  private static final int MUTED = Color.rgb(197, 219, 233);
  private static final int OUTLINE = Color.rgb(30, 151, 219);

  private static final String[] TITLES = {
      "چت هوشمند سفر", "حالت عجله دارم", "مسیر ترکیبی", "تعویض هوشمند ایستگاه", "مترو و ایستگاه‌ها",
      "تاکسی و محل سوارشدن", "اطمینان زمان رسیدن", "مقایسه زمان و هزینه", "راهنمای پیاده", "ترجیحات سفر هوشمند"
  };

  private NvSmartTravelUi() {}

  public static void openHub(MwmActivity a) {
    Screen s = screen(a, "هوشمند سفر", "بخش‌های ۱۳ تا ۲۲ • هر کارت یک موتور مستقل", false);
    s.body.addView(infoBanner(a, "✦", "هر کارت مستقیماً صفحه عملیاتی همان قابلیت را باز می‌کند", "هیچ صفحه نمایشی واسط بین کارت و موتور اصلی وجود ندارد.", CYAN));

    final LinearLayout grid = new LinearLayout(a);
    grid.setOrientation(LinearLayout.VERTICAL);
    for (int i = 0; i < 10; i += 2) {
      LinearLayout row = new LinearLayout(a);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

      int leftId = 13 + i;
      int rightId = 14 + i;
      row.addView(featureCard(a, leftId, icon(leftId), TITLES[i]), weight(a));
      if (i + 1 < 10)
        row.addView(featureCard(a, rightId, icon(rightId), TITLES[i + 1]), weight(a));
      grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 112)));
    }
    s.body.addView(grid);
  }

  public static void open(MwmActivity a, int id) {
    switch (id) {
      case 13 -> NvV031Actions.openChat(a);
      case 14 -> NvV031Actions.openHurry(a);
      case 15 -> NvV031Actions.openMixed(a);
      case 16 -> NvV031Actions.openStationTransfer(a);
      case 17 -> NvV031Actions.openMetroStatus(a);
      case 18 -> NvV031Actions.openTaxi(a);
      case 19 -> NvV031Actions.openEta(a);
      case 20 -> NvV031Actions.openTimeCost(a);
      case 21 -> NvV031Actions.openWalk(a);
      case 22 -> NvV031Actions.openPreferences(a);
      default -> openHub(a);
    }
  }

  private static void chat(MwmActivity a) {
    Screen s = screen(a, "چت هوشمند سفر ✦", "گفت‌وگوی طبیعی برای انتخاب مسیر", false);
    s.body.addView(chatBubble(a, "من عجله دارم!", true));
    s.body.addView(chatBubble(a, "مقصد را بگویید؛ NV نوع سفر، مسیرهای قابل استفاده و زمان تقریبی را بررسی می‌کند.", false));
    s.body.addView(section(a, "سریع‌ترین مسیر پیشنهادی", GREEN));
    s.body.addView(infoBanner(a, "NV", "بدون عدد ساختگی",
        "زمان، فاصله و نوع سفر فقط بعد از انتخاب مقصد و محاسبه واقعی نمایش داده می‌شود.", GREEN));
    s.body.addView(referenceMapCard(a, "موقعیت فعلی  •  مترو  •  تاکسی  •  مقصد", BLUE));
    s.body.addView(primary(a, "شروع چت و انتخاب مقصد", BLUE, () -> go(a, () -> NvV031Actions.openChat(a))));
  }

  private static void hurry(MwmActivity a) {
    Screen s = screen(a, "حالت عجله دارم", "انتخاب سریع‌ترین گزینه عملی", true);
    s.body.addView(locationCard(a, "●  موقعیت فعلی من", "●  مقصد"));
    s.body.addView(mapWindow(a, "سریع‌ترین مسیر روی نقشه • ETA زنده"));
    s.body.addView(option(a, "🚗  مستقیم", "مسیر خودرو • کمترین زمان قابل محاسبه", BLUE));
    s.body.addView(option(a, "🚇  مترو + تاکسی", "در صورت وجود ایستگاه قابل استفاده", GREEN));
    s.body.addView(option(a, "🚶  پیاده + مترو", "برای سفرهای شهری مناسب", PURPLE));
    s.body.addView(primary(a, "محاسبه سریع‌ترین مسیر", BLUE, () -> go(a, () -> NvV031Actions.openHurry(a))));
  }

  private static void mixed(MwmActivity a) {
    Screen s = screen(a, "مسیر ترکیبی", "ترکیب مترو، تاکسی و پیاده‌روی", true);
    s.body.addView(locationCard(a, "●  مبدأ: موقعیت فعلی", "●  مقصد: انتخاب مقصد"));
    s.body.addView(chips(a, new String[]{"ETA", "فاصله", "تعویض مسیر"}));
    s.body.addView(mapWindow(a, "نمایش سفر چندمرحله‌ای روی نقشه"));
    s.body.addView(step(a, "۱", "🚕", "تاکسی تا ایستگاه", "فاصله و زمان پس از انتخاب مقصد", GREEN));
    s.body.addView(step(a, "۲", "🚇", "مترو", "بررسی ایستگاه مناسب مبدأ و مقصد", BLUE));
    s.body.addView(step(a, "۳", "🚶", "پیاده‌روی / تعویض خط", "هدایت مرحله‌به‌مرحله", PURPLE));
    s.body.addView(step(a, "۴", "🚕", "تاکسی تا مقصد", "در صورت نیاز", GREEN));
    s.body.addView(primary(a, "محاسبه و شروع سفر", BLUE, () -> go(a, () -> NvV031Actions.openMixed(a))));
  }

  private static void stationTransfer(MwmActivity a) {
    Screen s = screen(a, "تعویض هوشمند ایستگاه", "پیشنهاد بهترین خروجی و ادامه مسیر", true);
    s.body.addView(mapWindow(a, "ایستگاه فعلی  ●━━━━●  خروجی پیشنهادی"));
    s.body.addView(infoBanner(a, "✓", "بهترین خروجی NV", "کمترین پیاده‌روی • دسترسی ساده‌تر • ادامه مسیر سریع‌تر", GREEN));
    s.body.addView(step(a, "۱", "🚇", "در ایستگاه مناسب پیاده شوید", "بر اساس مقصد نهایی", BLUE));
    s.body.addView(step(a, "۲", "↗", "خروجی پیشنهادی را انتخاب کنید", "خروجی نزدیک به ادامه مسیر", CYAN));
    s.body.addView(step(a, "۳", "🚶", "ادامه مسیر", "پیاده یا تاکسی", GREEN));
    s.body.addView(primary(a, "پیدا کردن ایستگاه و خروجی", BLUE, () -> go(a, () -> NvV031Actions.openStationTransfer(a))));
  }

  private static void metro(MwmActivity a) {
    Screen s = screen(a, "مترو و ایستگاه‌ها", "ایستگاه‌های نزدیک و وضعیت داده‌های موجود", true);
    s.body.addView(mapWindow(a, "ایستگاه‌های مترو روی نقشه • نزدیک‌ترین‌ها"));
    s.body.addView(infoBanner(a, "🚇", "وضعیت کلی مترو", "فقط اطلاعات معتبر و موجود نمایش داده می‌شود؛ داده ساختگی نمایش داده نمی‌شود.", GREEN));
    s.body.addView(metric(a, "ایستگاه نزدیک", "بررسی از موقعیت فعلی", BLUE));
    s.body.addView(metric(a, "وضعیت سرویس", "فعال / نامشخص", GREEN));
    s.body.addView(primary(a, "مشاهده مترو و ایستگاه‌ها", BLUE, () -> go(a, () -> NvV031Actions.openMetroStatus(a))));
  }

  private static void taxi(MwmActivity a) {
    Screen s = screen(a, "تاکسی و محل سوارشدن", "نقاط تاکسی ثبت‌شده و مسیر تا محل سوارشدن", true);
    s.body.addView(mapWindow(a, "خروجی ایستگاه  ───  محل سوار شدن تاکسی"));
    s.body.addView(infoBanner(a, "🚕", "محل سوار شدن", "نقطه مناسب بعد از محاسبه مسیر و خروجی تعیین می‌شود.", AMBER));
    s.body.addView(metric(a, "زمان خروج", "پس از محاسبه مسیر", BLUE));
    s.body.addView(metric(a, "فاصله تا سوار شدن", "از موقعیت فعلی", GREEN));
    s.body.addView(primary(a, "پیدا کردن تاکسی و ادامه مسیر", BLUE, () -> go(a, () -> NvV031Actions.openTaxi(a))));
  }

  private static void eta(MwmActivity a) {
    Screen s = screen(a, "اطمینان زمان رسیدن", "تحلیل ETA و عوامل تغییر زمان", false);
    LinearLayout hero = panel(a, BLUE);
    TextView eta = text(a, "ETA\nپس از محاسبه مسیر", 25, WHITE, Typeface.BOLD);
    eta.setGravity(Gravity.CENTER);
    hero.addView(eta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 110)));
    TextView confidence = text(a, "٪ اطمینان پس از محاسبه مسیر واقعی", 13, CYAN, Typeface.BOLD);
    confidence.setGravity(Gravity.CENTER);
    hero.addView(confidence);
    s.body.addView(hero);
    s.body.addView(metric(a, "ترافیک یا تأخیر", "بر اساس داده در دسترس", RED));
    s.body.addView(metric(a, "وضعیت مسیر", "بسته / عملیات / هشدار", AMBER));
    s.body.addView(metric(a, "سرعت واقعی دستگاه", "در محاسبه ETA استفاده می‌شود", GREEN));
    s.body.addView(primary(a, "محاسبه ETA واقعی", BLUE, () -> go(a, () -> NvV031Actions.openEta(a))));
  }

  private static void compare(MwmActivity a) {
    Screen s = screen(a, "مقایسه زمان و هزینه", "انتخاب بهترین گزینه بر اساس نیاز", false);
    s.body.addView(option(a, "🚗  سریع‌ترین", "خودرو • زمان و فاصله", BLUE));
    s.body.addView(option(a, "🚕🚇🚶  متعادل", "ترکیب تاکسی، مترو و پیاده", GREEN));
    s.body.addView(option(a, "⛽  اقتصادی", "مصرف تقریبی سوخت بر اساس تنظیمات", AMBER));
    s.body.addView(infoBanner(a, "⇄", "مقایسه واقعی", "بعد از انتخاب مقصد، زمان و فاصله گزینه‌ها محاسبه می‌شود.", CYAN));
    s.body.addView(primary(a, "محاسبه و مقایسه", BLUE, () -> go(a, () -> NvV031Actions.openTimeCost(a))));
  }

  private static void walk(MwmActivity a) {
    Screen s = screen(a, "راهنمای پیاده", "مسیر پیاده با راهنمای مرحله‌ای", true);
    s.body.addView(mapWindow(a, "راهنمای پیچ بعدی پس از شروع مسیر واقعی نمایش داده می‌شود"));
    s.body.addView(metric(a, "فاصله باقی‌مانده", "از موتور مسیر پیاده", BLUE));
    s.body.addView(metric(a, "زمان باقی‌مانده", "برآورد پویا", GREEN));
    s.body.addView(primary(a, "شروع راهنمای پیاده", BLUE, () -> go(a, () -> NvV031Actions.openWalk(a))));
  }

  private static void preferences(MwmActivity a) {
    Screen s = screen(a, "ترجیحات سفر هوشمند", "تنظیمات ذخیره می‌شوند و در محاسبات NV استفاده می‌شوند", false);
    s.body.addView(toggleRow(a, "استفاده از مترو (اولویت بالا)", "use_metro", true, "🚇"));
    s.body.addView(toggleRow(a, "استفاده از تاکسی", "use_taxi", true, "🚕"));
    s.body.addView(toggleRow(a, "به حداقل رساندن هزینه", "min_cost", false, "₮"));
    s.body.addView(toggleRow(a, "سریع‌ترین مسیر را پیشنهاد بده", "prefer_fastest", true, "⚡"));
    s.body.addView(toggleRow(a, "اجتناب از بزرگراه‌ها", "avoid_highways", false, "⊘"));
    s.body.addView(toggleRow(a, "مسیرهای پیاده‌روی کمتر", "less_walking", false, "🚶"));
    s.body.addView(toggleRow(a, "ترجیح مسیرهای امن‌تر", "safer_route", true, "🛡"));
    s.body.addView(infoBanner(a, "i", "تنظیمات واقعی",
        "مترو، تاکسی، هزینه و میزان پیاده‌روی مستقیماً در برنامه‌ریز NV استفاده می‌شوند. محدودیت بزرگراه و ایمنی به قابلیت موتور پایه نیز وابسته‌اند.",
        CYAN));
    s.body.addView(primary(a, "تنظیم مصرف سوخت و هشدارها", BLUE, () -> go(a, () -> NvV031Actions.openPreferences(a))));
  }

  private static Screen screen(MwmActivity a, String title, String subtitle, boolean revealMap) {
    remove(a);
    ViewGroup host = a.findViewById(android.R.id.content);
    FrameLayout root = new FrameLayout(a);
    root.setTag(TAG);
    root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    root.setBackgroundColor(revealMap ? Color.argb(105, 0, 8, 16) : BG);
    root.setElevation(dp(a, 60));
    host.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout column = new LinearLayout(a);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    column.setPadding(dp(a, 12), dp(a, 26), dp(a, 12), dp(a, 10));

    LinearLayout header = new LinearLayout(a);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    header.setPadding(dp(a, 4), dp(a, 3), dp(a, 4), dp(a, 3));
    header.setBackground(round(a, Color.argb(235, 4, 28, 48), OUTLINE, 18, 1));

    View nv = NvAnimatedBrand.createLogo(a, 13, () -> NvRuntimeController.showCodeMenu(a));
    header.addView(nv, new LinearLayout.LayoutParams(dp(a, 52), dp(a, 42)));

    LinearLayout tb = new LinearLayout(a);
    tb.setOrientation(LinearLayout.VERTICAL);
    TextView tt = text(a, title, 19, WHITE, Typeface.BOLD);
    TextView ss = text(a, subtitle, 11, MUTED, Typeface.NORMAL);
    ss.setMaxLines(2);
    tb.addView(tt);
    tb.addView(ss);
    header.addView(tb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    TextView close = text(a, "×", 28, WHITE, Typeface.NORMAL);
    close.setGravity(Gravity.CENTER);
    close.setClickable(true);
    close.setOnClickListener(v -> remove(a));
    header.addView(close, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
    column.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 70)));

    ScrollView sv = new ScrollView(a);
    sv.setFillViewport(true);
    sv.setBackgroundColor(Color.TRANSPARENT);
    LinearLayout body = new LinearLayout(a);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    body.setPadding(0, dp(a, 5), 0, dp(a, 20));
    sv.addView(body);
    column.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return new Screen(root, body);
  }

  private static void go(MwmActivity a, Runnable r) {
    r.run();
  }

  private static void remove(MwmActivity a) {
    ViewGroup host = a.findViewById(android.R.id.content);
    if (host == null) return;
    View v = host.findViewWithTag(TAG);
    if (v != null) host.removeView(v);
  }

  private static View featureCard(MwmActivity a, int id, String icon, String title) {
    int accent = id == 14 ? RED : (id == 15 ? GREEN : (id == 17 ? PURPLE : BLUE));
    LinearLayout c = panel(a, accent);
    c.setGravity(Gravity.CENTER);
    TextView ic = text(a, icon, 24, accent, Typeface.BOLD); ic.setGravity(Gravity.CENTER);
    TextView t = text(a, id + ". " + title, 13, WHITE, Typeface.BOLD); t.setGravity(Gravity.CENTER);
    TextView sub = text(a, shortSub(id), 9, MUTED, Typeface.NORMAL); sub.setGravity(Gravity.CENTER); sub.setMaxLines(2);
    c.addView(ic);
    c.addView(t);
    c.addView(sub);
    c.setClickable(true);
    c.setOnClickListener(v -> open(a, id));
    return c;
  }

  private static View mapWindow(MwmActivity a, String caption) {
    FrameLayout w = new FrameLayout(a);
    w.setBackground(round(a, Color.argb(28, 5, 40, 65), CYAN, 20, 2));
    w.setClickable(false);
    TextView badge = text(a, "نقشه زنده NV", 11, CYAN, Typeface.BOLD);
    badge.setGravity(Gravity.CENTER);
    badge.setBackground(round(a, Color.argb(225, 5, 30, 50), CYAN, 12, 1));
    FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(a, 110), dp(a, 34), Gravity.TOP | Gravity.RIGHT);
    bp.setMargins(0, dp(a, 10), dp(a, 10), 0);
    w.addView(badge, bp);
    TextView cap = text(a, caption, 13, WHITE, Typeface.BOLD);
    cap.setGravity(Gravity.CENTER);
    cap.setBackground(round(a, Color.argb(225, 3, 24, 42), OUTLINE, 14, 1));
    FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 48), Gravity.BOTTOM);
    cp.setMargins(dp(a, 10), 0, dp(a, 10), dp(a, 10));
    w.addView(cap, cp);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 245));
    lp.setMargins(dp(a, 2), dp(a, 7), dp(a, 2), dp(a, 7));
    w.setLayoutParams(lp);
    return w;
  }

  private static View referenceMapCard(MwmActivity a, String caption, int accent) {
    FrameLayout w = new FrameLayout(a);
    w.setBackground(round(a, Color.rgb(6, 42, 66), accent, 18, 2));
    TextView route = text(a, "●━━━━━●━━━━━●\n  🚶      🚇      🚕", 19, CYAN, Typeface.BOLD);
    route.setGravity(Gravity.CENTER);
    w.addView(route, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    TextView cap = text(a, caption, 12, WHITE, Typeface.BOLD);
    cap.setGravity(Gravity.CENTER);
    cap.setBackground(round(a, Color.argb(220, 4, 26, 44), accent, 12, 1));
    FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44), Gravity.BOTTOM);
    cp.setMargins(dp(a, 8), 0, dp(a, 8), dp(a, 8));
    w.addView(cap, cp);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 170));
    p.setMargins(dp(a, 3), dp(a, 6), dp(a, 3), dp(a, 6));
    w.setLayoutParams(p);
    return w;
  }

  private static View locationCard(MwmActivity a, String from, String to) {
    LinearLayout c = panel(a, BLUE);
    c.addView(metric(a, from, "مبدأ", CYAN));
    c.addView(metric(a, to, "مقصد", RED));
    return c;
  }



  private static View step(MwmActivity a, String n, String icon, String title, String sub, int accent) {
    LinearLayout r = new LinearLayout(a);
    r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    r.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8)); r.setBackground(round(a, PANEL2, OUTLINE, 16, 1));
    TextView num = text(a, n, 13, WHITE, Typeface.BOLD); num.setGravity(Gravity.CENTER); num.setBackground(round(a, accent, accent, 18, 1));
    r.addView(num, new LinearLayout.LayoutParams(dp(a, 34), dp(a, 34)));
    TextView ic = text(a, icon, 19, accent, Typeface.BOLD); ic.setGravity(Gravity.CENTER); r.addView(ic, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 42)));
    LinearLayout tx = new LinearLayout(a); tx.setOrientation(LinearLayout.VERTICAL); tx.addView(text(a, title, 13, WHITE, Typeface.BOLD)); tx.addView(text(a, sub, 10, MUTED, Typeface.NORMAL));
    r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(dp(a, 2), dp(a, 4), dp(a, 2), dp(a, 4)); r.setLayoutParams(lp);
    return r;
  }

  private static View option(MwmActivity a, String title, String sub, int accent) {
    LinearLayout c = panel(a, accent);
    c.addView(text(a, title, 15, WHITE, Typeface.BOLD));
    c.addView(text(a, sub, 11, MUTED, Typeface.NORMAL));
    return c;
  }

  private static View infoBanner(MwmActivity a, String icon, String title, String sub, int accent) {
    LinearLayout c = panel(a, accent);
    LinearLayout top = new LinearLayout(a); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL); top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    TextView ic = text(a, icon, 19, accent, Typeface.BOLD); ic.setGravity(Gravity.CENTER); top.addView(ic, new LinearLayout.LayoutParams(dp(a, 42), dp(a, 38)));
    TextView t = text(a, title, 15, WHITE, Typeface.BOLD); top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    c.addView(top); c.addView(text(a, sub, 11, MUTED, Typeface.NORMAL));
    return c;
  }

  private static View metric(MwmActivity a, String title, String value, int accent) {
    LinearLayout r = new LinearLayout(a);
    r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    r.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8)); r.setBackground(round(a, PANEL, OUTLINE, 14, 1));
    TextView t = text(a, title, 13, WHITE, Typeface.BOLD); r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    TextView v = text(a, value, 11, accent, Typeface.BOLD); v.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); r.addView(v, new LinearLayout.LayoutParams(dp(a, 150), ViewGroup.LayoutParams.WRAP_CONTENT));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, dp(a, 3), 0, dp(a, 3)); r.setLayoutParams(lp);
    return r;
  }

  private static View chips(MwmActivity a, String[] labels) {
    LinearLayout row = new LinearLayout(a); row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    for (String label : labels) {
      TextView t = text(a, label, 11, WHITE, Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setBackground(round(a, PANEL2, OUTLINE, 14, 1)); row.addView(t, weight(a));
    }
    row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 42)));
    return row;
  }

  private static View chatBubble(MwmActivity a, String msg, boolean mine) {
    TextView b = text(a, msg, 13, WHITE, mine ? Typeface.BOLD : Typeface.NORMAL);
    b.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
    b.setBackground(round(a, mine ? BLUE : PANEL2, mine ? CYAN : OUTLINE, 17, 1));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, mine ? 220 : 310), ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.gravity = mine ? Gravity.RIGHT : Gravity.LEFT; lp.setMargins(dp(a, 5), dp(a, 5), dp(a, 5), dp(a, 5)); b.setLayoutParams(lp);
    return b;
  }

  private static View section(MwmActivity a, String title, int accent) {
    TextView t = text(a, title, 14, accent, Typeface.BOLD); t.setPadding(dp(a, 3), dp(a, 8), dp(a, 3), dp(a, 3)); return t;
  }

  private static View toggleRow(MwmActivity a, String title, String key, boolean defaultValue, String icon) {
    android.content.SharedPreferences prefs = a.getSharedPreferences("nv_v030", Context.MODE_PRIVATE);
    final boolean[] state = {prefs.getBoolean(key, defaultValue)};

    LinearLayout r = new LinearLayout(a);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    r.setPadding(dp(a, 10), dp(a, 6), dp(a, 10), dp(a, 6));
    r.setBackground(round(a, PANEL, OUTLINE, 14, 1));
    r.setClickable(true);

    TextView i = text(a, icon, 18, state[0] ? CYAN : MUTED, Typeface.BOLD);
    i.setGravity(Gravity.CENTER);
    r.addView(i, new LinearLayout.LayoutParams(dp(a, 42), dp(a, 38)));

    TextView t = text(a, title, 13, WHITE, Typeface.BOLD);
    r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    TextView sw = text(a, state[0] ? "●━━" : "━━●", 15, state[0] ? BLUE : Color.GRAY, Typeface.BOLD);
    sw.setGravity(Gravity.CENTER);
    r.addView(sw, new LinearLayout.LayoutParams(dp(a, 62), dp(a, 36)));

    r.setOnClickListener(v -> {
      state[0] = !state[0];
      prefs.edit().putBoolean(key, state[0]).apply();
      i.setTextColor(state[0] ? CYAN : MUTED);
      sw.setText(state[0] ? "●━━" : "━━●");
      sw.setTextColor(state[0] ? BLUE : Color.GRAY);
    });

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 52));
    lp.setMargins(0, dp(a, 3), 0, dp(a, 3));
    r.setLayoutParams(lp);
    return r;
  }

  private static LinearLayout panel(MwmActivity a, int accent) {
    LinearLayout c = new LinearLayout(a);
    c.setOrientation(LinearLayout.VERTICAL); c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    c.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
    c.setBackground(round(a, Color.argb(245, 5, 32, 55), accent, 18, 1));
    c.setElevation(dp(a, 4));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(dp(a, 3), dp(a, 5), dp(a, 3), dp(a, 5)); c.setLayoutParams(lp);
    return c;
  }

  private static TextView primary(MwmActivity a, String title, int color, Runnable r) {
    TextView b = text(a, title, 15, WHITE, Typeface.BOLD); b.setGravity(Gravity.CENTER);
    b.setBackground(round(a, color, CYAN, 17, 1)); b.setClickable(true); b.setOnClickListener(v -> r.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 56)); lp.setMargins(dp(a, 3), dp(a, 10), dp(a, 3), dp(a, 5)); b.setLayoutParams(lp);
    return b;
  }

  private static TextView text(Context c, String s, int sp, int color, int style) {
    TextView v = new TextView(c); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(Typeface.DEFAULT, style);
    v.setGravity(Gravity.RIGHT); v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); v.setTextDirection(View.TEXT_DIRECTION_RTL); return v;
  }

  private static GradientDrawable round(Context c, int fill, int stroke, int radius, int strokeDp) {
    GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(c, radius)); d.setStroke(dp(c, strokeDp), stroke); return d;
  }

  private static LinearLayout.LayoutParams weight(Context c) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); p.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3)); return p;
  }

  private static String shortSub(int id) {
    switch (id) {
      case 13: return "گفت‌وگوی هوشمند";
      case 14: return "سریع‌ترین گزینه";
      case 15: return "مترو + تاکسی + پیاده";
      case 16: return "خروجی و ایستگاه";
      case 17: return "خطوط و ایستگاه‌ها";
      case 18: return "خروج و تاکسی";
      case 19: return "ETA و اطمینان";
      case 20: return "زمان، هزینه و مصرف";
      case 21: return "هدایت پیاده";
      case 22: return "تنظیم تجربه سفر";
      default: return "";
    }
  }

  private static String icon(int id) {
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
    final FrameLayout root; final LinearLayout body;
    Screen(FrameLayout root, LinearLayout body) { this.root = root; this.body = body; }
  }
}
