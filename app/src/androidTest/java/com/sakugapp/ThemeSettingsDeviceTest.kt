// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ThemeSettingsDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun posts()=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()})
    @Test fun legacyDisabledFlagsAreOverriddenWhileOtherSettingsFavoritesAndHistorySurvive()=runBlocking{
        val file=File(ui.activity.cacheDir,"v122-${System.nanoTime()}.preferences_pb")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val raw=PreferenceDataStoreFactory.create(scope=scope,produceFile={file})
        val db=Room.inMemoryDatabaseBuilder(ui.activity,LibraryDatabase::class.java).build()
        try{
            raw.edit{p->PreferenceRules.fixedFlags.forEach{(key,value)->p[booleanPreferencesKey(key)]=!value}
                p[stringPreferencesKey("accent")]="blue";p[stringPreferencesKey("themeMode")]="light"
                p[floatPreferencesKey("fontScale")]=1.15f;p[floatPreferencesKey("tagVerticalSpacing")]=5f;p[stringPreferencesKey("history")]="animated order:score"}
            val store=PreferencesStore(ui.activity,raw)
            fun fixed(p:AppSettings){assertTrue(p.gestures && p.screenshot && p.loop && p.muted && p.mobilePreview);assertFalse(p.wifiImages);assertEquals("mint",p.accent)}
            fixed(store.settings.first());store.initializeTheme();fixed(PreferencesStore(ui.activity,raw).settings.first())
            assertEquals(1.15f,store.settings.first().fontScale,0f);assertEquals(5f,store.settings.first().tagVerticalSpacing,0f)
            assertEquals("animated order:score",store.history.first().single())
            PreferenceRules.fixedFlags.keys.forEach{assertNull(raw.data.first()[booleanPreferencesKey(it)])}
            val p=posts();val library=LocalLibraryStore(db);library.ready();library.membership(p[0],1,true)
            val child=library.createSubfolder(1,"Keep child");library.classify(p[0].id,1,child,true)
            db.personal().viewed(ViewedPost(p[0].id,LibraryCodec.encodePost(p[0]),10));db.personal().policy(HistoryPolicy(enabled=false))
            val original=library.export();val personal=PersonalStore(db){20};personal.ensureRecording()
            assertEquals(10L,personal.history.first().single().viewedAt);personal.record(p[1]);assertTrue(personal.recording.first())
            assertEquals(setOf(p[0].id,p[1].id),personal.history.first().map{it.id}.toSet());assertEquals(original,library.export())
            personal.recording(false);personal.record(p[0]);assertEquals(p[0].id,personal.history.first().first().id)
        }finally{db.close();scope.cancel();file.delete()}
    }
    @Test fun compactSettingsPaletteMenusAndFolderActionsKeepTheirBehavior(){
        lateinit var app:AppViewModel;ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
        val before=app.settings.value
        ui.onNodeWithText(ui.activity.getString(R.string.ui_menu)).performClick()
        ui.onNode(hasText(ui.activity.getString(R.string.ui_posts)) and isSelected()).assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.ui_settings)).performClick()
        for(id in listOf(R.string.ui_gestures_enabled,R.string.ui_enable_snapshot_sharing,R.string.ui_default_loop,R.string.ui_default_mute,R.string.ui_allow_previews_on_metered_networks,R.string.ui_load_images_only_on_unmetered_networks,R.string.ui_record_browsing_history))
            ui.onAllNodesWithText(ui.activity.getString(id),substring=false).assertCountEquals(0)
        try{
            for((palette,mode)in listOf("pink" to "light","pink" to "dark","mint" to "light","mint" to "dark")){
                runBlocking{app.preferences.theme(mode)}
                ui.onNodeWithTag("accent-$palette").performScrollTo().performClick()
                ui.waitUntil(10000){app.settings.value.accent==palette && app.settings.value.themeMode==mode}
                assertEquals(palette,runBlocking{PreferencesStore(ui.activity).settings.first().accent})
                ui.waitForIdle()
                UiEvidence.capture("v122-settings-$palette-$mode.png")
            }
            ui.onNodeWithText(ui.activity.getString(R.string.ui_menu)).performClick()
            ui.onNodeWithText(ui.activity.getString(R.string.ui_settings)).assertIsSelected()
            UiEvidence.capture("v122-main-menu.png")
            ui.onNodeWithText(ui.activity.getString(R.string.ui_local_library)).performClick()
            val folder=runBlocking{app.localLibrary.createFolder("Theme folder")}
            try{
                ui.waitUntil(10000){app.library.value.folders.any{it.id==folder}}
                ui.onNodeWithTag("local-library").performScrollToNode(hasTestTag("folder-entry-$folder"))
                ui.onNodeWithTag("folder-entry-$folder").performClick();ui.onNodeWithTag("folder-more").performClick()
                ui.onNodeWithText(ui.activity.getString(R.string.ui_rename)).assertIsDisplayed()
                UiEvidence.capture("v122-folder-menu.png")
                ui.onNodeWithText(ui.activity.getString(R.string.ui_rename)).performClick()
                ui.onNode(hasText("Theme folder") and hasSetTextAction()).assertExists();ui.onNodeWithText(ui.activity.getString(R.string.ui_cancel)).performClick()
            }finally{runBlocking{app.localLibrary.deleteFolder(folder)}}
        }finally{runBlocking{app.preferences.theme(before.themeMode);app.preferences.accent(before.accent)}}
    }
    @Test fun defaultPlaybackIgnoresOldFlagsAndTemporaryControlsAndScreenshotStillWork(){
        val file=File(ui.activity.cacheDir,"v122-player.mp4")
        instrumentation.context.assets.open("frame-index-vfr.mp4").use{input->file.outputStream().use{input.copyTo(it)}}
        var player:Player?=null
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(true,accent="pink"){
            Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())){
                VideoPanel(file.toURI().toString(),AppSettings(muted=false,loop=false,gestures=false,screenshot=false),onPlayerReady={player=it})
            }
        }}}
        try{
            ui.waitUntil(20000){var ready=false;ui.runOnUiThread{ready=player?.playbackState==Player.STATE_READY};ready}
            ui.runOnUiThread{assertEquals(0f,player!!.volume,0f);assertEquals(Player.REPEAT_MODE_ONE,player!!.repeatMode)}
            ui.onNodeWithTag("more-controls").performScrollTo().performClick()
            ui.onNodeWithText(ui.activity.getString(R.string.ui_unmute)).performScrollTo().performClick()
            ui.onNodeWithText(ui.activity.getString(R.string.ui_loop_on)).performScrollTo().performClick()
            ui.waitUntil(5000){var applied=false;ui.runOnUiThread{applied=player!!.volume==1f && player!!.repeatMode==Player.REPEAT_MODE_OFF};applied}
            ui.runOnUiThread{assertEquals(1f,player!!.volume,0f);assertEquals(Player.REPEAT_MODE_OFF,player!!.repeatMode)}
            val device=UiDevice.getInstance(instrumentation)
            ui.waitUntil(15000){ui.onAllNodesWithContentDescription(ui.activity.getString(R.string.ui_video_frame_rendered)).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText(ui.activity.getString(R.string.ui_snapshot)).performScrollTo().performClick()
            ui.waitUntil(15000){device.currentPackageName!=ui.activity.packageName}
            device.pressBack()
        }finally{file.delete()}
    }
}
