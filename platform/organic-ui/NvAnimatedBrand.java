package app.organicmaps;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * NV v0.30 brand.
 * Opening screen intentionally contains the NV symbol only: no title, subtitle or version text.
 */
public final class NvAnimatedBrand {
  private NvAnimatedBrand() {}

  public static View createLogo(android.app.Activity activity, int unusedTextSp, Runnable action) {
    final View logo = logoView(activity);
    logo.setClickable(action != null);
    if (action != null) logo.setOnClickListener(v -> action.run());
    startSoftPulse(logo);
    return logo;
  }

  public static void install(android.app.Activity activity) {
    final ViewGroup host = activity.findViewById(android.R.id.content);
    if (host == null || host.findViewWithTag("nv-opening-brand") != null) return;

    final FrameLayout layer = new FrameLayout(activity);
    layer.setTag("nv-opening-brand");
    layer.setClickable(false);
    layer.setBackgroundColor(Color.rgb(3, 10, 17));
    layer.setElevation(dp(activity, 100));

    final View logo = logoView(activity);
    logo.setAlpha(0f);
    logo.setScaleX(0.82f);
    logo.setScaleY(0.82f);

    final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
        dp(activity, 286), dp(activity, 286), Gravity.CENTER);
    layer.addView(logo, lp);
    host.addView(layer, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    final ObjectAnimator alpha = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f);
    final ObjectAnimator sx = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.82f, 1.04f, 1f);
    final ObjectAnimator sy = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.82f, 1.04f, 1f);
    alpha.setDuration(620);
    sx.setDuration(1050);
    sy.setDuration(1050);
    sx.setInterpolator(new AccelerateDecelerateInterpolator());
    sy.setInterpolator(new AccelerateDecelerateInterpolator());

    final AnimatorSet intro = new AnimatorSet();
    intro.playTogether(alpha, sx, sy);
    intro.start();
  }

  private static View logoView(android.app.Activity activity) {
    final int resId = activity.getResources().getIdentifier(
        "nv_splash_logo", "drawable", activity.getPackageName());

    if (resId != 0) {
      final ImageView image = new ImageView(activity);
      image.setImageResource(resId);
      image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
      image.setAdjustViewBounds(true);
      return image;
    }

    // Build-time fallback only; normal NV builds always package nv_splash_logo.
    final TextView fallback = new TextView(activity);
    fallback.setText("NV");
    fallback.setTextColor(Color.WHITE);
    fallback.setTextSize(38);
    fallback.setGravity(Gravity.CENTER);
    return fallback;
  }

  private static void startSoftPulse(View logo) {
    final ObjectAnimator sx = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.96f, 1.04f, 0.96f);
    final ObjectAnimator sy = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.96f, 1.04f, 0.96f);
    sx.setDuration(2200);
    sy.setDuration(2200);
    sx.setRepeatCount(ValueAnimator.INFINITE);
    sy.setRepeatCount(ValueAnimator.INFINITE);
    sx.setInterpolator(new AccelerateDecelerateInterpolator());
    sy.setInterpolator(new AccelerateDecelerateInterpolator());

    final AnimatorSet set = new AnimatorSet();
    set.playTogether(sx, sy);
    logo.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override public void onViewAttachedToWindow(View v) { set.start(); }
      @Override public void onViewDetachedFromWindow(View v) { set.cancel(); }
    });
  }

  private static int dp(android.content.Context c, int v) {
    return Math.max(1, Math.round(c.getResources().getDisplayMetrics().density)) * v;
  }
}
