package com.pocketpad.app.ui

enum class FaceShape {
    CIRCLE, CROSS, SQUARE, TRIANGLE
}

/**
 * Face buttons are stored in the order bottom, right, left, top so the same
 * layout can carry Xbox letters or PlayStation shapes.
 */
enum class Skin(
    val accent: Int,
    val faceLabels: Array<String>,
    val faceColors: IntArray,
    val faceShapes: Array<FaceShape>,
    val shoulderLabels: Array<String>,
    val triggerLabels: Array<String>,
    val centerLabels: Array<String>,
    val bodyTop: Int,
    val bodyBottom: Int,
    val surface: Int
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
        faceShapes = arrayOf(
            FaceShape.CIRCLE,
            FaceShape.CIRCLE,
            FaceShape.CIRCLE,
            FaceShape.CIRCLE
        ),
        shoulderLabels = arrayOf("LB", "RB"),
        triggerLabels = arrayOf("LT", "RT"),
        centerLabels = arrayOf("VIEW", "MENU", "XBOX"),
        bodyTop = 0xFF18231D.toInt(),
        bodyBottom = 0xFF060907.toInt(),
        surface = 0xFF22352A.toInt()
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
        faceShapes = arrayOf(
            FaceShape.CROSS,
            FaceShape.CIRCLE,
            FaceShape.SQUARE,
            FaceShape.TRIANGLE
        ),
        shoulderLabels = arrayOf("L1", "R1"),
        triggerLabels = arrayOf("L2", "R2"),
        centerLabels = arrayOf("SHARE", "OPTIONS", "PS"),
        bodyTop = 0xFF17283B.toInt(),
        bodyBottom = 0xFF070A12.toInt(),
        surface = 0xFF203A59.toInt()
    )
}
