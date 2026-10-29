package com.pocketpad.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.pocketpad.app.input.Buttons
import com.pocketpad.app.input.GamepadState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A custom-drawn, multi-touch gamepad. Every pointer is tracked independently so
 * sticks, buttons, and triggers can be held at the same time.
 */
class GamepadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var skin: Skin = Skin.XBOX
        set(value) {
            field = value
            updateSkinLayout()
            invalidate()
        }

    var onStateChanged: ((GamepadState) -> Unit)? = null
    var onLayoutChanged: ((String) -> Unit)? = null

    var customizeMode: Boolean = false
        set(value) {
            field = value
            pointers.clear()
            state.reset()
            selectedControl = null
            editorPointerId = MotionEvent.INVALID_POINTER_ID
            invalidate()
        }

    private val state = GamepadState()
    private val pointers = SparseArray<Target>()

    private var widthPx = 0f
    private var heightPx = 0f
    private var unit = 0f

    private val leftStick = PointF()
    private val rightStick = PointF()
    private val dpad = PointF()
    private val face = PointF()
    private val baseLeftStick = PointF()
    private val baseRightStick = PointF()
    private val baseDpad = PointF()
    private val baseFace = PointF()
    private val baseGuide = PointF()
    private val baseBack = PointF()
    private val baseStart = PointF()
    private val transforms = ControlId.values().associateWith { Transform() }
    private var selectedControl: ControlId? = null
    private var editorPointerId = MotionEvent.INVALID_POINTER_ID
    private var editorResizing = false
    private var editorLastX = 0f
    private var editorLastY = 0f
    private var stickRadius = 0f
    private var dpadHalf = 0f
    private var faceOffset = 0f
    private var faceRadius = 0f

    private val ltRect = RectF()
    private val lbRect = RectF()
    private val rtRect = RectF()
    private val rbRect = RectF()

    private val guide = PointF()
    private val back = PointF()
    private val start = PointF()
    private var guideRadius = 0f
    private var smallRadius = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        widthPx = w.toFloat()
        heightPx = h.toFloat()
        unit = min(widthPx, heightPx)

        stickRadius = unit * 0.12f
        dpadHalf = unit * 0.09f
        faceOffset = unit * 0.095f
        faceRadius = unit * 0.058f
        guideRadius = unit * 0.045f
        smallRadius = unit * 0.032f

        updateSkinLayout()

        baseGuide.set(widthPx * 0.50f, heightPx * 0.11f)
        baseBack.set(widthPx * 0.41f, heightPx * 0.13f)
        baseStart.set(widthPx * 0.59f, heightPx * 0.13f)

        val margin = widthPx * 0.03f
        val triggerW = unit * 0.16f
        val triggerH = unit * 0.11f
        val top = heightPx * 0.04f
        ltRect.set(margin, top, margin + triggerW, top + triggerH)
        rtRect.set(widthPx - margin - triggerW, top, widthPx - margin, top + triggerH)

        val shoulderW = unit * 0.11f
        val shoulderH = unit * 0.075f
        val gap = unit * 0.02f
        lbRect.set(
            margin + triggerW + gap,
            top,
            margin + triggerW + gap + shoulderW,
            top + shoulderH
        )
        rbRect.set(
            widthPx - margin - triggerW - gap - shoulderW,
            top,
            widthPx - margin - triggerW - gap,
            top + shoulderH
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unit <= 0f) return

        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, heightPx,
            intArrayOf(skin.bodyTop, skin.bodyBottom),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, widthPx, heightPx, backgroundPaint)
        backgroundPaint.shader = null

        fillPaint.color = skin.surface
        fillPaint.alpha = 125
        canvas.drawRoundRect(
            unit * 0.025f,
            unit * 0.025f,
            widthPx - unit * 0.025f,
            heightPx - unit * 0.025f,
            unit * 0.045f,
            unit * 0.045f,
            fillPaint
        )
        fillPaint.alpha = 255

        drawDpad(canvas)
        drawStick(canvas, leftStick, state.leftX, state.leftY, "L", isHeld(Kind.LEFT_STICK), scale(ControlId.LEFT_STICK))
        drawStick(canvas, rightStick, state.rightX, state.rightY, "R", isHeld(Kind.RIGHT_STICK), scale(ControlId.RIGHT_STICK))
        drawFaceButtons(canvas)
        drawTrigger(canvas, ltRect, skin.triggerLabels[0], state.leftTrigger)
        drawTrigger(canvas, rtRect, skin.triggerLabels[1], state.rightTrigger)
        drawShoulder(canvas, lbRect, skin.shoulderLabels[0], state.buttons[Buttons.LB])
        drawShoulder(canvas, rbRect, skin.shoulderLabels[1], state.buttons[Buttons.RB])
        drawCenterButtons(canvas)
        if (customizeMode) drawEditorOverlay(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (customizeMode) return onCustomizeTouch(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                pointerDown(
                    event.getPointerId(index),
                    event.getX(index),
                    event.getY(index),
                    event.eventTime
                )
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    pointerMove(
                        event.getPointerId(i),
                        event.getX(i),
                        event.getY(i)
                    )
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerUp(event.getPointerId(event.actionIndex), event.eventTime)
            }

            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until pointers.size()) {
                    releaseTarget(pointers.valueAt(i))
                }
                pointers.clear()
                state.reset()
                notifyChanged()
            }
        }
        invalidate()
        return true
    }

    private fun pointerDown(id: Int, x: Float, y: Float, time: Long) {
        val hit = hitTest(x, y) ?: return
        val target = Target(hit.kind, hit.index, time, x, y)
        pointers.put(id, target)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        applyTarget(target, x, y)
        notifyChanged()
    }

    private fun pointerMove(id: Int, x: Float, y: Float) {
        val target = pointers.get(id) ?: return
        if (hypot(x - target.startX, y - target.startY) > unit * 0.02f) {
            target.moved = true
        }
        applyTarget(target, x, y)
        notifyChanged()
    }

    private fun pointerUp(id: Int, time: Long) {
        val target = pointers.get(id) ?: return
        pointers.remove(id)
        releaseTarget(target)

        val isStick = target.kind == Kind.LEFT_STICK || target.kind == Kind.RIGHT_STICK
        if (isStick && !target.moved && time - target.downTime < TAP_MS) {
            val button = if (target.kind == Kind.LEFT_STICK) Buttons.L3 else Buttons.R3
            state.buttons[button] = true
            notifyChanged()
            postDelayed({
                state.buttons[button] = false
                notifyChanged()
            }, TAP_HOLD_MS)
        }
        notifyChanged()
    }

    private fun applyTarget(target: Target, x: Float, y: Float) {
        when (target.kind) {
            Kind.LEFT_STICK -> applyStick(true, x, y)
            Kind.RIGHT_STICK -> applyStick(false, x, y)
            Kind.DPAD -> applyDpad(x, y)
            Kind.FACE -> state.buttons[target.index] = true
            Kind.SHOULDER -> state.buttons[shoulderButton(target.index)] = true
            Kind.TRIGGER -> applyTrigger(target.index, y)
            Kind.CENTER -> state.buttons[centerButton(target.index)] = true
        }
    }

    private fun releaseTarget(target: Target) {
        when (target.kind) {
            Kind.LEFT_STICK -> {
                state.leftX = GamepadState.CENTER
                state.leftY = GamepadState.CENTER
            }

            Kind.RIGHT_STICK -> {
                state.rightX = GamepadState.CENTER
                state.rightY = GamepadState.CENTER
            }

            Kind.DPAD -> state.hat = GamepadState.HAT_NEUTRAL
            Kind.FACE -> state.buttons[target.index] = false
            Kind.SHOULDER -> state.buttons[shoulderButton(target.index)] = false
            Kind.TRIGGER -> {
                if (target.index == 0) {
                    state.leftTrigger = 0
                } else {
                    state.rightTrigger = 0
                }
            }

            Kind.CENTER -> state.buttons[centerButton(target.index)] = false
        }
    }

    private fun applyStick(left: Boolean, x: Float, y: Float) {
        val center = if (left) leftStick else rightStick
        val radius = stickRadius * scale(if (left) ControlId.LEFT_STICK else ControlId.RIGHT_STICK)
        var dx = x - center.x
        var dy = y - center.y
        val distance = hypot(dx, dy)
        if (distance > radius) {
            val limit = radius / distance
            dx *= limit
            dy *= limit
        }
        val nx = (dx / radius).coerceIn(-1f, 1f)
        val ny = (dy / radius).coerceIn(-1f, 1f)
        val ax = (GamepadState.CENTER + nx * 127f).roundToInt().coerceIn(0, 255)
        val ay = (GamepadState.CENTER + ny * 127f).roundToInt().coerceIn(0, 255)
        if (left) {
            state.leftX = ax
            state.leftY = ay
        } else {
            state.rightX = ax
            state.rightY = ay
        }
    }

    private fun applyDpad(x: Float, y: Float) {
        val dx = x - dpad.x
        val dy = y - dpad.y
        if (hypot(dx, dy) < unit * 0.025f) {
            state.hat = GamepadState.HAT_NEUTRAL
            return
        }
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        state.hat = Math.floorMod(Math.round((angle + 90.0) / 45.0).toInt(), 8)
    }

    private fun applyTrigger(index: Int, y: Float) {
        val rect = if (index == 0) ltRect else rtRect
        if (rect.height() <= 0f) return
        val travel = ((y - rect.top) / (rect.height() * 0.7f)).coerceIn(0f, 1f)
        val value = (travel * 255f).roundToInt()
        if (index == 0) {
            state.leftTrigger = value
        } else {
            state.rightTrigger = value
        }
    }

    private fun hitTest(x: Float, y: Float): Hit? {
        val faceScale = scale(ControlId.FACE)
        for (i in 0 until 4) {
            if (hypot(x - faceX(i), y - faceY(i)) <= faceRadius * faceScale * 1.25f) {
                return Hit(Kind.FACE, i)
            }
        }
        val centerScale = scale(ControlId.CENTER)
        if (hypot(x - back.x, y - back.y) <= smallRadius * centerScale * 1.3f) return Hit(Kind.CENTER, 0)
        if (hypot(x - start.x, y - start.y) <= smallRadius * centerScale * 1.3f) return Hit(Kind.CENTER, 1)
        if (hypot(x - guide.x, y - guide.y) <= guideRadius * centerScale * 1.3f) return Hit(Kind.CENTER, 2)

        if (ltRect.contains(x, y)) return Hit(Kind.TRIGGER, 0)
        if (rtRect.contains(x, y)) return Hit(Kind.TRIGGER, 1)
        if (lbRect.contains(x, y)) return Hit(Kind.SHOULDER, 0)
        if (rbRect.contains(x, y)) return Hit(Kind.SHOULDER, 1)

        if (hypot(x - leftStick.x, y - leftStick.y) <= stickRadius * scale(ControlId.LEFT_STICK)) {
            return Hit(Kind.LEFT_STICK, 0)
        }
        if (hypot(x - rightStick.x, y - rightStick.y) <= stickRadius * scale(ControlId.RIGHT_STICK)) {
            return Hit(Kind.RIGHT_STICK, 0)
        }
        val dpadX = abs(x - dpad.x)
        val dpadY = abs(y - dpad.y)
        val dpadScale = scale(ControlId.DPAD)
        val arm = unit * 0.055f * dpadScale
        if ((dpadX <= dpadHalf * dpadScale && dpadY <= arm) ||
            (dpadY <= dpadHalf * dpadScale && dpadX <= arm)
        ) {
            return Hit(Kind.DPAD, 0)
        }
        return null
    }

    private fun drawStick(
        canvas: Canvas,
        center: PointF,
        ax: Int,
        ay: Int,
        label: String,
        active: Boolean,
        scale: Float
    ) {
        val radius = stickRadius * scale
        fillPaint.color = 0x33121C26
        canvas.drawCircle(center.x, center.y, radius, fillPaint)

        strokePaint.color = if (active) skin.accent else 0x66FFFFFF
        strokePaint.strokeWidth = unit * 0.006f
        canvas.drawCircle(center.x, center.y, radius, strokePaint)
        canvas.drawCircle(center.x, center.y, radius * 0.36f, strokePaint)

        val nx = (ax - GamepadState.CENTER) / 127f
        val ny = (ay - GamepadState.CENTER) / 127f
        val kx = center.x + nx * radius * 0.55f
        val ky = center.y + ny * radius * 0.55f

        fillPaint.color = if (active) skin.accent else 0xFF1B2530.toInt()
        canvas.drawCircle(kx, ky, radius * 0.42f, fillPaint)
        strokePaint.color = 0xCCFFFFFF.toInt()
        canvas.drawCircle(kx, ky, radius * 0.42f, strokePaint)

        textPaint.color = 0x99FFFFFF.toInt()
        textPaint.textSize = unit * 0.03f
        canvas.drawText(
            label,
            center.x,
            center.y - radius - unit * 0.015f,
            textPaint
        )
    }

    private fun drawDpad(canvas: Canvas) {
        val cx = dpad.x
        val cy = dpad.y
        val scale = scale(ControlId.DPAD)
        val half = dpadHalf * scale
        val arm = unit * 0.055f * scale
        val radius = arm * 0.7f

        fillPaint.color = 0x331C2836
        canvas.drawRoundRect(cx - half, cy - arm, cx + half, cy + arm, radius, radius, fillPaint)
        canvas.drawRoundRect(cx - arm, cy - half, cx + arm, cy + half, radius, radius, fillPaint)

        strokePaint.color = 0x66FFFFFF
        strokePaint.strokeWidth = unit * 0.005f
        canvas.drawRoundRect(cx - half, cy - arm, cx + half, cy + arm, radius, radius, strokePaint)
        canvas.drawRoundRect(cx - arm, cy - half, cx + arm, cy + half, radius, radius, strokePaint)

        if (state.hat != GamepadState.HAT_NEUTRAL) {
            val (dx, dy) = hatVector(state.hat)
            fillPaint.color = skin.accent
            canvas.drawCircle(
                cx + dx * half * 0.55f,
                cy + dy * half * 0.55f,
                arm * 0.75f,
                fillPaint
            )
        }
    }

    private fun drawFaceButtons(canvas: Canvas) {
        val scale = scale(ControlId.FACE)
        for (i in 0 until 4) {
            val cx = faceX(i)
            val cy = faceY(i)
            val pressed = state.buttons[i]

            if (skin == Skin.XBOX) {
                fillPaint.color = skin.faceColors[i]
                fillPaint.alpha = if (pressed) 255 else 215
                canvas.drawCircle(cx, cy, faceRadius * scale, fillPaint)

                strokePaint.color = if (pressed) Color.WHITE else 0x88FFFFFF.toInt()
                strokePaint.strokeWidth = unit * (if (pressed) 0.009f else 0.004f)
                canvas.drawCircle(cx, cy, faceRadius * scale, strokePaint)

                textPaint.color = Color.WHITE
                textPaint.textSize = faceRadius * scale * 1.15f
                canvas.drawText(
                    skin.faceLabels[i],
                    cx,
                    cy + textPaint.textSize * 0.35f,
                    textPaint
                )
            } else {
                drawPlayStationButton(canvas, cx, cy, i, pressed, scale)
            }
        }
    }

    private fun drawPlayStationButton(canvas: Canvas, cx: Float, cy: Float, index: Int, pressed: Boolean, scale: Float) {
        val radius = faceRadius * scale
        fillPaint.color = skin.faceColors[index]
        fillPaint.alpha = if (pressed) 70 else 28
        canvas.drawCircle(cx, cy, radius * 1.12f, fillPaint)
        fillPaint.alpha = 255

        strokePaint.color = skin.faceColors[index]
        strokePaint.strokeWidth = unit * (if (pressed) 0.010f else 0.007f)
        strokePaint.style = Paint.Style.STROKE
        val shape = skin.faceShapes[index]
        when (shape) {
            FaceShape.CROSS -> {
                canvas.drawLine(cx - radius * 0.45f, cy - radius * 0.45f, cx + radius * 0.45f, cy + radius * 0.45f, strokePaint)
                canvas.drawLine(cx + radius * 0.45f, cy - radius * 0.45f, cx - radius * 0.45f, cy + radius * 0.45f, strokePaint)
            }
            FaceShape.CIRCLE -> canvas.drawCircle(cx, cy, radius * 0.45f, strokePaint)
            FaceShape.SQUARE -> canvas.drawRect(cx - radius * 0.42f, cy - radius * 0.42f, cx + radius * 0.42f, cy + radius * 0.42f, strokePaint)
            FaceShape.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(cx, cy - radius * 0.52f)
                    lineTo(cx + radius * 0.52f, cy + radius * 0.42f)
                    lineTo(cx - radius * 0.52f, cy + radius * 0.42f)
                    close()
                }
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    private fun drawTrigger(canvas: Canvas, rect: RectF, label: String, value: Int) {
        fillPaint.color = 0x331C2836
        canvas.drawRoundRect(rect, unit * 0.02f, unit * 0.02f, fillPaint)

        if (value > 0) {
            canvas.save()
            canvas.clipRect(rect)
            fillPaint.color = skin.accent
            fillPaint.alpha = 110 + value * 120 / 255
            val fillTop = rect.bottom - rect.height() * (value / 255f)
            canvas.drawRect(rect.left, fillTop, rect.right, rect.bottom, fillPaint)
            canvas.restore()
        }

        strokePaint.color = 0x88FFFFFF.toInt()
        strokePaint.strokeWidth = unit * 0.005f
        canvas.drawRoundRect(rect, unit * 0.02f, unit * 0.02f, strokePaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = unit * 0.034f
        canvas.drawText(
            label,
            rect.centerX(),
            rect.centerY() + textPaint.textSize * 0.35f,
            textPaint
        )
    }

    private fun drawShoulder(canvas: Canvas, rect: RectF, label: String, pressed: Boolean) {
        fillPaint.color = if (pressed) skin.accent else 0x331C2836
        canvas.drawRoundRect(rect, unit * 0.02f, unit * 0.02f, fillPaint)

        strokePaint.color = if (pressed) Color.WHITE else 0x88FFFFFF.toInt()
        strokePaint.strokeWidth = unit * 0.005f
        canvas.drawRoundRect(rect, unit * 0.02f, unit * 0.02f, strokePaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = unit * 0.030f
        canvas.drawText(
            label,
            rect.centerX(),
            rect.centerY() + textPaint.textSize * 0.35f,
            textPaint
        )
    }

    private fun drawCenterButtons(canvas: Canvas) {
        val scale = scale(ControlId.CENTER)
        drawCenterButton(canvas, back, smallRadius * scale, skin.centerLabels[0], state.buttons[Buttons.BACK])
        drawCenterButton(canvas, start, smallRadius * scale, skin.centerLabels[1], state.buttons[Buttons.START])
        drawCenterButton(canvas, guide, guideRadius * scale, skin.centerLabels[2], state.buttons[Buttons.GUIDE])
    }

    private fun drawCenterButton(
        canvas: Canvas,
        center: PointF,
        radius: Float,
        label: String,
        pressed: Boolean
    ) {
        fillPaint.color = if (pressed) skin.accent else 0x441C2836
        canvas.drawCircle(center.x, center.y, radius, fillPaint)

        strokePaint.color = 0x88FFFFFF.toInt()
        strokePaint.strokeWidth = unit * 0.005f
        canvas.drawCircle(center.x, center.y, radius, strokePaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = radius * 0.9f
        val maxWidth = radius * 1.75f
        val measured = textPaint.measureText(label)
        if (measured > maxWidth) {
            textPaint.textSize *= maxWidth / measured
        }
        canvas.drawText(
            label,
            center.x,
            center.y + textPaint.textSize * 0.35f,
            textPaint
        )
    }

    private fun faceX(index: Int): Float = face.x + when (index) {
        1 -> faceOffset * scale(ControlId.FACE)
        2 -> -faceOffset * scale(ControlId.FACE)
        else -> 0f
    }

    private fun faceY(index: Int): Float = face.y + when (index) {
        0 -> faceOffset * scale(ControlId.FACE)
        3 -> -faceOffset * scale(ControlId.FACE)
        else -> 0f
    }

    private fun updateSkinLayout() {
        if (widthPx <= 0f || heightPx <= 0f) return
        val upper = heightPx * if (skin == Skin.PLAYSTATION) 0.43f else 0.55f
        val lower = heightPx * if (skin == Skin.PLAYSTATION) 0.72f else 0.86f
        baseLeftStick.set(widthPx * 0.16f, lower)
        baseDpad.set(widthPx * 0.16f, upper)
        baseFace.set(widthPx * 0.84f, upper)
        baseRightStick.set(widthPx * 0.84f, lower)
        applyPosition(ControlId.LEFT_STICK, baseLeftStick, leftStick)
        applyPosition(ControlId.RIGHT_STICK, baseRightStick, rightStick)
        applyPosition(ControlId.DPAD, baseDpad, dpad)
        applyPosition(ControlId.FACE, baseFace, face)
        applyPosition(ControlId.CENTER, baseGuide, guide)
        val center = transforms.getValue(ControlId.CENTER)
        back.set(baseBack.x + center.offsetX, baseBack.y + center.offsetY)
        start.set(baseStart.x + center.offsetX, baseStart.y + center.offsetY)
    }

    private fun applyPosition(id: ControlId, base: PointF, result: PointF) {
        val transform = transforms.getValue(id)
        result.set(base.x + transform.offsetX, base.y + transform.offsetY)
    }

    private fun scale(id: ControlId): Float = transforms.getValue(id).scale

    fun resetLayout() {
        transforms.values.forEach {
            it.offsetX = 0f
            it.offsetY = 0f
            it.scale = 1f
        }
        updateSkinLayout()
        onLayoutChanged?.invoke(serializeLayout())
        invalidate()
    }

    fun restoreLayout(serialized: String?) {
        if (serialized.isNullOrBlank()) return
        serialized.split(';').forEach { entry ->
            val parts = entry.split(',')
            if (parts.size != 4) return@forEach
            val id = ControlId.values().firstOrNull { it.name == parts[0] } ?: return@forEach
            val transform = transforms.getValue(id)
            transform.offsetX = parts[1].toFloatOrNull() ?: return@forEach
            transform.offsetY = parts[2].toFloatOrNull() ?: return@forEach
            transform.scale = (parts[3].toFloatOrNull() ?: 1f).coerceIn(MIN_SCALE, MAX_SCALE)
        }

    private fun serializeLayout(): String = ControlId.values().joinToString(";") { id ->
        val transform = transforms.getValue(id)
        baseGuide.set(guide.x, guide.y)
        baseBack.set(back.x, back.y)
        baseStart.set(start.x, start.y)
        updateSkinLayout()
        "${id.name},${transform.offsetX},${transform.offsetY},${transform.scale}"
    }

    private fun shoulderButton(index: Int): Int =
        if (index == 0) Buttons.LB else Buttons.RB

    private fun centerButton(index: Int): Int = when (index) {
        0 -> Buttons.BACK
        1 -> Buttons.START
        else -> Buttons.GUIDE
    }

    private fun hatVector(hat: Int): Pair<Float, Float> {
        val angle = Math.toRadians((-90 + hat * 45).toDouble())
        return cos(angle).toFloat() to sin(angle).toFloat()
    }

    private fun isHeld(kind: Kind): Boolean {
        for (i in 0 until pointers.size()) {
            if (pointers.valueAt(i).kind == kind) return true
        }
        return false
    }

    private fun onCustomizeTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val control = editorHitTest(event.x, event.y)
                selectedControl = control
                editorPointerId = event.getPointerId(0)
                editorResizing = control != null && isResizeHandle(control, event.x, event.y)
                editorLastX = event.x
                editorLastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 0 || event.getPointerId(0) != editorPointerId) return true
                val control = selectedControl ?: return true
                val transform = transforms.getValue(control)
                val dx = event.x - editorLastX
                val dy = event.y - editorLastY
                if (editorResizing) {
                    transform.scale = (transform.scale + (dx + dy) / unit).coerceIn(MIN_SCALE, MAX_SCALE)
                } else {
                    transform.offsetX += dx
                    transform.offsetY += dy
                }
                editorLastX = event.x
                editorLastY = event.y
                updateSkinLayout()
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (selectedControl != null) onLayoutChanged?.invoke(serializeLayout())
                editorPointerId = MotionEvent.INVALID_POINTER_ID
                editorResizing = false
            }
        }
        return true
    }

    private fun editorHitTest(x: Float, y: Float): ControlId? = ControlId.values()
        .asSequence()
        .map { it to editorBounds(it) }
        .filter { (_, bounds) -> bounds.contains(x, y) }
        .minByOrNull { (_, bounds) -> bounds.width() * bounds.height() }
        ?.first

    private fun isResizeHandle(id: ControlId, x: Float, y: Float): Boolean {
        val bounds = editorBounds(id)
        return hypot(x - bounds.right, y - bounds.bottom) <= unit * 0.045f
    }

    private fun editorBounds(id: ControlId): RectF {
        val bounds = RectF()
        when (id) {
            ControlId.LEFT_STICK -> bounds.set(leftStick.x - stickRadius * scale(id), leftStick.y - stickRadius * scale(id), leftStick.x + stickRadius * scale(id), leftStick.y + stickRadius * scale(id))
            ControlId.RIGHT_STICK -> bounds.set(rightStick.x - stickRadius * scale(id), rightStick.y - stickRadius * scale(id), rightStick.x + stickRadius * scale(id), rightStick.y + stickRadius * scale(id))
            ControlId.DPAD -> bounds.set(dpad.x - dpadHalf * scale(id), dpad.y - dpadHalf * scale(id), dpad.x + dpadHalf * scale(id), dpad.y + dpadHalf * scale(id))
            ControlId.FACE -> {
                val extent = (faceOffset + faceRadius) * scale(id)
                bounds.set(face.x - extent, face.y - extent, face.x + extent, face.y + extent)
            }
            ControlId.CENTER -> {
                val extent = unit * 0.11f * scale(id)
                bounds.set(guide.x - extent, guide.y - extent, guide.x + extent, guide.y + extent)
            }
        }
        bounds.inset(-unit * 0.025f, -unit * 0.025f)
        return bounds
    }

    private fun drawEditorOverlay(canvas: Canvas) {
        textPaint.color = Color.WHITE
        textPaint.textSize = unit * 0.032f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("CUSTOMIZE  -  drag to move, use the corner handle to resize", unit * 0.04f, heightPx - unit * 0.035f, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        for (id in ControlId.values()) {
            val bounds = editorBounds(id)
            strokePaint.color = if (id == selectedControl) skin.accent else 0x66FFFFFF
            strokePaint.strokeWidth = unit * 0.004f
            canvas.drawRoundRect(bounds, unit * 0.02f, unit * 0.02f, strokePaint)
            fillPaint.color = strokePaint.color
            canvas.drawCircle(bounds.right, bounds.bottom, unit * 0.025f, fillPaint)
        }
    }

    private fun notifyChanged() {
        onStateChanged?.invoke(state)
        invalidate()
    }

    private enum class Kind {
        LEFT_STICK, RIGHT_STICK, DPAD, FACE, SHOULDER, TRIGGER, CENTER
    }

    private enum class ControlId {
        LEFT_STICK, RIGHT_STICK, DPAD, FACE, CENTER
    }

    private data class Hit(val kind: Kind, val index: Int)

    private class Target(
        val kind: Kind,
        val index: Int,
        val downTime: Long,
        val startX: Float,
        val startY: Float
    ) {
        var moved = false
    }

    private class Transform(
        var offsetX: Float = 0f,
        var offsetY: Float = 0f,
        var scale: Float = 1f
    )

    private companion object {
        const val TAP_MS = 220L
        const val TAP_HOLD_MS = 90L
        const val MIN_SCALE = 0.65f
        const val MAX_SCALE = 1.65f
    }
}
