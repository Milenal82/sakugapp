// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sakugapp.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SearchDialog(query:String,apply:(String)->Unit,close:()->Unit) {
    var expression by remember { mutableStateOf(query) }
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("and") }
    var order by remember { mutableStateOf("keep") }
    var rating by remember { mutableStateOf("keep") }
    var guide by remember { mutableStateOf(false) }
    val finalQuery=SearchSyntax.filters(expression,order,rating)
    AlertDialog(onDismissRequest=close,title={Text(text("Search tools","検索ツール"))},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row { TextButton(onClick={guide=false}) { Text(text("Compose query","検索式を作成")) };TextButton(onClick={guide=true}){Text(text("Search reference","検索構文一覧"))} }
            if(guide) {
                Text(text("Examples replace the draft; Apply runs the search. Fields and operators are sent to the website unchanged.","例を選ぶと下書きを置換します。適用で検索します。構文はそのままサイトに送信します。"))
                for(example in SearchGuide.examples) {
                    TextButton(onClick={expression=example.query;order="keep";rating="keep";guide=false}) { Text(example.query) }
                    Text(when(LocalLanguage.current) { "zh-TW" -> example.zh;"ja" -> example.ja;else -> example.en },style=MaterialTheme.typography.bodySmall)
                }
                Text(text("Wildcard and mixed OR combinations have website limitations. Repeated fields cannot express multiple values; different fields can be combined.","ワイルドカード・OR混在にはサイト側の制限があります。同種フィールドの重複を避け、異なるフィールドを組み合わせてください。"))
            } else {
                OutlinedTextField(expression,{expression=it},label={Text(text("Query","検索式"))},modifier=Modifier.fillMaxWidth().testTag("query-expression"),minLines=2)
                Text(text("Score shortcuts","スコア条件"))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for(preset in SearchSyntax.scorePresets)FilterChip(selected=preset in SearchSyntax.tokens(expression),onClick={expression=SearchSyntax.score(expression,preset)},label={Text(preset)})
                }
                OutlinedTextField(name,{name=it},label={Text(text("Tag or field condition","タグ・フィールド条件"))},modifier=Modifier.fillMaxWidth(),singleLine=true)
                FlowRow { for((value,label) in listOf("and" to text("AND · all tags","AND・すべて"),"or" to text("OR · ~tag","OR・~タグ"),"exclude" to text("Exclude · -tag","除外・-タグ"))) FilterChip(selected=mode==value,onClick={mode=value},label={Text(label)}) }
                TextButton(enabled=name.isNotBlank() && name.none(Char::isWhitespace) && !name.startsWith('-') && !name.startsWith('~') && (mode!="or" || ':' !in name),onClick={expression=SearchSyntax.add(expression,name.trim(),mode);name=""}){Text(text("Add condition","条件を追加"))}
                Text(text("OR applies to ordinary tags. Enter field conditions such as -rating:questionable directly in Query.","ORは通常タグ用です。-rating:questionableなどの条件は検索式へ直接入力してください。"),style=MaterialTheme.typography.bodySmall)
                Text(text("Order","並び順"));FlowRow { for(value in listOf("keep","date")+SearchSyntax.orders) FilterChip(selected=order==value,onClick={order=value},label={Text(when(value){"keep"->text("Keep current","変更しない");"date"->text("Website default","サイトの既定");else->"order:$value"})}) }
                Text(text("Rating","レーティング"));FlowRow { for(value in listOf("keep","all","safe","questionable","explicit")) FilterChip(selected=rating==value,onClick={rating=value},label={Text(when(value){"keep"->text("Keep current","変更しない");"all"->text("Clear rating filter","評価条件を解除");else->value})}) }
                Text(finalQuery,style=MaterialTheme.typography.bodySmall)
                SearchSyntax.warnings(finalQuery).forEach { Text(translateMessage(it),color=MaterialTheme.colorScheme.tertiary,style=MaterialTheme.typography.bodySmall) }
            }
        }
    },confirmButton={TextButton(onClick={apply(finalQuery)}){Text(text("Apply","適用"))}},dismissButton={TextButton(onClick=close){Text(text("Cancel","キャンセル"))}})
}
