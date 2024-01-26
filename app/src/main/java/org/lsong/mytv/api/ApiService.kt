package org.lsong.mytv.api

import org.lsong.mytv.Category
import org.lsong.mytv.Channel
import retrofit2.Call
import retrofit2.http.GET

data class MyResponse (
    val categories: List<Category>,
    val channels: List<Channel>,
)

interface ApiService {
    @GET("live.json")
    fun getChannels(): Call<MyResponse>
}