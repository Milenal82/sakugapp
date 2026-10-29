// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
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
fun SettingsScreen(settings:AppSettings,model:AppViewModel,language:()->Unit,about:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var message by remember{mutableStateOf<String?>(null)}
    var clearing by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        val recording by model.recordHistory.collectAsState()
        SettingsGroup(R.drawable.ic_palette,text(R.string.ui_appearance)){
            SettingControl{
                Text(text(R.string.ui_theme_mode),style=MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){for((mode,label)in listOf("system" to R.string.ui_theme_system,"dark" to R.string.ui_theme_dark,"light" to R.string.ui_theme_light))FilterChip(selected=settings.themeMode==mode,onClick={model.theme(mode)},modifier=Modifier.semantics{contentDescription=context.getString(label)},label={Text(text(label))})}
            }
            val languageLabel=when(settings.language){"en"->R.string.language_english;"zh-CN"->R.string.language_simplified;"zh-TW"->R.string.language_traditional;"ja"->R.string.language_japanese;"fr"->R.string.language_french;"es"->R.string.language_spanish;"ru"->R.string.language_russian;"ko"->R.string.language_korean;else->R.string.ui_follow_system}
            SettingAction(text(R.string.ui_app_language),text(languageLabel),language)
            SettingControl{
                Text(text(R.string.ui_card_spacing,settings.cardSpacing.toInt().toString()),style=MaterialTheme.typography.bodyLarge)
                var spacing by remember(settings.cardSpacing){mutableFloatStateOf(settings.cardSpacing)}
                Slider(value=spacing,onValueChange={spacing=it},onValueChangeFinished={model.number("cardSpacing",spacing)},valueRange=0f..10f,steps=9)
            }
            SettingSwitch(text(R.string.ui_crop_rectangular_thumbnails),settings.cropThumbnails){model.flag("crop",it)}
            SettingSwitch(text(R.string.ui_wrap_tags),settings.wrapTags){model.flag("wrap",it)}
            SettingSwitch(text(R.string.ui_copyright_tags),settings.showCopyright){model.flag("copyright",it)}
            SettingSwitch(text(R.string.ui_animator_tags),settings.showArtist){model.flag("artist",it)}
            SettingSwitch(text(R.string.ui_show_other_tags),settings.showOtherTags){model.flag("otherTags",it)}
            SettingSwitch(text(R.string.ui_score),settings.showScore){model.flag("score",it)}
        }
        SettingsGroup(R.drawable.ic_tune,text(R.string.ui_browsing_search)){
            SettingControl{
                Text(text(R.string.ui_grid_columns),style=MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){for(columns in GridPolicy.options(LocalConfiguration.current.smallestScreenWidthDp))FilterChip(selected=settings.columns==columns,onClick={model.columns(columns)},modifier=Modifier.testTag("setting-columns-$columns"),label={Text(columns.toString())})}
            }
            SettingSwitch(text(R.string.ui_gestures_enabled),settings.gestures){model.flag("gestures",it)}
            SettingAction(text(R.string.ui_clear_search_history),onClick={model.clearHistory();message=context.getString(R.string.ui_search_history_cleared)})
        }
        SettingsGroup(R.drawable.ic_next,text(R.string.ui_video)){
            SettingSwitch(text(R.string.ui_auto_preview_visible_posts),settings.autoPreview){model.flag("auto",it)}
            SettingSwitch(text(R.string.ui_allow_previews_on_metered_networks),settings.mobilePreview){model.flag("mobile",it)}
            SettingSwitch(text(R.string.ui_default_mute),settings.muted){model.flag("muted",it)}
            SettingSwitch(text(R.string.ui_default_loop),settings.loop){model.flag("loop",it)}
            SettingSwitch(text(R.string.ui_enable_snapshot_sharing),settings.screenshot){model.flag("screenshot",it)}
            SettingSwitch(text(R.string.ui_mirror_video),settings.mirror){model.flag("mirror",it)}
            SettingControl{
                Text(text(R.string.ui_default_playback_speed),style=MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){for(speed in PreferenceRules.speeds)FilterChip(selected=settings.speed==speed,onClick={model.number("speed",speed)},label={Text("$speed×")})}
            }
            for((key,value,label)in listOf(Triple("brightness",settings.brightness,text(R.string.ui_brightness)),Triple("contrast",settings.contrast,text(R.string.ui_contrast)),Triple("saturation",settings.saturation,text(R.string.ui_saturation)))){
                SettingControl{
                    Text(text(R.string.ui_label_value,label,"%.2f".format(value)),style=MaterialTheme.typography.bodyLarge)
                    var draft by remember(value){mutableFloatStateOf(value)}
                    Slider(value=draft,onValueChange={draft=it},onValueChangeFinished={model.number(key,draft)},valueRange=if(key=="saturation")0f..2f else 0.5f..1.5f)
                }
            }
            SettingAction(text(R.string.ui_reset_video_effects),onClick={model.number("brightness",1f);model.number("contrast",1f);model.number("saturation",1f);model.flag("mirror",false)})
        }
        SettingsGroup(R.drawable.ic_download,text(R.string.ui_network_and_storage)){
            SettingSwitch(text(R.string.ui_load_images_only_on_unmetered_networks),settings.wifiImages){model.flag("wifiImages",it)}
            SettingAction(text(R.string.ui_clear_media_cache),enabled=!clearing,onClick={scope.launch{
                clearing=true
                try{withContext(Dispatchers.IO){Coil.imageLoader(context).diskCache?.clear();java.io.File(context.cacheDir,"frames").deleteRecursively();java.io.File(context.cacheDir,"screenshots").deleteRecursively()};Coil.imageLoader(context).memoryCache?.clear();message=context.getString(R.string.ui_cache_cleared)}
                catch(e:CancellationException){throw e}catch(e:Exception){message=context.getString(R.string.ui_unable_to_clear_all_cache_files)}finally{clearing=false}
            }})
        }
        SettingsGroup(R.drawable.ic_folder,text(R.string.ui_favorites_backup)){BackupActions(model)}
        SettingsGroup(R.drawable.ic_history,text(R.string.ui_privacy_history)){
            SettingSwitch(text(R.string.ui_record_browsing_history),recording){value->scope.launch{try{model.personal.recording(value)}catch(e:CancellationException){throw e}catch(e:Exception){message=libraryError(context,e)}}}
        }
        SettingsGroup(R.drawable.ic_book,text(R.string.ui_about_and_licenses)){
            SettingAction(text(R.string.ui_about_and_licenses),com.sakugapp.BuildConfig.VERSION_NAME,about)
        }
        message?.let{Text(translateMessage(it),modifier=Modifier.padding(horizontal=4.dp),style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable private fun SettingsGroup(@DrawableRes icon:Int,title:String,content:@Composable ColumnScope.()->Unit){
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(horizontal=12.dp)){
            Row(Modifier.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){Icon(painterResource(icon),null,Modifier.padding(6.dp).size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
                Text(title,style=MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable private fun SettingControl(content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
}

@Composable private fun SettingAction(label:String,value:String?=null,onClick:()->Unit,enabled:Boolean=true){
    Row(Modifier.fillMaxWidth().clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
            Text(label,style=MaterialTheme.typography.bodyLarge,color=if(enabled)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=.38f))
            value?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
}

@Composable private fun SettingSwitch(label:String,value:Boolean,change:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().toggleable(value=value,role=Role.Switch,onValueChange=change).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
        Switch(checked=value,onCheckedChange=null)
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
}
