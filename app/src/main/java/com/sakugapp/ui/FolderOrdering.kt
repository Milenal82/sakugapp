// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sakugapp.R

/** Stable database IDs are moved; the gesture never writes on every pointer event. */
@Composable internal fun FolderOrdering(entries:List<Pair<Long,String>>,save:(List<Long>)->Unit,close:()->Unit){
    val source=entries.map{it.first};var order by remember(source){mutableStateOf(source)}
    val names=entries.toMap();val list=rememberLazyListState()
    var dragging by remember{mutableStateOf<Long?>(null)}
    var start by remember{mutableFloatStateOf(0f)};var delta by remember{mutableFloatStateOf(0f)}
    val up=text(R.string.ui_move_up);val down=text(R.string.ui_move_down)
    fun move(id:Long,target:Int){val from=order.indexOf(id);if(from>=0 && target in order.indices){order=order.toMutableList().apply{add(target,removeAt(from))}}}
    fun finish(){dragging=null;delta=0f;if(order!=source)save(order)}
    AlertDialog(onDismissRequest=close,title={Text(text(R.string.ui_order_folders))},text={
        LazyColumn(state=list,modifier=Modifier.fillMaxWidth().heightIn(max=380.dp).testTag("folder-order-list"),verticalArrangement=Arrangement.spacedBy(4.dp)){
            items(order,key={it}){id->
                Surface(shape=MaterialTheme.shapes.small,color=if(dragging==id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier=Modifier.fillMaxWidth().zIndex(if(dragging==id)1f else 0f).graphicsLayer{
                        translationY=if(dragging==id)start+delta-(list.layoutInfo.visibleItemsInfo.firstOrNull{it.key==id}?.offset ?: 0)else 0f
                    }.testTag("order-row-$id").semantics{
                        customActions=listOf(CustomAccessibilityAction(up){move(id,order.indexOf(id)-1);finish();true},CustomAccessibilityAction(down){move(id,order.indexOf(id)+1);finish();true})
                    }){
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                        Icon(painterResource(R.drawable.ic_drag),text(R.string.ui_drag_to_sort),Modifier.size(48.dp).testTag("order-handle-$id").pointerInput(id,source){
                            detectDragGesturesAfterLongPress(onDragStart={
                                val info=list.layoutInfo.visibleItemsInfo.firstOrNull{it.key==id}
                                if(info!=null){dragging=id;start=info.offset.toFloat();delta=0f}
                            },onDragEnd=::finish,onDragCancel={dragging=null;delta=0f;order=source},onDrag={change,amount->
                                change.consume();delta+=amount.y
                                val own=list.layoutInfo.visibleItemsInfo.firstOrNull{it.key==id}
                                if(own!=null){val center=start+delta+own.size/2f
                                    list.layoutInfo.visibleItemsInfo.firstOrNull{it.key!=id && center in it.offset.toFloat()..(it.offset+it.size).toFloat()}?.let{target->move(id,order.indexOf(target.key as Long))}
                                }
                            })
                        }.padding(12.dp))
                        Text(names[id].orEmpty(),Modifier.weight(1f).padding(vertical=8.dp),style=MaterialTheme.typography.bodyLarge)
                        Row{
                            ActionIcon(R.drawable.ic_up,up,{move(id,order.indexOf(id)-1);finish()},enabled=order.firstOrNull()!=id)
                            ActionIcon(R.drawable.ic_down,down,{move(id,order.indexOf(id)+1);finish()},enabled=order.lastOrNull()!=id)
                        }
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick=close){Text(text(R.string.ui_close))}})
}
