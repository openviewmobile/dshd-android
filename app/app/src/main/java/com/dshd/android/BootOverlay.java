package com.dshd.android;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full-screen launch cover shown until the local host and its web UI are ready.
 *
 * <p>It deliberately lives above the native action bar and WebView: the old
 * shell used to expose an empty white WebView and a tiny status strip during
 * host startup. A dark, branded surface with a real progress indicator makes
 * the one-time rootfs unpack and the host boot feel like one deliberate step.
 */
final class BootOverlay extends FrameLayout {
  private static final int BG = Color.rgb(5, 7, 15);
  private static final Pattern PERCENT = Pattern.compile("(\\d{1,3})\\s*%");

  private final float density;
  private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextView statusView;
  private final TextView hintView;
  private final BootProgress progress;
  private Shader backgroundShader;
  private RadialGradient glowTop;
  private RadialGradient glowBottom;
  private RadialGradient glowCenter;
  private boolean dismissed;

  BootOverlay(Context context) {
    super(context);
    density = getResources().getDisplayMetrics().density;
    setWillNotDraw(false);
    setClickable(true);
    setFocusable(true);
    statusView = new TextView(context);
    hintView = new TextView(context);
    progress = new BootProgress(context);
    buildContent();
  }

  private int dp(float value) {
    return Math.round(value * density);
  }

  private void buildContent() {
    LinearLayout content = new LinearLayout(getContext());
    content.setOrientation(LinearLayout.VERTICAL);
    content.setGravity(Gravity.CENTER_HORIZONTAL);
    content.setPadding(dp(24), 0, dp(24), dp(36));

    FrameLayout mark = new FrameLayout(getContext());
    GradientDrawable markBackground = new GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        new int[] {0xFF172742, 0xFF0B1322});
    markBackground.setCornerRadius(dp(27));
    markBackground.setStroke(dp(1), 0xFF2C4164);
    mark.setBackground(markBackground);
    mark.setElevation(dp(8));

    ImageView logo = new ImageView(getContext());
    logo.setImageResource(R.drawable.ic_boot_logo);
    logo.setPadding(dp(16), dp(16), dp(16), dp(16));
    logo.setContentDescription(null);
    mark.addView(logo, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

    LinearLayout.LayoutParams markParams =
        new LinearLayout.LayoutParams(dp(94), dp(94));
    markParams.bottomMargin = dp(24);
    content.addView(mark, markParams);

    TextView title = new TextView(getContext());
    title.setText("DSHD");
    title.setTextColor(0xFFF2F7FF);
    title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
    title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    title.setLetterSpacing(0.16f);
    title.setGravity(Gravity.CENTER);
    content.addView(title);

    TextView subtitle = new TextView(getContext());
    subtitle.setText("DEEPSEEK HARNESS DESKTOP");
    subtitle.setTextColor(0xFF8196B7);
    subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
    subtitle.setLetterSpacing(0.23f);
    subtitle.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    subtitleParams.topMargin = dp(7);
    content.addView(subtitle, subtitleParams);

    statusView.setText("Preparing your workspace");
    statusView.setTextColor(0xFFD3E1F6);
    statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    statusView.setGravity(Gravity.CENTER);
    statusView.setMaxWidth(dp(310));
    LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    statusParams.topMargin = dp(34);
    content.addView(statusView, statusParams);

    LinearLayout.LayoutParams progressParams =
        new LinearLayout.LayoutParams(dp(218), dp(4));
    progressParams.topMargin = dp(17);
    content.addView(progress, progressParams);

    hintView.setText("Starting the local AI engine");
    hintView.setTextColor(0xFF7F94B4);
    hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    hintView.setGravity(Gravity.CENTER);
    hintView.setLineSpacing(dp(3), 1f);
    hintView.setMaxWidth(dp(310));
    LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    hintParams.topMargin = dp(17);
    content.addView(hintView, hintParams);

    LayoutParams contentParams = new LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    contentParams.gravity = Gravity.CENTER;
    addView(content, contentParams);

    TextView footer = new TextView(getContext());
    footer.setText("DSHD  " + BuildConfig.VERSION_NAME + "   ·   LOCAL RUNTIME");
    footer.setTextColor(0xFF4E627F);
    footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
    footer.setLetterSpacing(0.14f);
    footer.setGravity(Gravity.CENTER);
    LayoutParams footerParams = new LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    footerParams.gravity = Gravity.BOTTOM;
    footerParams.bottomMargin = dp(27);
    addView(footer, footerParams);
  }

