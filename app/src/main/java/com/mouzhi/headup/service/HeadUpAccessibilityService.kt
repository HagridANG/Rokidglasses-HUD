package com.mouzhi.headup.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.mouzhi.headup.WakeUpActivity
import com.mouzhi.headup.utils.DebugLogger

/**
 * AccessibilityService 版息屏仰头亮屏
 * 比 ForegroundService 存活优先级更高，Doze 模式下不会被系统杀死
 */
class HeadUpAccessibilityService : AccessibilityService(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "headup_accessibility_channel"
        const val NOTIFICATION_ID = 1002

        private const val ROLL_UP_THRESHOLD = 150f
        private const val ACCEL_Z_UP_THRESHOLD = -2.0f
        private const val TRIGGER_DURATION_MS = 1000L
        private const val CONSECUTIVE_SAMPLES_REQUIRED = 2
        private const val COOLDOWN_MS = 3000L
        private const val STARTUP_DELAY_MS = 3000L
        private const val SCREEN_ON_DURATION_MS = 15000L
        private const val KEEP_ALIVE_INTERVAL_MS = 10 * 60 * 1000L  // 10分钟自检
        private const val SENSOR_DEAD_THRESHOLD_MS = 3 * 60 * 1000L   // 3分钟无数据视为卡死

        @Volatile
        var isRunning = false
            private set
    }

    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var keepAliveRunnable: Runnable? = null

    private var isTiltedUp = false
    private var hasTriggeredInThisTilt = false
    private var tiltStartTime = 0L
    private var lastTriggerTime = 0L
    private var serviceStartTime = 0L
    private var lastLogTime = 0L
    private var lastStateLogTime = 0L
    private var lastSensorDataTime = 0L
    private var consecutiveSamples = 0

    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    DebugLogger.i("HeadUpAccessibilityService", "Screen turned off")
                }
                Intent.ACTION_SCREEN_ON -> {
                    DebugLogger.i("HeadUpAccessibilityService", "Screen turned on")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DebugLogger.i("HeadUpAccessibilityService", "onServiceConnected")
        isRunning = true
        serviceStartTime = System.currentTimeMillis()
        lastSensorDataTime = System.currentTimeMillis()
        initWakeLock()
        initSensor()
        registerScreenStateReceiver()
        startKeepAlive()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DebugLogger.i("HeadUpAccessibilityService", "onUnbind")
        isRunning = false
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.i("HeadUpAccessibilityService", "onDestroy")
        isRunning = false
        cleanup()
    }

    private fun cleanup() {
        sensorManager?.unregisterListener(this)
        releaseWakeLock()
        keepAliveRunnable?.let { mainHandler.removeCallbacks(it) }
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun registerScreenStateReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenStateReceiver, filter)
        } catch (e: Exception) {
            DebugLogger.e("HeadUpAccessibilityService", "Failed to register receiver", e.message ?: "")
        }
    }

    private fun initSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        rotationSensor = findWakeUpSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: findWakeUpSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        rotationSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            val wakeType = if (sensor.isWakeUpSensor) "WAKEUP" else "Non-wakeup"
            DebugLogger.i("HeadUpAccessibilityService", "Rotation sensor registered", "$wakeType | ${sensor.name}")
        } ?: run {
            DebugLogger.e("HeadUpAccessibilityService", "No rotation vector sensor found")
        }

        accelSensor = findWakeUpSensor(Sensor.TYPE_ACCELEROMETER)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            val wakeType = if (sensor.isWakeUpSensor) "WAKEUP" else "Non-wakeup"
            DebugLogger.i("HeadUpAccessibilityService", "Accelerometer registered", "$wakeType | ${sensor.name}")
        } ?: run {
            DebugLogger.e("HeadUpAccessibilityService", "No accelerometer found")
        }
    }

    private fun findWakeUpSensor(sensorType: Int): Sensor? {
        val sensors = sensorManager?.getSensorList(sensorType)
        return sensors?.firstOrNull { it.isWakeUpSensor }
    }

    private fun initWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "HeadUp:AccessibilityWakeLock"
        )
        wakeLock?.setReferenceCounted(false)
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            DebugLogger.e("HeadUpAccessibilityService", "WakeLock release error", e.message ?: "")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
                return
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_ROTATION_VECTOR -> {
            }
            else -> return
        }

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val yawDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
        val now = System.currentTimeMillis()

        if (now - lastLogTime > 1000) {
            lastLogTime = now
            lastSensorDataTime = now
            DebugLogger.i(
                "HeadUpAccessibilityService",
                "Sensor",
                "y=${yawDeg.toInt()} p=${pitchDeg.toInt()} r=${rollDeg.toInt()} | a=(${(accelX*10).toInt()/10f},${(accelY*10).toInt()/10f},${(accelZ*10).toInt()/10f})"
            )
        }

        if (now - serviceStartTime < STARTUP_DELAY_MS) {
            return
        }

        val isHeadUp = kotlin.math.abs(rollDeg) > ROLL_UP_THRESHOLD && accelZ < ACCEL_Z_UP_THRESHOLD

        if (isHeadUp || isTiltedUp || (now / 3000) != (lastStateLogTime / 3000)) {
            lastStateLogTime = now
            DebugLogger.i("HeadUpAccessibilityService", "State",
                "r=${rollDeg.toInt()} z=${accelZ.toInt()} headUp=$isHeadUp samples=$consecutiveSamples tilted=$isTiltedUp triggered=$hasTriggeredInThisTilt")
        }

        if (isHeadUp) {
            consecutiveSamples++
            if (!isTiltedUp && consecutiveSamples >= CONSECUTIVE_SAMPLES_REQUIRED) {
                isTiltedUp = true
                tiltStartTime = now
                DebugLogger.i("HeadUpAccessibilityService", "Tilt up started", "roll=${rollDeg.toInt()}deg z=${accelZ.toInt()}")
            } else if (isTiltedUp) {
                val tiltDuration = now - tiltStartTime
                if (tiltDuration >= TRIGGER_DURATION_MS && (now - lastTriggerTime) > COOLDOWN_MS && !hasTriggeredInThisTilt) {
                    lastTriggerTime = now
                    hasTriggeredInThisTilt = true
                    DebugLogger.i("HeadUpAccessibilityService", "Trigger condition met! roll=${rollDeg.toInt()}")
                    mainHandler.post { wakeScreen() }
                }
            }
        } else {
            if (kotlin.math.abs(rollDeg) < 120f && accelZ > -1.0f) {
                if (consecutiveSamples > 0 || isTiltedUp || hasTriggeredInThisTilt) {
                    DebugLogger.i("HeadUpAccessibilityService", "Tilt reset", "r=${rollDeg.toInt()}deg z=${accelZ.toInt()}")
                }
                consecutiveSamples = 0
                if (isTiltedUp) {
                    isTiltedUp = false
                }
                hasTriggeredInThisTilt = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun wakeScreen() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isInteractive) {
            DebugLogger.i("HeadUpAccessibilityService", "Screen already on, skip wake")
            return
        }

        DebugLogger.i("HeadUpAccessibilityService", "wakeScreen() called")

        showFullscreenNotification()

        try {
            wakeLock?.acquire(SCREEN_ON_DURATION_MS)
        } catch (e: Exception) {
            DebugLogger.e("HeadUpAccessibilityService", "WakeLock error", e.message ?: "")
        }

        val intent = Intent(this, WakeUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        try {
            startActivity(intent)
            DebugLogger.i("HeadUpAccessibilityService", "WakeUpActivity started")
        } catch (e: Exception) {
            DebugLogger.e("HeadUpAccessibilityService", "WakeUpActivity failed", e.message ?: "")
        }
    }

    private fun showFullscreenNotification() {
        try {
            val intent = Intent(this, WakeUpActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = Notification.Builder(this, CHANNEL_ID).apply {
                setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                setContentTitle(" ")
                setContentText(" ")
                setPriority(Notification.PRIORITY_MAX)
                setCategory(Notification.CATEGORY_ALARM)
                setFullScreenIntent(pendingIntent, true)
                setAutoCancel(true)
                setOngoing(false)
            }.build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(999, notification)

            mainHandler.postDelayed({
                notificationManager.cancel(999)
            }, 300)
        } catch (e: Exception) {
            DebugLogger.e("HeadUpAccessibilityService", "Fullscreen notification failed", e.message ?: "")
        }
    }

    private fun startKeepAlive() {
        keepAliveRunnable = Runnable {
            val now = System.currentTimeMillis()
            val timeSinceLastData = now - lastSensorDataTime
            DebugLogger.i("HeadUpAccessibilityService", "Keep-alive check", "lastData=${timeSinceLastData/1000}s ago")

            if (timeSinceLastData > SENSOR_DEAD_THRESHOLD_MS) {
                DebugLogger.w("HeadUpAccessibilityService", "Sensor appears dead, re-registering")
                sensorManager?.unregisterListener(this)
                initSensor()
                lastSensorDataTime = now
            }

            mainHandler.postDelayed(keepAliveRunnable!!, KEEP_ALIVE_INTERVAL_MS)
        }
        mainHandler.postDelayed(keepAliveRunnable!!, KEEP_ALIVE_INTERVAL_MS)
    }

}
