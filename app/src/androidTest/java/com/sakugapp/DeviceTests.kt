// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.graphics.Color
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sakugapp.data.*
import com.sakugapp.ui.FrameDecoder
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceTests {
    private val context get()=ApplicationProvider.getApplicationContext<android.app.Application>()
    @Test fun unicodeSeparatorInRealSummaryCannotEraseEveryCategory() {
        val testContext=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val body=testContext.assets.open("tag-summary-malformed-record.json").bufferedReader().use{it.readText()}
        android.util.Log.i("TagSummaryRegression","Android regex splits U+2028: ${"a\u2028b".split(Regex("\\s+")).size == 2}")
        // Android's ICU regex whitespace differs from the host JVM: exercise the production runtime.
        val types=PostParser.tagSummary(body)
        assertEquals(TagCategory.ARTIST,types["mokmok"]);assertEquals(TagCategory.COPYRIGHT,types["bakemonogatari"])
        assertFalse(types.containsKey("emil_lundmark"));assertFalse(types.containsKey("laura_begliomini"))
    }
    @Test fun exactFrameIndexesDecodeDistinctNonKeyFramesAndCanGoBack()=runBlocking {
        Assume.assumeTrue(Build.VERSION.SDK_INT>=28)
        val testContext=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val file=File(context.cacheDir,"decoder-test.mp4")
        testContext.assets.open("frame-index-vfr.mp4").use { input->file.outputStream().use{input.copyTo(it)} }
        val colors=JsonParser.parseString(testContext.assets.open("frame-index-colors.json").bufferedReader().use{it.readText()}).asJsonArray
        val decoder=FrameDecoder(context,"https://example.invalid/decoder-test.mp4",file)
        try {
            assertEquals(12,decoder.open())
            // This fixture contains B/P frames and nonuniform frame intervals.
            for(index in listOf(0,1,7,8,7,11,0)) {
                val bitmap=decoder.frame(index);val pixel=bitmap.getPixel(bitmap.width/2,bitmap.height/2)
                val expected=colors[index].asJsonArray
                assertTrue("red at index $index",kotlin.math.abs(Color.red(pixel)-expected[0].asInt)<25)
                assertTrue("green at index $index",kotlin.math.abs(Color.green(pixel)-expected[1].asInt)<25)
                assertTrue("blue at index $index",kotlin.math.abs(Color.blue(pixel)-expected[2].asInt)<25)
                bitmap.recycle()
            }
        } finally {decoder.close();file.delete()}
    }
    @Test fun preferencesAndHistorySurviveNewStoreInstance()=runBlocking {
        val first=PreferencesStore(context)
        try {
            first.language("zh-TW");first.flag("muted",false);first.remember("device_test_tag")
            val second=PreferencesStore(context)
            assertEquals("zh-TW",second.settings.first{it.language=="zh-TW"}.language)
            assertFalse(second.settings.first{!it.muted}.muted)
            assertEquals("device_test_tag",second.history.first{it.isNotEmpty()}.first())
        } finally {first.language("en");first.flag("muted",true);first.clearHistory()}
    }
    @Test fun accountSessionIsEncryptedAndRestoredWithoutSavingPasswords() {
        val session=SecureSession(context)
        val url=okhttp3.HttpUrl.Builder().scheme("https").host("www.sakugabooru.com").build()
        try {
            session.saveFromResponse(url,listOf(okhttp3.Cookie.Builder().name("test_session").value("synthetic-test-cookie").hostOnlyDomain(url.host).secure().httpOnly().build()))
            session.confirm(Account(123,"synthetic_test_identity"))
            val bytes=File(context.noBackupFilesDir,"account-session.aes").readBytes()
            assertFalse(String(bytes,Charsets.ISO_8859_1).contains("synthetic-test-cookie"))
            val restored=SecureSession(context)
            assertEquals(Account(123,"synthetic_test_identity"),restored.restore())
            assertEquals("synthetic-test-cookie",restored.loadForRequest(url).single().value)
            assertTrue(restored.loadForRequest(url.newBuilder().host("example.org").build()).isEmpty())
            restored.clear();assertNull(SecureSession(context).restore())
        } finally {session.clear()}
    }
}
