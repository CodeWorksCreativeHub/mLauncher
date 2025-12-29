package com.github.codeworkscreativehub.mlauncher.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Message(
    var text: String,
    var timestamp: String,
    var category: String,
    var priority: String
)

@JsonClass(generateAdapter = true)
data class MessageWrong(
    var a: String,
    var b: String,
    var c: String,
    var d: String
)

