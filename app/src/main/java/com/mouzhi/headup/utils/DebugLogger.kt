package com.mouzhi.headup.utils

import android.util.Log

object DebugLogger {
    fun d(tag: String, message: String, data: String = "") {
        val logMessage = if (data.isNotEmpty()) "$message - $data" else message
        Log.d("HeadUp_$tag", logMessage)
    }

    fun i(tag: String, message: String, data: String = "") {
        val logMessage = if (data.isNotEmpty()) "$message - $data" else message
        Log.i("HeadUp_$tag", logMessage)
    }

    fun w(tag: String, message: String, data: String = "") {
        val logMessage = if (data.isNotEmpty()) "$message - $data" else message
        Log.w("HeadUp_$tag", logMessage)
    }

    fun e(tag: String, message: String, data: String = "") {
        val logMessage = if (data.isNotEmpty()) "$message - $data" else message
        Log.e("HeadUp_$tag", logMessage)
    }
}
