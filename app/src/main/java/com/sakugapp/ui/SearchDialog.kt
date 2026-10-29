// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakugapp.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SearchDialog(query:String,source:SuggestionSource,light:Boolean,apply:(String)->Unit,close:()->Unit,history:List<String> = emptyList()) {
    val initial=remember(query){SearchSyntax.draft(query)}
    var expression by rememberSaveable(stateSaver=TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial.expression,TextRange(initial.expression.length))) }
    var name by rememberSaveable(stateSaver=TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var mode by rememberSaveable { mutableStateOf("and") }
    var order by rememberSaveable { mutableStateOf(initial.order) }
    var rating by rememberSaveable { mutableStateOf(initial.rating) }
    var historyOpen by rememberSaveable{mutableStateOf(false)}
    var guide by remember { mutableStateOf(false) }
    var expressionFocused by remember{mutableStateOf(false)}
    var nameFocused by remember{mutableStateOf(false)}
    val expressionSuggestions=rememberSuggestions(expression,expressionFocused,source)
    val nameSuggestions=rememberSuggestions(name,nameFocused,source)
    fun edit(value:TextFieldValue){
        expression=value
        val draft=SearchSyntax.draft(value.text);order=draft.order;rating=draft.rating
    }
    fun load(value:String){edit(TextFieldValue(value,TextRange(value.length)));name=TextFieldValue("");mode="and"}
    fun complete(value:TextFieldValue,tag:String):TextFieldValue {
        if(value.composition!=null || !value.selection.collapsed)return value
        val edit=SearchEditing.complete(value.text,value.selection.start,tag);return TextFieldValue(edit.text,TextRange(edit.cursor))
    }
    val finalQuery=SearchSyntax.filters(expression.text,order,rating)
    AlertDialog(onDismissRequest=close,title={Text(text(R.string.ui_search_tools))},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                SearchToolTile(R.drawable.ic_edit,text(R.string.ui_compose_query),onClick={guide=false},selected=!guide,modifier=Modifier.testTag("edit-query-tool"))
                SearchToolTile(R.drawable.ic_book,text(R.string.ui_search_reference_language),onClick={guide=true},selected=guide,modifier=Modifier.testTag("search-reference-tool"))
                SearchToolTile(R.drawable.ic_history,text(R.string.ui_search_history),onClick={val shown=historyOpen && !guide;guide=false;historyOpen=!shown},selected=historyOpen && !guide,modifier=Modifier.testTag("combined-history-toggle"))
            }
            if(guide) {
                Text(text(R.string.ui_examples_replace_the_draft_apply_runs_the_search_fields_and_opera))
                for(example in SearchGuide.examples) {
                    TextButton(onClick={load(example.query);guide=false}) { Text(example.query) }
                    Text(text(example.description),style=MaterialTheme.typography.bodySmall)
                }
                Text(text(R.string.ui_wildcard_and_mixed_or_combinations_have_website_limitations_repea))
            } else {
                AnimatedVisibility(historyOpen,enter=expandVertically(tween(Motion.history)),exit=shrinkVertically(tween(Motion.history))){Column(Modifier.testTag("combined-history")){
                    if(history.isEmpty())Text(text(R.string.ui_no_saved_searches),style=MaterialTheme.typography.bodySmall)
                    history.forEachIndexed{index,item->TextButton(onClick={
                        load(item);historyOpen=false
                    },modifier=Modifier.fillMaxWidth().testTag("combined-history-$index")){Text(item,maxLines=2,overflow=TextOverflow.Ellipsis)}}
                }}
                OutlinedTextField(expression,::edit,label={Text(text(R.string.ui_query))},modifier=Modifier.fillMaxWidth().testTag("query-expression").onFocusChanged{expressionFocused=it.isFocused},minLines=2)
                TagSuggestionsMenu(expressionSuggestions,light){edit(complete(expression,it))}
                Text(text(R.string.ui_score_shortcuts))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for(preset in SearchSyntax.scorePresets)FilterChip(selected=preset in SearchSyntax.tokens(expression.text),onClick={edit(SearchSyntax.score(expression.text,preset).let{TextFieldValue(it,TextRange(it.length))})},label={Text(preset)})
                }
                OutlinedTextField(name,{name=it},label={Text(text(R.string.ui_tag_or_field_condition))},modifier=Modifier.fillMaxWidth().onFocusChanged{nameFocused=it.isFocused},singleLine=true)
                TagSuggestionsMenu(nameSuggestions,light){name=complete(name,it)}
                FlowRow { for((value,label) in listOf("and" to text(R.string.ui_and_all_tags),"or" to text(R.string.ui_or_tag),"exclude" to text(R.string.ui_exclude_tag))) FilterChip(selected=mode==value,onClick={mode=value},label={Text(label)}) }
                TextButton(enabled=name.text.isNotBlank() && name.text.trim().none(Char::isWhitespace) && !name.text.trim().startsWith('-') && !name.text.trim().startsWith('~') && (mode!="or" || ':' !in name.text),onClick={edit(SearchSyntax.add(expression.text,name.text.trim(),mode).let{TextFieldValue(it,TextRange(it.length))});name=TextFieldValue("")}){Text(text(R.string.ui_add_condition))}
                Text(text(R.string.ui_or_applies_to_ordinary_tags_enter_field_conditions_such_as_rating),style=MaterialTheme.typography.bodySmall)
                Text(text(R.string.ui_order));FlowRow { for(value in listOf("keep","date")+SearchSyntax.orders) FilterChip(selected=order==value,onClick={
                    val result=SearchSyntax.filters(expression.text,value,"keep");expression=TextFieldValue(result,TextRange(result.length));order=value
                },modifier=Modifier.testTag("query-order-$value"),label={Text(when(value){"keep"->text(R.string.ui_keep_current);"date"->text(R.string.ui_website_default);else->"order:$value"})}) }
                Text(text(R.string.ui_rating));FlowRow { for(value in listOf("keep","all","safe","questionable","explicit")) FilterChip(selected=rating==value,onClick={
                    val result=SearchSyntax.filters(expression.text,"keep",value);expression=TextFieldValue(result,TextRange(result.length));rating=value
                },modifier=Modifier.testTag("query-rating-$value"),label={Text(when(value){"keep"->text(R.string.ui_keep_current);"all"->text(R.string.ui_clear_rating_filter);else->value})}) }
                Text(finalQuery,style=MaterialTheme.typography.bodySmall)
                SearchSyntax.warnings(finalQuery).forEach { Text(translateMessage(it),color=MaterialTheme.colorScheme.tertiary,style=MaterialTheme.typography.bodySmall) }
            }
        }
    },confirmButton={TextButton(onClick={apply(finalQuery)},modifier=Modifier.testTag("apply-combined-search")){Text(text(R.string.ui_apply))}},dismissButton={TextButton(onClick=close){Text(text(R.string.ui_cancel))}})
}
