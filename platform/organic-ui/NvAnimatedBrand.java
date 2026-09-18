package app.organicmaps;

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

/**
 * Highly visible animated NV brand.
 * The opening animation is deliberately long enough to be noticeable on real devices.
 */
public final class NvAnimatedBrand {
  private static final int CYAN = Color.rgb(39, 211, 255);
  private static final int BLUE = Color.rgb(42, 120, 255);
  private static final int NAVY = Color.rgb(3, 18, 31);

  private NvAnimatedBrand() {}

  public static View createLogo(android.app.Activity activity, int textSp, Runnable action) {
    final TextView logo = new TextView(activity);
    logo.setText("NV");
    logo.setTextSize(textSp);
    logo.setTextColor(Color.WHITE);
    logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    logo.setGravity(Gravity.CENTER);
    logo.setShadowLayer(dp(activity, 14), 0f, 0f, CYAN);
    logo.setBackground(round(activity, Color.rgb(6, 39, 67), CYAN, 18, 2));
    logo.setClickable(action != null);
    if (action != null) logo.setOnClickListener(v -> action.run());

    final ObjectAnimator sx = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.88f, 1.12f, 0.96f, 1.06f, 0.88f);
    final ObjectAnimator sy = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.88f, 1.12f, 0.96f, 1.06f, 0.88f);
    final ObjectAnimator rot = ObjectAnimator.ofFloat(logo, View.ROTATION, -4f, 4f, 0f, -4f);
    final ObjectAnimator alpha = ObjectAnimator.ofFloat(logo, View.ALPHA, 0.72f, 1f, 0.84f, 1f, 0.72f);
    for (ObjectAnimator a : new ObjectAnimator[]{sx, sy, rot, alpha}) {
      a.setDuration(1500);
      a.setRepeatCount(ValueAnimator.INFINITE);
      a.setInterpolator(new AccelerateDecelerateInterpolator());
    }

    final AnimatorSet set = new AnimatorSet();
    set.playTogether(sx, sy, rot, alpha);
    logo.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override public void onViewAttachedToWindow(View v) { set.start(); }
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
    layer.setBackgroundColor(Color.rgb(1, 11, 20));
    layer.setElevation(dp(activity, 100));

    final LinearLayout center = new LinearLayout(activity);
    center.setOrientation(LinearLayout.VERTICAL);
    center.setGravity(Gravity.CENTER);
    center.setPadding(dp(activity, 26), dp(activity, 26), dp(activity, 26), dp(activity, 22));
    center.setBackground(round(activity, Color.rgb(5, 27, 47), BLUE, 30, 2));
    center.setElevation(dp(activity, 30));

    final View logo = createLogo(activity, 48, null);
    center.addView(logo, new LinearLayout.LayoutParams(dp(activity, 136), dp(activity, 98)));

    final TextView title = text(activity, "NV", 28, Color.WHITE, Typeface.BOLD);
    title.setGravity(Gravity.CENTER);
    title.setPadding(0, dp(activity, 14), 0, 0);
    center.addView(title);

    final TextView fa = text(activity, "مسیریابی هوشمند برای زندگی واقعی", 14, Color.rgb(221, 236, 247), Typeface.BOLD);
    fa.setGravity(Gravity.CENTER);
    fa.setPadding(0, dp(activity, 8), 0, 0);
    center.addView(fa);

    final TextView en = text(activity, "Navigate a Better Tomorrow", 11, CYAN, Typeface.NORMAL);
    en.setGravity(Gravity.CENTER);
    en.setPadding(0, dp(activity, 5), 0, 0);
    center.addView(en);

    final TextView version = text(activity, "v0.29 • CLEAN BUILD", 10, Color.rgb(128, 190, 225), Typeface.BOLD);
    version.setGravity(Gravity.CENTER);
    version.setPadding(0, dp(activity, 10), 0, 0);
    center.addView(version);

    final FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(activity, 320), dp(activity, 292), Gravity.CENTER);
    layer.addView(center, cp);
    host.addView(layer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    layer.setAlpha(0f);
    layer.animate().alpha(1f).setDuration(250).start();
  }

  private static TextView text(android.content.Context c, String s, int sp, int color, int style) {
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
