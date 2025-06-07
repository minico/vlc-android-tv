package org.videolan.vlc.viewmodels

import android.content.Context
import android.net.Uri
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.widget.Toast
import androidx.databinding.Observable
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.*
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.videolan.resources.opensubtitles.AssrtResponse
import org.videolan.resources.util.NoConnectivityException
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.resources.opensubtitles.OpenSubtitle
import org.videolan.vlc.gui.dialogs.State
import org.videolan.vlc.gui.dialogs.SubtitleItem
import org.videolan.vlc.repository.ExternalSubRepository
import org.videolan.resources.opensubtitles.OpenSubtitleRepository
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import retrofit2.HttpException
import java.util.*

private const val LAST_USED_LANGUAGES = "last_used_subtitles"

class SubtitlesModel(private val context: Context, private val mediaUri: Uri, private val name:String, val coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()) : ViewModel() {
    val observableSearchName = ObservableField<String>()
    val observableSearchEpisode = ObservableField<String>()
    val observableSearchSeason = ObservableField<String>()
    val observableSearchLanguage = ObservableField<List<String>>()
    private var previousSearchLanguage: List<String>? = null
    val manualSearchEnabled = ObservableBoolean(false)
    val title = name

    val isApiLoading: MediatorLiveData<Boolean> = MediatorLiveData()
    val observableMessage = ObservableField<String>()
    val observableResultDescription = ObservableField<Spanned>()

    private val apiResultLiveData: MutableLiveData<List<OpenSubtitle>> = MutableLiveData()
    private val downloadedLiveData = Transformations.map(ExternalSubRepository.getInstance(context).getDownloadedSubtitles(mediaUri)) { list ->
        list.map { SubtitleItem(it.idSubtitle, mediaUri, it.subLanguageID, it.movieReleaseName, State.Downloaded, "") }
    }

    private val downloadingLiveData = ExternalSubRepository.getInstance(context).downloadingSubtitles

    val result: MediatorLiveData<List<SubtitleItem>> = MediatorLiveData()
    val history: MediatorLiveData<List<SubtitleItem>> = MediatorLiveData()

