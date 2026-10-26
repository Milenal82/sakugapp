// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sakugapp.R

val LocalLanguage = staticCompositionLocalOf { "en" }
@Composable fun text(@StringRes id:Int,vararg args:Any):String=stringResource(id,*args)

/** Translate app errors only. Post tags, names, source URLs and comments never pass here. */
internal fun localizedMessage(context:Context,message:String):String {
    messageResources[message]?.let{return context.getString(it)}
    if(message.any{it.code in 0x3000..0x9fff})return message
    val code=Regex("HTTP (-?[0-9]+)").find(message)?.groupValues?.get(1)
    if(code!=null){
        val id=when {
            message.startsWith("Access denied")->R.string.ui_access_denied
            message.startsWith("Session expired")->R.string.ui_session_denied
            message.startsWith("Rate limited")->R.string.ui_rate_limited
            message.startsWith("Media download rejected")->R.string.ui_media_rejected
            else->R.string.ui_http_failed
        }
        return context.getString(id,code)
    }
    if(message.startsWith("Playback failed: "))return context.getString(R.string.ui_playback_failed,message.substringAfter(": "))
    if(message.startsWith("Page load failed ("))return context.getString(R.string.ui_page_load_failed,message.substringAfter('(').substringBefore(')'))
    if(message.startsWith("Tag categories could not be loaded."))return context.getString(R.string.ui_category_failure,
        localizedMessage(context,message.substringAfter("post cards. ","")))
    return context.getString(R.string.ui_operation_failed)
}
@Composable fun translateMessage(message:String):String=localizedMessage(LocalContext.current,message)
