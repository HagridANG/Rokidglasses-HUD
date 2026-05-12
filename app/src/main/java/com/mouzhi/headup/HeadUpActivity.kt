package com.mouzhi.headup

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.mouzhi.headup.utils.DateTimeUtils
import com.mouzhi.headup.utils.DebugLogger

class HeadUpActivity : Activity() {

    companion object {
        private const val AUTO_CLOSE_DELAY_MS = 5000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var closeRunnable: Runnable? = null
    private lateinit var timeText: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.i("HeadUpActivity", "Created")

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }

        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        window.setFormat(PixelFormat.TRANSLUCENT)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
        }

        timeText = TextView(this).apply {
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            text = "--:--"
        }

        val dateText = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, dpToPx(1), 0, 0)
            text = ""
        }

        content.addView(timeText)
        content.addView(dateText)

        val contentParams = FrameLayout.LayoutParams(dpToPx(150), dpToPx(75)).apply {
            gravity = Gravity.START or Gravity.BOTTOM
            setMargins(dpToPx(8), 0, 0, dpToPx(8))
        }
        root.addView(content, contentParams)

        setContentView(root)

        updateDateTime(dateText)

        closeRunnable = Runnable {
            DebugLogger.i("HeadUpActivity", "Auto close")
            finish()
        }
        handler.postDelayed(closeRunnable!!, AUTO_CLOSE_DELAY_MS)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun updateDateTime(dateText: TextView) {
        val (timeStr, dateStr) = DateTimeUtils.formatDateTime()
        timeText.text = timeStr
        dateText.text = dateStr
    }

    override fun onDestroy() {
        super.onDestroy()
        closeRunnable?.let { handler.removeCallbacks(it) }
        sendBroadcast(Intent("com.mouzhi.headup.action.HEAD_UP_CLOSED"))
        DebugLogger.i("HeadUpActivity", "Destroyed")
    }
}
