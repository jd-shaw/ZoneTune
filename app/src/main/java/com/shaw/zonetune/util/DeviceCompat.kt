package com.shaw.zonetune.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

/**
 * 车机环境检测。极氪 7X 等 ZEEKR OS 对前台服务/通知超时极严，
 * 误用 startForegroundService 会在数秒内被系统杀进程。
 */
fun Context.isAutomotiveDevice(): Boolean {
    val pm = packageManager
    if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return true

    val uiMode = resources.configuration.uiMode
    if ((uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_CAR) {
        return true
    }

    // Zeekr / Geely seat OS often sideloads as a "tablet" without FEATURE_AUTOMOTIVE.
    val hints = listOf(
        Build.MANUFACTURER,
        Build.BRAND,
        Build.MODEL,
        Build.PRODUCT,
        Build.DEVICE,
        Build.FINGERPRINT,
    ).joinToString("|").lowercase()

    return listOf("zeekr", "ecarx", "flyme_auto", "flymeauto").any { it in hints }
}
