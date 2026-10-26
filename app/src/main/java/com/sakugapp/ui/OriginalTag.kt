// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sakugapp.data.Tag

@Composable internal fun OriginalTag(tag:Tag,color:Color,search:(String)->Unit,
    modifier:Modifier=Modifier,fontSize:TextUnit=14.sp,maxLines:Int=Int.MAX_VALUE){
    Text(tag.name,color=color,fontSize=fontSize,maxLines=maxLines,overflow=TextOverflow.Ellipsis,
        modifier=modifier.clickable{search(tag.name)}.padding(vertical=3.dp))
}
