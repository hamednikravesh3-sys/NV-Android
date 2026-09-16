package app.organicmaps;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import app.organicmaps.sdk.downloader.CountryItem;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.sdk.location.LocationState;
import app.organicmaps.sdk.settings.MapLanguageCode;

/**
 * NV map-first Persian UI.
 *
 * Design rule:
 * - The real Organic Maps screen remains visible.
 * - Only the search bar and four compact quick actions are persistent.
 * - All 22 NV sections open as a bottom sheet on this same map screen.
 * - Tehran is the default map download, but the viewport is never forced to Tehran,
 *   because doing so can make the user's live GPS position appear wrong.
 */
public final class NvMapMenuOverlay {
  private static final int NAVY = Color.rgb(4, 18, 33);
  private static final int PANEL = Color.rgb(7, 33, 55);
  private static final int PANEL_2 = Color.rgb(10, 48, 78);
  private static final int CYAN = Color.rgb(40, 206, 255);
  private static final int BLUE = Color.rgb(45, 139, 255);
  private static final int GREEN = Color.rgb(42, 214, 113);
  private static final int AMBER = Color.rgb(255, 188, 54);
  private static final int RED = Color.rgb(255, 70, 89);
  private static final int WHITE = Color.rgb(255, 255, 255);
  private static final int MUTED = Color.rgb(205, 220, 231);
  private static final int OUTLINE = Color.rgb(45, 126, 171);

  private static final double TEHRAN_LAT = 35.6892;
  private static final double TEHRAN_LON = 51.3890;
  private static final int LOCATION_PERMISSION_REQUEST = 9042;

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
      "نقشه واقعی، جستجو، موقعیت و سرویس‌های NV در همین صفحه.",
      "خدمات نزدیک را با موتور واقعی جستجوی نقشه پیدا کنید.",
      "جستجوی اورژانس، بیمارستان و مراکز درمانی نزدیک.",
      "بعد از انتخاب مکان، اطلاعات و عملیات مربوط به آن در نقشه نمایش داده می‌شود.",
      "مقصد را انتخاب کنید و از مسیریابی واقعی Organic Maps استفاده کنید.",
      "هشدارهای معتبر مسیر فقط در صورت وجود داده واقعی نمایش داده می‌شوند.",
      "داروخانه‌های اطراف را روی نقشه پیدا کنید.",
      "پارک، فضای سبز و مراکز تفریحی نزدیک.",
      "جستجوی فارسی نام مکان، مقصد و دسته‌بندی‌ها.",
      "برای مقایسه مسیر، ابتدا مقصد را انتخاب کنید و گزینه‌های واقعی موتور مسیریابی را ببینید.",
      "خدمات را در اطراف موقعیت فعلی یا محدوده قابل مشاهده نقشه جستجو کنید.",
      "دسترسی سریع به خدمات اضطراری و مراکز درمانی.",
      "راهنمای سفر روی نقشه؛ پاسخ زنده هوشمند نیازمند سرویس آنلاین مستقل است.",
      "مقصد را سریع انتخاب کنید تا کوتاه‌ترین زمان شروع سفر محاسبه شود.",
      "ترکیب پیاده، خودرو و حمل‌ونقل عمومی بر اساس قابلیت‌های هسته مسیریابی.",
      "ایستگاه‌های مترو و نقاط تعویض مسیر را روی نقشه پیدا کنید.",
      "ایستگاه‌های مترو واقعی‌اند؛ زمان زنده قطار فقط با API رسمی قابل نمایش است.",
      "ایستگاه تاکسی و نقاط حمل‌ونقل روی نقشه؛ رزرو زنده نیازمند اتصال ارائه‌دهنده است.",
      "زمان رسیدن بعد از تشکیل مسیر توسط موتور مسیریابی محاسبه می‌شود.",
      "زمان مسیر واقعی قابل مقایسه است؛ قیمت زنده به سرویس حمل‌ونقل نیاز دارد.",
      "برای مسیر پیاده مقصد را انتخاب کنید و راهنمای قدم‌به‌قدم را شروع کنید.",
      "زبان نقشه فارسی است و نقشه اولیه نصب تازه فقط تهران انتخاب می‌شود."
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
    private TextView locationQuick;

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
      host.addView(overlay, new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      addTopSearch();
      addQuickBar();
      addSheetLayer();

      try {
        MapLanguageCode.setMapLanguageCode("fa");
      } catch (Throwable ignored) {}

      overlay.postDelayed(this::ensureTehranDownloaded, 1400L);
    }

