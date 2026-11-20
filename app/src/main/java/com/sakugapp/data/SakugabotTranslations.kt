// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sakugapp.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ChineseTagName(val original:String, val category:TagCategory, val chinese:String, val aliases:Set<String>)

interface DetailTranslationSource {
    suspend fun lookup(tag:Tag):ChineseTagName?
}

/** Only explicit Chinese names, or a main name corroborated by the record's Chinese wiki title. */
object SakugabotNameParser {
    private fun JsonObject.string(key:String):String? = get(key)?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString?.trim()?.takeIf { it.isNotEmpty() && it.length<=300 && it.none(Char::isISOControl) }

    fun parse(body:String, tag:Tag):ChineseTagName? = runCatching {
        val expectedType=when(tag.category){TagCategory.ARTIST->"1";TagCategory.COPYRIGHT->"3";else->return null}
        val root=JsonParser.parseString(body).asJsonObject
        if(root.string("name")!=tag.name || root["type"]?.asString!=expectedType)return null
        val details=root.getAsJsonArray("detail") ?: return null
        val attributes=details.filter{it.isJsonObject}.mapNotNull { element->
            val entry=element.asJsonObject
            val code=entry.getAsJsonObject("attribute")?.string("code") ?: return@mapNotNull null
            entry.string("value")?.let{code to it}
        }.groupBy({it.first},{it.second})
        fun value(code:String)=attributes[code]?.distinct()?.singleOrNull()
        val chinese=value("name_zh") ?: value("name_main")?.takeIf { main->
            // Han characters alone do not prove that a Japanese main name is Chinese.
            val wiki=value("wiki_zh")?.toHttpUrlOrNullSafely()
            wiki?.host=="zh.wikipedia.org" && wiki.scheme=="https" &&
                wiki.pathSegments.size==2 && wiki.pathSegments[0] in setOf("wiki","zh-cn","zh-tw","zh-hans","zh-hant") &&
                wiki.pathSegments[1].replace('_',' ')==main.replace('_',' ') &&
                main.any{Character.UnicodeScript.of(it.code)==Character.UnicodeScript.HAN}
        } ?: return null
        ChineseTagName(tag.name,tag.category,chinese,
            listOfNotNull(tag.name,value("name_en"),value("name_ja"),value("name_main"),chinese).toSet())
    }.getOrNull()

    private fun String.toHttpUrlOrNullSafely()=runCatching{toHttpUrl()}.getOrNull()

}

/** A source is not a tag. Only a unique, whole-string copyright alias is safe to replace. */
fun translatedSource(source:String?,names:Collection<ChineseTagName>):String? {
    val original=source?.trim()?.takeIf{it.isNotEmpty()} ?: return null
    if(original.toHttpUrlOrNull()!=null || original.contains("://"))return null
    return names.filter{it.category==TagCategory.COPYRIGHT && original in it.aliases}
        .map{it.chinese}.distinct().singleOrNull()?.takeIf{it!=original}
}

/** App-scoped, bounded memory cache. No account client, cookies, writes, disk index or feed lookups. */
class SakugabotTranslations(
    private val base:HttpUrl="https://sakugabot.pw/api/".toHttpUrl(),
    private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS)
        .readTimeout(6,TimeUnit.SECONDS).callTimeout(8,TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build(),
    private val clock:()->Long={System.nanoTime()/1_000_000},
    private val intervalMs:Long=1_000,
    private val capacity:Int=256,
):DetailTranslationSource {
    private data class Entry(val expires:Long,val name:ChineseTagName?)
    private data class Reply(val code:Int,val body:String?,val retryAfterMs:Long)
    private val mutex=Mutex()
    private val cache=LinkedHashMap<Tag,Entry>(16,0.75f,true)
    private var nextRequest=0L
    private var cooldown=0L
    init { require(intervalMs>=0 && capacity in 1..256) }

    override suspend fun lookup(tag:Tag):ChineseTagName? {
        if(tag.category !in setOf(TagCategory.ARTIST,TagCategory.COPYRIGHT) ||
            !LibraryCodec.tagName(tag.name) || tag.name in setOf(".",".."))return null
        return mutex.withLock {
            val now=clock()
            cache[tag]?.let{if(now<it.expires)return@withLock it.name else cache.remove(tag)}
            if(now<cooldown)return@withLock null
            delay((nextRequest-now).coerceAtLeast(0))
            nextRequest=clock()+intervalMs
            try {
                val url=base.newBuilder().addPathSegment("tags").addPathSegment(tag.name).addPathSegment("").build()
                val reply=fetch(url)
                val name=if(reply.code==200 && reply.body!=null)withContext(Dispatchers.Default){SakugabotNameParser.parse(reply.body,tag)}else null
                val ttl=when{ name!=null->24*60*60_000L;reply.code==200 || reply.code==404->60*60_000L;else->5*60_000L }
                if(reply.code in setOf(421,429))cooldown=clock()+reply.retryAfterMs
                else if(reply.code!=200 && reply.code!=404)cooldown=clock()+60_000
                remember(tag,Entry(clock()+ttl,name))
                name
            }catch(e:CancellationException){throw e}
            catch(_:Exception){cooldown=clock()+60_000;remember(tag,Entry(clock()+5*60_000,null));null}
        }
    }

    private fun remember(tag:Tag,entry:Entry){
        cache[tag]=entry
        while(cache.size>capacity)cache.remove(cache.keys.first())
    }

    private suspend fun fetch(url:HttpUrl):Reply=suspendCancellableCoroutine { continuation->
        val call=client.newCall(Request.Builder().url(url).header("Accept","application/json")
            .header("User-Agent","Sakugapp/${BuildConfig.VERSION_NAME} (+https://github.com/Milenal82/sakugapp)").build())
        continuation.invokeOnCancellation{call.cancel()}
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException){if(!continuation.isCancelled)continuation.resumeWithException(e)}
            override fun onResponse(call:Call,response:Response){
                try {
                    val reply=response.use {
                        val body=if(it.code==200)it.body?.let{content->
                            if(content.source().request(512L*1024+1))throw IOException("Oversized translation response")
                            content.string()
                        }else null
                        val retry=it.header("Retry-After")?.let{value->value.toLongOrNull()?.coerceIn(1,3600)?.times(1000)
                            ?: runCatching{ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis()}.getOrNull()}
                        Reply(it.code,body,(retry ?: 60_000).coerceIn(1_000,3_600_000))
                    }
                    if(!continuation.isCancelled)continuation.resume(reply)
                }catch(e:Exception){if(!continuation.isCancelled)continuation.resumeWithException(e)}
            }
        })
    }
}
