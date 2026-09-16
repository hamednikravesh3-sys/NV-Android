package app.organicmaps;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.sdk.settings.MapLanguageCode;

/**
 * NV map-first Persian UI. This layer is attached directly to MwmActivity, so all
 * NV menus remain on the real Organic Maps screen instead of opening a separate home activity.
 */
public final class NvMapMenuOverlay {
  private static final int NAVY = Color.rgb(4, 18, 33);
  private static final int PANEL = Color.rgb(8, 39, 64);
  private static final int PANEL_2 = Color.rgb(12, 55, 86);
  private static final int CYAN = Color.rgb(28, 210, 255);
  private static final int BLUE = Color.rgb(37, 132, 255);
  private static final int GREEN = Color.rgb(49, 219, 108);
  private static final int AMBER = Color.rgb(255, 183, 45);
  private static final int RED = Color.rgb(244, 64, 84);
  private static final int WHITE = Color.rgb(247, 251, 255);
  private static final int MUTED = Color.rgb(171, 192, 208);
  private static final int OUTLINE = Color.rgb(31, 103, 144);

  private static final double TEHRAN_LAT = 35.6892;
  private static final double TEHRAN_LON = 51.3890;

  private static final String[] TITLES = {
      "صفحه اصلی", "اطراف من", "اورژانس", "جزئیات مکان", "حالت مسیریابی", "هشدارهای مسیر",
      "داروخانه", "پارک و تفریح", "جستجوی هوشمند", "مقایسه مسیرها", "محدوده جستجو", "حالت اضطراری",
      "چت هوشمند سفر", "حالت عجله دارم", "مسیر ترکیبی", "تعویض هوشمند ایستگاه", "حرکت زنده مترو",
      "هماهنگی تاکسی", "اطمینان زمان رسیدن", "مقایسه زمان و هزینه", "راهنمای پیاده", "ترجیحات سفر هوشمند"
  };

  private static final String[] ICONS = {
      "⌂", "◎", "✚", "●", "➤", "!", "✚", "♣", "⌕", "⇄", "◉", "SOS",
      "☏", "⚡", "↝", "M", "M", "T", "◷", "₮", "↟", "⚙"
  };

  private static final String[] DESCRIPTIONS = {
      "نقشه تهران، جستجو، موقعیت و سرویس‌های سریع در همین صفحه.",
      "دسترسی سریع به خدمات نزدیک؛ نتیجه واقعی توسط موتور جستجوی نقشه نمایش داده می‌شود.",
      "جستجوی اورژانس، بیمارستان، درمانگاه و خدمات پزشکی نزدیک.",
      "پس از انتخاب هر مکان، اطلاعات و عملیات مرتبط روی نقشه در دسترس است.",
      "انتخاب مقصد و ورود به مسیریابی واقعی Organic Maps.",
      "نمایش هشدارهای مرتبط با مسیر در صورت وجود داده معتبر در موتور نقشه.",
      "جستجوی داروخانه‌های اطراف روی نقشه.",
      "پارک، فضای سبز و مراکز تفریحی نزدیک.",
      "جستجوی فارسی مقصد، نام مکان و دسته‌بندی‌ها.",
      "پس از انتخاب مبدا و مقصد، گزینه‌های واقعی مسیر را در صفحه مسیریابی بررسی کنید.",
      "جستجوی نقطه‌ها و خدمات در محدوده اطراف نقشه.",
      "دسترسی فوری به شماره‌های امدادی و خدمات ضروری.",
      "راهنمای محلی سفر؛ برای پاسخ‌های زنده هوشمند نیاز به سرویس آنلاین مستقل است.",
      "ورود سریع به انتخاب مقصد برای کمینه کردن زمان شروع سفر.",
      "ترکیب پیاده، خودرو و حمل‌ونقل عمومی بر اساس امکانات هسته مسیریابی.",
      "جستجوی ایستگاه‌های مترو و نقاط تعویض مسیر روی نقشه.",
      "ایستگاه‌های مترو روی نقشه واقعی هستند؛ زمان زنده قطار نیازمند API رسمی مترو است.",
      "جستجوی تاکسی و نقاط حمل‌ونقل؛ رزرو زنده نیازمند اتصال ارائه‌دهنده تاکسی است.",
      "زمان رسیدن پس از تشکیل مسیر از موتور مسیریابی محاسبه می‌شود.",
      "مقایسه زمان مسیر واقعی؛ قیمت زنده فقط با اتصال سرویس حمل‌ونقل قابل محاسبه است.",
      "انتخاب مقصد برای مسیر پیاده و راهنمای قدم‌به‌قدم.",
      "زبان نقشه فارسی، نقشه اولیه تهران و کنترل ترجیحات پایه NV."
  };

