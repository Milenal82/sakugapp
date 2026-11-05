// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun rememberClipExport(post:Post,url:String,range:ABLoop,selecting:Boolean):@Composable ()->Unit{
    val context=LocalContext.current;val cache=LocalPageVideoCache.current;val scope=rememberCoroutineScope()
    val storage=remember{DownloadStorage(context.applicationContext)}
    var confirm by remember{mutableStateOf<ClipRange?>(null)};var mode by remember{mutableStateOf(ClipMode.COPY)}
    var busy by remember{mutableStateOf(false)};var job by remember{mutableStateOf<Job?>(null)};var message by remember{mutableStateOf<String?>(null)}
    val directory=rememberDirectoryAccess(storage,DownloadStorage.Kind.CLIP){message=context.getString(R.string.ui_directory_error)}
    fun start(selected:ClipRange,selectedMode:ClipMode){
        if(busy)return
        busy=true;confirm=null;message=null
        job=scope.launch{
            val folder=File(context.cacheDir,"clip-export/${java.util.UUID.randomUUID()}")
            try{
                val exporter=ClipExporter(context.applicationContext);val input=exporter.stage(url,cache,folder)
                val result=exporter.export(input,folder,selected,selectedMode)
                val filename=MediaFileNames.forClip(post,result.file.extension,selected,selectedMode)
                storage.saveClip(result.file,filename,result.mime)
                message=context.getString(R.string.ui_clip_saved,formatClipTime(result.actualStartUs/1000),formatClipTime(result.actualEndUs/1000),formatClipTime((result.actualEndUs-result.actualStartUs)/1000))
            }catch(e:CancellationException){message=context.getString(R.string.ui_download_cancelled);throw e}
            catch(e:Exception){message=when((e as? LibraryFailure)?.code ?: e.message){"clip_space"->context.getString(R.string.ui_storage_full);"clip_verify"->context.getString(R.string.ui_clip_verify);"clip_unsupported"->context.getString(R.string.ui_clip_unsupported);else->libraryError(context,e)}}
            finally{withContext(NonCancellable+Dispatchers.IO){folder.deleteRecursively()};busy=false}
        }
    }
    return {
    TextButton(enabled=range.valid && !selecting && !busy,onClick={confirm=ClipRange(range.aMs!!,range.bMs!!)},modifier=Modifier.testTag("export-clip")){Text(text(R.string.ui_export_clip))}
    if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={job?.cancel()}){Text(text(R.string.ui_cancel_download))}}
    message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
    confirm?.let{selected->AlertDialog(onDismissRequest={confirm=null},title={Text(text(R.string.ui_export_clip))},text={Column{
        Text(text(R.string.ui_clip_selection,formatClipTime(selected.startMs),formatClipTime(selected.endMs),formatClipTime(selected.durationMs)))
        FlowRow{for(value in ClipMode.entries)FilterChip(selected=mode==value,onClick={mode=value},label={Text(text(if(value==ClipMode.COPY)R.string.ui_clip_copy else R.string.ui_clip_precise))})}
        Text(text(if(mode==ClipMode.COPY)R.string.ui_clip_copy_warning else R.string.ui_clip_precise_warning),style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={val chosen=mode;confirm=null;directory{start(selected,chosen)}},modifier=Modifier.testTag("confirm-clip")){Text(text(R.string.ui_download_video))}},dismissButton={TextButton(onClick={confirm=null}){Text(text(R.string.ui_cancel))}})}
    }
}
internal fun formatClipTime(ms:Long)="%d:%02d.%03d".format(java.util.Locale.ROOT,ms/60000,ms/1000%60,ms%1000)
