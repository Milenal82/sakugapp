// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.res.Configuration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.LocaleCoordinator
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class BrowsingUpgradeDeviceTest {
    @Test fun platformLanguagePickerAndInAppChoicesUseOneReconciledState()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"locale-${System.nanoTime()}.preferences_pb")
        val job=SupervisorJob();val data=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={file})
        val store=PreferencesStore(context,data)
        val previous=withContext(Dispatchers.Main){AppCompatDelegate.getApplicationLocales()}
        try {
            store.language("en");LocaleCoordinator.reconcile(context,store)
            withContext(Dispatchers.Main){AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))}
            LocaleCoordinator.reconcile(context,store)
            assertEquals("ja",store.settings.first().language)
            LocaleCoordinator.choose(context,"zh-CN",store)
            assertEquals("zh-CN",withContext(Dispatchers.Main){AppCompatDelegate.getApplicationLocales().toLanguageTags()})
            withContext(Dispatchers.Main){AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())}
            LocaleCoordinator.reconcile(context,store)
            assertEquals("system",store.settings.first().language)
        }finally{
            withContext(Dispatchers.Main){AppCompatDelegate.setApplicationLocales(previous)}
            job.cancelAndJoin();file.delete()
        }
    }
    @Test fun legacyPreferenceKeysAndThirtySearchesSurviveWithNewDefaultsAndSingleDeletion()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"legacy-${System.nanoTime()}.preferences_pb")
        val job=SupervisorJob();val store=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={file})
        try {
            store.edit{p->p[stringPreferencesKey("language")]="zh-TW";p[stringPreferencesKey("history")]=(1..30).joinToString("\n"){"old_tag$it"};p[booleanPreferencesKey("muted")]=false;p[booleanPreferencesKey("lightTheme")]=true}
            val upgraded=PreferencesStore(context,store)
            val settings=upgraded.settings.first()
            assertEquals("zh-TW",settings.language);assertFalse(settings.muted);assertTrue(settings.lightTheme);assertEquals(2,settings.columns)
            assertEquals(30,upgraded.history.first().size)
            upgraded.columns(4);upgraded.remember("new_tag");upgraded.deleteHistory("old_tag5")
            val restored=PreferencesStore(context,store)
            assertEquals(4,restored.settings.first().columns)
            assertEquals("new_tag",restored.history.first().first());assertFalse(restored.history.first().contains("old_tag5"))
            upgraded.clearHistory();assertTrue(restored.history.first().isEmpty());assertTrue(restored.settings.first().lightTheme)
        }finally{job.cancelAndJoin();file.delete()}
    }
    @Test fun androidResourcesProvideSimplifiedChineseAndPreserveExistingLocales(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        fun localized(tag:String)=context.createConfigurationContext(Configuration(context.resources.configuration).apply{setLocale(Locale.forLanguageTag(tag))})
        assertEquals("菜单",localized("zh-CN").getString(R.string.ui_menu))
        assertEquals("搜索",localized("zh-CN").getString(R.string.ui_search))
        assertEquals("默认静音",localized("zh-CN").getString(R.string.ui_default_mute))
        assertEquals("選單",localized("zh-TW").getString(R.string.ui_menu))
        assertEquals("メニュー",localized("ja").getString(R.string.ui_menu))
        assertEquals("Menu",localized("en").getString(R.string.ui_menu))
        // Data is never passed through interface localization.
        assertEquals("mokmok",Tag("mokmok",TagCategory.ARTIST).name)
    }
}
