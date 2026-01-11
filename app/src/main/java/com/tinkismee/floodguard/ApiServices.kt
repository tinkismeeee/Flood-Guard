package com.tinkismee.floodguard

import retrofit2.http.*
import retrofit2.Call

interface ApiServices {
    @POST("api/sos")
    fun createSOS(@Body sos: SOS): Call<SOS>
    @GET("api/sos")
    fun getReports(): Call<List<Report>>
    @GET("api/sensors")
    fun getSensors(): Call<List<Sensor>>
}