// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import com.google.gson.JsonParser
import com.sakugapp.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TagSuggestion(val name:String,val category:TagCategory,val count:Long?)

interface SuggestionSource { suspend fun suggestions(prefix:String):List<TagSuggestion> }

object SuggestionParser {
    fun parse(body:String):List<TagSuggestion> = try {
        val root=JsonParser.parseString(body)
        if(!root.isJsonArray)throw DataFailure("Invalid tag suggestion response.")
        root.asJsonArray.mapNotNull { element ->
            if(!element.isJsonObject)return@mapNotNull null
            val o=element.asJsonObject
            val name=o["name"]?.takeIf{it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString
                ?.takeIf{it.isNotBlank() && it.none(Char::isWhitespace)} ?: return@mapNotNull null
            val type=o["type"]?.takeIf{it.isJsonPrimitive}?.asString?.toIntOrNull()
            val count=o["count"]?.takeIf{it.isJsonPrimitive}?.asString?.toLongOrNull()?.takeIf{it>=0}
            TagSuggestion(name,when(type){1->TagCategory.ARTIST;3->TagCategory.COPYRIGHT;0,4->TagCategory.OTHER;else->TagCategory.UNKNOWN},count)
        }.distinctBy{it.name}.take(8)
    }catch(e:DataFailure){throw e}catch(e:Exception){throw DataFailure("Invalid tag suggestion response.",e)}
}

/** Public, bounded, cancellable autocomplete. Does not send account cookies or perform writes. */
class TagSuggestionRepository(
    private val base:HttpUrl="https://www.sakugabooru.com/".toHttpUrl(),
    private val client:OkHttpClient=OkHttpClient.Builder().callTimeout(15,TimeUnit.SECONDS).retryOnConnectionFailure(false).build(),
    private val clock:()->Long={System.nanoTime()/1_000_000},
    private val permit:suspend()->Unit={PublicReadThrottle.awaitTurn()},
):SuggestionSource {
    private val cache=object:LinkedHashMap<String,Pair<Long,List<TagSuggestion>>>(32,0.75f,true){
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Pair<Long,List<TagSuggestion>>>?)=size>32
    }
    override suspend fun suggestions(prefix:String):List<TagSuggestion> {
        if(prefix.length !in 2..100 || prefix.any{it.isWhitespace() || it in ":*"})return emptyList()
        synchronized(cache){cache[prefix]?.let{if(clock()-it.first<120_000)return it.second}}
        permit()
        val url=base.resolve("tag.json")!!.newBuilder().addQueryParameter("name",prefix).addQueryParameter("limit","8").build()
        val result=SuggestionParser.parse(fetch(url))
        synchronized(cache){cache[prefix]=clock() to result}
        return result
    }
    private suspend fun fetch(url:HttpUrl):String=suspendCancellableCoroutine { continuation ->
        val call=client.newCall(Request.Builder().url(url).header("Accept","application/json")
            .header("User-Agent","Sakugapp/${BuildConfig.VERSION_NAME} (+https://github.com/Milenal82/sakugapp)").build())
        continuation.invokeOnCancellation{call.cancel()}
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException){if(!continuation.isCancelled)continuation.resumeWithException(DataFailure("Network connection failed. Check the connection and retry.",e))}
            override fun onResponse(call:Call,response:Response){
                try {
                    val body=response.use {
                        if(!it.isSuccessful)throw DataFailure(httpError(it.code))
                        val content=it.body ?: throw DataFailure("The server returned an empty response.")
                        if(content.source().request(512L*1024+1))throw DataFailure("The server response is too large.")
                        content.string()
                    }
                    if(!continuation.isCancelled)continuation.resume(body)
                }catch(e:Exception){if(!continuation.isCancelled)continuation.resumeWithException(if(e is DataFailure)e else DataFailure("Unable to read the network response.",e))}
            }
        })
    }
}
