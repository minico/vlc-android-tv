package org.videolan.resources.opensubtitles
import com.squareup.moshi.Json

data class AssrtSub(
        @field:Json(name = "subs") val subs: List<OpenSubtitle>,
        @field:Json(name = "action") val action: String,
        @field:Json(name = "keyword") val keyword: String,
        @field:Json(name = "result") val result: String
)

data class AssrtResponse(
        @field:Json(name = "status") val status: Int,
        @field:Json(name = "sub") val sub: AssrtSub
)

data class SubLangList(
        @field:Json(name = "langdou") val langdou: Boolean, //是否支持多语言字幕
        @field:Json(name = "langchs") val langchs: Boolean, //是否支持中文字幕
        @field:Json(name = "langeng") val langeng: Boolean //是否支持英文字幕
)

data class SubLang(
        @field:Json(name = "langlist") val langList: SubLangList, //字幕语言列表
        @field:Json(name = "desc") val desc: String, //字幕语言描述
)

data class SubFileList(
        @field:Json(name = "s") val fileSize: Int, //文件大小
        @field:Json(name = "f") val fileName: String, //文件名
        @field:Json(name = "url") val fileUrl: String, //文件地址
)

data class SubProducer(
        @field:Json(name = "uploader") val uploader: String, //上传者
        @field:Json(name = "verifier") val verifier: String, //校订者
        @field:Json(name = "producer") val producer: String, //制作者
        @field:Json(name = "source") val source: String, //字幕来源
)

data class OpenSubtitle(
        @field:Json(name = "id") val id: Int, //字幕ID
        @field:Json(name = "native_name") val nativeName: String, //影片原始名称
        @field:Json(name = "revision") val revision: Int, //字幕的修订版本ID，如不存在则为0
        @field:Json(name = "upload_time") val uploadTime: String, //上传时间
        @field:Json(name = "subtype") val subType: String, //字幕格式
        @field:Json(name = "vote_score") val voteScore: Int, //用户评分，如果没有人评分则为0
        @field:Json(name = "release_site") val releaseSite: String, //发行的字幕组名称 (可选)
        @field:Json(name = "videoname") val videoName: String, //字幕匹配的视频文件名 (可选)
        @field:Json(name = "vote_machine_translate") val voteMachineTranslate: String, //用户评价此字幕为机器翻译字幕 (可选)
        @field:Json(name = "lang") val lang: SubLang?, //字幕语言 (可选)

        //Details by subtitle id
//        @field:Json(name = "filename") val fileName: String, //字幕文件名
//        @field:Json(name = "size") val fileSize: Int, //字幕文件大小
        @field:Json(name = "url") val downloadUrl: String, //字幕下载地址
//        @field:Json(name = "view_count ") val viewCount : Int, //字幕浏览次数
//        @field:Json(name = "down_count") val downloadCount: Int, //字幕下载次数
//        @field:Json(name = "title") val title: String, //字幕标题
//        @field:Json(name = "filelist") val fileList: SubFileList, //字幕压缩包内含的文件列表 (可选)
//        @field:Json(name = "producer") val producer: SubProducer, //发布人 (可选)
)

data class QueryParameters(
        @field:Json(name = "query") val query: String,
        @field:Json(name = "episode") val episode: String,
        @field:Json(name = "season") val season: String
)

