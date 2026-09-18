package app.organicmaps;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import app.organicmaps.sdk.settings.MapLanguageCode;

/** Compact Persian NV home overlay. All action cards delegate to real runtime features. */
public final class NvMapMenuOverlay {
  private static final int PANEL = Color.rgb(7, 33, 55);
  private static final int PANEL_2 = Color.rgb(10, 48, 78);
  private static final int CYAN = Color.rgb(40, 206, 255);
  private static final int BLUE = Color.rgb(45, 139, 255);
  private static final int GREEN = Color.rgb(42, 214, 113);
  private static final int AMBER = Color.rgb(255, 188, 54);
  private static final int RED = Color.rgb(255, 70, 89);
  private static final int WHITE = Color.WHITE;
  private static final int MUTED = Color.rgb(205, 220, 231);
  private static final int OUTLINE = Color.rgb(45, 126, 171);

  private static final String[] TITLES = {
      "صفحه اصلی", "اطراف من", "اورژانس", "جزئیات مکان", "حالت مسیریابی", "هشدارهای مسیر",
      "داروخانه", "پارک و تفریح", "جستجوی هوشمند", "مقایسه مسیرها", "محدوده جستجو", "حالت اضطراری",
      "چت هوشمند سفر", "حالت عجله دارم", "مسیر ترکیبی", "تعویض هوشمند ایستگاه", "مترو و ایستگاه‌ها",
      "تاکسی و محل سوارشدن", "اطمینان زمان رسیدن", "مقایسه زمان و هزینه", "راهنمای پیاده", "ترجیحات سفر هوشمند"
  };

  private static final String[] ICONS = {
      "⌂", "◎", "✚", "●", "➤", "!", "✚", "♣", "⌕", "⇄", "◉", "SOS",
      "☏", "⚡", "↝", "M", "M", "T", "◷", "₮", "↟", "⚙"
  };

  private NvMapMenuOverlay() {}

  public static void install(MwmActivity activity) {
    final ViewGroup host = activity.findViewById(android.R.id.content);
    if (host == null || host.findViewWithTag("nv-map-overlay") != null) return;
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
      density = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
    }

    void install() {
      overlay = new FrameLayout(activity);
      overlay.setTag("nv-map-overlay");
      overlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      overlay.setClipChildren(false);
      overlay.setClipToPadding(false);
      overlay.setClickable(false);
      host.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      addSearchBar();
      addQuickBar();
      addSmartTravelPill();
      addSheetLayer();
      try { MapLanguageCode.setMapLanguageCode("fa"); } catch (Throwable ignored) {}
    }

    private void addSearchBar() {
      final LinearLayout bar = new LinearLayout(activity);
      bar.setOrientation(LinearLayout.HORIZONTAL);
      bar.setGravity(Gravity.CENTER_VERTICAL);
      bar.setPadding(dp(10), dp(6), dp(10), dp(6));
      bar.setBackground(round(Color.argb(250, 7, 33, 55), CYAN, 18));
      bar.setElevation(dp(10));
      bar.setClickable(true);
      bar.setOnClickListener(v -> NvV031Actions.openSmartSearch(activity));

      final View nv = NvAnimatedBrand.createLogo(activity, 16, () -> NvRuntimeController.showCodeMenu(activity));
      final LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(50), dp(42));
      np.setMargins(0, 0, dp(8), 0);
      bar.addView(nv, np);

