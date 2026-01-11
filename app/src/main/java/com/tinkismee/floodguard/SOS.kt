package com.tinkismee.floodguard

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
@Serializable
data class SOS(
    @SerialName("userId")
    val type: String,
    val message: String,
    val lat: Double,
    val long: Double
)
