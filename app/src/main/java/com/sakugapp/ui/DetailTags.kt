// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.sakugapp.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DetailTags(tags:List<Tag>,light:Boolean,search:(String)->Unit,wiki:(String)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        for(tag in tags) {
            val color=when(tag.category){TagCategory.COPYRIGHT->copyrightColor(light);TagCategory.ARTIST->artistColor(light);else->MaterialTheme.colorScheme.onSurfaceVariant}
            Surface(shape=MaterialTheme.shapes.small,border=BorderStroke(1.dp,color.copy(alpha=0.65f)),modifier=Modifier.testTag("detail-tag-${tag.name}")) {
                Row {
                    if(tag.category==TagCategory.ARTIST) {
                        val description=text(R.string.ui_artist_wiki)+tag.name
                        TextButton(onClick={wiki(UrlPolicy.artistWiki(tag.name))},modifier=Modifier.testTag("artist-wiki-${tag.name}").semantics{contentDescription=description}){Text("?",color=color)}
                        VerticalDivider(Modifier.height(40.dp),color=color.copy(alpha=0.35f))
                    }
                    OriginalTag(tag,color,search,Modifier.padding(horizontal=12.dp,vertical=4.dp),showNote=true)
                }
            }
        }
    }
}
