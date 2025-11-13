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

    fun navigateToRegistrationLiveness(activity: Activity) {
        val intent = Intent(activity, RegistrationLivenessActivity::class.java)
        activity.startActivity(intent)
    }

    fun navigateToRegistrationData(activity: Activity) {
        val intent = Intent(activity, RegistrationDataActivity::class.java)
        activity.startActivity(intent)
    }

    fun navigateToRegistrationVerifyContact(activity: Activity) {
        val intent = Intent(activity, RegistrationVerifyContactActivity::class.java)
        activity.startActivity(intent)
    }
}
