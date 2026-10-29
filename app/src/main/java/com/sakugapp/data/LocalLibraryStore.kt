// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LibrarySnapshot(val posts:List<FavoritePost> = emptyList(),val folders:List<FavoriteFolder> = emptyList(),val links:List<FavoriteLink> = emptyList(),val durations:Map<String,Long> = emptyMap(),val error:Boolean=false,val decoded:Map<Long,Post> = posts.associate{it.id to LibraryCodec.decodePost(it.snapshot)}){
    private val byFolder=links.groupBy{it.folderId}.mapValues{it.value.map{l->l.postId}.toSet()}
    private val byPost=links.groupBy{it.postId}.mapValues{it.value.map{l->l.folderId}.toSet()}
    fun inFolder(id:Long?)=posts.filter{p->if(id==null)p.id in byPost else p.id in byFolder[id].orEmpty()}
    fun contains(postId:Long,folderId:Long?=null)=if(folderId==null)postId in byPost else folderId in byPost[postId].orEmpty()
}
class LocalLibraryStore(private val db:LibraryDatabase){
    private val dao=db.library()
    // One transaction observes a coherent library. Duration-only changes do not reread or decode all posts.
    private val libraryData=flow{
        var cache=emptyMap<Long,Pair<String,Post>>()
        db.invalidationTracker.createFlow("favorite_posts","favorite_folders","favorite_links").collect{
            val raw=db.withTransaction{LibraryBackup(dao.foldersOnce(),dao.postsOnce(),dao.linksOnce())}
            cache=raw.posts.associate{p->p.id to (cache[p.id]?.takeIf{it.first==p.snapshot} ?: (p.snapshot to LibraryCodec.decodePost(p.snapshot)))}
            emit(LibrarySnapshot(raw.posts,raw.folders,raw.links,decoded=cache.mapValues{it.value.second}))
        }
    }
    val state=combine(libraryData,dao.facts()){data,facts->data.copy(durations=facts.associate{it.url to it.durationMs})}.flowOn(Dispatchers.Default)
    suspend fun ready(){dao.folder(FavoriteFolder(1,"",0))}
    private fun validateName(name:String):String=name.trim().also{if(it.isBlank()||it.length>80||it.any(Char::isISOControl))throw LibraryFailure("folder_name")}
    suspend fun createFolder(name:String):Long=db.withTransaction{
        ready();val clean=validateName(name);val all=dao.foldersOnce()
        if(all.size>=100)throw LibraryFailure("library_limit")
        if(all.any{it.name.equals(clean,true)})throw LibraryFailure("folder_name")
        dao.folder(FavoriteFolder(name=clean,position=(all.maxOfOrNull{it.position} ?: -1)+1))
    }
    suspend fun rename(id:Long,name:String)=db.withTransaction{
        if(id==1L)throw LibraryFailure("folder_name")
        val clean=validateName(name);if(dao.foldersOnce().any{it.id!=id && it.name.equals(clean,true)})throw LibraryFailure("folder_name")
        dao.rename(id,clean)
    }
    suspend fun deleteFolder(id:Long)=db.withTransaction{if(id==1L)throw LibraryFailure("folder_name");dao.deleteFolder(id);dao.pruneOrphans()}
    suspend fun reorder(id:Long,delta:Int)=db.withTransaction{
        val all=dao.foldersOnce().toMutableList();val from=all.indexOfFirst{it.id==id};val to=from+delta
        if(from>=0 && to in all.indices){val value=all.removeAt(from);all.add(to,value);all.forEachIndexed{i,f->dao.position(f.id,i)}}
    }
    suspend fun cover(folderId:Long,postId:Long)=db.withTransaction{if(dao.linksOnce().none{it.folderId==folderId && it.postId==postId})throw LibraryFailure("invalid_backup");dao.cover(folderId,postId)}
    suspend fun membership(post:Post,folderId:Long,present:Boolean)=db.withTransaction{
        ready();if(dao.foldersOnce().none{it.id==folderId})throw LibraryFailure("invalid_backup")
        if(present){
            val old=dao.postsOnce();val relations=dao.linksOnce()
            val checked=LibraryCodec.decodePost(LibraryCodec.encodePost(post));val snapshot=LibraryCodec.encodePost(checked)
            if(old.none{it.id==post.id} && (old.size>=10000 || old.sumOf{it.snapshot.toByteArray().size.toLong()}+snapshot.toByteArray().size>8*1024*1024))throw LibraryFailure("library_limit")
            if(relations.size>=50000 && relations.none{it.postId==post.id && it.folderId==folderId})throw LibraryFailure("library_limit")
            val now=System.currentTimeMillis();dao.post(FavoritePost(post.id,snapshot,now));dao.link(FavoriteLink(post.id,folderId,now))
        }
        else{dao.unlink(post.id,folderId);dao.pruneOrphans()}
    }
    suspend fun batch(ids:Set<Long>,from:Long?,to:Long?)=db.withTransaction{
        if(to!=null && dao.foldersOnce().none{it.id==to})throw LibraryFailure("invalid_backup")
        val existing=dao.postsOnce().map{it.id}.toSet();val links=dao.linksOnce().groupBy{it.postId};val now=System.currentTimeMillis()
        ids.filter{it in existing}.forEach{id->
            if(to!=null)dao.link(FavoriteLink(id,to,now))
            links[id].orEmpty().filter{(from==null || it.folderId==from) && it.folderId!=to}.forEach{dao.unlink(id,it.folderId)}
        };dao.pruneOrphans()
    }
    suspend fun duration(url:String,milliseconds:Long){if(milliseconds !in 1..86400000)return;LibraryCodec.trustedMedia(url);db.withTransaction{dao.fact(MediaFact(url,milliseconds,System.currentTimeMillis()));dao.trimFacts()}}
    suspend fun export():String=db.withTransaction{ready();LibraryCodec.encode(LibraryBackup(dao.foldersOnce(),dao.postsOnce(),dao.linksOnce(),db.personal().tagsOnce(),db.personal().notesOnce()))}
    suspend fun import(body:String)=withContext(Dispatchers.Default){
        val incoming=LibraryCodec.decode(body)
        db.withTransaction{
            ready();val oldFolders=dao.foldersOnce();val oldPosts=dao.postsOnce()
            if((oldPosts.map{it.id}+incoming.posts.map{it.id}).distinct().size>10000)throw LibraryFailure("library_limit")
            val map=mutableMapOf<Long,Long>();var position=(oldFolders.maxOfOrNull{it.position} ?: 0)+1
            for(folder in incoming.folders){
                val existing=if(folder.id==1L)oldFolders.first{it.id==1L}else dao.foldersOnce().firstOrNull{it.id!=1L && it.name.equals(folder.name,true)}
                val id=existing?.id ?: run{if(dao.foldersOnce().size>=100)throw LibraryFailure("library_limit");dao.folder(FavoriteFolder(name=folder.name,position=position++))}
                map[folder.id]=id
            }
            if((oldPosts+incoming.posts).distinctBy{it.id}.sumOf{it.snapshot.toByteArray().size.toLong()}>8*1024*1024)throw LibraryFailure("library_limit")
            if((dao.linksOnce()+incoming.links.map{it.copy(folderId=map[it.folderId]!!)}).distinctBy{it.postId to it.folderId}.size>50000)throw LibraryFailure("library_limit")
            incoming.posts.forEach{dao.post(it)}
            incoming.links.forEach{dao.link(it.copy(folderId=checkNotNull(map[it.folderId])))}
            incoming.folders.forEach{folder->val target=map[folder.id]!!;val cover=folder.coverPostId;if(cover!=null && dao.foldersOnce().first{it.id==target}.coverPostId==null && dao.linksOnce().any{it.folderId==target && it.postId==cover})dao.cover(target,cover)}
            val personal=db.personal();val oldTags=personal.tagsOnce();val oldNotes=personal.notesOnce()
            if((oldTags+incoming.tags).distinctBy{it.name}.size>PersonalRules.MAX_TAGS || (oldNotes+incoming.notes).distinctBy{it.name}.size>PersonalRules.MAX_NOTES)throw LibraryFailure("library_limit")
            incoming.tags.forEach{personal.tag(it)}
            incoming.notes.filter{n->oldNotes.none{it.name==n.name}}.forEach{personal.note(it)}
            dao.pruneOrphans()
        }
    }
}
