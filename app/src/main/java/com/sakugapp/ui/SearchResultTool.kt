// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.sakugapp.R

/** Compact presentation only; the caller retains its original search actions and state. */
@Composable internal fun SearchResultTool(icon:Int,title:String,onClick:()->Unit,modifier:Modifier=Modifier,selected:Boolean=false,enabled:Boolean=true){
 FilterChip(selected=selected,onClick=onClick,enabled=enabled,modifier=modifier,
  label={Text(title,style=MaterialTheme.typography.bodySmall)},
  leadingIcon={Icon(painterResource(if(selected)R.drawable.ic_check else icon),null,Modifier.size(16.dp))})
}
