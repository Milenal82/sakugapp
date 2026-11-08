// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.data.Post
import kotlinx.coroutines.delay

internal data class PreviewViewport(val visible:List<Long>,val scrolling:Boolean,val active:Boolean,
    val foreground:Boolean,val automatic:Boolean,val manualRevision:Int=0)
internal data class PreviewSelection(val id:Long?=null,val automatic:Boolean=false,val pausedByUser:Boolean=false){
    fun manual(id:Long)=if(this.id==id)PreviewSelection(pausedByUser=true)else PreviewSelection(id)
    fun retain(view:PreviewViewport):PreviewSelection {
        if(!view.active)return PreviewSelection()
        val retained=if(id !in view.visible || (automatic && !view.automatic))copy(id=null,automatic=false)else this
        return if(view.scrolling && retained.pausedByUser)retained.copy(pausedByUser=false)else retained
    }
    fun canStart(view:PreviewViewport)=id==null && !pausedByUser && view.active && view.foreground && view.automatic && !view.scrolling
}

internal const val AUTO_PREVIEW_SETTLE_MS=450L
/** collectLatest cancels this wait on scrolling, navigation, lifecycle or a manual action. */
internal suspend fun settledPreview(view:PreviewViewport,selection:()->PreviewSelection,posts:()->List<Post>):PreviewSelection? {
    if(!selection().canStart(view))return null
    delay(AUTO_PREVIEW_SETTLE_MS)
    if(!selection().canStart(view))return null
    val visible=view.visible.toHashSet()
    return posts().firstOrNull{it.id in visible && it.isVideo}?.let{PreviewSelection(it.id,automatic=true)}
}
