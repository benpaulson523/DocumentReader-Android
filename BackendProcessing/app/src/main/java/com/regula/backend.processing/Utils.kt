package com.regula.backend.processing

import android.content.Context
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import android.view.LayoutInflater

import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

fun formatDateOfBirth(dob: String): String {
    val regexList = listOf(
        Regex("^(\\d{1,2})[.](\\d{1,2})[.](\\d{4})$"),
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$"),
        Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})$"),
        Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$"),
        Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{2})$"),
        Regex("^(\\d{1,2})[.](\\d{1,2})[.](\\d{2})$")
    )
    for ((i, regex) in regexList.withIndex()) {
        val match = regex.find(dob)
        if (match != null) {
            val groups = match.groupValues
            return when (i) {
                0 -> "${groups[3]}-${groups[2].padStart(2,'0')}-${groups[1].padStart(2,'0')}"
                1 -> "${groups[1]}-${groups[2].padStart(2,'0')}-${groups[3].padStart(2,'0')}"
                2 -> "${groups[1]}-${groups[2].padStart(2,'0')}-${groups[3].padStart(2,'0')}"
                3 -> "${groups[3]}-${groups[1].padStart(2,'0')}-${groups[2].padStart(2,'0')}"
                4 -> {
                    val year = groups[3].toInt()
                    val fullYear = if (year >= 26) 1900 + year else 2000 + year
                    "${fullYear}-${groups[1].padStart(2,'0')}-${groups[2].padStart(2,'0')}"
                }
                5 -> {
                    val year = groups[3].toInt()
                    val fullYear = if (year >= 26) 1900 + year else 2000 + year
                    "${fullYear}-${groups[2].padStart(2,'0')}-${groups[1].padStart(2,'0')}"
                }
                else -> dob
            }
        }
    }
    return dob
}

fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun generateRegistrationCode(): String {
    fun randomLetters(length: Int): String {
        val chars = ('A'..'Z').toList()
        val secureRandom = java.security.SecureRandom()
        return (1..length)
            .map { chars[secureRandom.nextInt(chars.size)] }
            .joinToString("")
    }
    val part1 = randomLetters(3)
    val part2 = randomLetters(4)
    val part3 = randomLetters(5)
    val part4 = randomLetters(5)
    return "$part1-$part2-$part3-$part4"
}

@Suppress("DEPRECATION")
fun showToast(context: Context, text: String, yOffset: Int? = null) {
    val inflater = LayoutInflater.from(context)
    val layout = inflater.inflate(R.layout.custom_toast, null)
    val toastText = layout.findViewById<TextView>(R.id.toastText)
    val toastIcon = layout.findViewById<ImageView>(R.id.toastIcon)
    toastText.text = text
    toastIcon.setImageResource(R.mipmap.ic_launcher)
    val toast = Toast(context)
    toast.duration = Toast.LENGTH_LONG
    // Use addView for custom layout to avoid deprecated setter
    toast.setGravity(android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL, 0, yOffset ?: 280)
    toast.setView(layout)
    toast.show()
}

/**
 * Generates a registration QR code as a base64 string from the provided data.
 * @param registrationCode The registration code
 * @param firstName The first name
 * @param middleName The middle name
 * @param lastName The last name
 * @param dateOfBirth The date of birth
 * @param sex The sex
 * @return The QR code as a Bitmap, or null if generation fails
 */
fun generateRegistrationQRCode(
    registrationCode: String? = null,
    firstName: String? = null,
    middleName: String? = null,
    lastName: String? = null,
    dateOfBirth: String? = null,
    sex: String? = null
): Bitmap? {
    val qrData = "$registrationCode%%$firstName%%$middleName%%$lastName%%$dateOfBirth%%$sex"
    Log.i("Utils", "Making a QR code from qrData: $qrData")
    return try {
        val size = 512
        val bits = QRCodeWriter().encode(qrData, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Converts a Bitmap to a base64-encoded PNG string (no whitespace)
 */
fun bitmapToBase64(bitmap: Bitmap? = null): String? {
    return try {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val bytes = outputStream.toByteArray()
        Base64.encodeToString(bytes, Base64.NO_WRAP).replace("\\s".toRegex(), "")
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}