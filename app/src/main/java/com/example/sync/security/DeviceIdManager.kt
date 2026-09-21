package com.example.sync.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

object DeviceIdManager {
    private const val PREFS_NAME = "my_business_device_prefs"
    private const val KEY_DEVICE_ID = "sync_device_id"
    private const val KEY_DEVICE_NAME = "sync_device_name"

    fun getDeviceId(context: Context): String {
        val prefs = getPrefs(context)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            id = "ANDROID-$randomSuffix"
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getDeviceName(context: Context): String {
        val prefs = getPrefs(context)
        var name = prefs.getString(KEY_DEVICE_NAME, null)
        if (name.isNullOrBlank()) {
            val model = Build.MODEL ?: "Phone"
            name = "Android ($model)"
            prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
        }
        return name
    }

    fun setDeviceName(context: Context, newName: String) {
        getPrefs(context).edit().putString(KEY_DEVICE_NAME, newName.trim()).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
