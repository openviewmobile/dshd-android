package com.dshd.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.regex.Pattern

/**
 * Full-screen launch cover shown until the local host and its web UI are ready.
 *
 * <p>It deliberately lives above the native action bar and WebView: the old
 * shell used to expose an empty white WebView and a tiny status strip during
 * host startup. A dark, branded surface with a real progress indicator makes
 * the one-time rootfs unpack and the host boot feel like one deliberate step.
 */
internal class BootOverlay(context: Context) : FrameLayout(context) {
  private val density: Float
  private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val statusView: TextView
  private val hintView: TextView
  private val progress: BootProgress
  private var backgroundShader: Shader? = null
  private var glowTop: RadialGradient? = null
  private var glowBottom: RadialGradient? = null
  private var glowCenter: RadialGradient? = null
  private var dismissed = false

  init {
    density = resources.displayMetrics.density
    setWillNotDraw(false)
    isClickable = true
    isFocusable = true
    statusView = TextView(context)
    hintView = TextView(context)
    progress = BootProgress(context)
    buildContent()
  }

  private fun dp(value: Float): Int {
    return Math.round(value * density)
  }

  private fun buildContent() {
    val content = LinearLayout(getContext())
    content.orientation = LinearLayout.VERTICAL
    content.gravity = Gravity.CENTER_HORIZONTAL
    content.setPadding(dp(24f), 0, dp(24f), dp(36f))

    val mark = FrameLayout(getContext())
    val markBackground = GradientDrawable(
      GradientDrawable.Orientation.TL_BR,
      intArrayOf(0xFF172742.toInt(), 0xFF0B1322.toInt())
    )
    markBackground.cornerRadius = dp(27f).toFloat()
    markBackground.setStroke(dp(1f), 0xFF2C4164.toInt())
    mark.background = markBackground
    mark.elevation = dp(8f).toFloat()

    val logo = ImageView(getContext())
    logo.setImageResource(R.drawable.ic_boot_logo)
    logo.setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
    logo.contentDescription = null
    mark.addView(
      logo,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )

    val markParams = LinearLayout.LayoutParams(dp(94f), dp(94f))
    markParams.bottomMargin = dp(24f)
    content.addView(mark, markParams)

    val title = TextView(getContext())
    title.setText("DSHD")
    title.setTextColor(0xFFF2F7FF.toInt())
    title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
    title.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    title.letterSpacing = 0.16f
    title.gravity = Gravity.CENTER
    content.addView(title)

    val subtitle = TextView(getContext())
    subtitle.setText("DEEPSEEK HARNESS DESKTOP")
    subtitle.setTextColor(0xFF8196B7.toInt())
    subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
    subtitle.letterSpacing = 0.23f
    subtitle.gravity = Gravity.CENTER
    val subtitleParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
    subtitleParams.topMargin = dp(7f)
    content.addView(subtitle, subtitleParams)

    statusView.setText("Preparing your workspace")
    statusView.setTextColor(0xFFD3E1F6.toInt())
    statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    statusView.gravity = Gravity.CENTER
    statusView.maxWidth = dp(310f)
    val statusParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
    statusParams.topMargin = dp(34f)
    content.addView(statusView, statusParams)

    val progressParams = LinearLayout.LayoutParams(dp(218f), dp(4f))
    progressParams.topMargin = dp(17f)
    content.addView(progress, progressParams)

    hintView.setText("Starting the local AI engine")
    hintView.setTextColor(0xFF7F94B4.toInt())
    hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    hintView.gravity = Gravity.CENTER
    hintView.setLineSpacing(dp(3f).toFloat(), 1f)
    hintView.maxWidth = dp(310f)
    val hintParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
    hintParams.topMargin = dp(17f)
    content.addView(hintView, hintParams)

    val contentParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    )
    contentParams.gravity = Gravity.CENTER
    addView(content, contentParams)

