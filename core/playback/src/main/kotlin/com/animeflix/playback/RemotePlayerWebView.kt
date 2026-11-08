package com.animeflix.playback

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.KeyEvent
import android.view.MotionEvent
import android.os.SystemClock
import android.webkit.WebView

/** Remote gestures are dispatched only to this app-owned WebView. */
internal class RemotePlayerWebView(context: Context, private val tv: Boolean) : WebView(context) {
    var onExitControls: () -> Unit = {}
    private var pointer = false
    private var xPosition = -1f
    private var yPosition = -1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handledKeys = mutableSetOf<Int>()
    fun controlVideo() { pointer = true; isFocusable = true; isFocusableInTouchMode = true; requestFocus(); invalidate() }
    fun releaseControls() { pointer = false; clearFocus(); isFocusable = false; isFocusableInTouchMode = false; invalidate() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        xPosition = (if(xPosition < 0) w / 2f else xPosition).coerceIn(1f, (w - 1).coerceAtLeast(1).toFloat())
        yPosition = (if(yPosition < 0) h / 2f else yPosition).coerceIn(1f, (h - 1).coerceAtLeast(1).toFloat())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (tv && pointer && hasFocus()) {
            if (xPosition < 0) { xPosition = width / 2f; yPosition = height / 2f }
            paint.color = Color.BLACK
            canvas.drawCircle(xPosition + scrollX, yPosition + scrollY, 10 * resources.displayMetrics.density, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(xPosition + scrollX, yPosition + scrollY, 6 * resources.displayMetrics.density, paint)
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!tv || !pointer) return super.onKeyDown(keyCode, event)
        if (xPosition < 0) { xPosition = width / 2f; yPosition = height / 2f }
        val step = (if(event.repeatCount > 8) 48 else 18) * resources.displayMetrics.density
        when(keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> xPosition -= step
            KeyEvent.KEYCODE_DPAD_RIGHT -> xPosition += step
            KeyEvent.KEYCODE_DPAD_UP -> { if(yPosition < step) scrollBy(0, -step.toInt() * 4) else yPosition -= step }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if(yPosition > height - step) scrollBy(0, step.toInt() * 4) else yPosition += step }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0) {
                    val now = SystemClock.uptimeMillis()
                    val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, xPosition, yPosition, 0)
                    val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, xPosition, yPosition, 0)
                    dispatchTouchEvent(down); dispatchTouchEvent(up); down.recycle(); up.recycle()
                }
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> { pointer = false; clearFocus(); onExitControls() }
            else -> return super.onKeyDown(keyCode, event)
        }
        xPosition = xPosition.coerceIn(1f, (width - 1).coerceAtLeast(1).toFloat())
        yPosition = yPosition.coerceIn(1f, (height - 1).coerceAtLeast(1).toFloat())
        invalidate()
        handledKeys.add(keyCode)
        return true
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if(handledKeys.remove(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }
}
