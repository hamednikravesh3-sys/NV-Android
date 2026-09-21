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

/**
 * Modern map-first NV home overlay.
 * The map remains the visual focus; routing is the primary action.
 */
public final class NvMapMenuOverlay {
  private static final int WHITE = Color.WHITE;
  private static final int INK = Color.rgb(20, 29, 43);
  private static final int MUTED = Color.rgb(102, 116, 134);
  private static final int SURFACE = Color.rgb(255, 255, 255);
  private static final int SOFT = Color.rgb(244, 247, 250);
  private static final int BORDER = Color.rgb(226, 232, 240);
  private static final int BLUE = Color.rgb(45, 110, 245);
  private static final int PURPLE = Color.rgb(126, 87, 194);
  private static final int ORANGE = Color.rgb(255, 111, 0);
  private static final int GREEN = Color.rgb(24, 169, 89);
  private static final int RED = Color.rgb(226, 66, 75);

  private static final String[] TITLES = {
      "مسیریابی", "اطراف من", "مکان من", "اورژانس",
      "داروخانه", "مترو", "پارک و تفریح", "هشدارهای مسیر",
      "محدوده جستجو", "کد NV و QR", "تنظیمات سفر", "صفحه اصلی"
  };

  private static final String[] ICONS = {
      "➤", "⌖", "◎", "SOS",
      "✚", "M", "♣", "!",
      "◉", "▦", "⚙", "⌂"
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
      host.addView(overlay, new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      addRouteCard();
      addEmergencyButton();
      addBottomDock();
      addSheetLayer();
      try { MapLanguageCode.setMapLanguageCode("fa"); } catch (Throwable ignored) {}
    }

    private void addRouteCard() {
      final LinearLayout card = new LinearLayout(activity);
      card.setOrientation(LinearLayout.HORIZONTAL);
      card.setGravity(Gravity.CENTER_VERTICAL);
      card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      card.setPadding(dp(12), dp(8), dp(12), dp(8));
      card.setBackground(round(SURFACE, Color.TRANSPARENT, 24));
      card.setElevation(dp(14));
      card.setClickable(true);
      card.setOnClickListener(v -> NvV032Actions.openRoutePlanner(activity));

      final TextView action = label("➤", 21, WHITE, Typeface.BOLD, Gravity.CENTER);
      action.setBackground(round(BLUE, BLUE, 22));
      card.addView(action, new LinearLayout.LayoutParams(dp(44), dp(44)));

      final LinearLayout copy = new LinearLayout(activity);
      copy.setOrientation(LinearLayout.VERTICAL);
      copy.setPadding(dp(12), 0, dp(12), 0);
      final TextView title = label("مبدأ و مقصد", 17, INK, Typeface.BOLD, Gravity.RIGHT);
      final TextView sub = label("جستجو کنید یا مستقیم روی نقشه انتخاب کنید", 11, MUTED, Typeface.NORMAL, Gravity.RIGHT);
      sub.setSingleLine(true);
      copy.addView(title);
      copy.addView(sub);
      card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

      final TextView routeDots = label("●  ···  ⚑", 16, PURPLE, Typeface.BOLD, Gravity.CENTER);
      routeDots.setPadding(dp(6), 0, dp(6), 0);
      card.addView(routeDots, new LinearLayout.LayoutParams(dp(92), dp(44)));

      final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(70), Gravity.TOP);
      lp.setMargins(dp(14), dp(28), dp(14), 0);
      overlay.addView(card, lp);
    }