    private void addTopSearch() {
      LinearLayout bar = new LinearLayout(activity);
      bar.setOrientation(LinearLayout.HORIZONTAL);
      bar.setGravity(Gravity.CENTER_VERTICAL);
      bar.setPadding(dp(10), dp(6), dp(10), dp(6));
      bar.setBackground(round(Color.argb(250, 7, 33, 55), CYAN, 18));
      bar.setElevation(dp(10));
      bar.setClickable(true);
      bar.setOnClickListener(v -> activity.showSearch(""));

      TextView nv = label("NV", 16, WHITE, Typeface.BOLD, Gravity.CENTER);
      nv.setBackground(round(PANEL_2, OUTLINE, 14));
      LinearLayout.LayoutParams nvp = new LinearLayout.LayoutParams(dp(48), dp(42));
      nvp.setMargins(0, 0, dp(8), 0);
      bar.addView(nv, nvp);

      TextView search = label("کجا می‌خواهید بروید؟", 16, WHITE, Typeface.BOLD,
                              Gravity.CENTER_VERTICAL | Gravity.RIGHT);
      search.setSingleLine(true);
      bar.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1f));

      TextView icon = label("⌕", 28, CYAN, Typeface.BOLD, Gravity.CENTER);
      bar.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(58), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      lp.setMargins(dp(14), dp(34), dp(14), 0);
      overlay.addView(bar, lp);
    }

    private void addQuickBar() {
      LinearLayout row = new LinearLayout(activity);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER);
      row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

      locationQuick = quickButton("◎", "مکان من", GREEN, this::showLocationPrecision);
      row.addView(locationQuick, quickWeight());
      row.addView(quickButton("✚", "اطراف من", CYAN, this::showNearby), quickWeight());
      row.addView(quickButton("SOS", "اضطراری", RED, this::showSOS), quickWeight());
      row.addView(quickButton("☰", "منوها", AMBER, this::showAllMenus), quickWeight());

      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      lp.setMargins(dp(14), dp(100), dp(14), 0);
      overlay.addView(row, lp);
      refreshLocationQuick();
    }

    private TextView quickButton(String icon, String title, int accent, Runnable action) {
      TextView t = label(icon + "  " + title, 12, WHITE, Typeface.BOLD, Gravity.CENTER);
      t.setSingleLine(true);
      t.setBackground(round(Color.argb(245, 8, 39, 64), accent, 14));
      t.setClickable(true);
      t.setOnClickListener(v -> action.run());
      return t;
    }

    private LinearLayout.LayoutParams quickWeight() {
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1f);
      p.setMargins(dp(3), 0, dp(3), 0);
      return p;
    }

    private void addSheetLayer() {
      sheetLayer = new FrameLayout(activity);
      sheetLayer.setVisibility(View.GONE);
      sheetLayer.setBackgroundColor(Color.argb(125, 0, 0, 0));
      sheetLayer.setClickable(true);
      sheetLayer.setOnClickListener(v -> closeSheet());
      overlay.addView(sheetLayer, new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showAllMenus() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);

      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("همه منوهای NV", "۲۲ بخش؛ همه در همین صفحه نقشه", this::closeSheet));

      ScrollView sv = new ScrollView(activity);
      sv.setFillViewport(false);
      LinearLayout grid = new LinearLayout(activity);
      grid.setOrientation(LinearLayout.VERTICAL);
      grid.setPadding(dp(8), dp(2), dp(8), dp(12));

      for (int i = 0; i < TITLES.length; i += 2) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.addView(menuCard(i + 1), weightWithMargin());
        if (i + 1 < TITLES.length)
          row.addView(menuCard(i + 2), weightWithMargin());
        else
          row.addView(new View(activity), weightWithMargin());
        grid.addView(row, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
      }

      sv.addView(grid, new ScrollView.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      panel.addView(sv, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

      FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(590), Gravity.BOTTOM);
      pp.setMargins(dp(10), 0, dp(10), dp(12));
      sheetLayer.addView(panel, pp);
    }

    private View menuCard(int id) {
      int idx = id - 1;
      TextView card = label(ICONS[idx] + "\n" + TITLES[idx], 14, WHITE, Typeface.BOLD, Gravity.CENTER);
      card.setPadding(dp(8), dp(5), dp(8), dp(5));
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
      panel.addView(sheetHeader("اطراف من", "خدمت موردنظر را انتخاب کنید", this::closeSheet));

      String[][] items = {
          {"اورژانس", "اورژانس"}, {"بیمارستان", "بیمارستان"}, {"داروخانه", "داروخانه"},
          {"پلیس", "پلیس"}, {"آتش‌نشانی", "آتش نشانی"}, {"پارکینگ", "پارکینگ"},
          {"پمپ بنزین", "پمپ بنزین"}, {"رستوران", "رستوران"}, {"کافه", "کافه"},
          {"پارک", "پارک"}, {"مترو", "ایستگاه مترو"}, {"هتل", "هتل"}
      };

      ScrollView sv = new ScrollView(activity);
      LinearLayout grid = new LinearLayout(activity);
      grid.setOrientation(LinearLayout.VERTICAL);
      grid.setPadding(dp(8), dp(4), dp(8), dp(10));

      for (int i = 0; i < items.length; i += 2) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.addView(actionCard(items[i][0], searchAction(items[i][1])), weightWithMargin());
        if (i + 1 < items.length)
          row.addView(actionCard(items[i + 1][0], searchAction(items[i + 1][1])), weightWithMargin());
        grid.addView(row, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
      }

      sv.addView(grid);
      panel.addView(sv, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
      addSheetPanel(panel, dp(470));
    }

    private void showLocationPrecision() {
      refreshLocationQuick();
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);

      boolean fine = hasFineLocation();
      boolean gps = isGpsEnabled();

      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("موقعیت دقیق", "NV موقعیت تقریبی را به‌عنوان دقیق قبول نمی‌کند", this::closeSheet));

      String status;
      int statusColor;
      if (!fine) {
        status = "موقعیت دقیق اندروید برای NV فعال نیست. در این حالت سیستم ممکن است مکان تقریبی با اختلاف زیاد بدهد.";
        statusColor = AMBER;
      } else if (!gps) {
        status = "مجوز دقیق فعال است، اما GPS دستگاه خاموش است.";
        statusColor = AMBER;
      } else {
        status = "مجوز موقعیت دقیق و GPS فعال‌اند. با دکمه زیر موتور Organic Maps روی GPS واقعی متمرکز می‌شود.";
        statusColor = GREEN;
      }

      TextView state = label(status, 14, statusColor, Typeface.BOLD, Gravity.RIGHT);
      state.setPadding(dp(14), dp(12), dp(14), dp(12));
      panel.addView(state);

      if (!fine) {
        panel.addView(primary("درخواست مجوز موقعیت دقیق", BLUE, this::requestPreciseLocation));
        panel.addView(primary("باز کردن مجوزهای برنامه", PANEL_2, this::openAppSettings));
      } else if (!gps) {
        panel.addView(primary("روشن کردن GPS در تنظیمات", BLUE, this::openLocationSettings));
      } else {
        panel.addView(primary("پیدا کردن موقعیت من با GPS", GREEN, this::focusMyLocation));
      }

      panel.addView(primary("بستن", PANEL_2, this::closeSheet));
      addSheetPanel(panel, dp(360));
    }

    private void requestPreciseLocation() {
      try {
        activity.requestPermissions(
            new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
            LOCATION_PERMISSION_REQUEST);
        Toast.makeText(activity,
            "در پنجره مجوز، گزینه «موقعیت دقیق» را فعال کنید",
            Toast.LENGTH_LONG).show();
      } catch (Throwable ignored) {
        openAppSettings();
      }
    }

    private void focusMyLocation() {
      closeSheet();
      if (!hasFineLocation()) {
        showLocationPrecision();
        return;
      }
      if (!isGpsEnabled()) {
        openLocationSettings();
        return;
      }
      try {
        LocationState.nativeSwitchToNextMode();
        Toast.makeText(activity, "در حال دریافت GPS دقیق…", Toast.LENGTH_SHORT).show();
      } catch (Throwable ignored) {
        Toast.makeText(activity, "امکان فعال‌کردن موقعیت فعلی وجود ندارد", Toast.LENGTH_SHORT).show();
      }
    }

    private void openAppSettings() {
      try {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(intent);
      } catch (Throwable ignored) {}
    }

    private void openLocationSettings() {
      try {
        activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
      } catch (Throwable ignored) {}
    }

    private boolean hasFineLocation() {
      return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
          == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isGpsEnabled() {
      try {
        LocationManager lm = (LocationManager) activity.getSystemService(MwmActivity.LOCATION_SERVICE);
        return lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
      } catch (Throwable ignored) {
        return false;
      }
    }

    private void refreshLocationQuick() {
      if (locationQuick == null)
        return;
      boolean good = hasFineLocation() && isGpsEnabled();
      locationQuick.setText(good ? "◎  مکان دقیق" : "⚠  موقعیت");
      locationQuick.setBackground(round(
          Color.argb(245, 8, 39, 64), good ? GREEN : AMBER, 14));
    }

    private void showRadius() {
      showGenericText("محدوده جستجو",
          "برای جستجوی اطراف، دسته موردنظر را انتخاب کنید. نتیجه‌ها از داده واقعی نقشه می‌آیند.",
          new String[][] {{"جستجوی اطراف", ""}, {"پارکینگ", "پارکینگ"}, {"بیمارستان", "بیمارستان"}});
    }

    private void showSOS() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("حالت اضطراری", "تماس سریع با خدمات ضروری", this::closeSheet));

      TextView warning = label(
          "در شرایط خطر فوری، شماره مناسب را انتخاب کنید. شماره‌گیر باز می‌شود و تماس بدون تأیید شما برقرار نمی‌شود.",
          14, WHITE, Typeface.NORMAL, Gravity.RIGHT);
      warning.setPadding(dp(14), dp(10), dp(14), dp(12));
      panel.addView(warning);

      panel.addView(primary("اورژانس پزشکی ۱۱۵", RED, () -> dial("115")));
      panel.addView(primary("پلیس ۱۱۰", BLUE, () -> dial("110")));
      panel.addView(primary("آتش‌نشانی ۱۲۵", AMBER, () -> dial("125")));
      panel.addView(primary("بیمارستان‌های نزدیک", PANEL_2, () -> search("بیمارستان")));
      addSheetPanel(panel, dp(400));
    }

    private void showMetro() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("مترو", "ایستگاه واقعی روی نقشه", this::closeSheet));

      TextView t = label(
          "ایستگاه‌ها از داده واقعی نقشه جستجو می‌شوند. زمان زنده قطار، تأخیر و ازدحام فقط پس از اتصال API رسمی نمایش داده می‌شود.",
          14, WHITE, Typeface.NORMAL, Gravity.RIGHT);
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

      TextView t = label(
          "ایستگاه تاکسی و نقاط حمل‌ونقل از نقشه جستجو می‌شوند. رزرو و قیمت لحظه‌ای نیازمند اتصال سرویس تاکسی است.",
          14, WHITE, Typeface.NORMAL, Gravity.RIGHT);
      t.setPadding(dp(14), dp(12), dp(14), dp(12));
      panel.addView(t);

      panel.addView(primary("جستجوی ایستگاه تاکسی", BLUE, () -> search("ایستگاه تاکسی")));
      panel.addView(primary("انتخاب مقصد سفر", PANEL_2, () -> activity.showSearch("")));
      addSheetPanel(panel, dp(320));
    }

    private void showPreferences() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader("ترجیحات سفر هوشمند", "تنظیمات پایه NV", this::closeSheet));

      TextView p1 = label("✓ زبان نقشه: فارسی", 14, GREEN, Typeface.BOLD, Gravity.RIGHT);
      p1.setPadding(dp(14), dp(10), dp(14), dp(10));
      panel.addView(p1);

      TextView p2 = label("✓ نقشه اولیه نصب تازه: تهران", 14, GREEN, Typeface.BOLD, Gravity.RIGHT);
      p2.setPadding(dp(14), dp(10), dp(14), dp(10));
      panel.addView(p2);

      TextView p3 = label("✓ نمایش نقشه دیگر به‌صورت خودکار روی موقعیت شما تحمیل نمی‌شود", 14, GREEN,
                          Typeface.BOLD, Gravity.RIGHT);
      p3.setPadding(dp(14), dp(10), dp(14), dp(10));
      panel.addView(p3);

      panel.addView(primary("بررسی موقعیت دقیق", BLUE, this::showLocationPrecision));
      panel.addView(primary("باز کردن جستجوی مقصد", PANEL_2, () -> activity.showSearch("")));
      addSheetPanel(panel, dp(390));
    }

    private void showGeneric(int id, String actionText, Runnable action) {
      int idx = id - 1;
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);

      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader(TITLES[idx], "NV روی همین نقشه", this::closeSheet));

      TextView desc = label(DESCRIPTIONS[idx], 14, WHITE, Typeface.NORMAL, Gravity.RIGHT);
      desc.setPadding(dp(14), dp(12), dp(14), dp(12));
      panel.addView(desc);

      panel.addView(primary(actionText, BLUE, action));
      panel.addView(primary("بازگشت به نقشه", PANEL_2, this::closeSheet));
      addSheetPanel(panel, dp(315));
    }

    private void showGenericText(String title, String text, String[][] buttons) {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);

      LinearLayout panel = panelBase();
      panel.setOnClickListener(v -> {});
      panel.addView(sheetHeader(title, "NV روی همین نقشه", this::closeSheet));

      TextView desc = label(text, 14, WHITE, Typeface.NORMAL, Gravity.RIGHT);
      desc.setPadding(dp(14), dp(12), dp(14), dp(12));
      panel.addView(desc);

      for (String[] b : buttons) {
        String query = b[1];
        panel.addView(primary(b[0], BLUE, () -> {
          if (TextUtils.isEmpty(query))
            activity.showSearch("");
          else
            search(query);
        }));
      }
      addSheetPanel(panel, dp(370));
    }

    private LinearLayout panelBase() {
      LinearLayout panel = new LinearLayout(activity);
      panel.setOrientation(LinearLayout.VERTICAL);
      panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      panel.setBackground(round(Color.rgb(5, 27, 47), CYAN, 22));
      panel.setElevation(dp(20));
      panel.setClickable(true);
      return panel;
    }

    private View sheetHeader(String title, String subtitle, Runnable close) {
      LinearLayout h = new LinearLayout(activity);
      h.setOrientation(LinearLayout.HORIZONTAL);
      h.setGravity(Gravity.CENTER_VERTICAL);
      h.setPadding(dp(12), dp(8), dp(12), dp(8));

      TextView x = label("×", 29, WHITE, Typeface.NORMAL, Gravity.CENTER);
      x.setOnClickListener(v -> close.run());
      h.addView(x, new LinearLayout.LayoutParams(dp(44), dp(50)));

      LinearLayout texts = new LinearLayout(activity);
      texts.setOrientation(LinearLayout.VERTICAL);
      TextView a = label(title, 18, WHITE, Typeface.BOLD, Gravity.RIGHT);
      TextView b = label(subtitle, 12, MUTED, Typeface.NORMAL, Gravity.RIGHT);
      texts.addView(a);
      texts.addView(b);
      h.addView(texts, new LinearLayout.LayoutParams(0, dp(54), 1f));
      return h;
    }

    private View actionCard(String title, Runnable action) {
      TextView t = label(title, 14, WHITE, Typeface.BOLD, Gravity.CENTER);
      t.setBackground(round(PANEL_2, OUTLINE, 14));
      t.setOnClickListener(v -> action.run());
      return t;
    }

    private View primary(String title, int color, Runnable action) {
      TextView b = label(title, 14, WHITE, Typeface.BOLD, Gravity.CENTER);
      b.setBackground(round(color, color == PANEL_2 ? OUTLINE : color, 15));
      b.setOnClickListener(v -> action.run());

      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
      lp.setMargins(dp(12), dp(5), dp(12), dp(5));
      b.setLayoutParams(lp);
      return b;
    }

    private void addSheetPanel(LinearLayout panel, int height) {
      FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
      pp.setMargins(dp(10), 0, dp(10), dp(12));
      sheetLayer.addView(panel, pp);
    }

    private void closeSheet() {
      if (sheetLayer != null) {
        sheetLayer.removeAllViews();
        sheetLayer.setVisibility(View.GONE);
      }
      refreshLocationQuick();
    }

    private Runnable searchAction(String query) {
      return () -> search(query);
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

    private void ensureTehranDownloaded() {
      try {
        final String tehran = MapManager.nativeFindCountry(TEHRAN_LAT, TEHRAN_LON);
        if (TextUtils.isEmpty(tehran))
          return;

        final int status = MapManager.nativeGetStatus(tehran);
        if (status != CountryItem.STATUS_DONE && !MapManager.nativeIsDownloading()) {
          MapManager.startDownload(tehran);
          Toast.makeText(activity, "دانلود نقشه تهران آغاز شد", Toast.LENGTH_LONG).show();
        }
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

    private LinearLayout.LayoutParams weightWithMargin() {
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
          0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
      p.setMargins(dp(4), dp(4), dp(4), dp(4));
      return p;
    }

    private int dp(int v) {
      return v * density;
    }
  }
}
