package app.organicmaps;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Animated NV brand used on the opening screen and home bar. */
public final class NvAnimatedBrand {
  private static final int CYAN = Color.rgb(40, 206, 255);
  private static final int BLUE = Color.rgb(45, 139, 255);
  private static final int NAVY = Color.rgb(5, 27, 47);

  private NvAnimatedBrand() {}

  public static View createLogo(android.app.Activity activity, int textSp, Runnable action) {
    final TextView logo = new TextView(activity);
    logo.setText("NV");
    logo.setTextSize(textSp);
    logo.setTextColor(Color.WHITE);
    logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    logo.setGravity(Gravity.CENTER);
    logo.setShadowLayer(dp(activity, 10), 0f, 0f, CYAN);
    logo.setBackground(round(activity, Color.argb(245, 7, 33, 55), CYAN, 16, 2));
    logo.setClickable(action != null);
    if (action != null) logo.setOnClickListener(v -> action.run());

    final ObjectAnimator sx = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.93f, 1.08f, 0.93f);
    final ObjectAnimator sy = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.93f, 1.08f, 0.93f);
    final ObjectAnimator glow = ObjectAnimator.ofFloat(logo, View.ALPHA, 0.82f, 1f, 0.82f);
    sx.setRepeatCount(ValueAnimator.INFINITE);
    sy.setRepeatCount(ValueAnimator.INFINITE);
    glow.setRepeatCount(ValueAnimator.INFINITE);
    sx.setDuration(1800);
    sy.setDuration(1800);
    glow.setDuration(1800);
    sx.setInterpolator(new AccelerateDecelerateInterpolator());
    sy.setInterpolator(new AccelerateDecelerateInterpolator());
    glow.setInterpolator(new AccelerateDecelerateInterpolator());

    final AnimatorSet set = new AnimatorSet();
    set.playTogether(sx, sy, glow);
    logo.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override public void onViewAttachedToWindow(View v) { if (!set.isStarted()) set.start(); }
      @Override public void onViewDetachedFromWindow(View v) { set.cancel(); }
    });
    return logo;
  }

  public static void install(android.app.Activity activity) {
    final ViewGroup host = activity.findViewById(android.R.id.content);
    if (host == null || host.findViewWithTag("nv-opening-brand") != null) return;

    final FrameLayout layer = new FrameLayout(activity);
    layer.setTag("nv-opening-brand");
    layer.setClickable(false);
    layer.setBackgroundColor(Color.argb(190, 2, 14, 25));

    final LinearLayout card = new LinearLayout(activity);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setGravity(Gravity.CENTER);
    card.setPadding(dp(activity, 22), dp(activity, 22), dp(activity, 22), dp(activity, 18));
    card.setBackground(round(activity, Color.argb(245, 5, 27, 47), BLUE, 28, 2));
    card.setElevation(dp(activity, 16));

    final View logo = createLogo(activity, 42, null);
    card.addView(logo, new LinearLayout.LayoutParams(dp(activity, 118), dp(activity, 90)));

    final TextView name = text(activity, "NV", 24, Color.WHITE, Typeface.BOLD);
    name.setGravity(Gravity.CENTER);
    name.setPadding(0, dp(activity, 12), 0, 0);
    card.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    final TextView fa = text(activity, "مسیریابی هوشمند برای زندگی واقعی", 14, Color.rgb(211, 229, 241), Typeface.BOLD);
    fa.setGravity(Gravity.CENTER);
    fa.setPadding(0, dp(activity, 5), 0, 0);
    card.addView(fa, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    final TextView en = text(activity, "Navigate a Better Tomorrow", 11, CYAN, Typeface.NORMAL);
    en.setGravity(Gravity.CENTER);
    en.setPadding(0, dp(activity, 5), 0, 0);
    card.addView(en, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    final FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(activity, 300), dp(activity, 250), Gravity.CENTER);
    layer.addView(card, cp);
    host.addView(layer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    layer.setAlpha(0f);
    layer.animate().alpha(1f).setDuration(260).setListener(new AnimatorListenerAdapter() {}).start();
  }

  private static TextView text(android.content.Context c, String s, int sp, int color, int style) {
    final TextView v = new TextView(c);
    v.setText(s);
    v.setTextSize(sp);
    v.setTextColor(color);
    v.setTypeface(Typeface.DEFAULT, style);
    v.setGravity(Gravity.RIGHT);
    v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    return v;
  }

  private static GradientDrawable round(android.content.Context c, int fill, int stroke, int radius, int strokeDp) {
    final GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(dp(c, radius));
    d.setStroke(dp(c, strokeDp), stroke);
    return d;
  }

  private static int dp(android.content.Context c, int v) {
    return Math.max(1, Math.round(c.getResources().getDisplayMetrics().density)) * v;
  }
}
