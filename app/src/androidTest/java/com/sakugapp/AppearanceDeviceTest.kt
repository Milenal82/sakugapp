// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Only the two appearance settings, persisted data and the affected layout boundaries. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AppearanceDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun app():AppViewModel { lateinit var app:AppViewModel;ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java]};return app }
    private fun posts():List<Post>{
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        val types=assets.open("personal-tag-summary.json").bufferedReader().use{PostParser.tagSummary(it.readText())}
        return assets.open("library-real-posts.json").bufferedReader().use{PostParser.posts(it.readText(),types)}
    }
    @Test fun liveSettingsPersistWithoutChangingSystemFontsOrCompoundingNativePlayerText(){
        val app=app();val before=app.settings.value;val systemScale=ui.activity.resources.configuration.fontScale
        try {
            ui.onNodeWithText(ui.activity.getString(R.string.ui_menu)).performClick()
            ui.onNodeWithText(ui.activity.getString(R.string.ui_settings)).performClick()
            for(value in listOf(0.75f,1.25f)){
                ui.onNodeWithTag("setting-font-scale").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(value)}
                ui.waitUntil(10000){app.settings.value.fontScale==value}
                val layout=mutableListOf<TextLayoutResult>()
                ui.onNodeWithText(ui.activity.getString(R.string.ui_font_size,(value*100).toInt())).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layout)}
                assertEquals(systemScale*value,layout.single().layoutInput.density.fontScale,0.001f)
                assertEquals(systemScale,ui.activity.resources.configuration.fontScale,0f)
            }
            ui.onNodeWithTag("setting-tag-spacing").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(12f)}
            ui.waitUntil(10000){app.settings.value.tagVerticalSpacing==12f}
            ui.activityRule.scenario.recreate()
            ui.waitUntil(10000){app.settings.value.fontScale==1.25f && app.settings.value.tagVerticalSpacing==12f}
            // Read a copy with an independent DataStore: these are disk values, not only ViewModel state.
            runBlocking {
                val copy=File(context.cacheDir,"appearance-copy.preferences_pb")
                File(context.filesDir,"datastore/settings_v1.preferences_pb").copyTo(copy,overwrite=true)
                val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
                try {
                    val data=androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope=scope,produceFile={copy})
                    val restored=PreferencesStore(context,data).settings.first()
                    assertEquals(1.25f,restored.fontScale,0f);assertEquals(12f,restored.tagVerticalSpacing,0f)
                } finally {scope.cancel();copy.delete()}
            }
            ui.runOnUiThread {
                val host=VideoGestureHost(ui.activity)
                val time=host.playerView.findViewById<android.widget.TextView>(androidx.media3.ui.R.id.exo_position)
                val original=time.textSize
                for(value in listOf(0.75f,1.25f,1f)){
                    host.configure(AppSettings(fontScale=value),VideoTransform())
                    assertEquals(original*value,time.textSize,0.1f)
                }
            }
            UiEvidence.capture("appearance-settings.png")
        } finally {runBlocking{app.preferences.number("fontScale",before.fontScale);app.preferences.number("tagVerticalSpacing",before.tagVerticalSpacing)}}
    }
    @Test fun spacingChangesOnlyCaptionAndFontExtremesKeepTextToolsAndPlayerControlsReadable(){
        val post=posts().first();var scale by mutableFloatStateOf(1f);var gap by mutableFloatStateOf(0f)
        var search by mutableStateOf(false)
        val file=File(context.cacheDir,"appearance-pattern.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("frame-index-vfr.mp4").use{input->file.outputStream().use{input.copyTo(it)}}
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(light=scale<1f,fontScale=scale){
            val settings=AppSettings(fontScale=scale,tagVerticalSpacing=gap,autoPreview=false)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("appearance-content")){
                Box(Modifier.width(156.dp)){PostCard(post,settings,false,false,{}, {}, {}, {})}
                SearchToolTile(R.drawable.ic_tune,text(R.string.ui_search_tools),{},Modifier.testTag("appearance-tool"))
                Text(LocalDensity.current.fontScale.toString(),Modifier.testTag("appearance-density"))
                VideoPanel(file.toURI().toString(),settings)
            }
            if(search)SearchDialog("animated order:score",object:SuggestionSource{override suspend fun suggestions(prefix:String)=emptyList<TagSuggestion>()},scale<1f,{}, {search=false})
        }}}
        try {
            val media=ui.onNodeWithTag("post-media-${post.id}").fetchSemanticsNode().boundsInRoot
            val base=ui.onNodeWithTag("post-caption-${post.id}").fetchSemanticsNode().boundsInRoot.height
            ui.runOnUiThread{gap=12f}
            ui.waitForIdle()
            assertTrue("Real classified tags occupy several rows",ui.onNodeWithTag("post-caption-${post.id}").fetchSemanticsNode().boundsInRoot.height>base+10)
            assertEquals(media,ui.onNodeWithTag("post-media-${post.id}").fetchSemanticsNode().boundsInRoot)
            for(value in listOf(0.75f,1.25f)){
                ui.runOnUiThread{scale=value};ui.waitForIdle()
                ui.onNodeWithTag("post-media-${post.id}").performScrollTo()
                assertEquals(media.width,ui.onNodeWithTag("post-media-${post.id}").fetchSemanticsNode().boundsInRoot.width,0.5f)
                assertEquals(media.height,ui.onNodeWithTag("post-media-${post.id}").fetchSemanticsNode().boundsInRoot.height,0.5f)
                ui.onNodeWithTag("appearance-tool").performScrollTo().assertIsDisplayed()
                val bounds=ui.onNodeWithTag("appearance-tool").fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.width<=ui.onNodeWithTag("appearance-content").fetchSemanticsNode().boundsInRoot.width)
                val density=ui.activity.resources.displayMetrics.density
                assertTrue("Tools retain a 48 dp touch target",bounds.height>=48*density-1)
                ui.onNodeWithTag("more-controls").performScrollTo().performClick()
                ui.onNodeWithTag("speed-controls").performScrollTo().assertIsDisplayed()
                ui.onNodeWithTag("ab-controls").performScrollTo().assertIsDisplayed()
                UiEvidence.capture("appearance-player-${(value*100).toInt()}.png")
                ui.onNodeWithTag("more-controls").performScrollTo().performClick()
                ui.runOnUiThread{search=true};ui.waitForIdle()
                ui.onNodeWithTag("query-expression").performScrollTo().assertTextContains("animated order:score")
                ui.onNodeWithTag("edit-query-tool").performScrollTo().assertIsDisplayed()
                UiEvidence.capture("appearance-search-${(value*100).toInt()}.png")
                ui.runOnUiThread{search=false};ui.waitForIdle()
            }
        } finally {file.delete()}
    }
}
