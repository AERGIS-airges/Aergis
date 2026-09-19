package com.airgesture.control

/** Navigation destinations shown in the Aergis activity bottom bar. */
enum class AergisTab(
    val label: String,
    val symbol: String
) {
    CONTROL("Control", "◉"),
    PRACTICE("Practice", "✦"),
    SETUP("Setup", "⚙"),
    SYSTEM("System", "⌁")
}
