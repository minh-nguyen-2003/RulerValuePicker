package vn.vkey.minh_nguyen.rulervaluepicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A scroll-based ruler/value picker that supports 4 orientations:
 *  - horizontal_bottom: ruler on bottom, indicator arrow on top  (▼)
 *  - horizontal_top:    ruler on top, indicator arrow on bottom  (▲)
 *  - vertical_left:     ruler on left, indicator arrow on right  (◀ pointing left, indicator right)
 *  - vertical_right:    ruler on right, indicator arrow on left  (▶ pointing right, indicator left)
 */
class RulerValuePicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val ORIENTATION_HORIZONTAL_BOTTOM = 0
        const val ORIENTATION_HORIZONTAL_TOP = 1
        const val ORIENTATION_VERTICAL_LEFT = 2
        const val ORIENTATION_VERTICAL_RIGHT = 3

        private const val DEFAULT_MIN = 0f
        private const val DEFAULT_MAX = 100f
        private const val DEFAULT_INITIAL = 50f
        private const val DEFAULT_STEP = 1f

        private const val DEFAULT_LINE_COLOR = 0xFFBDBDBD.toInt()      // light grey
        private const val DEFAULT_TEXT_COLOR = 0xFF616161.toInt()       // dark grey
        private const val DEFAULT_TEXT_SIZE_SP = 14f

        private const val TICK_SPACING_DP = 12f
        private const val MINOR_TICK_LENGTH_DP = 16f
        private const val MAJOR_TICK_LENGTH_DP = 30f
        private const val HALF_TICK_LENGTH_DP = 22f
        private const val INDICATOR_SIZE_DP = 10f

        private const val TICK_WIDTH_DP = 1f
        private const val MAJOR_TICK_WIDTH_DP = 1.5f
        private const val MAJOR_TICK_INTERVAL = 10
        private const val HALF_TICK_INTERVAL = 5
    }

    // ---- Attributes ----
    private var minValue = DEFAULT_MIN
    private var maxValue = DEFAULT_MAX
    private var stepValue = DEFAULT_STEP
    private var orientation = ORIENTATION_HORIZONTAL_BOTTOM
    private var showMajorTicks = true
    private var showHalfTicks = true
    private var centerTicks = false


    private var lineColor = DEFAULT_LINE_COLOR
    private var textColor = DEFAULT_TEXT_COLOR
    private var textSizePx: Float

    // ---- Derived ----
    private var tickSpacingPx: Float
    private var minorTickLenPx: Float
    private var majorTickLenPx: Float
    private var halfTickLenPx: Float
    private var indicatorSizePx: Float

    private var tickWidthPx: Float
    private var majorTickWidthPx: Float

    private val totalSteps: Int get() = ((maxValue - minValue) / stepValue).roundToInt()
    private val totalTrackLength: Float get() = totalSteps * tickSpacingPx

    // ---- Scroll state ----
    /** Current scroll offset in pixels (0 = minValue is centred) */
    private var scrollOffset = 0f
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var lastTouchPos = 0f

    // ---- Paints ----
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- Listener ----
    var onValueChangedListener: OnValueChangedListener? = null
    private var lastReportedValue = Float.NaN

    interface OnValueChangedListener {
        fun onValueChanged(picker: RulerValuePicker, value: Float)
    }

    init {
        val density = resources.displayMetrics.density

        textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE_SP, resources.displayMetrics)
        tickSpacingPx = TICK_SPACING_DP * density
        minorTickLenPx = MINOR_TICK_LENGTH_DP * density
        majorTickLenPx = MAJOR_TICK_LENGTH_DP * density
        halfTickLenPx = HALF_TICK_LENGTH_DP * density
        indicatorSizePx = INDICATOR_SIZE_DP * density

        tickWidthPx = TICK_WIDTH_DP * density
        majorTickWidthPx = MAJOR_TICK_WIDTH_DP * density

        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.ScrollValuePicker, defStyleAttr, 0)
            minValue = ta.getFloat(R.styleable.ScrollValuePicker_svp_minValue, DEFAULT_MIN)
            maxValue = ta.getFloat(R.styleable.ScrollValuePicker_svp_maxValue, DEFAULT_MAX)
            val initialValue = ta.getFloat(R.styleable.ScrollValuePicker_svp_initialValue, DEFAULT_INITIAL)
            stepValue = ta.getFloat(R.styleable.ScrollValuePicker_svp_stepValue, DEFAULT_STEP)
            orientation = ta.getInt(R.styleable.ScrollValuePicker_svp_orientation, ORIENTATION_HORIZONTAL_BOTTOM)

            lineColor = ta.getColor(R.styleable.ScrollValuePicker_svp_lineColor, DEFAULT_LINE_COLOR)
            textColor = ta.getColor(R.styleable.ScrollValuePicker_svp_textColor, DEFAULT_TEXT_COLOR)
            textSizePx = ta.getDimension(R.styleable.ScrollValuePicker_svp_textSize, textSizePx)
            showMajorTicks = ta.getBoolean(R.styleable.ScrollValuePicker_svp_showMajorTicks, true)
            showHalfTicks = ta.getBoolean(R.styleable.ScrollValuePicker_svp_showHalfTicks, true)
            centerTicks = ta.getBoolean(R.styleable.ScrollValuePicker_svp_centerTicks, false)
            ta.recycle()

            // Set initial scroll offset
            scrollOffset = ((initialValue - minValue) / stepValue) * tickSpacingPx
        }

        setupPaints()
    }

    private fun setupPaints() {
        tickPaint.apply {
            color = lineColor
            strokeWidth = tickWidthPx
            style = Paint.Style.STROKE
        }
        majorTickPaint.apply {
            color = lineColor
            strokeWidth = majorTickWidthPx
            style = Paint.Style.STROKE
        }
        textPaint.apply {
            color = textColor
            textSize = textSizePx
            textAlign = Paint.Align.CENTER
        }

    }

    // ---- Value helpers ----
    private val currentValue: Float
        get() {
            val steps = (scrollOffset / tickSpacingPx).roundToInt()
            return (minValue + steps * stepValue).coerceIn(minValue, maxValue)
        }

    private fun reportValue() {
        val value = currentValue
        if (value != lastReportedValue) {
            lastReportedValue = value
            onValueChangedListener?.onValueChanged(this, value)
        }
    }

    fun setValue(value: Float) {
        val clamped = value.coerceIn(minValue, maxValue)
        scrollOffset = ((clamped - minValue) / stepValue) * tickSpacingPx
        reportValue()
        invalidate()
    }

    fun getValue(): Float = currentValue

    // ---- Measure ----
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val maxTickLen = when {
            showMajorTicks -> majorTickLenPx
            showHalfTicks -> halfTickLenPx
            else -> minorTickLenPx
        }
        val textSpace = if (showMajorTicks) textSizePx + 4f * density else 0f

        when {
            isHorizontal() -> {
                val desiredHeight = (maxTickLen + textSpace + indicatorSizePx * 2 + 24 * density).toInt()
                val w = resolveSize(
                    (300 * density).toInt(), widthMeasureSpec
                )
                val h = resolveSize(desiredHeight, heightMeasureSpec)
                setMeasuredDimension(w, h)
            }
            else -> {
                val desiredWidth = (maxTickLen + (if (showMajorTicks) textSizePx * 3 else 0f) + indicatorSizePx * 2 + 24 * density).toInt()
                val w = resolveSize(desiredWidth, widthMeasureSpec)
                val h = resolveSize(
                    (300 * density).toInt(), heightMeasureSpec
                )
                setMeasuredDimension(w, h)
            }
        }
    }

    private fun isHorizontal() =
        orientation == ORIENTATION_HORIZONTAL_BOTTOM || orientation == ORIENTATION_HORIZONTAL_TOP

    // ---- Draw ----
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (orientation) {
            ORIENTATION_HORIZONTAL_BOTTOM -> drawHorizontalBottom(canvas)
            ORIENTATION_HORIZONTAL_TOP -> drawHorizontalTop(canvas)
            ORIENTATION_VERTICAL_LEFT -> drawVerticalLeft(canvas)
            ORIENTATION_VERTICAL_RIGHT -> drawVerticalRight(canvas)
        }
    }

    // ============================
    //  HORIZONTAL BOTTOM
    //  Indicator ▼ on top, ruler ticks pointing downward from a baseline
    // ============================
    private fun drawHorizontalBottom(canvas: Canvas) {
        val centerX = width / 2f
        val rulerTop = indicatorSizePx * 2 + 4f * resources.displayMetrics.density


        // Draw ticks
        val halfWidth = width / 2f
        val startStep = max(0, ((scrollOffset - halfWidth) / tickSpacingPx).toInt() - 1)
        val endStep = min(totalSteps, ((scrollOffset + halfWidth) / tickSpacingPx).toInt() + 1)

        for (i in startStep..endStep) {
            val x = centerX + i * tickSpacingPx - scrollOffset
            val tickLen: Float
            val paint: Paint

            when {
                i % MAJOR_TICK_INTERVAL == 0 && showMajorTicks -> {
                    tickLen = majorTickLenPx
                    paint = majorTickPaint
                }
                i % HALF_TICK_INTERVAL == 0 && showHalfTicks -> {
                    tickLen = halfTickLenPx
                    paint = tickPaint
                }
                else -> {
                    tickLen = minorTickLenPx
                    paint = tickPaint
                }
            }

            val maxTickLen = when {
                showMajorTicks -> majorTickLenPx
                showHalfTicks -> halfTickLenPx
                else -> minorTickLenPx
            }
            val centerOffset = if (centerTicks) (maxTickLen - tickLen) / 2f else 0f

            canvas.drawLine(x, rulerTop + centerOffset, x, rulerTop + centerOffset + tickLen, paint)

            if (i % MAJOR_TICK_INTERVAL == 0 && showMajorTicks) {
                val labelValue = minValue + i * stepValue
                val label = formatLabel(labelValue)
                canvas.drawText(label, x, rulerTop + majorTickLenPx + textSizePx + 4f * resources.displayMetrics.density, textPaint)
            }
        }
    }

    // ============================
    //  HORIZONTAL TOP
    //  Indicator ▲ on bottom, ruler ticks pointing upward from a baseline
    // ============================
    private fun drawHorizontalTop(canvas: Canvas) {
        val centerX = width / 2f
        val density = resources.displayMetrics.density
        val textAreaHeight = textSizePx + 4f * density

        // Text area at top
        // Ruler baseline: after text area
        val rulerBottom = textAreaHeight + majorTickLenPx + 4f * density


        val halfWidth = width / 2f
        val startStep = max(0, ((scrollOffset - halfWidth) / tickSpacingPx).toInt() - 1)
        val endStep = min(totalSteps, ((scrollOffset + halfWidth) / tickSpacingPx).toInt() + 1)

        for (i in startStep..endStep) {
            val x = centerX + i * tickSpacingPx - scrollOffset
            val tickLen: Float
            val paint: Paint

            when {
                i % MAJOR_TICK_INTERVAL == 0 && showMajorTicks -> { tickLen = majorTickLenPx; paint = majorTickPaint }
                i % HALF_TICK_INTERVAL == 0 && showHalfTicks -> { tickLen = halfTickLenPx; paint = tickPaint }
                else -> { tickLen = minorTickLenPx; paint = tickPaint }
            }

            val maxTickLen = when {
                showMajorTicks -> majorTickLenPx
                showHalfTicks -> halfTickLenPx
                else -> minorTickLenPx
            }
            val centerOffset = if (centerTicks) (maxTickLen - tickLen) / 2f else 0f

            canvas.drawLine(x, rulerBottom - centerOffset, x, rulerBottom - centerOffset - tickLen, paint)

            if (i % MAJOR_TICK_INTERVAL == 0 && showMajorTicks) {
                val labelValue = minValue + i * stepValue
                canvas.drawText(formatLabel(labelValue), x, textAreaHeight - 2f * density, textPaint)
            }
        }

    }

    // ============================
    //  VERTICAL LEFT
    //  Ruler ticks on the left, indicator ▶ on right
    // ============================
    private fun drawVerticalLeft(canvas: Canvas) {
        val centerY = height / 2f
        val density = resources.displayMetrics.density
        val rulerRight = majorTickLenPx + 4f * density
        val textAreaLeft = rulerRight + 4f * density

        val halfHeight = height / 2f
        val startStep = max(0, ((scrollOffset - halfHeight) / tickSpacingPx).toInt() - 1)
        val endStep = min(totalSteps, ((scrollOffset + halfHeight) / tickSpacingPx).toInt() + 1)

        for (i in startStep..endStep) {
            val y = centerY + i * tickSpacingPx - scrollOffset
            val tickLen: Float
            val paint: Paint

            when {
                i % MAJOR_TICK_INTERVAL == 0 && showMajorTicks -> { tickLen = majorTickLenPx; paint = majorTickPaint }
                i % HALF_TICK_INTERVAL == 0 && showHalfTicks -> { tickLen = halfTickLenPx; paint = tickPaint }
                else -> { tickLen = minorTickLenPx; paint = tickPaint }
            }

            val maxTickLen = when {
                showMajorTicks -> majorTickLenPx
                showHalfTicks -> halfTickLenPx
                else -> minorTickLenPx
            }
            val centerOffset = if (centerTicks) (maxTickLen - tickLen) / 2f else 0f

            canvas.drawLine(rulerRight - centerOffset, y, rulerRight - centerOffset - tickLen, y, paint)

            if (i % MAJOR_TICK_INTERVAL == 0 && showMajorTicks) {
                val labelValue = minValue + i * stepValue
                canvas.drawText(formatLabel(labelValue), textAreaLeft + textSizePx, y + textSizePx * 0.35f, textPaint.apply { textAlign = Paint.Align.CENTER })
            }
        }

    }

    // ============================
    //  VERTICAL RIGHT
    //  Ruler ticks on the right, indicator ◀ on left
    // ============================
    private fun drawVerticalRight(canvas: Canvas) {
        val centerY = height / 2f
        val density = resources.displayMetrics.density
        val rulerLeft = width - majorTickLenPx - 4f * density
        val textAreaRight = rulerLeft - 4f * density

        val halfHeight = height / 2f
        val startStep = max(0, ((scrollOffset - halfHeight) / tickSpacingPx).toInt() - 1)
        val endStep = min(totalSteps, ((scrollOffset + halfHeight) / tickSpacingPx).toInt() + 1)

        for (i in startStep..endStep) {
            val y = centerY + i * tickSpacingPx - scrollOffset
            val tickLen: Float
            val paint: Paint

            when {
                i % MAJOR_TICK_INTERVAL == 0 && showMajorTicks -> { tickLen = majorTickLenPx; paint = majorTickPaint }
                i % HALF_TICK_INTERVAL == 0 && showHalfTicks -> { tickLen = halfTickLenPx; paint = tickPaint }
                else -> { tickLen = minorTickLenPx; paint = tickPaint }
            }

            val maxTickLen = when {
                showMajorTicks -> majorTickLenPx
                showHalfTicks -> halfTickLenPx
                else -> minorTickLenPx
            }
            val centerOffset = if (centerTicks) (maxTickLen - tickLen) / 2f else 0f

            canvas.drawLine(rulerLeft + centerOffset, y, rulerLeft + centerOffset + tickLen, y, paint)

            if (i % MAJOR_TICK_INTERVAL == 0 && showMajorTicks) {
                val labelValue = minValue + i * stepValue
                canvas.drawText(formatLabel(labelValue), textAreaRight - textSizePx * 0.5f, y + textSizePx * 0.35f, textPaint.apply { textAlign = Paint.Align.CENTER })
            }
        }

    }

    // ---- Label formatting ----
    private fun formatLabel(value: Float): String {
        return if (value == value.toLong().toFloat()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
    }

    // ================================
    //  TOUCH & SCROLL
    // ================================
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pos = if (isHorizontal()) event.x else event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                lastTouchPos = pos

                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val delta = lastTouchPos - pos
                scrollOffset = clampOffset(scrollOffset + delta)
                lastTouchPos = pos
                reportValue()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.let { vt ->
                    vt.addMovement(event)
                    vt.computeCurrentVelocity(1000, 8000f)
                    val velocity = if (isHorizontal()) -vt.xVelocity else -vt.yVelocity
                    fling(velocity)
                    vt.recycle()
                }
                velocityTracker = null
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun clampOffset(offset: Float): Float {
        return offset.coerceIn(0f, totalTrackLength)
    }

    private fun fling(velocity: Float) {
        scroller.forceFinished(true)
        scroller.fling(
            scrollOffset.toInt(), 0,
            velocity.toInt(), 0,
            0, totalTrackLength.toInt(),
            0, 0
        )
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = clampOffset(scroller.currX.toFloat())
            reportValue()
            if (scroller.isFinished) {
                snapToNearest()
            }
            postInvalidateOnAnimation()
        }
    }

    private fun snapToNearest() {
        val nearestStep = (scrollOffset / tickSpacingPx).roundToInt()
        val targetOffset = (nearestStep * tickSpacingPx).coerceIn(0f, totalTrackLength)
        val dx = (targetOffset - scrollOffset).toInt()
        if (abs(dx) > 0) {
            scroller.startScroll(scrollOffset.toInt(), 0, dx, 0, 150)
            postInvalidateOnAnimation()
        }
        reportValue()
    }
}
