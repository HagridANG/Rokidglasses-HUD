package com.mouzhi.headup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import com.mouzhi.headup.service.HeadUpAccessibilityService
import com.mouzhi.headup.utils.DebugLogger

class SimpleMainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var actionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.i("SimpleMainActivity", "Activity created")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF000000.toInt())
        }

        val titleText = TextView(this).apply {
            text = "息屏仰头亮屏"
            textSize = 20f
            setTextColor(0xFF00FF00.toInt())
            setPadding(0, 0, 0, 24)
        }

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 16)
        }

        val descText = TextView(this).apply {
            text = "息屏状态下仰头1秒\n自动点亮屏幕并回到主页\n无浮窗、无遮挡"
            textSize = 12f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 0, 0, 16)
        }

        actionText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(descText)
        layout.addView(actionText)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = isAccessibilityServiceEnabled()
        if (enabled) {
            statusText.text = "辅助功能服务已开启\n服务运行正常"
            statusText.setTextColor(0xFF00FF00.toInt())
            actionText.text = ""
            actionText.setOnClickListener(null)
        } else {
            statusText.text = "辅助功能服务未开启\n必须开启后才能息屏仰头亮屏"
            statusText.setTextColor(0xFFFF4444.toInt())
            actionText.text = "[点击此处前往设置开启]"
            actionText.setTextColor(0xFF00AAFF.toInt())
            actionText.setOnClickListener { openAccessibilitySettings() }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("${packageName}/${HeadUpAccessibilityService::class.java.canonicalName}")
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            statusText.text = "无法打开设置，请手动前往：\n设置 > 辅助功能 > 息屏仰头亮屏"
        }
    }
}
