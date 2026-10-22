// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

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
import androidx.compose.ui.platform.LocalContext
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
    Column(Modifier.padding(16.dp)){
        Text(text("App language","アプリの言語"),style=MaterialTheme.typography.titleLarge)
        Text(text("The official website supports English and Japanese. Traditional Chinese translates the native app; official pages use English.","公式サイトは英語・日本語のみです。繁体字中国語はアプリ画面を翻訳し、公式ページは英語になります。"),style=MaterialTheme.typography.bodySmall)
        for((code,label)in listOf("en" to "English","ja" to "日本語","zh-TW" to "繁體中文"))Row{
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
        Text(text("Appearance","外観"),style=MaterialTheme.typography.titleLarge)
        SettingSwitch(text("Light background","明るい背景"),settings.lightTheme){model.flag("lightTheme",it)}
        SettingSwitch(text("Crop rectangular thumbnails","長方形サムネイルを切り抜く"),settings.cropThumbnails){model.flag("crop",it)}
        SettingSwitch(text("Wrap tags","タグを折り返す"),settings.wrapTags){model.flag("wrap",it)}
        SettingSwitch(text("Copyright tags","作品タグ"),settings.showCopyright){model.flag("copyright",it)}
        SettingSwitch(text("Animator tags","アニメータータグ"),settings.showArtist){model.flag("artist",it)}
        SettingSwitch("Score",settings.showScore){model.flag("score",it)}
        HorizontalDivider();Text(text("Video","動画"),style=MaterialTheme.typography.titleLarge)
        SettingSwitch(text("Auto preview visible posts","表示中の投稿を自動プレビュー"),settings.autoPreview){model.flag("auto",it)}
        SettingSwitch(text("Allow previews on metered networks","従量制回線でプレビューを許可"),settings.mobilePreview){model.flag("mobile",it)}
        SettingSwitch(text("Default mute","既定でミュート"),settings.muted){model.flag("muted",it)}
        SettingSwitch(text("Default loop","既定でループ"),settings.loop){model.flag("loop",it)}
        SettingSwitch(text("Enable snapshot sharing","スクリーンショット共有"),settings.screenshot){model.flag("screenshot",it)}
        SettingSwitch(text("Mirror video","動画を左右反転"),settings.mirror){model.flag("mirror",it)}
        Text(text("Default playback speed","既定の再生速度"))
        FlowRow{for(speed in PreferenceRules.speeds)TextButton(onClick={model.number("speed",speed)}){Text(if(settings.speed==speed)"[$speed×]"else"$speed×")}}
        for((key,value,label)in listOf(Triple("brightness",settings.brightness,text("Brightness","明るさ")),Triple("contrast",settings.contrast,text("Contrast","コントラスト")),Triple("saturation",settings.saturation,text("Saturation","彩度")))){
            Text("$label: ${"%.2f".format(value)}")
            var draft by remember(value){mutableFloatStateOf(value)}
            Slider(value=draft,onValueChange={draft=it},onValueChangeFinished={model.number(key,draft)},valueRange=if(key=="saturation")0f..2f else 0.5f..1.5f)
        }
        TextButton(onClick={model.number("brightness",1f);model.number("contrast",1f);model.number("saturation",1f);model.flag("mirror",false)}){Text(text("Reset video effects","動画効果をリセット"))}
        HorizontalDivider();Text(text("Network and storage","ネットワーク・保存領域"),style=MaterialTheme.typography.titleLarge)
        SettingSwitch(text("Load images only on unmetered networks","非従量制回線のみで画像を読み込む"),settings.wifiImages){model.flag("wifiImages",it)}
        TextButton(enabled=!clearing,onClick={scope.launch{
            clearing=true
            try{withContext(Dispatchers.IO){Coil.imageLoader(context).diskCache?.clear();java.io.File(context.cacheDir,"frames").deleteRecursively();java.io.File(context.cacheDir,"screenshots").deleteRecursively()};Coil.imageLoader(context).memoryCache?.clear();message="Cache cleared."}
            catch(e:Exception){message="Unable to clear all cache files."}finally{clearing=false}
        }}){Text(text("Clear media cache","メディアキャッシュを削除"))}
        TextButton(onClick={model.clearHistory();message="Search history cleared."}){Text(text("Clear search history","検索履歴を削除"))}
        message?.let{Text(translateMessage(it))}
    }
}

@Composable private fun SettingSwitch(label:String,value:Boolean,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f).padding(top=12.dp));Switch(checked=value,onCheckedChange=change)}}
