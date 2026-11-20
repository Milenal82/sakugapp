// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/** Deterministic UI checks; public API responses are verified separately, never required here. */
@RunWith(AndroidJUnit4::class)
class SakugabotDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun app():AppViewModel {
        lateinit var result:AppViewModel
        ui.runOnUiThread{result=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
        return result
    }
    @Test fun actualSettingsSwitchCanBeChangedAndReopened(){
        val app=app();val before=app.settings.value.chineseDetailTranslations
        try{
            runBlocking{app.preferences.chineseDetailTranslations(false)}
            ui.waitUntil(5000){!app.settings.value.chineseDetailTranslations}
            ui.runOnUiThread{ui.activity.setContent{val settings by app.settings.collectAsStateWithLifecycle()
                SakugappTheme(settings.lightTheme){Surface{SettingsScreen(settings,app,{},{})}}
            }}
            ui.onNodeWithTag("setting-chinese-detail-translations").performScrollTo().assertIsOff().performClick()
            ui.waitUntil(5000){app.settings.value.chineseDetailTranslations}
            assertTrue(runBlocking{PreferencesStore(ui.activity).settings.first().chineseDetailTranslations})
            ui.onNodeWithTag("setting-chinese-detail-translations").assertIsOn().performClick()
            ui.waitUntil(5000){!app.settings.value.chineseDetailTranslations}
        }finally{runBlocking{app.preferences.chineseDetailTranslations(before)}}
    }
    @Test fun togglingUpdatesDetailImmediatelyKeepsRawSearchAndOfflineFallback(){
        val app=app();val before=app.settings.value.chineseDetailTranslations
        val tags=listOf(Tag("mob_psycho_100",TagCategory.COPYRIGHT),Tag("yutaka_nakamura",TagCategory.ARTIST),Tag("mokmok",TagCategory.ARTIST))
        val calls=mutableListOf<String>();var searched:String?=null
        val source=object:DetailTranslationSource {
            override suspend fun lookup(tag:Tag):ChineseTagName? {
                calls+=tag.name
                val chinese=when(tag.name){"mob_psycho_100"->"灵能百分百";"yutaka_nakamura"->"中村丰";else->return null}
                return ChineseTagName(tag.name,tag.category,chinese,setOf(tag.name))
            }
        }
        var light by mutableStateOf(false)
        try{
            runBlocking{app.preferences.chineseDetailTranslations(false)}
            ui.waitUntil(5000){!app.settings.value.chineseDetailTranslations}
            ui.runOnUiThread{ui.activity.setContent{
                val settings by app.settings.collectAsStateWithLifecycle()
                val names=detailNames(1,tags,settings.chineseDetailTranslations,source)
                SakugappTheme(light){Surface{Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)){
                    Switch(settings.chineseDetailTranslations,app::chineseDetailTranslations,Modifier.testTag("translation-toggle"))
                    DetailTags(tags,light,{searched=it},{},names.mapValues{it.value.chinese})
                }}}
            }}
            ui.waitForIdle();assertTrue(calls.isEmpty())
            ui.onNodeWithTag("translation-toggle").performClick()
            ui.waitUntil(5000){ui.onAllNodesWithTag("translated-tag-mob_psycho_100",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("灵能百分百").assertIsDisplayed();ui.onNodeWithText("中村丰").assertIsDisplayed()
            ui.onNodeWithTag("tag-mob_psycho_100",useUnmergedTree=true).assertIsDisplayed().performTouchInput{click()}
            assertEquals("mob_psycho_100",searched)
            ui.onNodeWithTag("tag-yutaka_nakamura",useUnmergedTree=true).assertIsDisplayed()
            ui.onNodeWithTag("tag-mokmok",useUnmergedTree=true).assertIsDisplayed()
            UiEvidence.capture("sakugabot-details-dark.png")
            ui.runOnUiThread{light=true};ui.waitForIdle();UiEvidence.capture("sakugabot-details-light.png")
            ui.onNodeWithTag("translation-toggle").performClick()
            ui.waitUntil(5000){ui.onAllNodesWithTag("translated-tag-mob_psycho_100",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
            ui.onNodeWithTag("tag-mob_psycho_100",useUnmergedTree=true).assertIsDisplayed()
        }finally{runBlocking{app.preferences.chineseDetailTranslations(before)}}
    }
    @Test fun publicApiOnAndroidAndOfflineClientBothKeepTheOriginalTag():Unit=runBlocking {
        val tag=Tag("yutaka_nakamura",TagCategory.ARTIST)
        val online=SakugabotTranslations()
        // One bounded public GET. A real outage is recorded as fallback, never invented data.
        val name=online.lookup(tag)
        val dnsCalls=AtomicInteger()
        val offline=SakugabotTranslations(client=okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false)
            .dns(object:okhttp3.Dns{
                override fun lookup(hostname:String):List<java.net.InetAddress>{dnsCalls.incrementAndGet();throw UnknownHostException("Offline test")}
            }).build())
        var source by mutableStateOf<DetailTranslationSource>(online)
        ui.runOnUiThread{ui.activity.setContent{
            val names=detailNames(2,listOf(tag),true,source)
            SakugappTheme(false){Surface{Box(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)){
                DetailTags(listOf(tag),false,{},{},names.mapValues{it.value.chinese})
            }}}
        }}
        if(name!=null){
            ui.waitUntil(5000){ui.onAllNodesWithTag("translated-tag-${tag.name}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText(name.chinese).assertIsDisplayed()
        }
        ui.onNodeWithTag("tag-${tag.name}",useUnmergedTree=true).assertIsDisplayed()
        UiEvidence.capture("sakugabot-real-api.png")
        ui.runOnUiThread{source=offline}
        ui.waitUntil(5000){dnsCalls.get()>0}
        ui.waitForIdle()
        ui.onAllNodesWithTag("translated-tag-${tag.name}",useUnmergedTree=true).assertCountEquals(0)
        ui.onNodeWithTag("tag-${tag.name}",useUnmergedTree=true).assertIsDisplayed()
        UiEvidence.capture("sakugabot-offline.png")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"ui-evidence").apply{mkdirs()}
        val record=File(directory,"sakugabot-network.json")
        record.writeText(com.google.gson.Gson().toJson(mapOf("android_api" to android.os.Build.VERSION.SDK_INT,
            "original_tag" to tag.name,"public_api_result" to if(name==null)"raw_fallback" else "translated",
            "chinese" to name?.chinese,"offline_original_visible" to true)))
        UiDevice.getInstance(instrumentation).executeShellCommand("cp ${record.absolutePath} /sdcard/Download/sakugapp-ui-evidence/${record.name}")
    }
}

