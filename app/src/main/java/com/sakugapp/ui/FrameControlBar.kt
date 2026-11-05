// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakugapp.R

@Composable internal fun FrameControlBar(frame:ConfirmedFrame?,position:Long,pending:Int,enabled:Boolean,step:(Int)->Unit,actions:@Composable ()->Unit){
    @Composable fun buttons(deltas:List<Int>){for(delta in deltas){val label=text(if(delta<0)R.string.ui_previous_frame else R.string.ui_next_frame)+" × ${kotlin.math.abs(delta)}";TextButton(enabled=enabled,onClick={step(delta)},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(48.dp).testTag("step-$delta").semantics{contentDescription=label}){Text(if(delta>0)"+$delta"else"−${-delta}",maxLines=1)}}}
    Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth().testTag("frame-toolbar")){
        BoxWithConstraints{
            val wide=maxWidth>=440.dp
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                if(wide)Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically){buttons(listOf(-3,-2,-1));Text(frame?.let{"${it.index+1}/${it.count}"} ?: formatClipTime(position),Modifier.weight(1f).testTag("frame-counter"),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall);buttons(listOf(1,2,3))}
                else{
                    Column{Row{buttons(listOf(-3,-2,-1))};Row{buttons(listOf(1,2,3))}}
                    Text(frame?.let{"${it.index+1}\n/${it.count}"} ?: formatClipTime(position),Modifier.weight(1f).testTag("frame-counter"),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)
                }
                actions()
            }
        }
    }
    Text(if(pending>0)text(R.string.ui_frame_working)else text(when{frame?.decoded==true && frame.exactTime->R.string.ui_frame_exact;frame?.decoded==true->R.string.ui_frame_index_estimated_time;else->R.string.ui_frame_estimate}),style=MaterialTheme.typography.labelSmall,modifier=Modifier.testTag("frame-precision"))
}
