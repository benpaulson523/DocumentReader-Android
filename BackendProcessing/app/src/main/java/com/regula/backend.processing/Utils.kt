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

fun generateMnemonicUUID(): String {
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
