// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R
import com.sakugapp.data.*

internal fun libraryError(context:android.content.Context,error:Exception):String{
    if(error is DataFailure && error.cause !is LibraryFailure)return localizedMessage(context,error.message.orEmpty())
    return context.getString(when(((error as? LibraryFailure) ?: (error.cause as? LibraryFailure))?.code){
    "note_length"->R.string.ui_note_length_error;"invalid_backup"->R.string.ui_invalid_backup;"folder_name"->R.string.ui_folder_name_error;"library_limit"->R.string.ui_library_limit;"media_limit"->R.string.ui_media_limit;else->R.string.ui_library_failed
})
}

