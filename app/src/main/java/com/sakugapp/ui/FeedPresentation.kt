// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.data.FeedState

/** Preserve list/Post identities when a fact belongs to another page or is already shown. */
internal fun presentFeed(state:FeedState,durations:Map<String,Long>):FeedState {
    val first=state.posts.indexOfFirst{p->durations[p.fileUrl]?.let{it>0 && it!=p.durationMs}==true}
    if(first<0)return state
    val changed=state.posts.toMutableList()
    for(index in first..changed.lastIndex){val post=changed[index];durations[post.fileUrl]?.takeIf{it>0 && it!=post.durationMs}?.let{changed[index]=post.copy(durationMs=it)}}
    return state.copy(posts=changed)
}
