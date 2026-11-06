// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DirectoryTag(val id:Long,val name:String,val category:TagCategory,val count:Long)
object TagDirectory {
    fun parse(body:String,type:TagCategory,order:String):List<DirectoryTag> {
        require(type in setOf(TagCategory.ARTIST,TagCategory.COPYRIGHT) && order in setOf("date","count"))
        try {
            val root=JsonParser.parseString(body);require(root.isJsonArray && root.asJsonArray.size()<=30)
            val tags=root.asJsonArray.map { entry->val o=entry.asJsonObject
                val category=when(o["type"].asInt){1->TagCategory.ARTIST;3->TagCategory.COPYRIGHT;else->TagCategory.UNKNOWN}
                DirectoryTag(o["id"].asLong,o["name"].asString,category,o["count"].asLong).also{require(it.id>0 && LibraryCodec.tagName(it.name) && it.category==type && it.count>=0)}
            }
            require(tags.map{it.name}.distinct().size==tags.size)
            require(tags.zipWithNext().all{(a,b)->if(order=="date")a.id>=b.id else a.count>=b.count})
            return tags
        }catch(e:Exception){throw DataFailure("Tag directory classification or ordering could not be verified.",e)}
    }
}
class TopicRepository(private val repository:SakugaRepository=SakugaRepository(),private val clock:()->Long={System.currentTimeMillis()}) {
    private val cache=mutableMapOf<String,Pair<Long,List<DirectoryTag>>>()
    suspend fun page(type:TagCategory,order:String,page:Int,refresh:Boolean=false):List<DirectoryTag> {
        require(page in 1..5)
        val key="$type:$order:$page"
        synchronized(cache){if(refresh)cache.keys.removeAll{it.startsWith("$type:$order:")};cache[key]?.takeIf{clock()-it.first<300000}?.let{return it.second}}
        val tags=withContext(Dispatchers.IO){repository.directory(type,order,page)}
        synchronized(cache){cache[key]=clock() to tags;while(cache.size>20)cache.remove(cache.keys.first())}
        return tags
    }
}
