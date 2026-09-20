package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _webAppUrlFlow = MutableStateFlow(getWebAppUrl())
    val webAppUrlFlow: StateFlow<String> = _webAppUrlFlow.asStateFlow()

    private val _businessProfileFlow = MutableStateFlow(getBusinessProfile())
    val businessProfileFlow: StateFlow<BusinessProfile> = _businessProfileFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(getThemeMode())
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    private val _appointmentSettingsFlow = MutableStateFlow(getAppointmentSettings())
    val appointmentSettingsFlow: StateFlow<com.example.data.model.AppointmentSettings> = _appointmentSettingsFlow.asStateFlow()

    private val _productsFlow = MutableStateFlow(getCachedProducts())
    val productsFlow: StateFlow<List<com.example.data.model.ProductItem>> = _productsFlow.asStateFlow()

    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, "System") ?: "System"
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
    }

    fun getWebAppUrl(): String {
        return prefs.getString(KEY_WEB_APP_URL, "")?.trim() ?: ""
    }

    fun setWebAppUrl(url: String) {
        val trimmed = url.trim()
        prefs.edit().putString(KEY_WEB_APP_URL, trimmed).apply()
        _webAppUrlFlow.value = trimmed
    }

    fun getBusinessProfile(): BusinessProfile {
        return BusinessProfile(
            businessName = prefs.getString(KEY_BUSINESS_NAME, "") ?: "",
            abnAcn = prefs.getString(KEY_ABN_ACN, "") ?: "",
            businessAddress = prefs.getString(KEY_BUSINESS_ADDRESS, "") ?: "",
            phoneMobile = prefs.getString(KEY_PHONE_MOBILE, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: ""
        )
    }

    fun setBusinessProfile(profile: BusinessProfile) {
        prefs.edit()
            .putString(KEY_BUSINESS_NAME, profile.businessName.trim())
            .putString(KEY_ABN_ACN, profile.abnAcn.trim())
            .putString(KEY_BUSINESS_ADDRESS, profile.businessAddress.trim())
            .putString(KEY_PHONE_MOBILE, profile.phoneMobile.trim())
            .putString(KEY_EMAIL, profile.email.trim())
            .apply()
        _businessProfileFlow.value = profile
    }

    fun getAppointmentSettings(): com.example.data.model.AppointmentSettings {
        val enabled = prefs.getBoolean(KEY_APPOINTMENTS_ENABLED, true)
        val slotDuration = prefs.getInt(KEY_SLOT_DURATION, 30)
        val startH = prefs.getInt(KEY_START_HOUR, 9)
        val startM = prefs.getInt(KEY_START_MINUTE, 0)
        val endH = prefs.getInt(KEY_END_HOUR, 18)
        val endM = prefs.getInt(KEY_END_MINUTE, 0)
        val buffer = prefs.getInt(KEY_BUFFER_MINUTES, 0)
        val daysStr = prefs.getString(KEY_WORKING_DAYS, "1,2,3,4,5,6") ?: "1,2,3,4,5,6"
        val days = try {
            daysStr.split(",").filter { it.isNotBlank() }.map { it.toInt() }.toSet()
        } catch (_: Exception) {
            setOf(1, 2, 3, 4, 5, 6)
        }
        val notifEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        val rem24hEnabled = prefs.getBoolean(KEY_REMINDER_24H_ENABLED, true)
        val autoSmsEnabled = prefs.getBoolean(KEY_AUTOMATED_SMS_ENABLED, false)

        return com.example.data.model.AppointmentSettings(
            bookingEnabled = enabled,
            slotDurationMinutes = slotDuration,
            startHour = startH,
            startMinute = startM,
            endHour = endH,
            endMinute = endM,
            bufferMinutes = buffer,
            workingDays = days,
            notificationsEnabled = notifEnabled,
            reminder24hEnabled = rem24hEnabled,
            automatedSmsEnabled = autoSmsEnabled
        )
    }

    fun setAppointmentSettings(settings: com.example.data.model.AppointmentSettings) {
        prefs.edit()
            .putBoolean(KEY_APPOINTMENTS_ENABLED, settings.bookingEnabled)
            .putInt(KEY_SLOT_DURATION, settings.slotDurationMinutes)
            .putInt(KEY_START_HOUR, settings.startHour)
            .putInt(KEY_START_MINUTE, settings.startMinute)
            .putInt(KEY_END_HOUR, settings.endHour)
            .putInt(KEY_END_MINUTE, settings.endMinute)
            .putInt(KEY_BUFFER_MINUTES, settings.bufferMinutes)
            .putString(KEY_WORKING_DAYS, settings.workingDays.joinToString(","))
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, settings.notificationsEnabled)
            .putBoolean(KEY_REMINDER_24H_ENABLED, settings.reminder24hEnabled)
            .putBoolean(KEY_AUTOMATED_SMS_ENABLED, settings.automatedSmsEnabled)
            .apply()
        _appointmentSettingsFlow.value = settings
    }

    fun generateTimeSlots(settings: com.example.data.model.AppointmentSettings = getAppointmentSettings()): List<String> {
        return settings.generateSlots()
    }

    fun isConfigured(): Boolean {
        return true
    }

    fun clearUrl() {
        prefs.edit().remove(KEY_WEB_APP_URL).apply()
        _webAppUrlFlow.value = ""
    }

    fun getCachedProducts(): List<com.example.data.model.ProductItem> {
        val rawJson = prefs.getString(KEY_CACHED_PRODUCTS, "") ?: ""
        if (rawJson.isBlank()) {
            return getDefaultProducts()
        }
        return try {
            val array = org.json.JSONArray(rawJson)
            val list = mutableListOf<com.example.data.model.ProductItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.example.data.model.ProductItem(
                        name = obj.optString("name", ""),
                        price = obj.optDouble("price", 0.0),
                        category = obj.optString("category", "")
                    )
                )
            }
            if (list.isEmpty()) getDefaultProducts() else list
        } catch (_: Exception) {
            getDefaultProducts()
        }
    }

    fun setCachedProducts(products: List<com.example.data.model.ProductItem>) {
        try {
            val array = org.json.JSONArray()
            for (p in products) {
                val obj = org.json.JSONObject().apply {
                    put("name", p.name)
                    put("price", p.price)
                    put("category", p.category)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_CACHED_PRODUCTS, array.toString()).apply()
            _productsFlow.value = products
        } catch (_: Exception) {}
    }

    fun saveProduct(product: com.example.data.model.ProductItem) {
        val current = getCachedProducts().toMutableList()
        val index = current.indexOfFirst { it.name.equals(product.name, ignoreCase = true) }
        if (index >= 0) {
            current[index] = product
        } else {
            current.add(product)
        }
        setCachedProducts(current)
    }

    fun deleteProduct(productName: String) {
        val current = getCachedProducts().toMutableList()
        current.removeAll { it.name.equals(productName, ignoreCase = true) }
        setCachedProducts(current)
    }

    fun resetDefaultProducts() {
        setCachedProducts(getDefaultProducts())
    }

    private fun getDefaultProducts(): List<com.example.data.model.ProductItem> {
        return listOf(
            com.example.data.model.ProductItem("Eyebrows", 10.00, "Threading"),
            com.example.data.model.ProductItem("Uper Lips", 5.00, "Threading"),
            com.example.data.model.ProductItem("Chin", 5.00, "Threading"),
            com.example.data.model.ProductItem("Forehead", 5.00, "Threading"),
            com.example.data.model.ProductItem("Sideburns", 12.00, "Threading"),
            com.example.data.model.ProductItem("Neck", 5.00, "Threading"),
            com.example.data.model.ProductItem("Full Face", 35.00, "Threading"),
            com.example.data.model.ProductItem("Underarms", 15.00, "Body Waxing"),
            com.example.data.model.ProductItem("Full Arms", 30.00, "Body Waxing"),
            com.example.data.model.ProductItem("1/2 Arms", 20.00, "Body Waxing"),
            com.example.data.model.ProductItem("3/4 Arms", 25.00, "Body Waxing"),
            com.example.data.model.ProductItem("Full Legs", 45.00, "Body Waxing"),
            com.example.data.model.ProductItem("1/2 Legs w Knee", 25.00, "Body Waxing"),
            com.example.data.model.ProductItem("1/2 Legs Below Knee", 20.00, "Body Waxing"),
            com.example.data.model.ProductItem("Back", 25.00, "Body Waxing"),
            com.example.data.model.ProductItem("1/2 Back", 20.00, "Body Waxing"),
            com.example.data.model.ProductItem("Stomach", 20.00, "Body Waxing"),
            com.example.data.model.ProductItem("Back of Nick", 15.00, "Body Waxing"),
            com.example.data.model.ProductItem("Sideburns", 15.00, "Facial Waxing"),
            com.example.data.model.ProductItem("Chun", 7.00, "Facial Waxing"),
            com.example.data.model.ProductItem("Upper Lips", 7.00, "Facial Waxing"),
            com.example.data.model.ProductItem("Neck", 7.00, "Facial Waxing"),
            com.example.data.model.ProductItem("Eyebrow", 12.00, "Tinting"),
            com.example.data.model.ProductItem("Eyelash", 20.00, "Tinting"),
            com.example.data.model.ProductItem("EBT+ELT", 30.00, "Tinting"),
            com.example.data.model.ProductItem("Henna (Herbal Henna + Oil)", 30.00, "Tinting"),
            com.example.data.model.ProductItem("Henna (Herbal Henna + Oil)", 25.00, "Tinting"),
            com.example.data.model.ProductItem("20min Clean Up (Cleanse, Scrub, Face Pack)", 20.00, "Herbal Facial (BYO)"),
            com.example.data.model.ProductItem("Hair Oil Massage (10 min)", 15.00, "Hair Care (BYO)"),
            com.example.data.model.ProductItem("Hand Henna -Starting From $10", 10.00, "Hair Care (BYO)")
        )
    }

    companion object {
        private const val PREF_NAME = "business_reporting_prefs"
        private const val KEY_WEB_APP_URL = "google_apps_script_url"
        private const val KEY_BUSINESS_NAME = "business_name"
        private const val KEY_ABN_ACN = "abn_acn"
        private const val KEY_BUSINESS_ADDRESS = "business_address"
        private const val KEY_PHONE_MOBILE = "phone_mobile"
        private const val KEY_EMAIL = "email_address"
        private const val KEY_CACHED_PRODUCTS = "cached_products_json"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_APPOINTMENTS_ENABLED = "appointments_enabled"
        private const val KEY_SLOT_DURATION = "appointment_slot_duration"
        private const val KEY_START_HOUR = "appointment_start_hour"
        private const val KEY_START_MINUTE = "appointment_start_minute"
        private const val KEY_END_HOUR = "appointment_end_hour"
        private const val KEY_END_MINUTE = "appointment_end_minute"
        private const val KEY_BUFFER_MINUTES = "appointment_buffer_minutes"
        private const val KEY_WORKING_DAYS = "appointment_working_days"
        private const val KEY_NOTIFICATIONS_ENABLED = "appointment_notifications_enabled"
        private const val KEY_REMINDER_24H_ENABLED = "appointment_reminder_24h_enabled"
        private const val KEY_AUTOMATED_SMS_ENABLED = "appointment_automated_sms_enabled"

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = AppPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
