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
    private suspend fun backupOnce()=LibraryBackup(dao.foldersOnce(),dao.postsOnce(),dao.linksOnce(),db.personal().tagsOnce(),db.personal().notesOnce())
    suspend fun preview(body:String):BackupPreview=withContext(Dispatchers.Default){
        val incoming=LibraryCodec.decode(body)
        db.withTransaction{LibraryMerge.preview(incoming,backupOnce())}
    }
    suspend fun import(body:String):BackupImportResult=import(preview(body))
    suspend fun import(preview:BackupPreview,folderPolicy:FolderConflictPolicy=FolderConflictPolicy.MERGE_BY_NAME,notePolicy:NoteConflictPolicy=NoteConflictPolicy.KEEP_EXISTING):BackupImportResult=withContext(Dispatchers.Default){
        db.withTransaction{
            // Replan against current data: changes made while a preview dialog is open are never overwritten implicitly.
            val plan=LibraryMerge.plan(preview.backup,backupOnce(),folderPolicy,notePolicy)
            plan.folders.forEach{dao.folder(it)}
            plan.posts.forEach{dao.post(it)}
            plan.links.forEach{dao.link(it)}
            plan.folders.forEach{dao.position(it.id,it.position);dao.cover(it.id,it.coverPostId)}
            val personal=db.personal()
            plan.tags.forEach{personal.tag(it);personal.category(it.name,it.category)}
            plan.notes.forEach{personal.note(it)}
            plan.result
        }
    }
}
