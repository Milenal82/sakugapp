// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*

internal data class TagPresentation(val enabled:Boolean=false,val curated:Map<String,String> = emptyMap(),val custom:Map<String,String> = emptyMap(),val store:LocalLibraryStore?=null)
internal val LocalTagPresentation=staticCompositionLocalOf{TagPresentation()}
internal fun libraryError(context:android.content.Context,error:Exception)=context.getString(when(((error as? LibraryFailure) ?: (error.cause as? LibraryFailure))?.code){
    "invalid_backup"->R.string.ui_invalid_backup;"folder_name"->R.string.ui_folder_name_error;"library_limit"->R.string.ui_library_limit;"media_limit"->R.string.ui_media_limit;else->R.string.ui_library_failed
})

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun TranslatedTag(tag:Tag,color:Color,search:(String)->Unit,modifier:Modifier=Modifier,fontSize:TextUnit=14.sp,maxLines:Int=Int.MAX_VALUE,dense:Boolean=false){
    val presentation=LocalTagPresentation.current
    val translated=TagDictionary.resolve(tag.name,presentation.curated,presentation.custom)
    var editor by remember(tag.name){mutableStateOf(false)}
    Column(modifier.combinedClickable(onClick={search(tag.name)},onLongClick={editor=true},onLongClickLabel=text(R.string.ui_translation_editor)).padding(vertical=3.dp)){
        Text(tag.name,color=color,fontSize=fontSize,maxLines=maxLines,overflow=TextOverflow.Ellipsis)
        if(presentation.enabled && !dense && translated!=null)Text(translated,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=(fontSize.value-1f).coerceAtLeast(10f).sp,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
    if(editor)TagEditor(tag.name,translated,search){editor=false}
}
@Composable private fun TagEditor(original:String,current:String?,search:(String)->Unit,close:()->Unit){
    val context=LocalContext.current;val clipboard=LocalClipboardManager.current;val scope=rememberCoroutineScope()
    val presentation=LocalTagPresentation.current
    var custom by remember(original){mutableStateOf(presentation.custom[original] ?: current.orEmpty())}
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=close,title={Text(original)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(current ?: text(R.string.ui_untranslated))
        TextButton(onClick={clipboard.setText(AnnotatedString(original));close()}){Text(text(R.string.ui_copy_original_tag))}
        TextButton(onClick={search(original);close()}){Text(text(R.string.ui_search_original_tag))}
        if(presentation.store!=null){
            OutlinedTextField(custom,{custom=it.take(300)},label={Text(text(R.string.ui_custom_translation))})
            TextButton(onClick={scope.launch{try{presentation.store.translation(original,null);close()}catch(e:CancellationException){throw e}catch(e:Exception){error=libraryError(context,e)}}}){Text(text(R.string.ui_remove_translation))}
        }
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={if(presentation.store!=null)TextButton(enabled=custom.isNotBlank(),onClick={scope.launch{try{presentation.store.translation(original,custom);close()}catch(e:CancellationException){throw e}catch(e:Exception){error=libraryError(context,e)}}}){Text(text(R.string.ui_save_translation))}},dismissButton={TextButton(onClick=close){Text(text(R.string.ui_close))}})
}
