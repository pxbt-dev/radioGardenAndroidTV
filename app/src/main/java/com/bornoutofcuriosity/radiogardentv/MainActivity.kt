package com.bornoutofcuriosity.radiogardentv

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Full-screen WebView on radio.garden, driven by a TV remote through an on-screen pointer.
 *
 * Arrows move the pointer (faster when held); at the top/bottom edge they scroll instead.
 * OK clicks. Long-press OK toggles spin mode, where arrows drag the globe.
 * Back leaves spin mode, then goes back in the site, then (pressed twice) exits.
 *
 * Audio keeps playing when you press Home, like a radio; exit with Back to stop it.
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var pointer: PointerView
    private val handler = Handler(Looper.getMainLooper())

    private var spinMode = false
    private var okLongPressed = false
    private var swipeInProgress = false
    private var lastBackAt = 0L

    private var gliding = false
    private var glideX = 0
    private var glideY = 0
    private var holdStartNanos = 0L
    private var lastFrameNanos = 0L
    private var releasedAtNanos = 0L
    private var lastWheelAt = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        pointer = PointerView(this)

        val root = FrameLayout(this)
        root.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(pointer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(HOME_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        stopGliding()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(glideFrame)
        webView.destroy()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> onArrowDown(event.keyCode)
                    KeyEvent.ACTION_UP -> onArrowUp()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        okLongPressed = false
                    } else if (!okLongPressed) {
                        okLongPressed = true
                        setSpinMode(!spinMode)
                    }
                } else if (event.action == KeyEvent.ACTION_UP && !okLongPressed) {
                    if (spinMode) setSpinMode(false) else click(pointer.px, pointer.py)
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun onBack() {
        when {
            spinMode -> setSpinMode(false)
            webView.canGoBack() -> webView.goBack()
            SystemClock.uptimeMillis() - lastBackAt < EXIT_CONFIRM_MS -> finish()
            else -> {
                lastBackAt = SystemClock.uptimeMillis()
                Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setSpinMode(on: Boolean) {
        stopGliding()
        spinMode = on
        pointer.spinMode = on
        Toast.makeText(this, if (on) R.string.drag_on else R.string.drag_off, Toast.LENGTH_SHORT).show()
    }

    private fun onArrowDown(keyCode: Int) {
        val (dx, dy) = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
            KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
            else -> 0 to 1
        }

        if (spinMode) {
            // Arrow = direction you travel over the globe, so the "finger" moves the other way.
            val d = dp(SPIN_SWIPE_DP)
            swipe(webView.width / 2f, webView.height / 2f, -dx * d, -dy * d)
            return
        }

        if (gliding && dx == glideX && dy == glideY) return // key repeat while held

        val now = System.nanoTime()
        // Some IR remotes send a held key as quick down/up pairs; treat a fast re-press of the
        // same arrow as one continuous hold so the glide keeps its speed.
        val continuesHold = dx == glideX && dy == glideY && now - releasedAtNanos < REPRESS_GRACE_NS
        if (!continuesHold) holdStartNanos = now
        glideX = dx
        glideY = dy
        gliding = true
        lastFrameNanos = 0L
        // A frame from the previous press may still be queued; never run two glide loops at once.
        Choreographer.getInstance().removeFrameCallback(glideFrame)
        Choreographer.getInstance().postFrameCallback(glideFrame)
    }

    private fun onArrowUp() {
        if (!gliding) return
        gliding = false
        releasedAtNanos = System.nanoTime()
    }

    private fun stopGliding() {
        gliding = false
        glideX = 0
        glideY = 0
    }

    /**
     * Moves the pointer every frame while an arrow is held. Speed eases (smoothstep) from slow to
     * a moderate top speed over GLIDE_RAMP_S, so there is no jump at the start and no rush later.
     */
    private val glideFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!gliding) return
            if (lastFrameNanos != 0L) {
                val dt = (frameTimeNanos - lastFrameNanos) / 1e9f
                val t = ((frameTimeNanos - holdStartNanos) / 1e9f / GLIDE_RAMP_S).coerceIn(0f, 1f)
                val ease = t * t * (3f - 2f * t)
                val speed = dp(1) * (GLIDE_START_DP_S + (GLIDE_MAX_DP_S - GLIDE_START_DP_S) * ease)
                movePointerBy(glideX * speed * dt, glideY * speed * dt)
            }
            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun movePointerBy(mx: Float, my: Float) {
        val maxX = webView.width - 1f
        val maxY = webView.height - 1f
        val ny = pointer.py + my

        // Pushing past the top/bottom edge scrolls (or zooms the globe), at a steady rate.
        val now = SystemClock.uptimeMillis()
        if ((ny < 0f || ny > maxY) && now - lastWheelAt >= WHEEL_INTERVAL_MS) {
            lastWheelAt = now
            wheel(pointer.px, pointer.py, if (ny < 0f) 1f else -1f)
        }
        pointer.moveTo((pointer.px + mx).coerceIn(0f, maxX), ny.coerceIn(0f, maxY))
        hover(pointer.px, pointer.py)
    }

    private fun click(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, x, y, downTime)
        touch(MotionEvent.ACTION_UP, x, y, downTime)
    }

    /** A short drag from (x, y) by (dx, dy), spread over a few frames so the page sees a real gesture. */
    private fun swipe(x: Float, y: Float, dx: Float, dy: Float) {
        if (swipeInProgress) return
        swipeInProgress = true
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, x, y, downTime)
        for (i in 1..SWIPE_STEPS) {
            handler.postDelayed({
                val f = i / SWIPE_STEPS.toFloat()
                touch(MotionEvent.ACTION_MOVE, x + dx * f, y + dy * f, downTime)
                if (i == SWIPE_STEPS) {
                    touch(MotionEvent.ACTION_UP, x + dx, y + dy, downTime)
                    swipeInProgress = false
                }
            }, i * SWIPE_FRAME_MS)
        }
    }

    private fun touch(action: Int, x: Float, y: Float, downTime: Long) {
        val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun hover(x: Float, y: Float) = mouseEvent(MotionEvent.ACTION_HOVER_MOVE, x, y, 0f)

    /** Mouse-wheel scroll at (x, y): scrolls lists, zooms the globe. Positive = up / zoom in. */
    private fun wheel(x: Float, y: Float, amount: Float) = mouseEvent(MotionEvent.ACTION_SCROLL, x, y, amount)

    private fun mouseEvent(action: Int, x: Float, y: Float, vScroll: Float) {
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
        }
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(
            now, now, action, 1, arrayOf(props), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
        webView.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density

    private companion object {
        const val HOME_URL = "https://radio.garden/"
        const val GLIDE_START_DP_S = 150f
        const val GLIDE_MAX_DP_S = 600f
        const val GLIDE_RAMP_S = 1.2f
        const val REPRESS_GRACE_NS = 180_000_000L
        const val WHEEL_INTERVAL_MS = 90L
        const val SPIN_SWIPE_DP = 120
        const val SWIPE_STEPS = 6
        const val SWIPE_FRAME_MS = 16L
        const val EXIT_CONFIRM_MS = 2000L
    }
}
