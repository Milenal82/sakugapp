// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sakugapp.R
import com.sakugapp.data.DownloadStorage
import kotlinx.coroutines.*
import java.io.File

@Composable internal fun rememberDownloadAccess(storage:DownloadStorage,denied:()->Unit):(DownloadStorage.Kind,()->Unit)->Unit{
    val context=LocalContext.current;var pending by remember{mutableStateOf<(() -> Unit)?>(null)}
    val latestDenied by rememberUpdatedState(denied)
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed->val action=pending;pending=null;if(allowed)action?.invoke()else latestDenied()}
    return {kind,action->if(storage.needsLegacyPermission(kind) && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){pending=action;permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)}else action()}
}

@Composable internal fun DownloadDirectories(model:AppViewModel){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val storage=model.downloadStorage
    var revision by remember{mutableIntStateOf(0)};var selecting by rememberSaveable{mutableStateOf(DownloadStorage.Kind.SINGLE)}
    var message by remember{mutableStateOf<String?>(null)};var job by remember{mutableStateOf<Job?>(null)};var busy by remember{mutableStateOf(false)}
    val access=rememberDirectoryAccess(storage,DownloadStorage.Kind.BATCH){message=context.getString(R.string.ui_directory_error)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null)scope.launch{try{withContext(Dispatchers.IO){storage.choose(selecting,uri)};revision++}catch(e:Exception){message=context.getString(R.string.ui_directory_error)}}}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        for(kind in DownloadStorage.Kind.entries){
            Text(text(when(kind){DownloadStorage.Kind.SINGLE->R.string.ui_single_directory;DownloadStorage.Kind.BATCH->R.string.ui_batch_directory;DownloadStorage.Kind.CLIP->R.string.ui_clip_directory}),style=MaterialTheme.typography.bodyLarge)
            val uri=remember(kind,revision){storage.directory(kind)}
            Text(uri?.let{runCatching{android.provider.DocumentsContract.getTreeDocumentId(it)}.getOrDefault(it.toString())} ?: text(R.string.ui_choose_directory),style=MaterialTheme.typography.bodySmall)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(enabled=!busy,onClick={selecting=kind;picker.launch(uri)}){Text(text(R.string.ui_change_directory))}
                TextButton(enabled=uri!=null && !busy,onClick={scope.launch{withContext(Dispatchers.IO){storage.reset(kind)};revision++}}){Text(text(R.string.ui_reset_directory))}
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        }
        Text(text(R.string.ui_legacy_export_hint),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=!busy,onClick={access{busy=true;job=scope.launch{
            var done=0;var failed=0;var lastFailure:String?=null
            try{val files=withContext(Dispatchers.IO){File(context.filesDir,"favorites-media").listFiles().orEmpty().filter{it.isFile && !it.name.startsWith(".") && it.extension in setOf("mp4","webm","mkv")}}
                for(file in files){try{if(storage.exportLegacy(file))done++}catch(e:CancellationException){throw e}catch(e:Exception){failed++;lastFailure=libraryError(context,e)}}
                message=context.getString(R.string.ui_batch_result,done,failed)+(lastFailure?.let{"\n$it"} ?: "")
            }catch(e:CancellationException){message=context.getString(R.string.ui_download_cancelled);throw e}finally{busy=false}
        }}}){Text(text(R.string.ui_export_legacy))}
        if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={job?.cancel()}){Text(text(R.string.ui_cancel_download))}}
        message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable internal fun rememberDirectoryAccess(storage:DownloadStorage,kind:DownloadStorage.Kind,error:()->Unit):(() -> Unit)->Unit{
    val scope=rememberCoroutineScope();var pending by remember{mutableStateOf<(() -> Unit)?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
        val action=pending;pending=null
        if(uri!=null)scope.launch{try{withContext(Dispatchers.IO){storage.choose(kind,uri)};action?.invoke()}catch(e:CancellationException){throw e}catch(e:Exception){error()}}
    }
    return {action->if(storage.directory(kind)==null){pending=action;picker.launch(null)}else action()}
}