  void setMessage(String raw) {
    if (dismissed || raw == null) return;
    String message = raw.trim();
    if (message.isEmpty()) return;
    int percent = -1;
    Matcher matcher = PERCENT.matcher(message);
    if (matcher.find()) {
      try {
        percent = Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException ignored) {
        // Stay indeterminate if the message is malformed.
      }
    }
    progress.setProgress(percent);
    statusView.setText(humanize(message));
    hintView.setText(hintFor(message));
    statusView.setContentDescription(humanize(message));
  }

  private String humanize(String message) {
    if (message.startsWith("Installing bundled rootfs")) return "Preparing the built-in runtime";
    if (message.startsWith("Updating bundled rootfs")) return "Updating the built-in runtime";
    if (message.startsWith("Downloading rootfs")) return "Downloading the runtime";
    if (message.startsWith("Importing rootfs") || message.startsWith("Unpacking runtime")) {
      return "Unpacking the runtime";
    }
    if (message.startsWith("Finalizing rootfs")) return "Finishing setup";
    if (message.startsWith("Rootfs ready")) return "Runtime ready";
    if (message.startsWith("Starting local dsh Host")) return "Starting the local AI engine";
    if (message.startsWith("Restarting dsh Host")) return "Restarting the local AI engine";
    return message;
  }

  private String hintFor(String message) {
    if (message.startsWith("Installing bundled rootfs")
        || message.startsWith("Updating bundled rootfs")
        || message.startsWith("Importing rootfs")
        || message.startsWith("Unpacking runtime")
        || message.startsWith("Finalizing rootfs")) {
      return "First launch unpacks once. Keep DSHD open (1–3 min).";
    }
    if (message.startsWith("Downloading rootfs")) {
      return "One-time download. Wi-Fi is recommended.";
    }
    if (message.startsWith("Rootfs ready")) return "Opening your workspace…";
    if (message.startsWith("Starting local dsh Host")
        || message.startsWith("Restarting dsh Host")) {
      return "Loading tools and your workspace.";
    }
    return "Everything stays on this device.";
  }

  void dismiss() {
    if (dismissed) return;
    dismissed = true;
    setClickable(false);
    setFocusable(false);
    progress.stopAnimation();
    animate().alpha(0f).setDuration(300).withEndAction(() -> setVisibility(GONE)).start();
  }

