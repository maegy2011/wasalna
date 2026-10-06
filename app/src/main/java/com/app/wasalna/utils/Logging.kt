package com.app.wasalna.utils

import android.util.Log

private const val TAG = "Wasalna"

fun logInfo(message: String) = Log.i(TAG, message)
fun logWarn(message: String) = Log.w(TAG, message)
fun logError(message: String, throwable: Throwable? = null) =
    Log.e(TAG, message, throwable)
