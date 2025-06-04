package org.videolan.resources.opensubtitles

import retrofit2.http.GET
import retrofit2.http.Query

//Passing 0 for numbers and "" for strings ignores that parameters
interface IOpenSubtitleService {
    @GET("search")
    suspend fun queryByName(@Query("token") token: String = "VJmwTuuEU5QR2dGQYxftQVElKnNygTj6",
                            @Query("q") q: String,
                            @Query("filelist") filelist: Int = 1,
                            @Query("cnt") cnt: Int = 15,
                            @Query("is_file") is_file: Int = 0,
                            @Query("no_muxer") no_muxer: Int = 1): AssrtResponse


    @GET("detail")
    suspend fun queryById(@Query("token") token: String = "VJmwTuuEU5QR2dGQYxftQVElKnNygTj6",
                          @Query("id") id: Int): AssrtResponse
}


