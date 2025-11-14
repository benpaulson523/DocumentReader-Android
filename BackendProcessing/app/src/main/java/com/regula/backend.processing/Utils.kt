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
    val adjectives = listOf(
        "quick", "bright", "silent", "brave", "calm", "eager", "gentle", "happy", "jolly", "kind", "lucky", "merry", "nice", "proud", "silly", "witty",
        "bold", "clever", "daring", "fierce", "graceful", "humble", "keen", "loyal", "noble", "playful", "quirky", "radiant", "serene", "spirited", "sturdy", "swift",
        "thoughtful", "vivid", "zesty", "charming", "diligent", "fearless", "glad", "honest", "inventive", "jovial", "modest", "optimistic", "patient", "resourceful", "sincere", "trusty"
    )
    val nouns = listOf(
        "lion", "tiger", "bear", "fox", "wolf", "eagle", "owl", "shark", "panda", "zebra", "koala", "otter", "rabbit", "deer", "falcon",
        "badger", "buffalo", "cheetah", "dolphin", "ferret", "giraffe", "jackal", "lemur", "lynx", "moose", "narwhal", "penguin", "raccoon",
        "seal", "swan", "toucan", "vulture", "yak", "antelope", "camel", "goat", "iguana", "jaguar", "kangaroo", "lemur", "mole"
    )
    val adj = adjectives.random()
    val noun = nouns.random()
    val uuid = java.util.UUID.randomUUID().toString().substring(0, 8)
    return "$adj-$noun-$uuid"
}
