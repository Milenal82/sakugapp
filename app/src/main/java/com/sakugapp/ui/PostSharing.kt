// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.content.Intent
import com.sakugapp.data.Post

internal fun postShareIntent(post:Post):Intent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
    type="text/plain"
    putExtra(Intent.EXTRA_TEXT,post.pageUrl)
},null)
