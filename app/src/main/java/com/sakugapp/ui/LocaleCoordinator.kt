// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.content.Context
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sakugapp.data.PreferenceRules
import com.sakugapp.data.PreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One writer reconciles old preferences, the in-app picker, and Android's application-language picker. */
internal object LocaleCoordinator {
    private val mutex=Mutex()
    suspend fun choose(context:Context,language:String,store:PreferencesStore=PreferencesStore(context))=mutex.withLock {
        store.language(language)
        apply(language,store)
    }
    suspend fun reconcile(context:Context,store:PreferencesStore=PreferencesStore(context))=mutex.withLock {
        val (saved,expected)=store.localeState.first()
        val actual=withContext(Dispatchers.Main.immediate){AppCompatDelegate.getApplicationLocales().toLanguageTags()}
        // Null means migration from 1.0.0: preserve its explicit setting before using platform state.
        val language=if(expected!=null && actual!=expected)PreferenceRules.platformLanguage(actual)else saved
        if(language!=saved)store.language(language)
        apply(language,store)
    }
    private suspend fun apply(language:String,store:PreferencesStore){
        val system=Resources.getSystem().configuration.locales[0]
        val resolved=PreferenceRules.resolveLanguage(language,system.toLanguageTag())
        // A true platform default is possible for en / Simplified Chinese. Other system
        // languages use the requested English fallback while retaining the user's system mode.
        val target=if(language=="system" && (system.language=="en" || (system.language=="zh" && resolved=="zh-CN")))""else resolved
        store.recordAppliedLocale(target)
        withContext(Dispatchers.Main.immediate){
            if(AppCompatDelegate.getApplicationLocales().toLanguageTags()!=target)
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target))
        }
    }
}
