package com.regula.backend.processing

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "settings"
    private const val KEY_SERVER_ADDRESS = "server_address"
    private const val KEY_SERVER_PORT = "server_port"

    private var serverAddress: String? = null
    private var serverPort: String? = null
    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // Use saved preference if present, otherwise fall back to compile-time defaults in Constants
            serverAddress = prefs.getString(KEY_SERVER_ADDRESS, Constants.NEUVOTE_SERVER_ADDRESS)
            serverPort = prefs.getString(KEY_SERVER_PORT, Constants.NEUVOTE_SERVER_PORT)
            initialized = true
        }
    }

    fun getServerAddress(): String = serverAddress ?: ""
    fun getServerPort(): String = serverPort ?: ""

    fun setServerAddress(context: Context, address: String) {
        serverAddress = address
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_ADDRESS, address).apply()
    }

    fun setServerPort(context: Context, port: String) {
        serverPort = port
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_PORT, port).apply()
    }
}