  private NvMapMenuOverlay() {}

  public static void install(MwmActivity activity) {
    final ViewGroup host = activity.findViewById(android.R.id.content);
    if (host == null || host.findViewWithTag("nv-map-overlay") != null)
      return;
    new Controller(activity, host).install();
  }

  private static final class Controller {
    private final MwmActivity activity;
    private final ViewGroup host;
    private final int density;
    private FrameLayout overlay;
    private FrameLayout sheetLayer;

    Controller(MwmActivity activity, ViewGroup host) {
      this.activity = activity;
      this.host = host;
      this.density = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
    }

    void install() {
      overlay = new FrameLayout(activity);
      overlay.setTag("nv-map-overlay");
      overlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      overlay.setClipChildren(false);
      overlay.setClipToPadding(false);
      overlay.setClickable(false);
      host.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      addTopSearch();
      addServiceRail();
      addAllMenuStrip();
      addBottomDock();
      addSheetLayer();

      try {
        MapLanguageCode.setMapLanguageCode("fa");
      } catch (Throwable ignored) {}

      overlay.postDelayed(this::ensureTehranDefaults, 1400L);
    }

    private void addTopSearch() {
      LinearLayout bar = new LinearLayout(activity);
      bar.setOrientation(LinearLayout.HORIZONTAL);
      bar.setGravity(Gravity.CENTER_VERTICAL);
      bar.setPadding(dp(12), dp(8), dp(12), dp(8));
      bar.setBackground(round(PANEL, CYAN, 18));
      bar.setElevation(dp(9));
      bar.setClickable(true);
      bar.setOnClickListener(v -> activity.showSearch(""));

      TextView search = label("کجا می‌خواهید بروید؟", 15, WHITE, Typeface.BOLD, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
      search.setSingleLine(true);
      bar.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1f));

