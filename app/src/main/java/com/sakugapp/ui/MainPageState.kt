// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.Saver

/** Three main-page UI snapshots, not Posts/media data. Each transitioning entry has its own registry. */
internal class MainPageState(restored:Map<String,Map<String,List<Any?>>> = emptyMap()) {
    private val saved=restored.toMutableMap()
    private data class Entry(val route:String,val registry:SaveableStateRegistry)
    private val entries=mutableMapOf<String,Entry>()
    private val owners=mutableMapOf<String,String>()
    fun values(route:String)=saved[route]
    fun attach(key:String,route:String,registry:SaveableStateRegistry){entries[key]=Entry(route,registry)}
    fun activate(key:String){entries[key]?.let{owners[it.route]=key}}
    fun capture(){owners.values.toList().forEach{key->entries[key]?.let{saved[it.route]=it.registry.performSave()}}}
    fun detach(key:String){entries[key]?.let{if(owners[it.route]==key)saved[it.route]=it.registry.performSave()};entries.remove(key)}
    fun snapshot():HashMap<String,Map<String,List<Any?>>>{capture();return HashMap(saved)}
    companion object {
        val StateSaver=Saver<MainPageState,HashMap<String,Map<String,List<Any?>>>>(
            save={it.snapshot()},restore={MainPageState(it)})
    }
}

/** Separate entry registries avoid duplicate route keys during rapid interrupted transitions.
 * Main-page saveable fields use explicit names: automatic composition hashes include the entry ID
 * and cannot be copied between newly created entries. Transient widget/focus state stays per entry.
 */
@Composable internal fun MainPageState.Content(route:String,entryKey:String,active:Boolean,content:@Composable ()->Unit){
    val parent=LocalSaveableStateRegistry.current
    val registry=remember(this,entryKey,parent){SaveableStateRegistry(values(route)){parent?.canBeSaved(it) ?: true}}
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry,content=content)
    // Register after children: disposal runs in reverse, before their saveable providers unregister.
    DisposableEffect(this,entryKey,registry){attach(entryKey,route,registry);onDispose{detach(entryKey)}}
    SideEffect{if(active)activate(entryKey)}
}
