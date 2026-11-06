// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.preferences by preferencesDataStore("settings_v1")

@androidx.compose.runtime.Immutable
data class AppSettings(
    val language: String = "system", val showCopyright: Boolean = true, val showArtist: Boolean = true,
    val showScore: Boolean = true, val wrapTags: Boolean = true, val cropThumbnails: Boolean = false,
    val autoPreview: Boolean = false, val mobilePreview: Boolean = true, val wifiImages: Boolean = false,
    val muted: Boolean = true, val loop: Boolean = true, val speed: Float = 1f,
    val mirror: Boolean = false, val saturation: Float = 1f, val brightness: Float = 1f,
    val contrast: Float = 1f, val screenshot: Boolean = true, val lightTheme:Boolean = false,
    val columns: Int = 2, val themeMode:String = "system", val cardSpacing:Float = 8f,
    val librarySort:Int = 0,val gestures:Boolean = true, val showOtherTags:Boolean = true,
    val fontScale:Float = 1f, val tagVerticalSpacing:Float = 16f,
    val accent:String = "default",
)

object PreferenceRules {
    val columnOptions = listOf(1,2,3,4,6,8,12)
    val themeModes = setOf("system","dark","light")
    val accents = setOf("default","mint","pink","amber")
    fun palette(value:String?)=when(value){"blue"->"mint";in accents->value!!;else->"default"}
    val fixedFlags=mapOf("gestures" to true,"screenshot" to true,"loop" to true,"muted" to true,"mobile" to true,"wifiImages" to false)
    fun legacyTheme(mode:String?,light:Boolean?,hasLegacyData:Boolean):String = mode?.takeIf{it in themeModes} ?: when(light){true->"light";false->"dark";null->if(hasLegacyData)"dark" else "system"}
    val languages = setOf("system", "en", "ja", "zh-TW", "zh-CN", "fr", "es", "ru", "ko")
    fun resolveLanguage(language: String, systemTag: String): String {
        require(language in languages)
        if (language != "system") return language
        val locale=java.util.Locale.forLanguageTag(systemTag)
        return when(locale.language){
            "zh"->if(locale.script=="Hant" || locale.country in setOf("TW","HK","MO"))"zh-TW" else "zh-CN"
            "ja","fr","es","ru","ko"->locale.language
            else->"en"
        }
    }
    fun platformLanguage(tag:String):String {
        if(tag.isBlank())return "system"
        val locale=java.util.Locale.forLanguageTag(tag.substringBefore(','))
        return when(locale.language){"ja","fr","es","ru","ko"->locale.language;"zh"->if(locale.script=="Hant" || locale.country in setOf("TW","HK","MO"))"zh-TW"else"zh-CN";else->"en"}
    }
    // Traditional Chinese translates the native app; the official site only offers en/ja.
    fun siteLanguage(language: String): String { require(language in languages); return if(language == "ja") "ja" else "en" }
    fun acceptLanguage(language: String): String { require(language in languages); return when(language){"zh-TW"->"zh-TW,zh;q=0.9,en;q=0.7";"zh-CN"->"zh-CN,zh;q=0.9,en;q=0.7";"system"->"en";else->language} }
    val speeds = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
    fun history(previous: List<String>, query: String): List<String> = query.trim().let { text ->
        (if (text.isBlank()) previous else listOf(text) + previous).filter(String::isNotBlank).distinct().take(50)
    }
    fun advanced(query: String, order: String, rating: String): String {
        return SearchSyntax.filters(query, order, rating)
    }
}

