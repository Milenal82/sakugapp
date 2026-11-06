// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui
import androidx.compose.runtime.Composable
import com.sakugapp.R
@Composable internal fun extensionError(message:String)=when(message){
    "favorites_identity"->text(R.string.ui_favorites_identity_error)
    "favorites_unverified"->text(R.string.ui_favorites_verification_error)
    "tag_directory_invalid"->text(R.string.ui_directory_verification_error)
    "quiz_pool_limit"->text(R.string.ui_pool_limit)
    "quiz_pool_save"->text(R.string.ui_pool_save_error)
    "quiz_pool"->text(R.string.ui_quiz_pool_error)
    "quiz_insufficient"->text(R.string.ui_quiz_insufficient)
    else->translateMessage(message)
}
