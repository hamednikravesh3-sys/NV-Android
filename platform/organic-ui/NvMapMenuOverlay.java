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
  private static final int PANEL = Color.rgb(15, 23, 42);
  private static final int PANEL_2 = Color.rgb(30, 41, 59);
  private static final int CYAN = Color.rgb(14, 165, 233);
  private static final int BLUE = Color.rgb(59, 130, 246);
  private static final int GREEN = Color.rgb(34, 197, 94);
  private static final int AMBER = Color.rgb(249, 115, 22);
  private static final int PURPLE = Color.rgb(139, 92, 246);
  private static final int RED = Color.rgb(239, 68, 68);
  private static final int WHITE = Color.WHITE;
  private static final int MUTED = Color.rgb(203, 213, 225);
  private static final int OUTLINE = Color.rgb(71, 85, 105);

  private static final String[] TITLES = {
      "صفحه اصلی", "مسیریابی", "اطراف من", "اورژانس", "جزئیات مکان", "هشدارهای مسیر",
      "داروخانه", "پارک و تفریح", "محدوده جستجو", "مترو و ایستگاه‌ها",
      "تاکسی و محل سوارشدن", "تنظیمات سفر"
  };

  private static final String[] ICONS = {
      "⌂", "➤", "◎", "✚", "●", "!", "✚", "♣", "◉", "M", "T", "⚙"
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
      addSheetLayer();
      try { MapLanguageCode.setMapLanguageCode("fa"); } catch (Throwable ignored) {}
    }

    private void addSearchBar() {
      final LinearLayout card = new LinearLayout(activity);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(dp(14), dp(10), dp(14), dp(10));
      card.setBackground(round(Color.argb(248, 15, 23, 42), Color.argb(120, 148, 163, 184), 24));
      card.setElevation(dp(14));
      card.setClickable(true);
      card.setOnClickListener(v -> NvV032Actions.openRoutePlanner(activity));

      final LinearLayout titleRow = new LinearLayout(activity);
      titleRow.setOrientation(LinearLayout.HORIZONTAL);
      titleRow.setGravity(Gravity.CENTER_VERTICAL);
      titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

      final View nv = NvAnimatedBrand.createLogo(activity, 14, () -> NvRuntimeController.showCodeMenu(activity));
      final LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(38), dp(34));
      np.setMargins(dp(8), 0, 0, 0);
      titleRow.addView(nv, np);

      final TextView title = label("مسیریابی NV", 13, MUTED, Typeface.BOLD, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
      titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1f));

      final TextView hint = label("انتخاب", 12, CYAN, Typeface.BOLD, Gravity.CENTER);
      hint.setBackground(round(Color.argb(50, 14, 165, 233), Color.argb(90, 14, 165, 233), 14));
      hint.setPadding(dp(10), 0, dp(10), 0);
      titleRow.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));
      card.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

      card.addView(endpointPreview("●", "مبدأ", "جستجو یا انتخاب روی نقشه", PURPLE));

      final View divider = new View(activity);
      divider.setBackgroundColor(Color.argb(70, 148, 163, 184));
      final LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
      dpv.setMargins(dp(42), 0, dp(8), 0);
      card.addView(divider, dpv);

      card.addView(endpointPreview("⚑", "مقصد", "جستجو یا انتخاب روی نقشه", AMBER));

      final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(132), Gravity.TOP);
      lp.setMargins(dp(14), dp(28), dp(14), 0);
      overlay.addView(card, lp);
    }

    private View endpointPreview(String icon, String title, String subtitle, int accent) {
      final LinearLayout row = new LinearLayout(activity);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      row.setPadding(dp(4), dp(3), dp(4), dp(3));

      final TextView mark = label(icon, "⚑".equals(icon) ? 25 : 22, accent, Typeface.BOLD, Gravity.CENTER);
      row.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(36)));

      final LinearLayout texts = new LinearLayout(activity);
      texts.setOrientation(LinearLayout.VERTICAL);
      texts.setGravity(Gravity.RIGHT);
      final TextView t = label(title, 14, WHITE, Typeface.BOLD, Gravity.RIGHT);
      final TextView s = label(subtitle, 11, MUTED, Typeface.NORMAL, Gravity.RIGHT);
      s.setSingleLine(true);
      texts.addView(t);
      texts.addView(s);
      row.addView(texts, new LinearLayout.LayoutParams(0, dp(42), 1f));

      final TextView arrow = label("‹", 28, MUTED, Typeface.NORMAL, Gravity.CENTER);
      row.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(38)));
      return row;
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
      row.addView(quickButton("☰", "بیشتر", AMBER, this::showAllMenus), quickWeight());

      final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(52), Gravity.TOP);
      lp.setMargins(dp(14), dp(170), dp(14), 0);
      overlay.addView(row, lp);
    }

    private TextView quickButton(String icon, String title, int accent, Runnable action) {
      final TextView t = label(icon + "  " + title, 11, WHITE, Typeface.BOLD, Gravity.CENTER);
      t.setSingleLine(true);
      t.setBackground(round(Color.argb(238, 15, 23, 42), Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent)), 18));
      t.setElevation(dp(8));
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
      panel.addView(header("همه منوهای NV", "هر بخش مستقیماً عملیات خودش را اجرا می‌کند", this::closeSheet));
      panel.addView(primary("کد NV و QR برای نقطه دلخواه", GREEN, () -> { closeSheet(); NvRuntimeController.startCodePicker(activity); }));

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
      card.setBackground(round(Color.argb(245, 30, 41, 59), id == 4 ? RED : OUTLINE, 20));
      card.setPadding(dp(6), dp(5), dp(6), dp(5));
      card.setClickable(true);
      card.setOnClickListener(v -> { closeSheet(); handleMenu(id); });
      return card;
    }

    private void handleMenu(int id) {
      switch (id) {
        case 1 -> NvV032Actions.goHome(activity);
        case 2 -> NvV032Actions.openRoutePlanner(activity);
        case 3 -> showNearby();
        case 4 -> NvSmartActions.openNearby(activity, "اورژانس");
        case 5 -> NvV032Actions.openPlaceDetails(activity);
        case 6 -> NvV032Actions.openRouteAlerts(activity);
        case 7 -> NvSmartActions.openNearby(activity, "داروخانه");
        case 8 -> NvSmartActions.openNearby(activity, "پارک");
        case 9 -> NvV032Actions.openRadius(activity);
        case 10 -> NvV032Actions.openMetroStatus(activity);
        case 11 -> NvV032Actions.openTaxi(activity);
        case 12 -> NvV032Actions.openPreferences(activity);
        default -> NvV032Actions.openRoutePlanner(activity);
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

    private LinearLayout panelBase() {
      final LinearLayout p = new LinearLayout(activity);
      p.setOrientation(LinearLayout.VERTICAL);
      p.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      p.setBackground(round(Color.argb(252, 15, 23, 42), OUTLINE, 28));
      p.setElevation(dp(24));
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
      b.setBackground(round(color, color == PANEL_2 ? OUTLINE : color, 18));
      b.setClickable(true);
      b.setOnClickListener(v -> action.run());
      final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
      lp.setMargins(dp(12), dp(5), dp(12), dp(5));
      b.setLayoutParams(lp);
      return b;
    }

    private View actionCard(String title, Runnable action) {
      final TextView t = label(title, 14, WHITE, Typeface.BOLD, Gravity.CENTER);
      t.setBackground(round(Color.argb(245, 30, 41, 59), OUTLINE, 18));
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
