package com.example

import androidx.compose.ui.graphics.ColorMatrix

enum class FilterType(val displayName: String) {
    ORIGINAL("原图"),
    NOIR("复古黑白"),
    SEPIA("暖黄老相片"),
    COOL("冷调人像"),
    WARM("暖阳日光"),
    INVERT("奇幻反色"),
    CYBERPUNK("科幻霓虹"),
    VIVID("莫奈鲜艳");

    fun getComposeMatrix(): ColorMatrix {
        return when (this) {
            ORIGINAL -> ColorMatrix()
            NOIR -> ColorMatrix(floatArrayOf(
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0f,      0f,      0f,      1f, 0f
            ))
            SEPIA -> ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f
            ))
            COOL -> ColorMatrix(floatArrayOf(
                0.8f, 0f,   0f,   0f, -10f,
                0f,   0.95f, 0f,   0f, 0f,
                0f,   0f,   1.25f, 0f, 25f,
                0f,   0f,   0f,   1f, 0f
            ))
            WARM -> ColorMatrix(floatArrayOf(
                1.2f, 0f,   0f,   0f, 20f,
                0f,   1.1f, 0f,   0f, 10f,
                0f,   0f,   0.85f, 0f, -5f,
                0f,   0f,   0f,   1f, 0f
            ))
            INVERT -> ColorMatrix(floatArrayOf(
                -1f, 0f,  0f,  0f, 255f,
                0f,  -1f, 0f,  0f, 255f,
                0f,  0f,  -1f, 0f, 255f,
                0f,  0f,  0f,  1f, 0f
            ))
            CYBERPUNK -> ColorMatrix(floatArrayOf(
                1.1f, 0.1f, 0.3f, 0f, 30f,
                0.1f, 0.8f, 0.2f, 0f, -15f,
                0.3f, 0.1f, 1.4f, 0f, 40f,
                0f,   0f,   0f,   1f, 0f
            ))
            VIVID -> ColorMatrix(floatArrayOf(
                1.25f, 0f,   0f,   0f, 5f,
                0f,   1.25f, 0f,   0f, 5f,
                0f,   0f,   1.25f, 0f, 5f,
                0f,   0f,   0f,   1f, 0f
            ))
        }
    }

    fun getAndroidMatrixValues(): FloatArray {
        return when (this) {
            ORIGINAL -> floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            NOIR -> floatArrayOf(
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0f,      0f,      0f,      1f, 0f
            )
            SEPIA -> floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f
            )
            COOL -> floatArrayOf(
                0.8f, 0f,   0f,   0f, -10f,
                0f,   0.95f, 0f,   0f, 0f,
                0f,   0f,   1.25f, 0f, 25f,
                0f,   0f,   0f,   1f, 0f
            )
            WARM -> floatArrayOf(
                1.2f, 0f,   0f,   0f, 20f,
                0f,   1.1f, 0f,   0f, 10f,
                0f,   0f,   0.85f, 0f, -5f,
                0f,   0f,   0f,   1f, 0f
            )
            INVERT -> floatArrayOf(
                -1f, 0f,  0f,  0f, 255f,
                0f,  -1f, 0f,  0f, 255f,
                0f,  0f,  -1f, 0f, 255f,
                0f,  0f,  0f,  1f, 0f
            )
            CYBERPUNK -> floatArrayOf(
                1.1f, 0.1f, 0.3f, 0f, 30f,
                0.1f, 0.8f, 0.2f, 0f, -15f,
                0.3f, 0.1f, 1.4f, 0f, 40f,
                0f,   0f,   0f,   1f, 0f
            )
            VIVID -> floatArrayOf(
                1.25f, 0f,   0f,   0f, 5f,
                0f,   1.25f, 0f,   0f, 5f,
                0f,   0f,   1.25f, 0f, 5f,
                0f,   0f,   0f,   1f, 0f
            )
        }
    }
}
