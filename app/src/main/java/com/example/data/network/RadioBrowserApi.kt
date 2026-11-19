package com.example.data.network

import com.example.data.model.RadioStation
import retrofit2.http.GET
import retrofit2.http.Query

interface RadioBrowserApi {

    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String? = null,
        @Query("countrycode") countryCode: String? = null,
        @Query("tag") tag: String? = null,
        @Query("bitrateMax") bitrateMax: Int? = null,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("limit") limit: Int = 50
    ): List<RadioStation>

    @GET("json/stations/topvote")
    suspend fun getTopStations(
        @Query("limit") limit: Int = 40
    ): List<RadioStation>
}
