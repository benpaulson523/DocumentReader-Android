package com.regula.backend.processing

import android.app.Activity
import android.content.Context
import android.content.Intent

object NavigationHelper {
    fun navigateToSettings(activity: Activity) {
        val intent = Intent(activity, SettingsActivity::class.java)
        activity.startActivity(intent)
    }

    fun navigateToRegistrationStart(activity: Activity) {
        val intent = Intent(activity, RegistrationStartActivity::class.java)
        activity.startActivity(intent)
    }

    fun navigateToRegistrationSelectDoc(activity: Activity) {
        val intent = Intent(activity, RegistrationSelectDocActivity::class.java)
        activity.startActivity(intent)
    }

    fun navigateToRegistrationScanDoc(activity: Activity) {
        val intent = Intent(activity, RegistrationScanDocActivity::class.java)
        activity.startActivity(intent)
    }

    fun navigateToFacialScan(activity: Activity) {
        val intent = Intent(activity, FacialScanActivity::class.java)
        activity.startActivity(intent)
    }

    fun navigateToVerifyEmail(activity: Activity) {
        val intent = Intent(activity, VerifyEmailActivity::class.java)
        activity.startActivity(intent)
    }
}
