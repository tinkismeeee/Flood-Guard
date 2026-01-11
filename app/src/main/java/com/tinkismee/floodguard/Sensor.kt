package com.tinkismee.floodguard

data class Sensor(
    val id: Int,
    val sensor_id: String,
    val water_level: Double?,
    val flow_rate: Double?,
    val lat: Double,
    val lng: Double,
    val warning_level: String,
    val recorded_at: String
)
