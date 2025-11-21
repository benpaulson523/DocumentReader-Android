package com.regula.backend.processing

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
        val chars = ('A'..'Z')
        return (1..length).map { chars.random() }.joinToString("")
    }
    val part1 = randomLetters(3)
    val part2 = randomLetters(4)
    val part3 = randomLetters(5)
    val part4 = randomLetters(5)
    return "$part1-$part2-$part3-$part4"
}

fun generateQRCodeBitmap(text: String): android.graphics.Bitmap? {
    try {
        val size = 400 // pixels
        val hints = mapOf<com.google.zxing.EncodeHintType, Any>(
            com.google.zxing.EncodeHintType.MARGIN to 1
        )
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            text,
            com.google.zxing.BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bmp
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}