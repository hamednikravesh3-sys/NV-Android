package app.organicmaps;

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

/** Compact, user-facing Smart Travel hub. Every card opens a real v0.32 action directly. */
public final class NvSmartTravelUi {
  private static final String TAG = "nv-smart-travel-v032";
  private static final int BG = Color.rgb(3, 17, 29);
  private static final int PANEL = Color.rgb(7, 35, 57);
  private static final int BLUE = Color.rgb(40, 132, 255);
  private static final int CYAN = Color.rgb(47, 210, 255);
  private static final int GREEN = Color.rgb(42, 210, 115);
  private static final int AMBER = Color.rgb(255, 187, 54);
  private static final int WHITE = Color.WHITE;
  private static final int MUTED = Color.rgb(200, 220, 233);

  private NvSmartTravelUi() {}

  public static void openHub(MwmActivity a) {
    remove(a);
    ViewGroup host = a.findViewById(android.R.id.content);
    if (host == null) return;

    FrameLayout root = new FrameLayout(a);
    root.setTag(TAG);
    root.setBackgroundColor(BG);
    root.setElevation(dp(a, 50));
    host.addView(root, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout column = new LinearLayout(a);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(dp(a, 12), dp(a, 26), dp(a, 12), dp(a, 12));
    column.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

    LinearLayout header = new LinearLayout(a);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    header.setPadding(dp(a, 8), dp(a, 5), dp(a, 8), dp(a, 5));
    header.setBackground(round(a, PANEL, BLUE, 18, 1));

    View nv = NvAnimatedBrand.createLogo(a, 13, () -> NvRuntimeController.showCodeMenu(a));
    header.addView(nv, new LinearLayout.LayoutParams(dp(a, 52), dp(a, 42)));

    LinearLayout titles = new LinearLayout(a);
    titles.setOrientation(LinearLayout.VERTICAL);
    TextView t = text(a, "هوشمند سفر", 19, WHITE, Typeface.BOLD);
    TextView st = text(a, "مسیر مناسب را بر اساس نیازت انتخاب کن", 11, MUTED, Typeface.NORMAL);
    titles.addView(t); titles.addView(st);
    header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    TextView close = text(a, "×", 28, WHITE, Typeface.NORMAL);
    close.setGravity(Gravity.CENTER);
    close.setClickable(true);
    close.setOnClickListener(v -> remove(a));
    header.addView(close, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 46)));
    column.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 64)));

    TextView notice = text(a,
        "اطلاعات زنده‌ای که منبع معتبر ندارند نمایش داده نمی‌شوند؛ NV بین داده آنلاین، آفلاین و برآورد تقریبی تفاوت قائل می‌شود.",
        11, CYAN, Typeface.NORMAL);
    notice.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 10));
    notice.setBackground(round(a, Color.rgb(5, 29, 47), CYAN, 14, 1));
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    np.setMargins(0, dp(a, 8), 0, dp(a, 8));
    column.addView(notice, np);

    ScrollView scroll = new ScrollView(a);
    LinearLayout body = new LinearLayout(a);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

    body.addView(card(a, "✦", "چت هوشمند سفر", "مقصد را طبیعی بنویس", BLUE, () -> NvV032Actions.openChat(a)));
    body.addView(card(a, "⚡", "عجله دارم", "خودرو، ترکیبی و پیاده را مقایسه می‌کند", Color.rgb(255, 78, 91), () -> NvV032Actions.openHurry(a)));
    body.addView(card(a, "↝", "مسیر ترکیبی", "پیاده/تاکسی + مترو + ادامه تا مقصد", GREEN, () -> NvV032Actions.openMixed(a)));
    body.addView(card(a, "M", "تعویض هوشمند ایستگاه", "ایستگاه و خروجی مناسب را بررسی می‌کند", CYAN, () -> NvV032Actions.openStationTransfer(a)));
    body.addView(card(a, "🚇", "مترو و ایستگاه‌ها", "ایستگاه‌های واقعی اطراف؛ بدون جعل قطار زنده", Color.rgb(143, 92, 255), () -> NvV032Actions.openMetroStatus(a)));
    body.addView(card(a, "🚕", "تاکسی و محل سوارشدن", "نقاط تاکسی ثبت‌شده و مسیر دسترسی", AMBER, () -> NvV032Actions.openTaxi(a)));
    body.addView(card(a, "◷", "زمان رسیدن", "ETA با منبع و محدودیت مشخص", BLUE, () -> NvV032Actions.openEta(a)));
    body.addView(card(a, "⇄", "مقایسه زمان و هزینه", "چند مسیر خودرو + ترکیبی + پیاده", GREEN, () -> NvV032Actions.openTimeCost(a)));
    body.addView(card(a, "🚶", "راهنمای پیاده", "مسیریابی پیاده با موتور نقشه", CYAN, () -> NvV032Actions.openWalk(a)));
    body.addView(card(a, "⚙", "تنظیمات سفر", "هزینه، مصرف سوخت، حریم خصوصی و هشدارها", BLUE, () -> NvV032Actions.openPreferences(a)));

    scroll.addView(body);
    column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private static View card(MwmActivity a, String icon, String title, String sub, int accent, Runnable action) {
    LinearLayout row = new LinearLayout(a);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    row.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
    row.setBackground(round(a, PANEL, accent, 16, 1));
    row.setClickable(true);
    row.setOnClickListener(v -> { remove(a); action.run(); });

    TextView ic = text(a, icon, 20, accent, Typeface.BOLD);
    ic.setGravity(Gravity.CENTER);
    row.addView(ic, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 44)));

    LinearLayout tx = new LinearLayout(a);
    tx.setOrientation(LinearLayout.VERTICAL);
    tx.addView(text(a, title, 14, WHITE, Typeface.BOLD));
    tx.addView(text(a, sub, 11, MUTED, Typeface.NORMAL));
    row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    TextView arrow = text(a, "‹", 24, accent, Typeface.BOLD);
    arrow.setGravity(Gravity.CENTER);
    row.addView(arrow, new LinearLayout.LayoutParams(dp(a, 32), dp(a, 40)));

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(0, dp(a, 4), 0, dp(a, 4));
    row.setLayoutParams(lp);
    return row;
  }

  private static void remove(MwmActivity a) {
    ViewGroup host = a.findViewById(android.R.id.content);
    if (host == null) return;
    View v = host.findViewWithTag(TAG);
    if (v != null) host.removeView(v);
  }

  private static TextView text(android.content.Context c, String s, int sp, int color, int style) {
    TextView v = new TextView(c);
    v.setText(s);
    v.setTextSize(sp);
    v.setTextColor(color);
    v.setTypeface(Typeface.DEFAULT, style);
    v.setGravity(Gravity.RIGHT);
    v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    v.setTextDirection(View.TEXT_DIRECTION_RTL);
    return v;
  }

  private static GradientDrawable round(android.content.Context c, int fill, int stroke, int radius, int strokeDp) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(dp(c, radius));
    d.setStroke(dp(c, strokeDp), stroke);
    return d;
  }

  private static int dp(android.content.Context c, int v) {
    return Math.max(1, Math.round(c.getResources().getDisplayMetrics().density)) * v;
  }
}
