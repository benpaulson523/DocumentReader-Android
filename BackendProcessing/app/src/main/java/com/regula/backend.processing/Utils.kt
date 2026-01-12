package com.regula.backend.processing

import android.content.Context
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import android.view.LayoutInflater

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
