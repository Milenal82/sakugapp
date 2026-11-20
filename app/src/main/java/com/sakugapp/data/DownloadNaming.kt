// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import com.sakugapp.SakugappApplication
import kotlinx.coroutines.flow.first

/** Snapshot preferences and already verified names once per new transfer, never make tag requests. */
class DownloadNaming(context:Context) {
    private val preferences=PreferencesStore(context.applicationContext)
    private val translations=(context.applicationContext as SakugappApplication).tagTranslations
    suspend fun names(post:Post):Map<Tag,String> = TranslationLabels.download(post.tags,
        preferences.settings.first().chineseDetailTranslations,translations)
}
