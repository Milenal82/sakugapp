// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.media3.common.Player
import java.lang.ref.WeakReference

/** A background/transitioning panel must not keep a second decoder playing. */
internal object PlaybackResources {
    private var active=WeakReference<Player>(null)
    fun claim(player:Player){
        val previous=active.get();active=WeakReference(player)
        if(previous!=null && previous!==player){previous.pause();previous.stop()}
    }
    fun release(player:Player){if(active.get()===player)active.clear()}
}

data class FullscreenHandoff(val orientation:Int,val play:Boolean)