      TextView icon = label("⌕", 27, CYAN, Typeface.BOLD, Gravity.CENTER);
      bar.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(44)));

      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      lp.setMargins(dp(78), dp(36), dp(12), 0);
      overlay.addView(bar, lp);

      TextView nv = label("NV", 17, WHITE, Typeface.BOLD, Gravity.CENTER);
      nv.setBackground(round(PANEL, OUTLINE, 18));
      nv.setElevation(dp(9));
      nv.setOnClickListener(v -> showAllMenus());
      FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(dp(56), dp(60), Gravity.TOP | Gravity.LEFT);
      nlp.setMargins(dp(12), dp(36), 0, 0);
      overlay.addView(nv, nlp);
    }

    private void addServiceRail() {
      LinearLayout rail = new LinearLayout(activity);
      rail.setOrientation(LinearLayout.VERTICAL);
      rail.setGravity(Gravity.CENTER);
      rail.setPadding(dp(4), dp(4), dp(4), dp(4));
      rail.setBackground(round(Color.argb(225, 6, 28, 47), OUTLINE, 20));
      rail.setClickable(true);
      rail.setElevation(dp(8));

      rail.addView(railButton("✚", "اورژانس", RED, () -> search("اورژانس")));
      rail.addView(railButton("H", "بیمارستان", BLUE, () -> search("بیمارستان")));
      rail.addView(railButton("+", "داروخانه", GREEN, () -> search("داروخانه")));
      rail.addView(railButton("P", "پارکینگ", CYAN, () -> search("پارکینگ")));
      rail.addView(railButton("M", "مترو", AMBER, () -> search("ایستگاه مترو")));
      rail.addView(railButton("⋮", "همه", WHITE, this::showAllMenus));

      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
      lp.setMargins(dp(8), 0, 0, dp(70));
      overlay.addView(rail, lp);
    }

    private View railButton(String icon, String title, int accent, Runnable action) {
      LinearLayout box = new LinearLayout(activity);
      box.setOrientation(LinearLayout.VERTICAL);
      box.setGravity(Gravity.CENTER);
      box.setPadding(dp(2), dp(4), dp(2), dp(4));
      box.setClickable(true);
      box.setOnClickListener(v -> action.run());
      LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(56), dp(59));
      blp.setMargins(0, dp(2), 0, dp(2));
      box.setLayoutParams(blp);

      TextView a = label(icon, 18, accent, Typeface.BOLD, Gravity.CENTER);
      TextView b = label(title, 9, WHITE, Typeface.BOLD, Gravity.CENTER);
      b.setMaxLines(1);
      box.addView(a, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
      box.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
      return box;
    }

    private void addAllMenuStrip() {
      HorizontalScrollView scroll = new HorizontalScrollView(activity);
      scroll.setHorizontalScrollBarEnabled(false);
      scroll.setFillViewport(false);
      scroll.setBackground(round(Color.argb(225, 4, 22, 38), OUTLINE, 16));
      scroll.setClickable(true);
      LinearLayout row = new LinearLayout(activity);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(6), dp(4), dp(6), dp(4));
      row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      for (int i = 0; i < TITLES.length; i++) {
        final int id = i + 1;
        TextView chip = label(ICONS[i] + "  " + TITLES[i], 11, WHITE, Typeface.BOLD, Gravity.CENTER);
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setBackground(round(PANEL_2, OUTLINE, 14));
        chip.setOnClickListener(v -> handleMenu(id));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        cp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(chip, cp);
      }
      scroll.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      lp.setMargins(dp(8), 0, dp(8), dp(78));
      overlay.addView(scroll, lp);
    }

    private void addBottomDock() {
      LinearLayout dock = new LinearLayout(activity);
      dock.setOrientation(LinearLayout.HORIZONTAL);
      dock.setGravity(Gravity.CENTER);
      dock.setPadding(dp(5), dp(4), dp(5), dp(8));
      dock.setBackground(round(Color.argb(248, 5, 23, 40), OUTLINE, 18));
      dock.setClickable(true);
      dock.setElevation(dp(12));

      dock.addView(dockButton("⌖", "نقشه", CYAN, this::closeSheet), weight());
      dock.addView(dockButton("⌕", "جستجو", WHITE, () -> activity.showSearch("")), weight());
      dock.addView(dockButton("◎", "اطراف من", GREEN, () -> handleMenu(2)), weight());
      dock.addView(dockButton("SOS", "اضطراری", RED, () -> handleMenu(12)), weight());
      dock.addView(dockButton("☰", "همه منوها", AMBER, this::showAllMenus), weight());

      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      lp.setMargins(dp(8), 0, dp(8), dp(2));
      overlay.addView(dock, lp);
    }

    private View dockButton(String icon, String title, int accent, Runnable action) {
      LinearLayout box = new LinearLayout(activity);
      box.setOrientation(LinearLayout.VERTICAL);
      box.setGravity(Gravity.CENTER);
      box.setClickable(true);
      box.setOnClickListener(v -> action.run());
      TextView a = label(icon, 20, accent, Typeface.BOLD, Gravity.CENTER);
      TextView b = label(title, 10, WHITE, Typeface.BOLD, Gravity.CENTER);
      b.setMaxLines(1);
      box.addView(a, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
      box.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
      return box;
    }

    private void addSheetLayer() {
      sheetLayer = new FrameLayout(activity);
      sheetLayer.setVisibility(View.GONE);
      sheetLayer.setBackgroundColor(Color.argb(55, 0, 0, 0));
      sheetLayer.setClickable(true);
      sheetLayer.setOnClickListener(v -> closeSheet());
      overlay.addView(sheetLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showAllMenus() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);

      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("همه منوهای NV", "۲۲ بخش فارسی روی همین نقشه", this::closeSheet));

      ScrollView sv = new ScrollView(activity);
      sv.setFillViewport(false);
      LinearLayout grid = new LinearLayout(activity);
      grid.setOrientation(LinearLayout.VERTICAL);
      grid.setPadding(dp(8), dp(2), dp(8), dp(10));
      for (int i = 0; i < TITLES.length; i += 2) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.addView(menuCard(i + 1), weightWithMargin());
        if (i + 1 < TITLES.length)
          row.addView(menuCard(i + 2), weightWithMargin());
        else {
          View spacer = new View(activity);
          row.addView(spacer, weightWithMargin());
        }
        grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(82)));
      }
      sv.addView(grid, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      panel.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

      FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(520), Gravity.BOTTOM);
      pp.setMargins(dp(8), 0, dp(8), dp(78));
      sheetLayer.addView(panel, pp);
    }

    private View menuCard(int id) {
      int idx = id - 1;
      TextView card = label(ICONS[idx] + "\n" + TITLES[idx], 13, WHITE, Typeface.BOLD, Gravity.CENTER);
      card.setPadding(dp(6), dp(4), dp(6), dp(4));
      card.setBackground(round(PANEL_2, id == 12 ? RED : OUTLINE, 16));
      card.setClickable(true);
      card.setOnClickListener(v -> {
        closeSheet();
        handleMenu(id);
      });
      return card;
    }

    private void handleMenu(int id) {
      switch (id) {
        case 1 -> closeSheet();
        case 2 -> showNearby();
        case 3 -> search("اورژانس");
        case 7 -> search("داروخانه");
        case 8 -> search("پارک");
        case 9 -> activity.showSearch("");
        case 11 -> showRadius();
        case 12 -> showSOS();
        case 16 -> search("ایستگاه مترو");
        case 17 -> showMetro();
        case 18 -> showTaxi();
        case 21 -> showGeneric(id, "انتخاب مقصد پیاده", () -> activity.showSearch(""));
        case 22 -> showPreferences();
        default -> showGeneric(id, "انتخاب مقصد", () -> activity.showSearch(""));
      }
    }

    private void showNearby() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("اطراف من", "دسته موردنظر را انتخاب کنید", this::closeSheet));
      String[][] items = {
          {"اورژانس", "اورژانس"}, {"بیمارستان", "بیمارستان"}, {"داروخانه", "داروخانه"},
          {"پلیس", "پلیس"}, {"آتش‌نشانی", "آتش نشانی"}, {"پارکینگ", "پارکینگ"},
          {"پمپ بنزین", "پمپ بنزین"}, {"رستوران", "رستوران"}, {"کافه", "کافه"},
          {"پارک", "پارک"}, {"مترو", "ایستگاه مترو"}, {"هتل", "هتل"}
      };
      LinearLayout grid = new LinearLayout(activity);
      grid.setOrientation(LinearLayout.VERTICAL);
      grid.setPadding(dp(8), dp(4), dp(8), dp(8));
      for (int i = 0; i < items.length; i += 2) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.addView(actionCard(items[i][0], () -> search(items[i][1])), weightWithMargin());
        if (i + 1 < items.length)
          row.addView(actionCard(items[i + 1][0], () -> search(items[i + 1][1])), weightWithMargin());
        grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
      }
      panel.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      addSheetPanel(panel, dp(455));
    }

    private void showRadius() {
      showGenericText("محدوده جستجو", "برای جستجو در اطراف موقعیت یا محدوده فعلی نقشه، یک دسته را جستجو کنید. نتیجه‌ها بر اساس داده واقعی نقشه نمایش داده می‌شوند.",
          new String[][] {{"جستجوی اطراف", ""}, {"پارکینگ", "پارکینگ"}, {"بیمارستان", "بیمارستان"}});
    }

    private void showSOS() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("حالت اضطراری", "تماس سریع با خدمات ضروری", this::closeSheet));
      TextView warning = label("در شرایط خطر فوری، شماره مناسب را انتخاب کنید. تماس به‌صورت شماره‌گیر باز می‌شود و بدون تأیید شما برقرار نمی‌شود.", 13, WHITE, Typeface.NORMAL, Gravity.RIGHT);
      warning.setPadding(dp(14), dp(10), dp(14), dp(12));
      panel.addView(warning);
      panel.addView(primary("اورژانس پزشکی ۱۱۵", RED, () -> dial("115")));
      panel.addView(primary("پلیس ۱۱۰", BLUE, () -> dial("110")));
      panel.addView(primary("آتش‌نشانی ۱۲۵", AMBER, () -> dial("125")));
      panel.addView(primary("نمایش بیمارستان‌های نزدیک", PANEL_2, () -> search("بیمارستان")));
      addSheetPanel(panel, dp(390));
    }

    private void showMetro() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("حرکت و ایستگاه مترو", "نقشه واقعی + وضعیت داده زنده", this::closeSheet));
      TextView t = label("ایستگاه‌ها و موقعیت آن‌ها از داده نقشه قابل جستجو هستند. زمان زنده قطار، تأخیر و ازدحام فقط پس از اتصال به API رسمی مترو نمایش داده می‌شود؛ در این نسخه عدد ساختگی نشان داده نمی‌شود.", 13, WHITE, Typeface.NORMAL, Gravity.RIGHT);
      t.setPadding(dp(14), dp(12), dp(14), dp(12));
      panel.addView(t);
      panel.addView(primary("نمایش ایستگاه‌های مترو", BLUE, () -> search("ایستگاه مترو")));
      panel.addView(primary("جستجوی ورودی مترو", PANEL_2, () -> search("ورودی مترو")));
      addSheetPanel(panel, dp(330));
    }

    private void showTaxi() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("هماهنگی تاکسی", "مکان‌های واقعی روی نقشه", this::closeSheet));
      TextView t = label("ایستگاه تاکسی و نقاط حمل‌ونقل از نقشه جستجو می‌شوند. رزرو و قیمت لحظه‌ای تاکسی به اتصال سرویس ارائه‌دهنده نیاز دارد.", 13, WHITE, Typeface.NORMAL, Gravity.RIGHT);
      t.setPadding(dp(14), dp(12), dp(14), dp(12));
      panel.addView(t);
      panel.addView(primary("جستجوی ایستگاه تاکسی", BLUE, () -> search("ایستگاه تاکسی")));
      panel.addView(primary("انتخاب مقصد سفر", PANEL_2, () -> activity.showSearch("")));
      addSheetPanel(panel, dp(315));
    }

    private void showPreferences() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("ترجیحات سفر هوشمند", "تنظیمات پایه NV", this::closeSheet));
      TextView p1 = label("✓ زبان نوشته‌های نقشه: فارسی", 14, GREEN, Typeface.BOLD, Gravity.RIGHT);
      p1.setPadding(dp(14), dp(10), dp(14), dp(10));
      panel.addView(p1);
      TextView p2 = label("✓ نقشه اولیه نصب تازه: تهران", 14, GREEN, Typeface.BOLD, Gravity.RIGHT);
      p2.setPadding(dp(14), dp(10), dp(14), dp(10));
      panel.addView(p2);
      panel.addView(primary("نمایش تهران روی نقشه", BLUE, this::showTehran));
      panel.addView(primary("باز کردن جستجوی مقصد", PANEL_2, () -> activity.showSearch("")));
      addSheetPanel(panel, dp(330));
    }

    private void showGeneric(int id, String actionText, Runnable action) {
      int idx = id - 1;
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader(TITLES[idx], "NV روی نقشه", this::closeSheet));
      TextView desc = label(DESCRIPTIONS[idx], 13, WHITE, Typeface.NORMAL, Gravity.RIGHT);
      desc.setPadding(dp(14), dp(12), dp(14), dp(12));
      panel.addView(desc);
      panel.addView(primary(actionText, BLUE, action));
      panel.addView(primary("بازگشت به نقشه", PANEL_2, this::closeSheet));
      addSheetPanel(panel, dp(305));
    }

    private void showGenericText(String title, String text, String[][] buttons) {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader(title, "NV روی نقشه", this::closeSheet));
      TextView desc = label(text, 13, WHITE, Typeface.NORMAL, Gravity.RIGHT);
      desc.setPadding(dp(14), dp(12), dp(14), dp(12));
      panel.addView(desc);
      for (String[] b : buttons) {
        String query = b[1];
        panel.addView(primary(b[0], BLUE, () -> {
          if (TextUtils.isEmpty(query)) activity.showSearch(""); else search(query);
        }));
      }
      addSheetPanel(panel, dp(365));
    }

    private LinearLayout panelBase() {
      LinearLayout panel = new LinearLayout(activity);
      panel.setOrientation(LinearLayout.VERTICAL);
      panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      panel.setBackground(round(Color.argb(250, 5, 27, 47), CYAN, 22));
      panel.setElevation(dp(18));
      panel.setClickable(true);
      return panel;
    }

    private View sheetHeader(String title, String subtitle, Runnable close) {
      LinearLayout h = new LinearLayout(activity);
      h.setOrientation(LinearLayout.HORIZONTAL);
      h.setGravity(Gravity.CENTER_VERTICAL);
      h.setPadding(dp(12), dp(8), dp(12), dp(8));
      TextView x = label("×", 28, MUTED, Typeface.NORMAL, Gravity.CENTER);
      x.setOnClickListener(v -> close.run());
      h.addView(x, new LinearLayout.LayoutParams(dp(42), dp(48)));
      LinearLayout texts = new LinearLayout(activity);
      texts.setOrientation(LinearLayout.VERTICAL);
      TextView a = label(title, 18, WHITE, Typeface.BOLD, Gravity.RIGHT);
      TextView b = label(subtitle, 11, MUTED, Typeface.NORMAL, Gravity.RIGHT);
      texts.addView(a);
      texts.addView(b);
      h.addView(texts, new LinearLayout.LayoutParams(0, dp(52), 1f));
      return h;
    }

    private View actionCard(String title, Runnable action) {
      TextView t = label(title, 13, WHITE, Typeface.BOLD, Gravity.CENTER);
      t.setBackground(round(PANEL_2, OUTLINE, 14));
      t.setOnClickListener(v -> action.run());
      return t;
    }

    private View primary(String title, int color, Runnable action) {
      TextView b = label(title, 14, WHITE, Typeface.BOLD, Gravity.CENTER);
      b.setBackground(round(color, color == PANEL_2 ? OUTLINE : color, 15));
      b.setOnClickListener(v -> action.run());
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
      lp.setMargins(dp(12), dp(5), dp(12), dp(5));
      b.setLayoutParams(lp);
      return b;
    }

    private void addSheetPanel(LinearLayout panel, int height) {
      FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
      pp.setMargins(dp(8), 0, dp(8), dp(78));
      sheetLayer.addView(panel, pp);
    }

    private void closeSheet() {
      if (sheetLayer != null) {
        sheetLayer.removeAllViews();
        sheetLayer.setVisibility(View.GONE);
      }
    }

    private void search(String query) {
      closeSheet();
      activity.showSearch(query);
    }

    private void dial(String number) {
      try {
        activity.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
      } catch (Throwable ignored) {
        Toast.makeText(activity, "امکان باز کردن شماره‌گیر وجود ندارد", Toast.LENGTH_SHORT).show();
      }
    }

    private void ensureTehranDefaults() {
      try {
        final String tehran = MapManager.nativeFindCountry(TEHRAN_LAT, TEHRAN_LON);
        if (TextUtils.isEmpty(tehran))
          return;
        SharedPreferences p = activity.getSharedPreferences("nv_map_defaults", MwmActivity.MODE_PRIVATE);
        if (MapManager.nativeGetDownloadedCount() == 0 && !MapManager.nativeIsDownloading()) {
          MapManager.startDownload(tehran);
          Toast.makeText(activity, "دانلود نقشه تهران آغاز شد", Toast.LENGTH_LONG).show();
        }
        if (!p.getBoolean("tehran_shown_once", false)) {
          Framework.nativeShowCountry(tehran, false);
          p.edit().putBoolean("tehran_shown_once", true).apply();
        }
      } catch (Throwable ignored) {}
    }

    private void showTehran() {
      closeSheet();
      try {
        String tehran = MapManager.nativeFindCountry(TEHRAN_LAT, TEHRAN_LON);
        if (!TextUtils.isEmpty(tehran))
          Framework.nativeShowCountry(tehran, false);
      } catch (Throwable ignored) {}
    }

    private TextView label(String text, float sp, int color, int style, int gravity) {
      TextView t = new TextView(activity);
      t.setText(text);
      t.setTextSize(sp);
      t.setTextColor(color);
      t.setTypeface(Typeface.DEFAULT, style);
      t.setGravity(gravity);
      t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      t.setTextDirection(View.TEXT_DIRECTION_RTL);
      return t;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
      GradientDrawable d = new GradientDrawable();
      d.setColor(fill);
      d.setCornerRadius(dp(radiusDp));
      d.setStroke(dp(1), stroke);
      return d;
    }

    private LinearLayout.LayoutParams weight() {
      return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private LinearLayout.LayoutParams weightWithMargin() {
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
      p.setMargins(dp(4), dp(4), dp(4), dp(4));
      return p;
    }

    private int dp(int v) {
      return v * density;
    }
  }
}
