package com.pocketpad.app.ui

/**
 * Face buttons are stored in the order bottom, right, left, top so the same
 * layout can carry Xbox letters or PlayStation shapes.
 */
enum class Skin(
    val accent: Int,
    val faceLabels: Array<String>,
    val faceColors: IntArray,
    val shoulderLabels: Array<String>,
    val triggerLabels: Array<String>,
    val centerLabels: Array<String>
) {
    XBOX(
        accent = 0xFF107C10.toInt(),
        faceLabels = arrayOf("A", "B", "X", "Y"),
        faceColors = intArrayOf(
            0xFF5DC21F.toInt(),
            0xFFE23B3B.toInt(),
            0xFF2F7ED8.toInt(),
            0xFFF2C230.toInt()
        ),
        shoulderLabels = arrayOf("LB", "RB"),
        triggerLabels = arrayOf("LT", "RT"),
        centerLabels = arrayOf("VIEW", "MENU", "XBOX")
    ),
    PLAYSTATION(
        accent = 0xFF0070D1.toInt(),
        faceLabels = arrayOf("✕", "○", "□", "△"),
        faceColors = intArrayOf(
            0xFF3A8DDE.toInt(),
            0xFFE0454B.toInt(),
            0xFFE86AA6.toInt(),
            0xFF37C26B.toInt()
        ),
        shoulderLabels = arrayOf("L1", "R1"),
        triggerLabels = arrayOf("L2", "R2"),
        centerLabels = arrayOf("SHARE", "OPTIONS", "PS")
    )
}