  boolean isDismissed() {
    return dismissed;
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    backgroundShader = new LinearGradient(0, 0, 0, height,
        new int[] {0xFF04060D, 0xFF0A1120, 0xFF05070F},
        new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    glowTop = new RadialGradient(width * 0.82f, height * 0.18f, width * 0.78f,
        new int[] {0x334F8CFF, 0x00000000}, new float[] {0f, 1f}, Shader.TileMode.CLAMP);
    glowBottom = new RadialGradient(width * 0.16f, height * 0.80f, width * 0.86f,
        new int[] {0x226B4DFF, 0x00000000}, new float[] {0f, 1f}, Shader.TileMode.CLAMP);
    glowCenter = new RadialGradient(width * 0.50f, height * 0.38f, width * 0.52f,
        new int[] {0x1A2DD4BF, 0x00000000}, new float[] {0f, 1f}, Shader.TileMode.CLAMP);
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int width = getWidth();
    int height = getHeight();
    if (width == 0 || height == 0) return;

    backgroundPaint.setShader(backgroundShader);
    canvas.drawRect(0, 0, width, height, backgroundPaint);

    glowPaint.setShader(glowTop);
    canvas.drawRect(0, 0, width, height, glowPaint);
    glowPaint.setShader(glowBottom);
    canvas.drawRect(0, 0, width, height, glowPaint);
    glowPaint.setShader(glowCenter);
    canvas.drawRect(0, 0, width, height, glowPaint);

    particlePaint.setColor(0xFFD7E8FF);
    for (int i = 0; i < 22; i++) {
      float x = ((i * 73 + 11) % 100) / 100f * width;
      float y = ((i * 37 + 7) % 100) / 100f * height;
      // Static stars: animating a full-screen radial background was costly
      // during the exact seconds when the host and WebView need the CPU.
      particlePaint.setAlpha(i % 4 == 0 ? 42 : 22);
      canvas.drawCircle(x, y, dp(0.7f + (i % 3) * 0.45f), particlePaint);
    }

    // A restrained horizon line gives the otherwise empty surface depth.
    particlePaint.setColor(0xFF8EB8FF);
    particlePaint.setAlpha(13);
    particlePaint.setStrokeWidth(dp(1));
    float horizon = height * 0.62f;
    canvas.drawLine(width * 0.14f, horizon, width * 0.86f, horizon, particlePaint);
    particlePaint.setAlpha(255);
  }

  /** Thin determinate/indeterminate bar; avoids the platform spinner look. */
  private static final class BootProgress extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ValueAnimator animator;
    private Shader fillShader;
    private float phase;
    private int percent = -1;

    BootProgress(Context context) {
      super(context);
      trackPaint.setColor(0xFF1A2740);
      fillPaint.setColor(0xFF4F8CFF);
    }

    void setProgress(int value) {
      int next = value < 0 ? -1 : Math.max(0, Math.min(100, value));
      if (next == percent) return;
      percent = next;
      if (percent >= 0) {
        if (animator != null) animator.cancel();
      } else {
        startAnimationIfNeeded();
      }
      invalidate();
    }

    void stopAnimation() {
      if (animator != null) animator.cancel();
    }

    private void startAnimationIfNeeded() {
      if (animator == null) {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1600L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
          phase = (float) animation.getAnimatedValue();
          if (percent < 0) invalidate();
        });
      }
      if (!animator.isStarted()) animator.start();
    }

    @Override protected void onAttachedToWindow() {
      super.onAttachedToWindow();
      if (percent < 0) startAnimationIfNeeded();
    }

    @Override protected void onDetachedFromWindow() {
      if (animator != null) animator.cancel();
      super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
      super.onSizeChanged(width, height, oldWidth, oldHeight);
      if (width > 0) {
        fillShader = new LinearGradient(0, 0, width, 0,
            new int[] {0xFF3C7DFF, 0xFF8D6BFF}, null, Shader.TileMode.CLAMP);
      }
    }

    @Override protected void onDraw(Canvas canvas) {
      float width = getWidth();
      float height = getHeight();
      float radius = height / 2f;
      rect.set(0, 0, width, height);
      canvas.drawRoundRect(rect, radius, radius, trackPaint);

      if (fillShader == null && width > 0) {
        fillShader = new LinearGradient(0, 0, width, 0,
            new int[] {0xFF3C7DFF, 0xFF8D6BFF}, null, Shader.TileMode.CLAMP);
      }
      fillPaint.setShader(fillShader);
      if (percent >= 0) {
        float filled = Math.max(height, width * percent / 100f);
        rect.set(0, 0, filled, height);
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
      } else {
        float segment = width * 0.30f;
        float left = (width + segment) * phase - segment;
        int save = canvas.save();
        canvas.clipRect(0, 0, width, height);
        rect.set(left, 0, left + segment, height);
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        canvas.restoreToCount(save);
      }
    }
  }
}
