// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sakugapp.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SearchDialog(query:String,source:SuggestionSource,light:Boolean,apply:(String)->Unit,close:()->Unit) {
    var expression by remember { mutableStateOf(TextFieldValue(query,TextRange(query.length))) }
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var mode by remember { mutableStateOf("and") }
    var order by remember { mutableStateOf("keep") }
    var rating by remember { mutableStateOf("keep") }
    var guide by remember { mutableStateOf(false) }
    var expressionFocused by remember{mutableStateOf(false)}
    var nameFocused by remember{mutableStateOf(false)}
    val expressionSuggestions=rememberSuggestions(expression,expressionFocused,source)
    val nameSuggestions=rememberSuggestions(name,nameFocused,source)
    fun complete(value:TextFieldValue,tag:String):TextFieldValue {
        if(value.composition!=null || !value.selection.collapsed)return value
        val edit=SearchEditing.complete(value.text,value.selection.start,tag);return TextFieldValue(edit.text,TextRange(edit.cursor))
    }
    val finalQuery=SearchSyntax.filters(expression.text,order,rating)
    AlertDialog(onDismissRequest=close,title={Text(text(R.string.ui_search_tools))},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row { TextButton(onClick={guide=false}) { Text(text(R.string.ui_compose_query)) };TextButton(onClick={guide=true}){Text(text(R.string.ui_search_reference_language))} }
            if(guide) {
                Text(text(R.string.ui_examples_replace_the_draft_apply_runs_the_search_fields_and_opera))
                for(example in SearchGuide.examples) {
                    TextButton(onClick={expression=TextFieldValue(example.query,TextRange(example.query.length));order="keep";rating="keep";guide=false}) { Text(example.query) }
                    Text(text(example.description),style=MaterialTheme.typography.bodySmall)
                }
                Text(text(R.string.ui_wildcard_and_mixed_or_combinations_have_website_limitations_repea))
            } else {
                OutlinedTextField(expression,{expression=it},label={Text(text(R.string.ui_query))},modifier=Modifier.fillMaxWidth().testTag("query-expression").onFocusChanged{expressionFocused=it.isFocused},minLines=2)
                TagSuggestionsMenu(expressionSuggestions,light){expression=complete(expression,it)}
                Text(text(R.string.ui_score_shortcuts))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for(preset in SearchSyntax.scorePresets)FilterChip(selected=preset in SearchSyntax.tokens(expression.text),onClick={expression=SearchSyntax.score(expression.text,preset).let{TextFieldValue(it,TextRange(it.length))}},label={Text(preset)})
                }
                OutlinedTextField(name,{name=it},label={Text(text(R.string.ui_tag_or_field_condition))},modifier=Modifier.fillMaxWidth().onFocusChanged{nameFocused=it.isFocused},singleLine=true)
                TagSuggestionsMenu(nameSuggestions,light){name=complete(name,it)}
                FlowRow { for((value,label) in listOf("and" to text(R.string.ui_and_all_tags),"or" to text(R.string.ui_or_tag),"exclude" to text(R.string.ui_exclude_tag))) FilterChip(selected=mode==value,onClick={mode=value},label={Text(label)}) }
                TextButton(enabled=name.text.isNotBlank() && name.text.trim().none(Char::isWhitespace) && !name.text.trim().startsWith('-') && !name.text.trim().startsWith('~') && (mode!="or" || ':' !in name.text),onClick={expression=SearchSyntax.add(expression.text,name.text.trim(),mode).let{TextFieldValue(it,TextRange(it.length))};name=TextFieldValue("")}){Text(text(R.string.ui_add_condition))}
                Text(text(R.string.ui_or_applies_to_ordinary_tags_enter_field_conditions_such_as_rating),style=MaterialTheme.typography.bodySmall)
                Text(text(R.string.ui_order));FlowRow { for(value in listOf("keep","date")+SearchSyntax.orders) FilterChip(selected=order==value,onClick={order=value},label={Text(when(value){"keep"->text(R.string.ui_keep_current);"date"->text(R.string.ui_website_default);else->"order:$value"})}) }
                Text(text(R.string.ui_rating));FlowRow { for(value in listOf("keep","all","safe","questionable","explicit")) FilterChip(selected=rating==value,onClick={rating=value},label={Text(when(value){"keep"->text(R.string.ui_keep_current);"all"->text(R.string.ui_clear_rating_filter);else->value})}) }
                Text(finalQuery,style=MaterialTheme.typography.bodySmall)
                SearchSyntax.warnings(finalQuery).forEach { Text(translateMessage(it),color=MaterialTheme.colorScheme.tertiary,style=MaterialTheme.typography.bodySmall) }
            }
        }
    },confirmButton={TextButton(onClick={apply(finalQuery)}){Text(text(R.string.ui_apply))}},dismissButton={TextButton(onClick=close){Text(text(R.string.ui_cancel))}})
}
