package dev.island.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

class IslandItem(
    val icon: Drawable?,
    val title: String,
    val text: String,
    val intent: PendingIntent?
)

object IslandOverlay {

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<IslandItem>()
    private var showing = false
    private val hideRunnable = Runnable { hide() }

    private class Cur(
        val root: FrameLayout,
        val island: FrameLayout,
        val content: View,
        val bg: GradientDrawable,
        val cw: Int, val ch: Int, val ew: Int, val eh: Int
    )

    private var cur: Cur? = null
    private var anim: ValueAnimator? = null
    private var frac = 0f

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    private fun dp(v: Int): Int =
        (v * app.resources.displayMetrics.density).toInt()

    private fun pref(key: String, def: Int): Int =
        app.getSharedPreferences("island", Context.MODE_PRIVATE).getInt(key, def)

    fun canShow(): Boolean = Settings.canDrawOverlays(app)

    fun demo() {
        post(
            IslandItem(
                app.packageManager.getApplicationIcon(app.packageName),
                "Island",
                "Тест: так будет выглядеть уведомление",
                null
            )
        )
    }

    fun post(item: IslandItem) {
        main.post {
            if (!canShow()) return@post
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) return@post
            if (queue.size >= 5) queue.removeFirst()
            queue.addLast(item)
            if (!showing) next()
        }
    }

    private fun next() {
        val item = queue.removeFirstOrNull() ?: return
        showing = true

        val dm = app.resources.displayMetrics
        val offY = dp(pref("y", 11))
        val cw = dp(pref("w", 90))
        val ch = dp(pref("h", 30))
        val ew = min(dm.widthPixels - dp(24), dp(380))
        val eh = dp(76)

        val bg = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = ch / 2f
        }

        // содержимое карточки (фиксированного размера, обрезается рамкой острова)
        val icon = ImageView(app).apply {
            setImageDrawable(item.icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, dp(10).toFloat())
                }
            }
            clipToOutline = true
        }
        val title = TextView(app).apply {
            text = item.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val body = TextView(app).apply {
            text = item.text
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val texts = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(body)
        }
        val content = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(18), 0)
            addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(12) })
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val island = FrameLayout(app).apply {
            background = bg
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            addView(content, FrameLayout.LayoutParams(ew, eh, Gravity.CENTER))
        }

        val root = FrameLayout(app)
        root.addView(
            island,
            FrameLayout.LayoutParams(cw, ch, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = offY
            }
        )

        val c = Cur(root, island, content, bg, cw, ch, ew, eh)
        cur = c
        applyFrac(c, 0f)

        // жесты: тап — открыть, свайп вверх — убрать
        var downY = 0f
        island.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY
                    main.removeCallbacks(hideRunnable)
                }
                MotionEvent.ACTION_UP -> {
                    val dy = e.rawY - downY
                    if (dy < -dp(18)) {
                        hide()
                    } else if (abs(dy) < dp(10)) {
                        open(item.intent)
                        hide()
                    } else {
                        main.postDelayed(hideRunnable, 2000)
                    }
                }
                MotionEvent.ACTION_CANCEL -> main.postDelayed(hideRunnable, 2000)
            }
            true
        }

        val lp = WindowManager.LayoutParams(
            ew + dp(16),
            offY + eh + dp(12),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            wm().addView(root, lp)
        } catch (e: Exception) {
            cur = null
            showing = false
            return
        }

        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { applyFrac(c, it.animatedValue as Float) }
            start()
        }
        main.postDelayed(hideRunnable, 4500)
    }

    private fun applyFrac(c: Cur, v: Float) {
        frac = v
        val lp = c.island.layoutParams
        lp.width = (c.cw + (c.ew - c.cw) * v).toInt().coerceAtLeast(c.cw)
        lp.height = (c.ch + (c.eh - c.ch) * v).toInt().coerceAtLeast(c.ch)
        c.island.layoutParams = lp
        c.bg.cornerRadius = min(lp.height / 2f, dp(34).toFloat())
        c.content.alpha = ((v - 0.35f) / 0.5f).coerceIn(0f, 1f)
    }

    private fun hide() {
        main.removeCallbacks(hideRunnable)
        val c = cur ?: return
        cur = null
        anim?.cancel()
        anim = ValueAnimator.ofFloat(frac, 0f).apply {
            duration = 260
            interpolator = AccelerateInterpolator()
            addUpdateListener { applyFrac(c, it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    try {
                        wm().removeView(c.root)
                    } catch (_: Exception) {
                    }
                    showing = false
                    next()
                }
            })
            start()
        }
    }

    private fun open(pi: PendingIntent?) {
        if (pi == null) return
        try {
            val opts: Bundle? = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }.toBundle()
            } else null
            pi.send(app, 0, null, null, null, null, opts)
        } catch (_: Exception) {
        }
    }

    private fun wm(): WindowManager =
        app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
}
