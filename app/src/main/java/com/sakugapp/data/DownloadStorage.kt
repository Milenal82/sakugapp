// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract as Docs
import android.provider.MediaStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.security.MessageDigest

/** Independent persisted destinations and owned-file receipts; old private index is untouched. */
class DownloadStorage(private val context:Context,private val downloader:VideoDownloader=VideoDownloader(),private val output:((Uri)->OutputStream)?=null){
    enum class Kind(val path:String){SINGLE("Movies/Sakugapp/"),CLIP("Movies/Sakugapp/Clips/"),BATCH("Movies/Sakugapp/Batch/")}
    private val prefs=context.getSharedPreferences("download_destinations_v1",Context.MODE_PRIVATE)
    private val resolver=context.contentResolver
    companion object {private val lock=Mutex()}
    fun directory(kind:Kind):Uri?=prefs.getString("tree_${kind.name}",null)?.let(Uri::parse)
    fun choose(kind:Kind,uri:Uri){
        require(Docs.isTreeUri(uri));val flags=Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(uri,flags)
        val doc=Docs.buildDocumentUriUsingTree(uri,Docs.getTreeDocumentId(uri))
        resolver.query(doc,arrayOf(Docs.Document.COLUMN_FLAGS),null,null,null)?.use{check(it.moveToFirst() && it.getLong(0) and Docs.Document.FLAG_DIR_SUPPORTS_CREATE.toLong()!=0L)} ?: throw IOException("directory_access")
        check(prefs.edit().putString("tree_${kind.name}",uri.toString()).commit())
    }
    fun reset(kind:Kind){prefs.edit().remove("tree_${kind.name}").commit()}
    fun needsLegacyPermission(kind:Kind)=Build.VERSION.SDK_INT<29 && directory(kind)==null
    private fun oldKey(post:Post)="${post.id}-${key(post,Kind.BATCH).substringAfter("BATCH_").take(24)}.${VideoDownloadPlan.from(post).filename.substringAfterLast('.')}"
    private fun key(post:Post,kind:Kind)=kind.name+"_"+MessageDigest.getInstance("SHA-256").digest(VideoDownloadPlan.from(post).url.toString().toByteArray()).joinToString(""){"%02x".format(it)}
    private fun readable(uri:Uri)=runCatching{resolver.query(uri,arrayOf(android.provider.OpenableColumns.SIZE),null,null,null)?.use{it.moveToFirst() && (it.isNull(0) || it.getLong(0)!=0L)}==true}.getOrDefault(false)
    fun existing(post:Post,kind:Kind):Uri?=runCatching{
        val stored=prefs.getString(key(post,kind),null) ?: if(kind==Kind.BATCH)prefs.getString("old_"+oldKey(post),null)else null
        val uri=stored?.let(Uri::parse) ?: return@runCatching null
        uri.takeIf(::readable)
    }.getOrNull()
    private data class Target(val uri:Uri,val pending:Boolean)
    private fun create(filename:String,mime:String,kind:Kind):Target{
        val tree=directory(kind)
        if(tree!=null)try{
            if(resolver.persistedUriPermissions.none{it.uri==tree && it.isReadPermission && it.isWritePermission})throw LibraryFailure("directory_access")
            val parent=Docs.buildDocumentUriUsingTree(tree,Docs.getTreeDocumentId(tree));val names=mutableSetOf<String>()
            resolver.query(Docs.buildChildDocumentsUriUsingTree(tree,Docs.getTreeDocumentId(tree)),arrayOf(Docs.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use{while(it.moveToNext())names+=it.getString(0)} ?: throw IOException()
            val name=MediaFileNames.unique(filename){it in names}
            return Target(Docs.createDocument(resolver,parent,mime,name) ?: throw IOException(),false)
        }catch(e:Exception){val failure=storageFailure(e);throw if((failure as? LibraryFailure)?.code=="storage_full")failure else LibraryFailure("directory_access").also{it.initCause(e)}}
        try{
            val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,filename);put(MediaStore.Video.Media.MIME_TYPE,mime)}
            if(Build.VERSION.SDK_INT>=29){values.put(MediaStore.Video.Media.RELATIVE_PATH,kind.path);values.put(MediaStore.Video.Media.IS_PENDING,1)}
            else{
                val folder=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),if(kind==Kind.BATCH)"Sakugapp/Batch" else "Sakugapp")
                if(!folder.isDirectory && !folder.mkdirs())throw IOException()
                val name=MediaFileNames.unique(filename){File(folder,it).exists()};values.put(MediaStore.Video.Media.DISPLAY_NAME,name);values.put(MediaStore.Video.Media.DATA,File(folder,name).path)
            }
            return Target(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values) ?: throw IOException(),Build.VERSION.SDK_INT>=29)
        }catch(e:Exception){val failure=storageFailure(e);throw if((failure as? LibraryFailure)?.code=="storage_full")failure else LibraryFailure("directory_access").also{it.initCause(e)}}
    }
    private fun delete(target:Target){runCatching{if(Docs.isDocumentUri(context,target.uri))Docs.deleteDocument(resolver,target.uri)else resolver.delete(target.uri,null,null)}}
    private fun commit(target:Target,post:Post,kind:Kind,bytes:Long){
        if(target.pending && resolver.update(target.uri,ContentValues().apply{put(MediaStore.Video.Media.IS_PENDING,0)},null,null)!=1)throw IOException()
        if(!prefs.edit().putString(key(post,kind),target.uri.toString()).putLong("bytes_"+key(post,kind),bytes).commit())throw IOException()
    }
    suspend fun download(post:Post,kind:Kind,progress:(Long,Long)->Unit={_,_->}):Uri=lock.withLock{withContext(Dispatchers.IO){
        existing(post,kind)?.let{return@withContext it}
        if(kind==Kind.BATCH && post.fileSize?.let{it>OfflineLibraryMedia.MAX_FILE}==true)throw LibraryFailure("media_limit")
        val used=if(kind==Kind.BATCH)prefs.all.filter{it.key.startsWith("bytes_BATCH_")}.entries.sumOf{(name,value)->
            val receipt=name.removePrefix("bytes_");val uri=prefs.getString(receipt,null)
            val valid=uri!=null && readable(Uri.parse(uri))
            if(valid)(value as? Long) ?: 0L else{prefs.edit().remove(name).remove(receipt).commit();0L}
        }else 0L
        val available=if(kind==Kind.BATCH)(OfflineLibraryMedia.MAX_TOTAL-used).coerceAtLeast(0)else Long.MAX_VALUE
        val max=if(kind==Kind.BATCH)minOf(OfflineLibraryMedia.MAX_FILE,available)else Long.MAX_VALUE
        if(max<=0 || (post.fileSize ?: 0)>max)throw LibraryFailure("media_limit")
        val target=VideoDownloadPlan.from(post).let{create(it.filename,it.mime,kind)};var received=0L
        try{
            PublicReadThrottle.awaitTurn()
            downloader.download(VideoDownloadPlan.from(post),open={object:FilterOutputStream((output?.invoke(target.uri) ?: resolver.openOutputStream(target.uri,"w")) ?: throw IOException()){
                override fun write(buffer:ByteArray,offset:Int,count:Int){if(received+count>max)throw LibraryFailure("media_limit");out.write(buffer,offset,count);received+=count}
                override fun write(value:Int){if(++received>max)throw LibraryFailure("media_limit");out.write(value)}
            }},discard={delete(target)},progress=progress)
            ensureActive();commit(target,post,kind,received);target.uri
        }catch(e:CancellationException){delete(target);throw e}catch(e:Exception){delete(target);throw storageFailure(e)}
    }}
    suspend fun exportLegacy(file:File):Boolean=lock.withLock{withContext(Dispatchers.IO){
        val receipt="legacy_"+file.name+"_"+file.length()
        val previous=prefs.getString(receipt,null)
        if(previous!=null && readable(Uri.parse(previous)))return@withContext false
        val mime=when(file.extension.lowercase()){"mp4"->"video/mp4";"webm"->"video/webm";"mkv"->"video/x-matroska";else->throw LibraryFailure("download_storage")}
        val target=create(file.name,mime,Kind.BATCH)
        try{file.inputStream().use{input->resolver.openOutputStream(target.uri,"w")!!.use{out->val buffer=ByteArray(32768);while(true){ensureActive();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}}}
            ensureActive();if(target.pending)resolver.update(target.uri,ContentValues().apply{put(MediaStore.Video.Media.IS_PENDING,0)},null,null)
            val edit=prefs.edit().putString(receipt,target.uri.toString())
            val old=java.util.Properties().apply{runCatching{File(file.parentFile,".media-index").inputStream().use{load(it)}}}
            old.stringPropertyNames().filter{old.getProperty(it)==file.name}.forEach{edit.putString("old_"+it,target.uri.toString())}
            if(Regex("\\d+-[a-f0-9]{24}\\.(mp4|webm|mkv)").matches(file.name))edit.putString("old_"+file.name,target.uri.toString())
            check(edit.commit());true
        }catch(e:CancellationException){delete(target);throw e}catch(e:Exception){delete(target);throw storageFailure(e)}
    }}
    suspend fun saveClip(file:File,filename:String,mime:String):Uri=lock.withLock{withContext(Dispatchers.IO){
        if(directory(Kind.CLIP)==null)throw LibraryFailure("directory_access")
        val target=create(filename,mime,Kind.CLIP)
        try{file.inputStream().use{input->((output?.invoke(target.uri) ?: resolver.openOutputStream(target.uri,"w")) ?: throw IOException()).use{out->if(out is FileOutputStream){
                val stat=runCatching{android.system.Os.fstatvfs(out.fd)}.getOrNull()
                if(stat!=null && stat.f_blocks>0 && stat.f_bavail*stat.f_frsize<file.length())throw LibraryFailure("storage_full")
            };val buffer=ByteArray(65536);while(true){ensureActive();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}}}
            ensureActive();if(target.pending)check(resolver.update(target.uri,ContentValues().apply{put(MediaStore.Video.Media.IS_PENDING,0)},null,null)==1);target.uri
        }catch(e:CancellationException){delete(target);throw e}catch(e:Exception){delete(target);throw storageFailure(e)}
    }}
    private fun storageFailure(e:Exception):Exception{
        if(e is DataFailure && e.cause==null)return e
        if(e is LibraryFailure)return e
        if(e is DataFailure && e.cause is LibraryFailure)return e.cause as LibraryFailure
        var cause:Throwable?=e
        while(cause!=null){if(cause is android.database.sqlite.SQLiteFullException)return LibraryFailure("storage_full");if(cause.message?.contains("ENOSPC")==true || cause.message?.contains("No space left")==true)return LibraryFailure("storage_full");if(cause is android.system.ErrnoException && cause.errno==android.system.OsConstants.ENOSPC)return LibraryFailure("storage_full");cause=cause.cause}
        return LibraryFailure("download_storage")
    }
}
