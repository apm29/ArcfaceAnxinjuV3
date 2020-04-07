package com.apm29.anxinju.model

import android.graphics.Rect

data class DrawInfo(
    var rect: Rect,
    var sex: Int,
    var age: Int,
    var liveness: Int,
    var color: Int,
    var name: String? = null
)