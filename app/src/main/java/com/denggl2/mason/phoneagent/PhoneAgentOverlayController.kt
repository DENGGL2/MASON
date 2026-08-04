package com.denggl2.mason.phoneagent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.denggl2.mason.tool.NotificationTool
import com.denggl2.mason.tool.canUsePromotedNotificationIsland
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.pow

@Singleton
class PhoneAgentOverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationTool: NotificationTool,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayView: View? = null
    private var edgeView: View? = null
    private var hideRunnable: Runnable? = null
    private var notificationJob: kotlinx.coroutines.Job? = null

    fun show(action: String, focusPoint: PhoneAgentPoint? = null, onStop: () -> Unit) {
        handler.post {
            hideNow()
            val canDrawOverlay = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Settings.canDrawOverlays(context)

            if (canDrawOverlay) showEdgePulse(focusPoint)

            if (canUsePromotedNotificationIsland(context)) {
                notificationJob = notificationScope.launch {
                    val result = runCatching {
                        notificationTool.execute(
                            mapOf(
                                "title" to "Mason 屏幕助手",
                                "text" to "正在操控屏幕 · $action",
                                NotificationTool.EXTRA_ALLOW_FOREGROUND to "true",
                                NotificationTool.EXTRA_LIVE_UPDATE to "true",
                                NotificationTool.EXTRA_LIVE_UPDATE_PROGRESS to "5",
                                NotificationTool.EXTRA_LIVE_UPDATE_SHORT_TEXT to "操控中",
                                NotificationTool.EXTRA_NOTIFICATION_ID to
                                    NotificationTool.PHONE_AGENT_NOTIFICATION_ID.toString(),
                                NotificationTool.EXTRA_PHONE_AGENT_STATUS to "true",
                                NotificationTool.EXTRA_PREVIEW_MODE to NotificationTool.PREVIEW_MODE_ISLAND,
                            ),
                        )
                    }.getOrNull()
                    if (result?.success != true || result.data["live_update_requested"] != "true") {
                        handler.post {
                            if (canDrawOverlay && overlayView == null) showFloating(action, onStop)
                        }
                    }
                }
            } else if (canDrawOverlay) {
                showFloating(action, onStop)
            }

            hideRunnable = Runnable(::hideNow).also {
                handler.postDelayed(it, VISIBLE_DURATION_MS)
            }
        }
    }

    fun hide() {
        handler.post(::hideNow)
    }

    fun pulse(point: PhoneAgentPoint) {
        handler.post {
            (edgeView as? EdgePulseView)?.pulse(point)
        }
    }

    private fun showEdgePulse(focusPoint: PhoneAgentPoint?) {
        val view = EdgePulseView(context, focusPoint)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        runCatching { windowManager.addView(view, params) }
            .onSuccess { edgeView = view }
    }

    private fun showFloating(action: String, onStop: () -> Unit) {
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (7 * density).toInt(),
                (1 * density).toInt(),
                (4 * density).toInt(),
                (1 * density).toInt(),
            )
            background = GradientDrawable().apply {
                setColor(Color.argb(238, 28, 28, 30))
                cornerRadius = 5 * density
            }
        }
        val label = TextView(context).apply {
            text = "Mason 屏幕助手 · $action"
            setTextColor(Color.WHITE)
            textSize = 14f
            includeFontPadding = false
            maxLines = 1
        }
        val stop = TextView(context).apply {
            text = "\u00D7"
            textSize = 14f
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "停止屏幕助手"
            setOnClickListener {
                onStop()
                hideNow()
            }
        }
        container.addView(
            label,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        val stopParams = LinearLayout.LayoutParams(
            (18 * density).toInt(),
            (18 * density).toInt(),
        ).apply {
            marginStart = (4 * density).toInt()
        }
        container.addView(
            stop,
            stopParams,
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * density).toInt()
            y = (72 * density).toInt()
        }
        runCatching { windowManager.addView(container, params) }
            .onSuccess { overlayView = container }
    }

    private fun hideNow() {
        hideRunnable?.let(handler::removeCallbacks)
        hideRunnable = null
        notificationJob?.cancel()
        notificationJob = null
        notificationManager.cancel(NotificationTool.PHONE_AGENT_NOTIFICATION_ID)
        overlayView?.let { view -> runCatching { windowManager.removeView(view) } }
        overlayView = null
        edgeView?.let { view -> runCatching { windowManager.removeView(view) } }
        edgeView = null
    }

    private class EdgePulseView(
        context: Context,
        focusPoint: PhoneAgentPoint?,
    ) : View(context) {
        private val density = resources.displayMetrics.density
        private val edgeGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val edgeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val clickHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val clickRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        private val clickInnerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5 * density
            maskFilter = BlurMaskFilter(5 * density, BlurMaskFilter.Blur.NORMAL)
        }
        private val clickCoreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(4 * density, BlurMaskFilter.Blur.NORMAL)
        }
        private val clickCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private var topEdgeGradient: LinearGradient? = null
        private var bottomEdgeGradient: LinearGradient? = null
        private var leftEdgeGradient: LinearGradient? = null
        private var rightEdgeGradient: LinearGradient? = null
        private var edgeFadePx = 0f
        private var progress = 0f
        private var clickProgress = 0f
        private var clickPoint = focusPoint
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3_200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
        }
        private val clickAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 540L
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                clickProgress = it.animatedValue as Float
                invalidate()
            }
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        fun pulse(point: PhoneAgentPoint) {
            clickPoint = point
            clickProgress = 0f
            clickAnimator.cancel()
            clickAnimator.start()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animator.start()
            if (clickPoint != null) clickAnimator.start()
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            clickAnimator.cancel()
            super.onDetachedFromWindow()
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            edgeFadePx = 34 * density
            val edgeColors = intArrayOf(
                Color.argb(224, 62, 168, 255),
                Color.argb(128, 62, 168, 255),
                Color.argb(28, 62, 168, 255),
                Color.TRANSPARENT,
            )
            val edgePositions = floatArrayOf(0f, 0.34f, 0.72f, 1f)
            topEdgeGradient = LinearGradient(
                0f,
                0f,
                0f,
                edgeFadePx,
                edgeColors,
                edgePositions,
                Shader.TileMode.CLAMP,
            )
            bottomEdgeGradient = LinearGradient(
                0f,
                height.toFloat(),
                0f,
                height - edgeFadePx,
                edgeColors,
                edgePositions,
                Shader.TileMode.CLAMP,
            )
            leftEdgeGradient = LinearGradient(
                0f,
                0f,
                edgeFadePx,
                0f,
                edgeColors,
                edgePositions,
                Shader.TileMode.CLAMP,
            )
            rightEdgeGradient = LinearGradient(
                width.toFloat(),
                0f,
                width - edgeFadePx,
                0f,
                edgeColors,
                edgePositions,
                Shader.TileMode.CLAMP,
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val topGradient = topEdgeGradient ?: return
            val bottomGradient = bottomEdgeGradient ?: return
            val leftGradient = leftEdgeGradient ?: return
            val rightGradient = rightEdgeGradient ?: return
            val breathing = 0.92f + 0.08f * kotlin.math.sin(progress * Math.PI * 2.0).toFloat()
            val edgeAlpha = (238 * breathing).toInt().coerceIn(0, 255)
            edgeGradientPaint.alpha = edgeAlpha
            edgeGradientPaint.shader = topGradient
            canvas.drawRect(0f, 0f, width.toFloat(), edgeFadePx, edgeGradientPaint)
            edgeGradientPaint.shader = bottomGradient
            canvas.drawRect(0f, height - edgeFadePx, width.toFloat(), height.toFloat(), edgeGradientPaint)
            edgeGradientPaint.shader = leftGradient
            canvas.drawRect(0f, 0f, edgeFadePx, height.toFloat(), edgeGradientPaint)
            edgeGradientPaint.shader = rightGradient
            canvas.drawRect(width - edgeFadePx, 0f, width.toFloat(), height.toFloat(), edgeGradientPaint)

            edgeLinePaint.color = Color.argb(
                (196 * breathing).toInt().coerceIn(0, 255),
                49,
                158,
                255,
            )
            val edgeLine = 2 * density
            canvas.drawRect(0f, 0f, width.toFloat(), edgeLine, edgeLinePaint)
            canvas.drawRect(0f, height - edgeLine, width.toFloat(), height.toFloat(), edgeLinePaint)
            canvas.drawRect(0f, 0f, edgeLine, height.toFloat(), edgeLinePaint)
            canvas.drawRect(width - edgeLine, 0f, width.toFloat(), height.toFloat(), edgeLinePaint)

            val point = clickPoint ?: return
            val pulse = clickProgress.coerceIn(0f, 1f)
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            val ringProgress = ((pulse - 0.04f) / 0.96f).coerceIn(0f, 1f)
            val haloProgress = ((pulse - 0.12f) / 0.88f).coerceIn(0f, 1f)
            val coreAlpha = ((1f - pulse / 0.58f).coerceIn(0f, 1f) * 220f).toInt()
            val ringAlpha = ((1f - ringProgress).coerceIn(0f, 1f).pow(1.35f) * 255f)
                .toInt()
                .coerceIn(0, 255)
            val haloAlpha = ((1f - haloProgress) * 92f).toInt().coerceIn(0, 92)

            val haloRadius = lerp(18f, 96f, haloProgress) * density
            clickHaloPaint.shader = RadialGradient(
                x,
                y,
                haloRadius,
                intArrayOf(
                    Color.argb(haloAlpha, 108, 211, 255),
                    Color.argb((haloAlpha * 0.42f).toInt(), 74, 174, 255),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, haloRadius, clickHaloPaint)

            clickInnerRingPaint.color = Color.argb((ringAlpha * 0.34f).toInt(), 80, 190, 255)
            clickRingPaint.color = Color.argb(ringAlpha, 99, 207, 255)
            clickCoreGlowPaint.color = Color.argb((coreAlpha * 0.62f).toInt(), 102, 211, 255)
            clickCorePaint.color = Color.argb(coreAlpha, 224, 249, 255)
            val ringRadius = lerp(8f, 72f, ringProgress) * density
            canvas.drawCircle(x, y, ringRadius, clickInnerRingPaint)
            canvas.drawCircle(x, y, ringRadius, clickRingPaint)
            canvas.drawCircle(x, y, (4f + 1f * (1f - pulse)) * density, clickCoreGlowPaint)
            canvas.drawCircle(x, y, (2.5f + 0.9f * (1f - pulse)) * density, clickCorePaint)
        }

        private fun lerp(start: Float, end: Float, progress: Float): Float =
            start + (end - start) * progress
    }

    private companion object {
        const val VISIBLE_DURATION_MS = 8_000L
    }
}