    private void addEmergencyButton() {
      final TextView sos = label("SOS", 12, WHITE, Typeface.BOLD, Gravity.CENTER);
      sos.setBackground(round(RED, RED, 26));
      sos.setElevation(dp(12));
      sos.setClickable(true);
      sos.setOnClickListener(v -> showSOS());

      final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
          dp(52), dp(52), Gravity.TOP | Gravity.LEFT);
      lp.setMargins(dp(16), dp(112), 0, 0);
      overlay.addView(sos, lp);
    }

    private void addBottomDock() {
      final LinearLayout dock = new LinearLayout(activity);
      dock.setOrientation(LinearLayout.HORIZONTAL);
      dock.setGravity(Gravity.CENTER);
      dock.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      dock.setPadding(dp(6), dp(5), dp(6), dp(5));
      dock.setBackground(round(Color.argb(250, 255, 255, 255), Color.TRANSPARENT, 24));
      dock.setElevation(dp(16));

      dock.addView(navButton("➤", "مسیر", BLUE, () -> NvV032Actions.openRoutePlanner(activity)), navWeight());
      dock.addView(navButton("⌖", "اطراف", INK, this::showNearby), navWeight());

      final View location = navButton("◎", "مکان من", INK, () -> NvRuntimeController.showLocationStatus(activity));
      location.setTag("nv-location-quick");
      dock.addView(location, navWeight());

      dock.addView(navButton("☰", "بیشتر", INK, this::showAllMenus), navWeight());

      final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(70), Gravity.BOTTOM);
      lp.setMargins(dp(14), 0, dp(14), dp(22));
      overlay.addView(dock, lp);
    }

    private View navButton(String icon, String title, int color, Runnable action) {
      final LinearLayout item = new LinearLayout(activity);
      item.setOrientation(LinearLayout.VERTICAL);
      item.setGravity(Gravity.CENTER);
      item.setClickable(true);
      item.setOnClickListener(v -> action.run());

      final TextView i = label(icon, 20, color, Typeface.BOLD, Gravity.CENTER);
      final TextView t = label(title, 10, color, Typeface.BOLD, Gravity.CENTER);
      item.addView(i, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(31)));
      item.addView(t, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(25)));
      return item;
    }

    private LinearLayout.LayoutParams navWeight() {
      final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(60), 1f);
      p.setMargins(dp(2), 0, dp(2), 0);
      return p;
    }

    private void addSheetLayer() {
      sheetLayer = new FrameLayout(activity);
      sheetLayer.setVisibility(View.GONE);
      sheetLayer.setBackgroundColor(Color.argb(92, 15, 23, 42));
      sheetLayer.setClickable(true);
      sheetLayer.setOnClickListener(v -> closeSheet());
      overlay.addView(sheetLayer, new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showAllMenus() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);

      final LinearLayout panel = panelBase();
      panel.addView(sheetHandle());
      panel.addView(header("بیشتر", "ابزارهای کاربردی NV", this::closeSheet));

      final ScrollView scroll = new ScrollView(activity);
      scroll.setVerticalScrollBarEnabled(false);
      final LinearLayout grid = new LinearLayout(activity);
      grid.setOrientation(LinearLayout.VERTICAL);
      grid.setPadding(dp(8), dp(2), dp(8), dp(12));

      for (int i = 0; i < TITLES.length; i += 2) {
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.addView(menuCard(i + 1), weightCard());
        if (i + 1 < TITLES.length)
          row.addView(menuCard(i + 2), weightCard());
        else
          row.addView(new View(activity), weightCard());
        grid.addView(row, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(82)));
      }

      scroll.addView(grid);
      panel.addView(scroll, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
      addPanel(panel, dp(560));
    }

    private View menuCard(int id) {
      final int idx = id - 1;
      final LinearLayout card = new LinearLayout(activity);
      card.setOrientation(LinearLayout.HORIZONTAL);
      card.setGravity(Gravity.CENTER_VERTICAL);
      card.setPadding(dp(12), dp(8), dp(12), dp(8));
      card.setBackground(round(SOFT, BORDER, 17));
      card.setClickable(true);
      card.setOnClickListener(v -> { closeSheet(); handleMenu(id); });

      final int accent = id == 4 ? RED : (id == 1 ? BLUE : INK);
      final TextView icon = label(ICONS[idx], id == 4 ? 10 : 20, accent, Typeface.BOLD, Gravity.CENTER);
      if (id == 4) {
        icon.setTextColor(WHITE);
        icon.setBackground(round(RED, RED, 18));
      }
      card.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
      final TextView title = label(TITLES[idx], 13, INK, Typeface.BOLD, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
      title.setPadding(dp(8), 0, dp(8), 0);
      card.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));
      return card;
    }

    private void handleMenu(int id) {
      switch (id) {
        case 1 -> NvV032Actions.openRoutePlanner(activity);
        case 2 -> showNearby();
        case 3 -> NvRuntimeController.showLocationStatus(activity);
        case 4 -> showSOS();
        case 5 -> NvSmartActions.openNearby(activity, "داروخانه");
        case 6 -> NvV032Actions.openMetroStatus(activity);
        case 7 -> NvSmartActions.openNearby(activity, "پارک");
        case 8 -> NvV032Actions.openRouteAlerts(activity);
        case 9 -> NvV032Actions.openRadius(activity);
        case 10 -> NvRuntimeController.startCodePicker(activity);
        case 11 -> NvV032Actions.openPreferences(activity);
        case 12 -> NvV032Actions.goHome(activity);
        default -> NvV032Actions.openRoutePlanner(activity);
      }
    }

    private void showNearby() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);

      final LinearLayout panel = panelBase();
      panel.addView(sheetHandle());
      panel.addView(header("اطراف من", "دسته موردنظر را انتخاب کنید", this::closeSheet));

      final String[] cats = {
          "بیمارستان", "داروخانه", "درمانگاه", "پلیس",
          "آتش‌نشانی", "پارکینگ", "پمپ بنزین", "رستوران",
          "کافه", "پارک", "ایستگاه مترو", "هتل"
      };

      final ScrollView sv = new ScrollView(activity);
      sv.setVerticalScrollBarEnabled(false);
      final LinearLayout grid = new LinearLayout(activity);
      grid.setOrientation(LinearLayout.VERTICAL);
      grid.setPadding(dp(8), 0, dp(8), dp(10));

      for (int i = 0; i < cats.length; i += 2) {
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final String a = cats[i];
        row.addView(actionCard(a, () -> {
          closeSheet();
          NvSmartActions.openNearby(activity, a);
        }), weightCard());
        if (i + 1 < cats.length) {
          final String b = cats[i + 1];
          row.addView(actionCard(b, () -> {
            closeSheet();
            NvSmartActions.openNearby(activity, b);
          }), weightCard());
        }
        grid.addView(row, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
      }

      sv.addView(grid);
      panel.addView(sv, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
      addPanel(panel, dp(500));
    }

    private void showSOS() {
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.VISIBLE);

      final LinearLayout panel = panelBase();
      panel.addView(sheetHandle());
      panel.addView(header("اضطراری", "دسترسی سریع به خدمات ضروری", this::closeSheet));
      panel.addView(primary("نزدیک‌ترین اورژانس‌ها", RED,
          () -> { closeSheet(); NvSmartActions.openNearby(activity, "اورژانس"); }));
      panel.addView(primary("نزدیک‌ترین بیمارستان‌ها", BLUE,
          () -> { closeSheet(); NvSmartActions.openNearby(activity, "بیمارستان"); }));
      panel.addView(primary("تماس با ۱۱۵", RED, () -> dial("115")));
      panel.addView(secondary("تماس با ۱۱۰", () -> dial("110")));
      panel.addView(secondary("تماس با ۱۲۵", () -> dial("125")));
      addPanel(panel, dp(430));
    }

    private LinearLayout panelBase() {
      final LinearLayout p = new LinearLayout(activity);
      p.setOrientation(LinearLayout.VERTICAL);
      p.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      p.setPadding(dp(10), dp(8), dp(10), dp(12));
      p.setBackground(round(SURFACE, Color.TRANSPARENT, 28));
      p.setElevation(dp(22));
      p.setClickable(true);
      p.setOnClickListener(v -> {});
      return p;
    }

    private View sheetHandle() {
      final TextView handle = label("━", 22, Color.rgb(190, 198, 209),
          Typeface.BOLD, Gravity.CENTER);
      return handle;
    }

    private View header(String title, String subtitle, Runnable close) {
      final LinearLayout h = new LinearLayout(activity);
      h.setOrientation(LinearLayout.HORIZONTAL);
      h.setGravity(Gravity.CENTER_VERTICAL);
      h.setPadding(dp(8), dp(2), dp(8), dp(8));

      final LinearLayout tx = new LinearLayout(activity);
      tx.setOrientation(LinearLayout.VERTICAL);
      tx.addView(label(title, 20, INK, Typeface.BOLD, Gravity.RIGHT));
      final TextView sub = label(subtitle, 12, MUTED, Typeface.NORMAL, Gravity.RIGHT);
      sub.setMaxLines(2);
      tx.addView(sub);
      h.addView(tx, new LinearLayout.LayoutParams(
          0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

      final TextView x = label("×", 28, INK, Typeface.NORMAL, Gravity.CENTER);
      x.setBackground(round(SOFT, BORDER, 22));
      x.setClickable(true);
      x.setOnClickListener(v -> close.run());
      h.addView(x, new LinearLayout.LayoutParams(dp(44), dp(44)));
      return h;
    }

    private View primary(String title, int color, Runnable action) {
      final TextView b = label(title, 14, WHITE, Typeface.BOLD, Gravity.CENTER);
      b.setBackground(round(color, color, 17));
      b.setClickable(true);
      b.setOnClickListener(v -> action.run());
      final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
      lp.setMargins(dp(8), dp(5), dp(8), dp(5));
      b.setLayoutParams(lp);
      return b;
    }

    private View secondary(String title, Runnable action) {
      final TextView b = label(title, 14, INK, Typeface.BOLD, Gravity.CENTER);
      b.setBackground(round(SOFT, BORDER, 17));
      b.setClickable(true);
      b.setOnClickListener(v -> action.run());
      final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
      lp.setMargins(dp(8), dp(5), dp(8), dp(5));
      b.setLayoutParams(lp);
      return b;
    }

    private View actionCard(String title, Runnable action) {
      final TextView t = label(title, 13, INK, Typeface.BOLD, Gravity.CENTER);
      t.setBackground(round(SOFT, BORDER, 16));
      t.setClickable(true);
      t.setOnClickListener(v -> action.run());
      return t;
    }

    private void addPanel(LinearLayout panel, int height) {
      final FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
      p.setMargins(dp(8), 0, dp(8), dp(8));
      sheetLayer.addView(panel, p);
    }

    private void closeSheet() {
      if (sheetLayer == null) return;
      sheetLayer.removeAllViews();
      sheetLayer.setVisibility(View.GONE);
    }

    private void dial(String number) {
      try {
        activity.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
      } catch (Throwable e) {
        Toast.makeText(activity, "شماره‌گیر در دسترس نیست", Toast.LENGTH_SHORT).show();
      }
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
      if (stroke != Color.TRANSPARENT)
        d.setStroke(dp(1), stroke);
      return d;
    }

    private LinearLayout.LayoutParams weightCard() {
      final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
          0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
      p.setMargins(dp(4), dp(4), dp(4), dp(4));
      return p;
    }

    private int dp(int value) {
      return value * density;
    }
  }
}
