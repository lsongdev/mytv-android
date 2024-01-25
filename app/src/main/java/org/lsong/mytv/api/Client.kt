package org.lsong.mytv.api

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface ChannelCallback {
    fun onSuccess(response: MyResponse)
    fun onError(error: String)
}

object RetrofitClient {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://lsong.org/iptv/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    public fun getChannels(callback: ChannelCallback) {
        RetrofitClient.apiService.getChannels().enqueue(object : Callback<MyResponse> {
            override fun onResponse(call: Call<MyResponse>, response: Response<MyResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        Log.d("MainActivity", "Response: ${it.toString()}")
                        callback.onSuccess(it)
                    }
                } else {
                    Log.e("MainActivity", "Error Response: ${response.errorBody()?.string()}")
                    callback.onError(response.errorBody()?.string() ?: "Unknown error")
                }
            }
            override fun onFailure(call: Call<MyResponse>, t: Throwable) {
                Log.e("MainActivity", "Network Request Failed", t)
                callback.onError(t.message ?: "Network request failed")
            }
        })
    }
}

