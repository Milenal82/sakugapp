// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.preferences by preferencesDataStore("settings_v1")

data class AppSettings(
    val language: String = "en", val showCopyright: Boolean = true, val showArtist: Boolean = true,
    val showScore: Boolean = true, val wrapTags: Boolean = true, val cropThumbnails: Boolean = false,
    val autoPreview: Boolean = false, val mobilePreview: Boolean = false, val wifiImages: Boolean = false,
    val muted: Boolean = true, val loop: Boolean = true, val speed: Float = 1f,
    val mirror: Boolean = false, val saturation: Float = 1f, val brightness: Float = 1f,
    val contrast: Float = 1f, val screenshot: Boolean = true,
)

object PreferenceRules {
    val languages = setOf("en", "ja", "zh-TW")
    // Traditional Chinese translates the native app; the official site only offers en/ja.
    fun siteLanguage(language: String): String { require(language in languages); return if(language == "ja") "ja" else "en" }
    fun acceptLanguage(language: String): String { require(language in languages); return if(language == "zh-TW") "zh-TW,zh;q=0.9,en;q=0.7" else language }
    val speeds = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
    fun history(previous: List<String>, query: String): List<String> = query.trim().let { text ->
        if (text.isBlank()) previous else (listOf(text) + previous.filter { it != text }).take(30)
    }
    fun advanced(query: String, order: String, rating: String): String {
        return SearchSyntax.filters(query, order, rating)
    }
}

class PreferencesStore(context: Context) {
    private val store = context.applicationContext.preferences
    private val data = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    private fun Preferences.flag(key: String, default: Boolean) = this[booleanPreferencesKey(key)] ?: default
    val settings: Flow<AppSettings> = data.map { p ->
        AppSettings(language = p[stringPreferencesKey("language")]?.takeIf { it in PreferenceRules.languages } ?: "en",
            showCopyright=p.flag("copyright",true),showArtist=p.flag("artist",true),showScore=p.flag("score",true),
            wrapTags=p.flag("wrap",true),cropThumbnails=p.flag("crop",false),autoPreview=p.flag("auto",false),
            mobilePreview=p.flag("mobile",false),wifiImages=p.flag("wifiImages",false),muted=p.flag("muted",true),
            loop=p.flag("loop",true),speed=p[floatPreferencesKey("speed")]?.takeIf { it in PreferenceRules.speeds } ?: 1f,
            mirror=p.flag("mirror",false),saturation=(p[floatPreferencesKey("saturation")] ?: 1f).coerceIn(0f,2f),
            brightness=(p[floatPreferencesKey("brightness")] ?: 1f).coerceIn(0.5f,1.5f),contrast=(p[floatPreferencesKey("contrast")] ?: 1f).coerceIn(0.5f,1.5f),
            screenshot=p.flag("screenshot",true))
    }
    val history: Flow<List<String>> = data.map { it[stringPreferencesKey("history")]?.split('\n')?.filter(String::isNotBlank).orEmpty() }
    suspend fun remember(query: String) { store.edit { p ->
        val previous=p[stringPreferencesKey("history")]?.split('\n').orEmpty()
        p[stringPreferencesKey("history")] = PreferenceRules.history(previous,query.replace('\n',' ')).joinToString("\n")
    } }
    suspend fun clearHistory() { store.edit { it.remove(stringPreferencesKey("history")) } }
    suspend fun flag(name: String,value: Boolean) { require(name in setOf("copyright","artist","score","wrap","crop","auto","mobile","wifiImages","muted","loop","mirror","screenshot")); store.edit { it[booleanPreferencesKey(name)]=value } }
    suspend fun language(value: String) { require(value in PreferenceRules.languages);store.edit { it[stringPreferencesKey("language")]=value } }
    suspend fun number(name: String,value: Float) { require(value.isFinite()); require(when(name) { "speed"->value in PreferenceRules.speeds; "brightness","contrast"->value in 0.5f..1.5f; "saturation"->value in 0f..2f; else->false });store.edit { it[floatPreferencesKey(name)]=value } }
}
