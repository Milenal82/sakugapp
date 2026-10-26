// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sakugapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal data class SuggestionState(val active:Boolean=false,val loading:Boolean=false,
    val values:List<TagSuggestion> = emptyList(),val error:String?=null,val prefix:String?=null)

@Composable internal fun rememberSuggestions(value:TextFieldValue,focused:Boolean,source:SuggestionSource):SuggestionState {
    val context=LocalContext.current
    var state by remember{mutableStateOf(SuggestionState())}
    val currentPrefix=SearchEditing.prefix(value.text,value.selection.start)
    val valid=focused && value.composition==null && value.selection.collapsed && currentPrefix!=null
    LaunchedEffect(value.text,value.selection,value.composition,focused,source){
        val prefix=SearchEditing.prefix(value.text,value.selection.start)
        if(!focused || value.composition!=null || !value.selection.collapsed || prefix==null){state=SuggestionState();return@LaunchedEffect}
        state=SuggestionState(active=true,loading=true,prefix=prefix)
        delay(300)
        try{state=SuggestionState(active=true,values=source.suggestions(prefix),prefix=prefix)}
        catch(e:CancellationException){throw e}
        catch(e:Exception){state=SuggestionState(active=true,error=e.message ?: context.getString(R.string.ui_unable_to_load_tag_suggestions),prefix=prefix)}
    }
    return if(!valid)SuggestionState()else if(state.prefix==currentPrefix)state else SuggestionState(active=true,loading=true,prefix=currentPrefix)
}

@Composable internal fun TagSuggestionsMenu(state:SuggestionState,light:Boolean,select:(String)->Unit){
    if(!state.active)return
    Surface(tonalElevation=3.dp,shape=MaterialTheme.shapes.small){
        Column(Modifier.fillMaxWidth().heightIn(max=180.dp).verticalScroll(rememberScrollState()).padding(4.dp)){
            if(state.loading){LinearProgressIndicator(Modifier.fillMaxWidth());Text(text(R.string.ui_loading_suggestions),style=MaterialTheme.typography.bodySmall)}
            else if(state.error!=null)Text(translateMessage(state.error),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            else if(state.values.isEmpty())Text(text(R.string.ui_no_matching_tags_you_can_still_search_manually),style=MaterialTheme.typography.bodySmall)
            else state.values.forEach{tag->TextButton(onClick={select(tag.name)},modifier=Modifier.fillMaxWidth()){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(tag.name,modifier=Modifier.weight(1f),color=when(tag.category){TagCategory.ARTIST->artistColor(light);TagCategory.COPYRIGHT->copyrightColor(light);TagCategory.OTHER->if(light)Color(0xFF1E5AA8)else Color(0xFF90CAF9);else->MaterialTheme.colorScheme.onSurfaceVariant})
                    Text(when(tag.category){TagCategory.ARTIST->text(R.string.ui_artist);TagCategory.COPYRIGHT->text(R.string.ui_copyright);TagCategory.OTHER->text(R.string.ui_other);TagCategory.UNKNOWN->text(R.string.ui_unknown)},style=MaterialTheme.typography.labelSmall)
                }
            }}
        }
    }
}