class PreferencesStore(context: Context,private val store:DataStore<Preferences> = context.applicationContext.preferences) {
    private val data = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    private fun Preferences.flag(key: String, default: Boolean) = this[booleanPreferencesKey(key)] ?: default
    val settings: Flow<AppSettings> = data.map { p ->
        AppSettings(language = p[stringPreferencesKey("language")]?.takeIf { it in PreferenceRules.languages } ?: "system",
            showCopyright=p.flag("copyright",true),showArtist=p.flag("artist",true),showScore=p.flag("score",true),
            wrapTags=p.flag("wrap",true),cropThumbnails=p.flag("crop",false),autoPreview=p.flag("auto",false),
            mobilePreview=true,wifiImages=false,muted=true,
            loop=true,speed=p[floatPreferencesKey("speed")]?.takeIf { it in PreferenceRules.speeds } ?: 1f,
            mirror=p.flag("mirror",false),saturation=(p[floatPreferencesKey("saturation")] ?: 1f).coerceIn(0f,2f),
            brightness=(p[floatPreferencesKey("brightness")] ?: 1f).coerceIn(0.5f,1.5f),contrast=(p[floatPreferencesKey("contrast")] ?: 1f).coerceIn(0.5f,1.5f),
            screenshot=true,lightTheme=p.flag("lightTheme",false),
            columns=(p[intPreferencesKey("columns")] ?: 2).takeIf{it in PreferenceRules.columnOptions} ?: 2,
            themeMode=PreferenceRules.legacyTheme(p[stringPreferencesKey("themeMode")],p[booleanPreferencesKey("lightTheme")],p.asMap().keys.any{it.name!="locale_last_applied"}),
            cardSpacing=(p[floatPreferencesKey("cardSpacing")] ?: 8f).coerceIn(0f,10f),gestures=true,
            librarySort=(p[intPreferencesKey("librarySort")] ?: 0).coerceIn(0,3),showOtherTags=p.flag("otherTags",true),
            fontScale=p[floatPreferencesKey("fontScale")]?.takeIf{it.isFinite() && it in 0.75f..1.25f} ?: 1f,
            tagVerticalSpacing=p[floatPreferencesKey("tagVerticalSpacing")]?.takeIf{it.isFinite() && it in 0f..20f} ?: 16f,
            accent=PreferenceRules.palette(p[stringPreferencesKey("accent")]))
    }
    val quiz:Flow<QuizSettings> = data.map{p->QuizSettings((p[intPreferencesKey("quiz_count")] ?: 20).takeIf{it in QuizSettings.counts} ?: 20,p[booleanPreferencesKey("quiz_custom")] ?: false,p[stringPreferencesKey("quiz_pool")]?.split('\n').orEmpty().filter{LibraryCodec.tagName(it)}.distinct().take(QuizSettings.MAX_WORKS))}
    suspend fun quizWork(value:String){require(LibraryCodec.tagName(value));store.edit{p->val previous=p[stringPreferencesKey("quiz_pool")]?.split('\n').orEmpty().filter{LibraryCodec.tagName(it)};p[stringPreferencesKey("quiz_pool")]=QuizPoolRules.add(previous,value).joinToString("\n")}}
    suspend fun quizMode(custom:Boolean){store.edit{it[booleanPreferencesKey("quiz_custom")]=custom}}
    suspend fun quizCount(count:Int){require(count in QuizSettings.counts);store.edit{it[intPreferencesKey("quiz_count")]=count}}
    suspend fun quizRemoveWork(value:String){store.edit{p->p[stringPreferencesKey("quiz_pool")]=p[stringPreferencesKey("quiz_pool")]?.split('\n').orEmpty().filterNot{it==value}.joinToString("\n")}}
    suspend fun quizClearWorks(){store.edit{it.remove(stringPreferencesKey("quiz_pool"))}}
    suspend fun quiz(value:QuizSettings){require(value.count in QuizSettings.counts && value.works.size<=QuizSettings.MAX_WORKS && value.works.all{LibraryCodec.tagName(it)});store.edit{it[intPreferencesKey("quiz_count")]=value.count;it[booleanPreferencesKey("quiz_custom")]=value.custom;it[stringPreferencesKey("quiz_pool")]=value.works.distinct().joinToString("\n")}}
    val history: Flow<List<String>> = data.map { PreferenceRules.history(it[stringPreferencesKey("history")]?.split('\n').orEmpty(), "") }
    val localeState:Flow<Pair<String,String?>> = data.map { p ->
        (p[stringPreferencesKey("language")]?.takeIf{it in PreferenceRules.languages} ?: "system") to p[stringPreferencesKey("locale_last_applied")]
    }
    suspend fun initializeTheme(){store.edit{p->
        if(p[stringPreferencesKey("themeMode")]==null)p[stringPreferencesKey("themeMode")]=PreferenceRules.legacyTheme(null,p[booleanPreferencesKey("lightTheme")],p.asMap().keys.any{it.name!="locale_last_applied"})
        p.remove(booleanPreferencesKey("tagTranslations"))
        PreferenceRules.fixedFlags.keys.forEach{p.remove(booleanPreferencesKey(it))}
        p[stringPreferencesKey("accent")]=PreferenceRules.palette(p[stringPreferencesKey("accent")])
    }}
    suspend fun recordAppliedLocale(value:String){store.edit{it[stringPreferencesKey("locale_last_applied")]=value}}
    suspend fun remember(query: String) { store.edit { p ->
        val previous=p[stringPreferencesKey("history")]?.split('\n').orEmpty()
        p[stringPreferencesKey("history")] = PreferenceRules.history(previous,query.replace('\n',' ')).joinToString("\n")
    } }
    suspend fun clearHistory() { store.edit { it.remove(stringPreferencesKey("history")) } }
    suspend fun deleteHistory(query:String) { store.edit { p ->
        p[stringPreferencesKey("history")] = p[stringPreferencesKey("history")]?.split('\n').orEmpty().filterNot { it==query }.joinToString("\n")
    } }
    suspend fun librarySort(value:Int){require(value in 0..3);store.edit{it[intPreferencesKey("librarySort")]=value}}
    suspend fun columns(value:Int) { require(value in PreferenceRules.columnOptions);store.edit{it[intPreferencesKey("columns")]=value} }
    suspend fun flag(name: String,value: Boolean) { require(name in setOf("copyright","artist","score","wrap","crop","auto","mobile","wifiImages","muted","loop","mirror","screenshot","lightTheme","gestures","otherTags")); store.edit { if(name in PreferenceRules.fixedFlags)it.remove(booleanPreferencesKey(name))else it[booleanPreferencesKey(name)]=value;if(name=="lightTheme")it[stringPreferencesKey("themeMode")]=if(value)"light"else"dark" } }
    suspend fun theme(value:String){require(value in PreferenceRules.themeModes);store.edit{it[stringPreferencesKey("themeMode")]=value;if(value!="system")it[booleanPreferencesKey("lightTheme")]=value=="light"}}
    suspend fun accent(value:String){require(value in PreferenceRules.accents || value=="blue");store.edit{it[stringPreferencesKey("accent")]=PreferenceRules.palette(value)}}
    suspend fun language(value: String) { require(value in PreferenceRules.languages);store.edit { it[stringPreferencesKey("language")]=value } }
    suspend fun number(name: String,value: Float) { require(value.isFinite()); require(when(name) { "speed"->value in PreferenceRules.speeds; "brightness","contrast"->value in 0.5f..1.5f; "saturation"->value in 0f..2f; "cardSpacing"->value in 0f..10f; "fontScale"->value in 0.75f..1.25f; "tagVerticalSpacing"->value in 0f..20f; else->false });store.edit { it[floatPreferencesKey(name)]=value } }
}
