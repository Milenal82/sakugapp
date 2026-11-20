// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.sakugapp.R
import java.util.Locale

/** One row. On narrow windows only the arrow cells compact; their height remains 48 dp. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun FrameControlBar(frame:ConfirmedFrame?,position:Long,pending:Int,enabled:Boolean,step:(Int)->Unit,active:Boolean=frame!=null,actions:@Composable ()->Unit){
    var chosen by remember{mutableIntStateOf(0)}
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified){
        Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.fillMaxWidth().testTag("frame-toolbar")){
            BoxWithConstraints{
                val wide=maxWidth>=440.dp
                val infoWidth=(maxWidth*.16f).coerceIn(36.dp,140.dp)
                val cell=((maxWidth-108.dp-infoWidth)/6).coerceAtMost(48.dp)
                val font=minOf(MaterialTheme.typography.labelSmall.fontSize.value,cell.value/1.85f/LocalDensity.current.fontScale).sp
                @Composable fun button(delta:Int){
                    val current=active && chosen==delta
                    TextButton(enabled=enabled,onClick={chosen=delta;step(delta)},shape=RoundedCornerShape(10.dp),
                        colors=ButtonDefaults.textButtonColors(containerColor=if(current)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor=if(current)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface),
                        contentPadding=PaddingValues(0.dp),modifier=Modifier.width(cell).height(48.dp).testTag("step-$delta").semantics{selected=current}){
                    Text((if(delta<0)"<"else">").repeat(kotlin.math.abs(delta)),fontSize=font,fontFamily=FontFamily.Monospace,maxLines=1)
                }}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        for(delta in listOf(-3,-2,-1))button(delta)
                        val time=frame?.timeMs ?: position
                        val label=frame?.let{(if(!it.decoded)"~"else"")+(it.index+1)} ?: "—"
                        Column(Modifier.width(infoWidth).height(48.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                            Text(text(R.string.ui_frame_tools),fontSize=9.sp,lineHeight=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
                                color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("frame-tool-title"))
                            Text(if(!wide)"$label·${String.format(Locale.ROOT,"%.3f",time/1000.0)}"else "$label/${frame?.count ?: "—"} · ${formatClipTime(time)}",
                                Modifier.testTag("frame-counter"),fontSize=MaterialTheme.typography.labelSmall.fontSize,lineHeight=14.sp,
                                maxLines=1,overflow=TextOverflow.Ellipsis,fontFamily=FontFamily.Monospace)
                        }
                        for(delta in listOf(1,2,3))button(delta)
                    }
                    actions()
                }
            }
        }
    }
}
