package org.videolan.resources.opensubtitles

import android.util.Log

class OpenSubtitleRepository(private val openSubtitleService: IOpenSubtitleService) {
    val TAG = "OpenSubtitleRepository"

    var queryCount = 0
    var lastQueryTime = 0L

    suspend fun checkQuta(): Boolean {
        val threshold = 10 * 1000; // 10 seconds
        if (queryCount >=4) {
            Log.w(TAG, "Throttling OpenSubtitle queries: $queryCount in last $threshold ms")
            return false
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastQueryTime > threshold) {
                queryCount -= ((currentTime - lastQueryTime) / threshold).toInt()
                if(queryCount < 0) {
                    queryCount = 0
                }
                Log.i(TAG, "Resetting OpenSubtitle query count to $queryCount")
            }
            queryCount++
            lastQueryTime = currentTime
            return true
        }
    }

    /*
    Multiple query functions are created based on below rules:
        1) Tags are valid only with imdbid
        2) We should use moviehash and moviebytesize together
        3) precedence: (movieBytesize and moviehash) > imdbid > name
    */

//    suspend fun queryWithImdbid(imdbId: Int, tag: String?, episode: Int? , season: Int?, languageId: String? ): List<OpenSubtitle> {
//        val actualEpisode = episode ?: 0
//        val actualSeason = season ?: 0
//        val actualLanguageId = languageId ?: ""
//        val actualTag = tag ?: ""
//        return openSubtitleService.query(
//                imdbId = String.format("%07d", imdbId),
//                tag = actualTag,
//                episode = actualEpisode,
//                season = actualSeason,
//                languageId = actualLanguageId)
//    }
//
//    suspend fun queryWithHash(movieByteSize: Long, movieHash: String, languageId: String?): List<OpenSubtitle> {
//        val actualLanguageId = languageId ?: ""
//        return openSubtitleService.query(
//                movieByteSize = movieByteSize.toString(),
//                movieHash = movieHash,
//                languageId = actualLanguageId)
//    }

    suspend fun queryWithName(name: String, episode: Int?, season: Int?, languageId: String?): AssrtResponse? {
        if(checkQuta()) {
            Log.i(TAG, "Proceeding with OpenSubtitle query for name: $name")
            val actualEpisode = episode ?: 0
            val actualSeason = season ?: 0
            val actualLanguageId = languageId ?: ""
            return openSubtitleService.queryByName(q = name)
        } else {
            Log.w(TAG, "Throttling OpenSubtitle query for name: $name")
            return null
        }
    }

//    suspend fun queryWithImdbid(imdbId: Int, tag: String?, episode: Int? , season: Int?, languageIds: List<String>? ): List<OpenSubtitle> {
//        val actualEpisode = episode ?: 0
//        val actualSeason = season ?: 0
//        val actualLanguageIds = languageIds?.toSet()?.run { if (contains("") || isEmpty()) setOf("") else this } ?: setOf("")
//        val actualTag = tag ?: ""
//        return actualLanguageIds.flatMap {
//            openSubtitleService.query(
//                    imdbId = String.format("%07d", imdbId),
//                    tag = actualTag,
//                    episode = actualEpisode,
//                    season = actualSeason,
//                    languageId = it) }
//    }
//
//    suspend fun queryWithHash(movieByteSize: Long, movieHash: String?, languageIds: List<String>?): List<OpenSubtitle> {
//        val actualLanguageIds = languageIds?.toSet()?.run { if (contains("") || isEmpty()) setOf("") else this } ?: setOf("")
//        return actualLanguageIds.flatMap {
//            openSubtitleService.query(
//                    movieByteSize = movieByteSize.toString(),
//                    movieHash = movieHash ?: "",
//                    languageId = it)
//        }
//    }

    suspend fun queryWithName(name: String): AssrtResponse? {
        if(!checkQuta()) {
            Log.w(TAG, "Throttling OpenSubtitle query for name: $name")
            return null
        }
        return openSubtitleService.queryByName(q = name)
    }

    suspend fun queryWithId(id: String): AssrtResponse? {
        if(!checkQuta()) {
            Log.w(TAG, "Throttling OpenSubtitle query for id: $id")
            return null
        }
        return openSubtitleService.queryById(id = id.toInt())
    }

    companion object {
        // To ensure the instance can be overridden in tests.
        var instance = lazy { OpenSubtitleRepository(OpenSubtitleClient.instance) }
        fun getInstance() = instance.value
    }
}
