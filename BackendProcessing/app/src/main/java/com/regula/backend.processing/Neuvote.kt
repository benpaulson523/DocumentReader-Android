package com.regula.backend.processing

object Neuvote {
    fun getNeuvoteServerUrl(): String {
        val address = SettingsManager.getServerAddress()
        val port = SettingsManager.getServerPort()
        return "http://$address:$port"
    }
}
