// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

internal object SearchElasticPolicy {
    private const val RESISTANCE=.25f
    fun drag(offset:Float,delta:Float,limit:Float):Float =
        if(!delta.isFinite())offset else (offset+delta*RESISTANCE).coerceIn(-limit,limit)
    /** Consume only enough reverse drag to unwind; the remaining gesture scrolls normally. */
    fun unwind(offset:Float,delta:Float):Float=when{
        offset>0 && delta<0->delta.coerceAtLeast(-offset/RESISTANCE)
        offset<0 && delta>0->delta.coerceAtMost(-offset/RESISTANCE)
        else->0f
    }
}

private class SearchElasticState(private val limit:Float) {
    var offset by mutableFloatStateOf(0f);private set
    private var returning:Job?=null
    fun drag(delta:Float){returning?.cancel();returning=null;offset=SearchElasticPolicy.drag(offset,delta,limit)}
    fun unwind(delta:Float):Float=SearchElasticPolicy.unwind(offset,delta).also{if(it!=0f)drag(it)}
    fun clear(){returning?.cancel();returning=null;offset=0f}
    suspend fun settle()=coroutineScope{
        returning?.cancel()
        val job=launch{
            // Animation core inherits the system's duration scale, including zero/reduced motion.
            animate(offset,0f,animationSpec=tween(180)){value,_->offset=value}
        }
        returning=job
        try{job.join()}finally{if(returning===job)returning=null}
    }
}

/** Only unconsumed vertical scroll moves the layer. No pointer interception or per-delta launches. */
@OptIn(ExperimentalLayoutApi::class,ExperimentalFoundationApi::class)
@Composable internal fun Modifier.searchElasticScroll(scroll:ScrollState):Modifier {
    val limit=with(LocalDensity.current){24.dp.toPx()}
    val state=remember(limit){SearchElasticState(limit)}
    val enabled=!WindowInsets.isImeVisible
    val currentEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(enabled){if(!enabled)state.clear()}
    DisposableEffect(state){onDispose{state.clear()}}
    val connection=remember(state){object:NestedScrollConnection{
        override fun onPreScroll(available:Offset,source:NestedScrollSource):Offset =
            if(currentEnabled && source==NestedScrollSource.UserInput)Offset(0f,state.unwind(available.y))else Offset.Zero
        override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset {
            if(!currentEnabled || source!=NestedScrollSource.UserInput || available.y==0f)return Offset.Zero
            state.drag(available.y);return Offset(0f,available.y)
        }
        override suspend fun onPreFling(available:Velocity):Velocity {
            if(!currentEnabled || state.offset==0f)return Velocity.Zero
            state.settle();return Velocity(0f,available.y)
        }
        override suspend fun onPostFling(consumed:Velocity,available:Velocity):Velocity {
            if(currentEnabled && state.offset!=0f)state.settle()
            return Velocity.Zero
        }
    }}
    return nestedScroll(connection).verticalScroll(scroll,overscrollEffect=null).graphicsLayer{translationY=state.offset}
}
