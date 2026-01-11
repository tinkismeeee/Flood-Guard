package com.tinkismee.floodguard

data class SOS(
    val user_id: Int,
    val type: String,
    val message: String,
    val lat: Double,
    val lng: Double
)