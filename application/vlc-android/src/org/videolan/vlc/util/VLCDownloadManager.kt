package org.videolan.vlc.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import android.database.Cursor
import android.widget.Toast
import androidx.databinding.ObservableField
import kotlinx.coroutines.*
import org.json.JSONObject
import org.videolan.resources.AppContextProvider
import org.videolan.resources.opensubtitles.AssrtResponse
import org.videolan.resources.opensubtitles.OpenSubtitleRepository
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.SubtitleItem
import org.videolan.vlc.gui.helpers.hf.getExtWritePermission
import org.videolan.vlc.repository.ExternalSubRepository


object VLCDownloadManager: BroadcastReceiver(), LifecycleObserver {
    private val downloadManager = AppContextProvider.appContext.getSystemService<DownloadManager>()!!
    private var dlDeferred : CompletableDeferred<SubDlResult>? = null
    private lateinit var defaultSubsDirectory : String

    override fun onReceive(context: Context, intent: Intent?) {
        intent?.run {
            val id = getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L)
            ExternalSubRepository.getInstance(context).getDownloadingSubtitle(id)?.let { subtitleItem ->
                val (state, localUri) = getDownloadState(id)
                when(state) {
                    DownloadManager.STATUS_SUCCESSFUL -> dlDeferred?.complete(SubDlSuccess(id, subtitleItem, localUri))
                    DownloadManager.STATUS_FAILED -> dlDeferred?.complete(SubDlFailure(id))
                    else -> dlDeferred?.complete(SubDlFailure(id))
                }
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun register() {
        AppContextProvider.appContext.applicationContext.registerReceiver(this, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun unRegister() {
        ExternalSubRepository.getInstance(AppContextProvider.appContext).downloadingSubtitles.observeForever {
            it?.keys?.forEach {
                downloadManager.remove(it)
            }
        }

        AppContextProvider.appContext.applicationContext.unregisterReceiver(this)
    }

    suspend fun download(context: FragmentActivity, subtitleItem: SubtitleItem) {
        var resp: AssrtResponse? = null
        try {
            resp = OpenSubtitleRepository.getInstance().queryWithId(subtitleItem.idSubtitle)
        } catch (e: retrofit2.HttpException) {
            Log.e("VLCDownloadManager", "Error starting download: ${e.message}", e)
            try {
                val jBody = JSONObject(e.response()?.errorBody()?.string())
                if (jBody != null) {
                    val status = jBody.getInt("status")
                    when (status) {
                        20000 -> Toast.makeText(context, "请求缺少参数", Toast.LENGTH_SHORT).show()
                        20001 -> Toast.makeText(context, "Token 不存在", Toast.LENGTH_SHORT).show()
                        20400 -> Toast.makeText(context, "API 终结点不存在", Toast.LENGTH_SHORT).show()
                        20900 -> Toast.makeText(context, "字幕不存在", Toast.LENGTH_SHORT).show()
                        30000 -> Toast.makeText(context, "服务器抽风了", Toast.LENGTH_SHORT).show()
                        30001 -> Toast.makeText(context, "数据库挂了", Toast.LENGTH_SHORT).show()
                        30002 -> Toast.makeText(context, "搜索引擎挂了", Toast.LENGTH_SHORT).show()
                        30300 -> Toast.makeText(context, "站长代码少打了一个分号", Toast.LENGTH_SHORT).show()
                        30900 -> Toast.makeText(context, "配额超限了", Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(context, "未知错误", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (je: Exception) {
                Log.e("SubtitlesModel", "Error parsing error response", je)
                Toast.makeText(context, "解析 HTTP 响应错误", Toast.LENGTH_SHORT).show()
            }
            return downloadFailed(0, context)
        }

        if (resp.sub.subs?.isEmpty() == true) return downloadFailed(0, context)
        Log.i(
            "VLCDownloadManager",
            "Downloading subtitle ${subtitleItem.idSubtitle} for ${subtitleItem.mediaUri.path} (${subtitleItem.movieReleaseName})"
        )

        val request = DownloadManager.Request(resp.sub.subs!!.get(0).downloadUrl.toUri())
        Log.i("VLCDownloadManager", "Download URL: ${resp.sub.subs!!.get(0).downloadUrl}")
        request.setAllowedNetworkTypes(
            DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
        )
        request.setDescription(subtitleItem.movieReleaseName)
        request.setTitle(context.resources.getString(R.string.download_subtitle_title))
        request.setVisibleInDownloadsUi(false)
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            getDownloadPath(subtitleItem)
        )
        val id = downloadManager.enqueue(request)

        CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis()
            var isCompleted = false

            while (System.currentTimeMillis() - startTime < 10000) {
                delay(1000)

                val query = DownloadManager.Query().setFilterById(id)
                val cursor: Cursor? = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        isCompleted = true
                        break
                    }
                    cursor.close()
                }
            }

            if (!isCompleted) {
                downloadManager.remove(id)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "下载超时，已取消", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val deferred = CompletableDeferred<SubDlResult>().also { dlDeferred = it }
        ExternalSubRepository.getInstance(context.applicationContext).addDownloadingItem(id, subtitleItem)
        when (val result = deferred.await()) {
            is SubDlFailure -> downloadFailed(result.id, context)
            is SubDlSuccess -> downloadSuccessful(result.id, result.subtitleItem, result.localUri, context)
        }
    }

    private suspend fun downloadSuccessful(id:Long, subtitleItem: SubtitleItem, localUri: String, context: FragmentActivity) {
        Log.i("VLCDownloadManager", "Subtitle ${subtitleItem.idSubtitle} downloaded successfully to $localUri")

        val extractDirectory = getFinalDirectory(context, subtitleItem) ?: return

        Log.i("VLCDownloadManager", "Extracting subtitle to $extractDirectory")

        val uri = Uri.parse(localUri)
        if (uri == null || uri.path == null) {
            Log.e("VLCDownloadManager", "Invalid local URI: $localUri")
            downloadFailed(id, context)
            return
        }

        Toast.makeText(context, "下载成功，开始解压缩: " + uri.path, Toast.LENGTH_SHORT).show()

        try {
            val downloadedPaths = FileUtils.unpackZip(uri.path!!, extractDirectory)
            Log.i("VLCDownloadManager", "Downloaded paths: $downloadedPaths")
            Toast.makeText(context, "成功解压缩到文件夹: " + extractDirectory, Toast.LENGTH_SHORT).show()
            subtitleItem.run {
                ExternalSubRepository.getInstance(context).removeDownloadingItem(id)
                downloadedPaths.forEach {
                    Log.i("VLCDownloadManager", "Subtitle: $it")
                    if (it.endsWith(".srt") or it.endsWith(".ass"))
                        ExternalSubRepository.getInstance(context).saveDownloadedSubtitle(
                            idSubtitle,
                            it,
                            mediaUri.path!!,
                            subLanguageID,
                            movieReleaseName
                        )
                }
                withContext(Dispatchers.IO) { FileUtils.deleteFile(localUri) }
            }
        } catch (e: java.util.zip.ZipException) {
            Log.e("VLCDownloadManager", "Error extracting subtitle: ${e.message}", e)
            downloadFailed(id, context)
            Toast.makeText(context, "解压缩失败, ZIP 文件异常", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("VLCDownloadManager", "Error extracting subtitle: ${e.message}", e)
            downloadFailed(id, context)
            Toast.makeText(context, "解压缩失败", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun getFinalDirectory(context: FragmentActivity, subtitleItem: SubtitleItem) : String? {
        return (context.applicationContext.getExternalFilesDir(null))?.absolutePath ?: defaultSubsDirectory
//        if (!this::defaultSubsDirectory.isInitialized) defaultSubsDirectory = "${context.applicationContext.getExternalFilesDir(null)!!.absolutePath}/subtitles"
//        if (subtitleItem.mediaUri.scheme != "file") return defaultSubsDirectory
//        val folder = subtitleItem.mediaUri.path.getParentFolder() ?: return context.getExternalFilesDir("subs")?.absolutePath
//        val canWrite = context.isStarted() && context.getExtWritePermission(folder.toUri())
//        return if (canWrite) folder
//        else (context.applicationContext.getExternalFilesDir(null))?.absolutePath ?: defaultSubsDirectory
    }

    private fun downloadFailed(id: Long, context: Context) {
        ExternalSubRepository.getInstance(context).removeDownloadingItem(id)
    }

    private fun getDownloadPath(subtitleItem: SubtitleItem) = "VLC/${subtitleItem.movieReleaseName}_${subtitleItem.idSubtitle}.zip"

    private fun getDownloadState(downloadId: Long): Pair<Int, String> {
        val query = DownloadManager.Query()
        query.setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor == null || cursor.count == 0) {
            Log.e("VLCDownloadManager", "No download found for ID: $downloadId")
            return Pair(DownloadManager.STATUS_FAILED, "")
        }

        cursor.moveToFirst()
        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

        val status = if (statusIndex != -1)
            cursor.getInt(statusIndex)
        else DownloadManager.STATUS_FAILED

        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        val localUri = if (localUriIndex != -1)
            cursor.getString(localUriIndex)
        else ""

        return Pair(status, if (localUri != null) localUri.toUri().path!! else "")
    }
}

private sealed class SubDlResult
private class SubDlSuccess(val id:Long, val subtitleItem: SubtitleItem, val localUri: String) : SubDlResult()
private class SubDlFailure(val id:Long) : SubDlResult()
