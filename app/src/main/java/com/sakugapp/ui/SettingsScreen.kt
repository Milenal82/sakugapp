// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.sakugapp.data.*
import coil.Coil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

fun meteredNetwork(context:Context)=callbackFlow {
    val manager=context.getSystemService(ConnectivityManager::class.java)
    fun update(){trySend(manager.isActiveNetworkMetered)}
    val listener=object:ConnectivityManager.NetworkCallback(){override fun onAvailable(network:Network)=update();override fun onLost(network:Network)=update();override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities)=update()}
    manager.registerDefaultNetworkCallback(listener);update();awaitClose{manager.unregisterNetworkCallback(listener)}
}

@Composable
fun LanguageScreen(current:String,choose:(String)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Text(text(R.string.ui_app_language),style=MaterialTheme.typography.titleLarge)
        Text(text(R.string.ui_language_explanation),style=MaterialTheme.typography.bodySmall)
        for((code,label)in listOf("system" to text(R.string.ui_follow_system),"en" to text(R.string.language_english),"zh-CN" to text(R.string.language_simplified),"ja" to text(R.string.language_japanese),"zh-TW" to text(R.string.language_traditional),"fr" to text(R.string.language_french),"es" to text(R.string.language_spanish),"ru" to text(R.string.language_russian),"ko" to text(R.string.language_korean)))Row{
            RadioButton(selected=current==code,onClick={choose(code)})
            TextButton(onClick={choose(code)}){Text(label)}
        }
    }
}

@OptIn(coil.annotation.ExperimentalCoilApi::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings:AppSettings,model:AppViewModel) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var message by remember{mutableStateOf<String?>(null)}
    var clearing by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        val recording by model.recordHistory.collectAsState()
        SettingSwitch(text(R.string.ui_record_browsing_history),recording){value->scope.launch{try{model.personal.recording(value)}catch(e:CancellationException){throw e}catch(e:Exception){message=libraryError(context,e)}}}
        Text(text(R.string.ui_appearance),style=MaterialTheme.typography.titleLarge)
        Text(text(R.string.ui_grid_columns))
        FlowRow{for(columns in GridPolicy.options(LocalConfiguration.current.smallestScreenWidthDp))FilterChip(selected=settings.columns==columns,onClick={model.columns(columns)},modifier=Modifier.testTag("setting-columns-$columns"),label={Text(columns.toString())})}
        Text(text(R.string.ui_theme_mode))
        FlowRow{for((mode,label)in listOf("system" to R.string.ui_theme_system,"dark" to R.string.ui_theme_dark,"light" to R.string.ui_theme_light))FilterChip(selected=settings.themeMode==mode,onClick={model.theme(mode)},modifier=Modifier.semantics{contentDescription=context.getString(label)},label={Text(text(label))})}
        Text(text(R.string.ui_card_spacing,settings.cardSpacing.toInt().toString()))
        var spacing by remember(settings.cardSpacing){mutableFloatStateOf(settings.cardSpacing)}
        Slider(value=spacing,onValueChange={spacing=it},onValueChangeFinished={model.number("cardSpacing",spacing)},valueRange=0f..10f,steps=9)
        SettingSwitch(text(R.string.ui_show_other_tags),settings.showOtherTags){model.flag("otherTags",it)}
        SettingSwitch(text(R.string.ui_gestures_enabled),settings.gestures){model.flag("gestures",it)}
        SettingSwitch(text(R.string.ui_crop_rectangular_thumbnails),settings.cropThumbnails){model.flag("crop",it)}
        SettingSwitch(text(R.string.ui_wrap_tags),settings.wrapTags){model.flag("wrap",it)}
        SettingSwitch(text(R.string.ui_copyright_tags),settings.showCopyright){model.flag("copyright",it)}
        SettingSwitch(text(R.string.ui_animator_tags),settings.showArtist){model.flag("artist",it)}
        SettingSwitch(text(R.string.ui_score),settings.showScore){model.flag("score",it)}
        HorizontalDivider();Text(text(R.string.ui_video),style=MaterialTheme.typography.titleLarge)
        SettingSwitch(text(R.string.ui_auto_preview_visible_posts),settings.autoPreview){model.flag("auto",it)}
        SettingSwitch(text(R.string.ui_allow_previews_on_metered_networks),settings.mobilePreview){model.flag("mobile",it)}
        SettingSwitch(text(R.string.ui_default_mute),settings.muted){model.flag("muted",it)}
        SettingSwitch(text(R.string.ui_default_loop),settings.loop){model.flag("loop",it)}
        SettingSwitch(text(R.string.ui_enable_snapshot_sharing),settings.screenshot){model.flag("screenshot",it)}
        SettingSwitch(text(R.string.ui_mirror_video),settings.mirror){model.flag("mirror",it)}
        Text(text(R.string.ui_default_playback_speed))
        FlowRow{for(speed in PreferenceRules.speeds)TextButton(onClick={model.number("speed",speed)}){Text(if(settings.speed==speed)"[$speed×]"else"$speed×")}}
        for((key,value,label)in listOf(Triple("brightness",settings.brightness,text(R.string.ui_brightness)),Triple("contrast",settings.contrast,text(R.string.ui_contrast)),Triple("saturation",settings.saturation,text(R.string.ui_saturation)))){
            Text(text(R.string.ui_label_value,label,"%.2f".format(value)))
            var draft by remember(value){mutableFloatStateOf(value)}
            Slider(value=draft,onValueChange={draft=it},onValueChangeFinished={model.number(key,draft)},valueRange=if(key=="saturation")0f..2f else 0.5f..1.5f)
        }
        TextButton(onClick={model.number("brightness",1f);model.number("contrast",1f);model.number("saturation",1f);model.flag("mirror",false)}){Text(text(R.string.ui_reset_video_effects))}
        HorizontalDivider();Text(text(R.string.ui_network_and_storage),style=MaterialTheme.typography.titleLarge)
        SettingSwitch(text(R.string.ui_load_images_only_on_unmetered_networks),settings.wifiImages){model.flag("wifiImages",it)}
        TextButton(enabled=!clearing,onClick={scope.launch{
            clearing=true
            try{withContext(Dispatchers.IO){Coil.imageLoader(context).diskCache?.clear();java.io.File(context.cacheDir,"frames").deleteRecursively();java.io.File(context.cacheDir,"screenshots").deleteRecursively()};Coil.imageLoader(context).memoryCache?.clear();message=context.getString(R.string.ui_cache_cleared)}
            catch(e:Exception){message=context.getString(R.string.ui_unable_to_clear_all_cache_files)}finally{clearing=false}
        }}){Text(text(R.string.ui_clear_media_cache))}
        TextButton(onClick={model.clearHistory();message=context.getString(R.string.ui_search_history_cleared)}){Text(text(R.string.ui_clear_search_history))}
        message?.let{Text(translateMessage(it))}
    }
}

@Composable private fun SettingSwitch(label:String,value:Boolean,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f).padding(top=12.dp));Switch(checked=value,onCheckedChange=change,modifier=Modifier.semantics{contentDescription=label})}}