    private var searchJob: Job? = null
    init {
        observableSearchLanguage.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                if (observableSearchLanguage.get() != previousSearchLanguage) {
                    previousSearchLanguage = observableSearchLanguage.get()
                    saveLastUsedLanguage(observableSearchLanguage.get() ?: listOf())
                    search(!manualSearchEnabled.get())
                }
            }
        })

        history.apply {
            addSource(downloadedLiveData) {
                viewModelScope.launch {
                    value = merge(it, downloadingLiveData.value?.values?.filter { it.mediaUri == mediaUri })
                }
            }

            addSource(downloadingLiveData) {
                viewModelScope.launch {
                    value = merge(downloadedLiveData.value, it?.values?.filter { it.mediaUri == mediaUri })
                }
            }
        }

        result.apply {
            addSource(apiResultLiveData) {
                viewModelScope.launch {
                    value = updateListState(it, history.value)
                }

            }

            addSource(history) {
                viewModelScope.launch {
                    value = updateListState(apiResultLiveData.value, it)
                }
            }
        }
    }

    private suspend fun merge(downloadedResult: List<SubtitleItem>?, downloadingResult: List<SubtitleItem>?): List<SubtitleItem> = withContext(coroutineContextProvider.Default) {
        downloadedResult.orEmpty() + downloadingResult?.toList().orEmpty()
    }

    private suspend fun updateListState(apiResultLiveData: List<OpenSubtitle>?, history: List<SubtitleItem>?): MutableList<SubtitleItem> = withContext(coroutineContextProvider.Default) {
        val list = mutableListOf<SubtitleItem>()
        apiResultLiveData?.forEach { openSubtitle ->
            val exist = history?.find { it.idSubtitle == openSubtitle.id.toString() }
            val state = exist?.state ?: State.NotDownloaded
            if (!openSubtitle.nativeName.isEmpty()) {
                list.add(
                    SubtitleItem(
                        openSubtitle.id.toString(),
                        mediaUri,
                        openSubtitle.lang?.desc ?: "未知",
                        openSubtitle.nativeName,
                        state,
                        ""
                    )
                )
            }
        }
        list
    }

    fun processOriginalName(input: String): String {
        val noSufix = input.removeSuffix(".mp4").removeSuffix(".mkv").removeSuffix(".avi")
                        .removeSuffix(".ts").removeSuffix(".webm").removeSuffix(".flv")
        val regex = Regex("\\d+")
        val matches = regex.findAll(noSufix)
        for (match in matches) {
            val prefix = noSufix.substring(0, match.range.first)
            if (prefix.length >= 3) {
                return prefix.replace('.', ' ')
            }
        }
        return noSufix.replace('.', ' ')
    }

    private suspend fun getSubtitleByName(name: String): AssrtResponse {
        Log.i(this::class.java.simpleName, "Getting subs by name with $name")
        val splitedName = processOriginalName(name)
        val builder = StringBuilder(context.getString(R.string.sub_result_by_name, "<i>$splitedName</i>"))
        observableResultDescription.set(Html.fromHtml(builder.toString()))
        manualSearchEnabled.set(true)
        return OpenSubtitleRepository.getInstance().queryWithName(splitedName)
    }

    fun onRefresh() {
        if (manualSearchEnabled.get() && observableSearchName.get().isNullOrEmpty()) {
            isApiLoading.postValue(false)
            return
        }

        search(!manualSearchEnabled.get())
    }

    fun search(byFile: Boolean) {
        searchJob?.cancel()
        isApiLoading.postValue(true)
        observableMessage.set("")
        apiResultLiveData.postValue(listOf())

        searchJob = viewModelScope.launch {
            try {
                var resp = if (byFile) {
                    withContext(coroutineContextProvider.IO) {
                        getSubtitleByName(name)
                    }
                } else {
                    observableSearchName.get()?.let {
                        getSubtitleByName(it)
                    } ?: null
                }

                if (resp != null) {
                    when (resp.status ) {
                        0 -> if (isActive) apiResultLiveData.postValue(resp?.sub?.subs)
                        1 -> observableMessage.set("用户不存在")
                        101 -> observableMessage.set("搜索关键字长度必须大于3")
                    }
                } else {
                    observableMessage.set(context.getString(R.string.no_result))
                    Log.e("SubtitlesModel", "No subtitles found for $name")
                }
            } catch(e: JsonDataException) {
                Log.e("SubtitlesModel", "Error parsing response", e)
                observableMessage.set("Json 解析错误，可能字幕列表为空")
            } catch (e: HttpException) {
                try {
                    val jBody = JSONObject(e.response()?.errorBody()?.string())
                    if (jBody != null) {
                        val status = jBody.getInt("status")
                        when (status) {
                            20000 -> observableMessage.set("请求缺少参数")
                            20001 -> observableMessage.set("Token 不存在")
                            20400 -> observableMessage.set("API 终结点不存在")
                            20900 -> observableMessage.set("字幕不存在")
                            30000 -> observableMessage.set("服务器抽风了")
                            30001 -> observableMessage.set("数据库挂了")
                            30002 -> observableMessage.set("搜索引擎挂了")
                            30300 -> observableMessage.set("站长代码少打了一个分号")
                            30900 -> observableMessage.set("配额超限了")
                            else -> observableMessage.set("未知错误")
                        }
                    }
                } catch (je: Exception) {
                    Log.e("SubtitlesModel", "Error parsing error response", je)
                    observableMessage.set(context.getString(R.string.subs_download_error))
                }
            } catch (e: Exception) {
                Log.e("SubtitlesModel", e.message, e)
                observableMessage.set(e.message)
                if (e is NoConnectivityException)
                    observableMessage.set(context.getString(R.string.no_internet_connection))
                else
                    observableMessage.set(context.getString(R.string.subs_download_error))
            } finally {
                isApiLoading.postValue(false)
            }
        }
    }

    fun deleteSubtitle(mediaPath: String, idSubtitle: String) {
        ExternalSubRepository.getInstance(context).deleteSubtitle(mediaPath, idSubtitle)
    }

    fun getLastUsedLanguage(): List<String> {
        val language = try {
            Locale.getDefault().isO3Language
        } catch (e: MissingResourceException) {
            "eng"
        }
        return Settings.getInstance(context).getStringSet(LAST_USED_LANGUAGES, setOf(language))?.map { it.getCompliantLanguageID() } ?: emptyList()
    }

    fun saveLastUsedLanguage(lastUsedLanguages: List<String>) = Settings.getInstance(context).putSingle(LAST_USED_LANGUAGES, lastUsedLanguages)

    class Factory(private val context: Context, private val mediaUri: Uri, private val name: String) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SubtitlesModel(context.applicationContext, mediaUri, name) as T
        }
    }

    // Locale ID Control, because of OpenSubtitle support of ISO639-2 codes
    // e.g. French ID can be 'fra' or 'fre', OpenSubtitles considers 'fre' but Android Java Locale provides 'fra'
    private fun String.getCompliantLanguageID() = when (this) {
        "fra" -> "fre"
        "deu" -> "ger"
        "zho" -> "chi"
        "ces" -> "cze"
        "fas" -> "per"
        "nld" -> "dut"
        "ron" -> "rum"
        "slk" -> "slo"
        else -> this
    }
}