      final TextView prompt = label("کجا می‌خواهید بروید؟", 16, WHITE, Typeface.BOLD, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
      prompt.setSingleLine(true);
      bar.addView(prompt, new LinearLayout.LayoutParams(0, dp(44), 1f));
      bar.addView(label("⌕", 28, CYAN, Typeface.BOLD, Gravity.CENTER), new LinearLayout.LayoutParams(dp(44), dp(44)));

      final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58), Gravity.TOP);
      lp.setMargins(dp(14), dp(34), dp(14), 0);
      overlay.addView(bar, lp);
    }

    private void addQuickBar() {
      final LinearLayout row = new LinearLayout(activity);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      row.setGravity(Gravity.CENTER);

      final TextView location = quickButton("◎", "مکان من", GREEN, () -> NvRuntimeController.showLocationStatus(activity));
      location.setTag("nv-location-quick");
      row.addView(location, quickWeight());
      row.addView(quickButton("✚", "اطراف من", CYAN, this::showNearby), quickWeight());
      row.addView(quickButton("SOS", "اضطراری", RED, this::showSOS), quickWeight());
      row.addView(quickButton("☰", "منوها", AMBER, this::showAllMenus), quickWeight());

      final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP);
      lp.setMargins(dp(14), dp(100), dp(14), 0);
      overlay.addView(row, lp);
    }

    private void addSmartTravelPill() {
      final TextView smart = label("✦  هوشمند سفر", 13, WHITE, Typeface.BOLD, Gravity.CENTER);
      smart.setBackground(round(Color.argb(248, 9, 52, 88), BLUE, 16));
      smart.setElevation(dp(12));
      smart.setClickable(true);
      smart.setOnClickListener(v -> NvSmartTravelUi.openHub(activity));
      final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(154), dp(42), Gravity.TOP | Gravity.RIGHT);
      lp.setMargins(0, dp(154), dp(14), 0);
      overlay.addView(smart, lp);
    }

    private TextView quickButton(String icon, String title, int accent, Runnable action) {
      final TextView t = label(icon + "  " + title, 12, WHITE, Typeface.BOLD, Gravity.CENTER);
      t.setSingleLine(true);
      t.setBackground(round(Color.argb(245, 8, 39, 64), accent, 14));
      t.setClickable(true);
      t.setOnClickListener(v -> action.run());
      return t;
    }

    private LinearLayout.LayoutParams quickWeight() {
      final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1f);
      p.setMargins(dp(3), 0, dp(3), 0);
      return p;
    }

    private void addSheetLayer() {
      sheetLayer = new FrameLayout(activity);
      sheetLayer.setVisibility(View.GONE);
      sheetLayer.setBackgroundColor(Color.argb(130, 0, 0, 0));
      sheetLayer.setClickable(true);
      sheetLayer.setOnClickListener(v -> closeSheet());
      overlay.addView(sheetLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showAllMenus() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      final LinearLayout panel = panelBase();
      panel.addView(header("همه منوهای NV", "هر منو اکنون به یک عملیات واقعی متصل است", this::closeSheet));
      panel.addView(primary("کد NV و QR برای نقطه دلخواه", GREEN, () -> { closeSheet(); NvRuntimeController.startCodePicker(activity); }));
      panel.addView(primary("هوشمند سفر • صفحات ۱۳ تا ۲۲", BLUE, () -> { closeSheet(); NvSmartTravelUi.openHub(activity); }));

      final ScrollView scroll = new ScrollView(activity);
      final LinearLayout grid = new LinearLayout(activity);
      grid.setOrientation(LinearLayout.VERTICAL);
      grid.setPadding(dp(8), dp(2), dp(8), dp(12));
      for (int i = 0; i < TITLES.length; i += 2) {
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.addView(menuCard(i + 1), weightCard());
        if (i + 1 < TITLES.length) row.addView(menuCard(i + 2), weightCard());
        else row.addView(new View(activity), weightCard());
        grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
      }
      scroll.addView(grid);
      panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
      addPanel(panel, dp(600));
    }

    private View menuCard(int id) {
      final int idx = id - 1;
      final TextView card = label(ICONS[idx] + "\n" + TITLES[idx], 14, WHITE, Typeface.BOLD, Gravity.CENTER);
      card.setBackground(round(PANEL_2, id == 12 ? RED : OUTLINE, 16));
      card.setPadding(dp(6), dp(5), dp(6), dp(5));
      card.setClickable(true);
      card.setOnClickListener(v -> { closeSheet(); handleMenu(id); });
      return card;
    }

    private void handleMenu(int id) {
      switch (id) {
        case 1 -> NvV031Actions.goHome(activity);
        case 2 -> showNearby();
        case 3 -> NvSmartActions.openNearby(activity, "اورژانس");
        case 4 -> NvV031Actions.openPlaceDetails(activity);
        case 5 -> NvV031Actions.openRouteMode(activity);
        case 6 -> NvV031Actions.openRouteAlerts(activity);
        case 7 -> NvSmartActions.openNearby(activity, "داروخانه");
        case 8 -> NvSmartActions.openNearby(activity, "پارک");
        case 9 -> NvV031Actions.openSmartSearch(activity);
        case 10 -> NvV031Actions.openCompareRoutes(activity);
        case 11 -> NvV031Actions.openRadius(activity);
        case 12 -> showSOS();
        case 13 -> NvV031Actions.openChat(activity);
        case 14 -> NvV031Actions.openHurry(activity);
        case 15 -> NvV031Actions.openMixed(activity);
        case 16 -> NvV031Actions.openStationTransfer(activity);
        case 17 -> NvV031Actions.openMetroStatus(activity);
        case 18 -> NvV031Actions.openTaxi(activity);
        case 19 -> NvV031Actions.openEta(activity);
        case 20 -> NvV031Actions.openTimeCost(activity);
        case 21 -> NvV031Actions.openWalk(activity);
        case 22 -> NvV031Actions.openPreferences(activity);
        default -> NvV031Actions.openRouteMode(activity);
      }
    }

    private void showNearby() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      final LinearLayout panel = panelBase();
      panel.addView(header("اطراف من", "نتایج بر اساس فاصله از موقعیت فعلی مرتب می‌شوند", this::closeSheet));
      final String[] cats = {"اورژانس", "بیمارستان", "داروخانه", "درمانگاه", "پلیس", "آتش‌نشانی", "پارکینگ", "پمپ بنزین", "رستوران", "کافه", "پارک", "ایستگاه مترو", "ایستگاه تاکسی", "هتل"};
      final ScrollView sv = new ScrollView(activity);
      final LinearLayout grid = new LinearLayout(activity);
      grid.setOrientation(LinearLayout.VERTICAL);
      for (int i = 0; i < cats.length; i += 2) {
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final String a = cats[i];
        row.addView(actionCard(a, () -> { closeSheet(); NvSmartActions.openNearby(activity, a); }), weightCard());
        if (i + 1 < cats.length) {
          final String b = cats[i + 1];
          row.addView(actionCard(b, () -> { closeSheet(); NvSmartActions.openNearby(activity, b); }), weightCard());
        }
        grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
      }
      sv.addView(grid);
      panel.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
      addPanel(panel, dp(490));
    }

    private void showSOS() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      final LinearLayout panel = panelBase();
      panel.addView(header("حالت اضطراری", "تماس سریع و نزدیک‌ترین مراکز", this::closeSheet));
      panel.addView(primary("نزدیک‌ترین اورژانس‌ها", RED, () -> { closeSheet(); NvSmartActions.openNearby(activity, "اورژانس"); }));
      panel.addView(primary("نزدیک‌ترین بیمارستان‌ها", BLUE, () -> { closeSheet(); NvSmartActions.openNearby(activity, "بیمارستان"); }));
      panel.addView(primary("شماره‌گیر ۱۱۵", RED, () -> dial("115")));
      panel.addView(primary("شماره‌گیر ۱۱۰", BLUE, () -> dial("110")));
      panel.addView(primary("شماره‌گیر ۱۲۵", AMBER, () -> dial("125")));
      addPanel(panel, dp(420));
    }

    private void showPreferences() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);
      final LinearLayout panel = panelBase();
      panel.addView(header("ترجیحات سفر هوشمند", "نوع مسیر را مستقیم انتخاب کنید", this::closeSheet));
      panel.addView(primary("سریع‌ترین / عجله دارم", RED, () -> { closeSheet(); NvV031Actions.openHurry(activity); }));
      panel.addView(primary("مسیر ترکیبی مترو + پیاده", GREEN, () -> { closeSheet(); NvV031Actions.openMixed(activity); }));
      panel.addView(primary("مسیر پیاده", CYAN, () -> { closeSheet(); NvV031Actions.openWalk(activity); }));
      panel.addView(primary("جستجوی مقصد", BLUE, () -> { closeSheet(); NvV031Actions.openSmartSearch(activity); }));
      addPanel(panel, dp(390));
    }

    private LinearLayout panelBase() {
      final LinearLayout p = new LinearLayout(activity);
      p.setOrientation(LinearLayout.VERTICAL);
      p.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      p.setBackground(round(Color.rgb(5, 27, 47), CYAN, 22));
      p.setElevation(dp(20));
      p.setClickable(true);
      p.setOnClickListener(v -> {});
      return p;
    }

    private View header(String title, String subtitle, Runnable close) {
      final LinearLayout h = new LinearLayout(activity);
      h.setOrientation(LinearLayout.HORIZONTAL);
      h.setGravity(Gravity.CENTER_VERTICAL);
      h.setPadding(dp(12), dp(8), dp(12), dp(8));
      final TextView x = label("×", 29, WHITE, Typeface.NORMAL, Gravity.CENTER);
      x.setClickable(true);
      x.setOnClickListener(v -> close.run());
      h.addView(x, new LinearLayout.LayoutParams(dp(44), dp(50)));
      final LinearLayout tx = new LinearLayout(activity);
      tx.setOrientation(LinearLayout.VERTICAL);
      tx.addView(label(title, 18, WHITE, Typeface.BOLD, Gravity.RIGHT));
      final TextView sub = label(subtitle, 12, MUTED, Typeface.NORMAL, Gravity.RIGHT);
      sub.setMaxLines(2);
      tx.addView(sub);
      h.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      return h;
    }

    private View primary(String title, int color, Runnable action) {
      final TextView b = label(title, 14, WHITE, Typeface.BOLD, Gravity.CENTER);
      b.setBackground(round(color, color == PANEL_2 ? OUTLINE : color, 15));
      b.setClickable(true);
      b.setOnClickListener(v -> action.run());
      final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
      lp.setMargins(dp(12), dp(5), dp(12), dp(5));
      b.setLayoutParams(lp);
      return b;
    }

    private View actionCard(String title, Runnable action) {
      final TextView t = label(title, 14, WHITE, Typeface.BOLD, Gravity.CENTER);
      t.setBackground(round(PANEL_2, OUTLINE, 14));
      t.setClickable(true);
      t.setOnClickListener(v -> action.run());
      return t;
    }

    private void addPanel(LinearLayout panel, int height) {
      final FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
      p.setMargins(dp(10), 0, dp(10), dp(12));
      sheetLayer.addView(panel, p);
    }

    private void closeSheet() {
      if (sheetLayer == null) return;
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.GONE);
    }

    private void dial(String number) {
      try { activity.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))); }
      catch (Throwable e) { Toast.makeText(activity, "شماره‌گیر در دسترس نیست", Toast.LENGTH_SHORT).show(); }
    }

    private TextView label(String text, float sp, int color, int style, int gravity) {
      final TextView t = new TextView(activity);
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
      final GradientDrawable d = new GradientDrawable();
      d.setColor(fill);
      d.setCornerRadius(dp(radiusDp));
      d.setStroke(dp(1), stroke);
      return d;
    }

    private LinearLayout.LayoutParams weightCard() {
      final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
      p.setMargins(dp(4), dp(4), dp(4), dp(4));
      return p;
    }

    private int dp(int value) { return value * density; }
  }
}