    val footer = TextView(getContext())
    footer.setText("DSHD  " + BuildConfig.VERSION_NAME + "   ·   LOCAL RUNTIME")
    footer.setTextColor(0xFF4E627F.toInt())
    footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
    footer.letterSpacing = 0.14f
    footer.gravity = Gravity.CENTER
    val footerParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    )
    footerParams.gravity = Gravity.BOTTOM
    footerParams.bottomMargin = dp(27f)
    addView(footer, footerParams)
  }

  fun setMessage(raw: String?) {
    if (dismissed || raw == null) return
    val message = raw.trim()
    if (message.isEmpty()) return
    var percent = -1
    val matcher = PERCENT.matcher(message)
    if (matcher.find()) {
      val group = matcher.group(1)
      if (group != null) {
        try {
          percent = group.toInt()
        } catch (ignored: NumberFormatException) {
          // Stay indeterminate if the message is malformed.
        }
      }
    }
    progress.setProgress(percent)
    statusView.setText(humanize(message))
    hintView.setText(hintFor(message))
    statusView.contentDescription = humanize(message)
  }

  private fun humanize(message: String): String {
    if (message.startsWith("Installing bundled rootfs")) return "Preparing the built-in runtime"
    if (message.startsWith("Updating bundled rootfs")) return "Updating the built-in runtime"
    if (message.startsWith("Downloading rootfs")) return "Downloading the runtime"
    if (message.startsWith("Importing rootfs") || message.startsWith("Unpacking runtime")) {
      return "Unpacking the runtime"
    }
    if (message.startsWith("Finalizing rootfs")) return "Finishing setup"
    if (message.startsWith("Rootfs ready")) return "Runtime ready"
    if (message.startsWith("Starting local dsh Host")) return "Starting the local AI engine"
    if (message.startsWith("Restarting dsh Host")) return "Restarting the local AI engine"
    return message
  }

  private fun hintFor(message: String): String {
    if (message.startsWith("Installing bundled rootfs") ||
      message.startsWith("Updating bundled rootfs") ||
      message.startsWith("Importing rootfs") ||
      message.startsWith("Unpacking runtime") ||
      message.startsWith("Finalizing rootfs")
    ) {
      return "First launch unpacks once. Keep DSHD open (1–3 min)."
    }
    if (message.startsWith("Downloading rootfs")) {
      return "One-time download. Wi-Fi is recommended."
    }
    if (message.startsWith("Rootfs ready")) return "Opening your workspace…"
    if (message.startsWith("Starting local dsh Host") ||
      message.startsWith("Restarting dsh Host")
    ) {
      return "Loading tools and your workspace."
    }
    return "Everything stays on this device."
  }

  fun dismiss() {
    if (dismissed) return
    dismissed = true
    isClickable = false
    isFocusable = false
    progress.stopAnimation()
    animate().alpha(0f).setDuration(300L).withEndAction { visibility = View.GONE }.start()
  }

  fun isDismissed(): Boolean {
    return dismissed
  }

  override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
    super.onSizeChanged(width, height, oldWidth, oldHeight)
    backgroundShader = LinearGradient(
      0f, 0f, 0f, height.toFloat(),
      intArrayOf(0xFF04060D.toInt(), 0xFF0A1120.toInt(), 0xFF05070F.toInt()),
      floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    glowTop = RadialGradient(
      width * 0.82f, height * 0.18f, width * 0.78f,
      intArrayOf(0x334F8CFF, 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
    )
    glowBottom = RadialGradient(
      width * 0.16f, height * 0.80f, width * 0.86f,
      intArrayOf(0x226B4DFF, 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
    )
    glowCenter = RadialGradient(
      width * 0.50f, height * 0.38f, width * 0.52f,
      intArrayOf(0x1A2DD4BF, 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
    )
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val width = getWidth()
    val height = getHeight()
    if (width == 0 || height == 0) return

    backgroundPaint.shader = backgroundShader
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

    glowPaint.shader = glowTop
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
    glowPaint.shader = glowBottom
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
    glowPaint.shader = glowCenter
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

    particlePaint.setColor(0xFFD7E8FF.toInt())
    for (i in 0 until 22) {
      val x = ((i * 73 + 11) % 100) / 100f * width
      val y = ((i * 37 + 7) % 100) / 100f * height
      // Static stars: animating a full-screen radial background was costly
      // during the exact seconds when the host and WebView need the CPU.
      particlePaint.setAlpha(if (i % 4 == 0) 42 else 22)
      canvas.drawCircle(x, y, dp(0.7f + (i % 3) * 0.45f).toFloat(), particlePaint)
    }

    // A restrained horizon line gives the otherwise empty surface depth.
    particlePaint.setColor(0xFF8EB8FF.toInt())
    particlePaint.setAlpha(13)
    particlePaint.setStrokeWidth(dp(1f).toFloat())
    val horizon = height * 0.62f
    canvas.drawLine(width * 0.14f, horizon, width * 0.86f, horizon, particlePaint)
    particlePaint.setAlpha(255)
  }

  /** Thin determinate/indeterminate bar; avoids the platform spinner look. */
  private class BootProgress(context: Context) : View(context) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var animator: ValueAnimator? = null
    private var fillShader: Shader? = null
    private var phase = 0f
    private var percent = -1

    init {
      trackPaint.setColor(0xFF1A2740.toInt())
      fillPaint.setColor(0xFF4F8CFF.toInt())
    }

    fun setProgress(value: Int) {
      val next = if (value < 0) -1 else Math.max(0, Math.min(100, value))
      if (next == percent) return
      percent = next
      if (percent >= 0) {
        animator?.cancel()
      } else {
        startAnimationIfNeeded()
      }
      invalidate()
    }

    fun stopAnimation() {
      animator?.cancel()
    }

    private fun startAnimationIfNeeded() {
      if (animator == null) {
        val created = ValueAnimator.ofFloat(0f, 1f)
        created.duration = 1600L
        created.repeatCount = ValueAnimator.INFINITE
        created.interpolator = LinearInterpolator()
        created.addUpdateListener { animation ->
          phase = animation.animatedValue as Float
          if (percent < 0) invalidate()
        }
        animator = created
      }
      animator?.let { if (!it.isStarted) it.start() }
    }

    override fun onAttachedToWindow() {
      super.onAttachedToWindow()
      if (percent < 0) startAnimationIfNeeded()
    }

    override fun onDetachedFromWindow() {
      animator?.cancel()
      super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
      super.onSizeChanged(width, height, oldWidth, oldHeight)
      if (width > 0) {
        fillShader = LinearGradient(
          0f, 0f, width.toFloat(), 0f,
          intArrayOf(0xFF3C7DFF.toInt(), 0xFF8D6BFF.toInt()),
          null, Shader.TileMode.CLAMP
        )
      }
    }

    override fun onDraw(canvas: Canvas) {
      val width = getWidth().toFloat()
      val height = getHeight().toFloat()
      val radius = height / 2f
      rect.set(0f, 0f, width, height)
      canvas.drawRoundRect(rect, radius, radius, trackPaint)

      if (fillShader == null && width > 0) {
        fillShader = LinearGradient(
          0f, 0f, width, 0f,
          intArrayOf(0xFF3C7DFF.toInt(), 0xFF8D6BFF.toInt()),
          null, Shader.TileMode.CLAMP
        )
      }
      fillPaint.shader = fillShader
      if (percent >= 0) {
        val filled = Math.max(height, width * percent / 100f)
        rect.set(0f, 0f, filled, height)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
      } else {
        val segment = width * 0.30f
        val left = (width + segment) * phase - segment
        val save = canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        rect.set(left, 0f, left + segment, height)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.restoreToCount(save)
      }
    }
  }

  companion object {
    private val BG = Color.rgb(5, 7, 15)
    private val PERCENT = Pattern.compile("(\\d{1,3})\\s*%")
  }
}
