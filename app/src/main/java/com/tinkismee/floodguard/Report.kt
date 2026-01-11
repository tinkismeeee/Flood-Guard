package com.tinkismee.floodguard

data class Report(
    val id: Int,
    val user_id: Int?,
    val type: String,
    val message: String?,
    val lat: Double,
    val lng: Double,
    val status: String?,
    val created_at: String?
)
