// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.app.Application
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sakugapp.data.PageVideoCache

class PageMediaModel(app:Application):AndroidViewModel(app){
    private var session:PageVideoCache?=null
    fun current()=session ?: PageVideoCache(getApplication()).also{session=it}
    fun retire(cache:PageVideoCache){if(session===cache){session=null;cache.close()}}
    override fun onCleared(){session?.close();session=null}
}
internal val LocalPageVideoCache=staticCompositionLocalOf<PageVideoCache?>{null}
/** A real navigation exit closes the session; configuration/fullscreen changes preserve it. */
@Composable internal fun PageMediaScope(content:@Composable ()->Unit){
    val model:PageMediaModel=viewModel();val activity=LocalActivity.current
    val cache=remember(model){model.current()}
    DisposableEffect(model,cache){onDispose{if(activity?.isChangingConfigurations!=true)model.retire(cache)}}
    CompositionLocalProvider(LocalPageVideoCache provides cache,content=content)
}
