package com.mouzhi.headup

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.mouzhi.headup.utils.DebugLogger

/**
 * 专门用于息屏状态下点亮屏幕的透明 Activity
 * Android 10+ 推荐的后台唤醒方式
 */
class WakeUpActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.i("WakeUpActivity", "onCreate")

        // 点亮屏幕（多种方式确保兼容）
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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 发送 Home Intent 回到主页
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        startActivity(homeIntent)
        DebugLogger.i("WakeUpActivity", "Home intent sent")

        // 立即关闭自己
        Handler(Looper.getMainLooper()).postDelayed({
            DebugLogger.i("WakeUpActivity", "Finishing")
            finish()
        }, 300)
    }
}
