// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

/** Independent local records: no account identity, no website write operations. */
class PersonalStore(private val db:LibraryDatabase,private val clock:()->Long=System::currentTimeMillis){
    private val dao=db.personal()
    val tags=dao.tags()
    val notes=dao.notes()
    val history=dao.history()
    val recording=dao.policy().map{it?.enabled ?: true}.distinctUntilChanged()
    private fun name(value:String){if(!LibraryCodec.tagName(value))throw LibraryFailure("invalid_backup")}
    suspend fun saveTag(tag:Tag)=db.withTransaction{
        name(tag.name)
        if(dao.tagsOnce().none{it.name==tag.name} && dao.tagsOnce().size>=PersonalRules.MAX_TAGS)throw LibraryFailure("library_limit")
        dao.tag(SavedTag(tag.name,tag.category.name,clock()));dao.category(tag.name,tag.category.name)
    }
    suspend fun removeTag(value:String){name(value);dao.removeTag(value)}
    suspend fun note(value:String,text:String)=db.withTransaction{
        name(value);val clean=PersonalRules.note(text)
        if(clean.isBlank())dao.removeNote(value)else{
            if(dao.notesOnce().none{it.name==value} && dao.notesOnce().size>=PersonalRules.MAX_NOTES)throw LibraryFailure("library_limit")
            dao.note(TagNote(value,clean,clock()))
        }
    }
    suspend fun learn(tags:List<Tag>)=db.withTransaction{tags.filter{it.category!=TagCategory.UNKNOWN}.forEach{dao.category(it.name,it.category.name)}}
    suspend fun recording(enabled:Boolean){dao.policy(HistoryPolicy(enabled=enabled))}
    suspend fun record(post:Post)=db.withTransaction{
        if(dao.policyOnce()?.enabled==false)return@withTransaction
        val checked=LibraryCodec.decodePost(LibraryCodec.encodePost(post))
        val rows=dao.historyOnce();val time=maxOf(clock(),(rows.maxOfOrNull{it.viewedAt} ?: 0)+1)
        val value=ViewedPost(post.id,LibraryCodec.encodePost(checked),time)
        val keep=PersonalRules.historyKeep(listOf(value)+rows.filter{it.id!=post.id}).map{it.id}.toSet()
        dao.viewed(value);(rows.map{it.id}+value.id).filter{it !in keep}.forEach{dao.removeView(it)}
    }
    suspend fun removeHistory(id:Long){dao.removeView(id)}
    suspend fun clearHistory(){dao.clearViews()}
}
