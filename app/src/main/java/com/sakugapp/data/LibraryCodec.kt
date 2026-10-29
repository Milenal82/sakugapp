// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import com.google.gson.*
import java.net.URI

class LibraryFailure(val code:String):Exception(code)
data class LibraryBackup(val folders:List<FavoriteFolder>,val posts:List<FavoritePost>,val links:List<FavoriteLink>,val tags:List<SavedTag> = emptyList(),val notes:List<TagNote> = emptyList())
object LibraryCodec {
    const val MAX_BYTES=20*1024*1024
    private val gson=Gson()
    private val strictGson=GsonBuilder().setStrictness(Strictness.STRICT).create()
    fun trustedMedia(url:String?):String? {
        if(url==null)return null
        val safe=PostParser.safeUrl(url) ?: throw LibraryFailure("invalid_backup")
        val host=URI(safe).host.lowercase()
        if(host!="sakugabooru.com" && !host.endsWith(".sakugabooru.com"))throw LibraryFailure("invalid_backup")
        return safe
    }
    fun tagName(value:String)=value.isNotBlank() && value.length<=200 && value.none{it.isWhitespace() || it.isISOControl()}
    fun encodePost(post:Post)=gson.toJson(gson.toJsonTree(post).asJsonObject.apply{addProperty("pageUrl",post.pageUrl)})
    private fun JsonObject.long(key:String,required:Boolean=false):Long? {
        val v=get(key)
        if(v==null || v.isJsonNull){if(required)throw LibraryFailure("invalid_backup");return null}
        if(!v.isJsonPrimitive || !v.asJsonPrimitive.isNumber)throw LibraryFailure("invalid_backup")
        return v.asString.toLongOrNull() ?: throw LibraryFailure("invalid_backup")
    }
    private fun JsonObject.text(key:String,max:Int=4096):String? {
        val v=get(key) ?: return null
        if(v.isJsonNull)return null
        if(!v.isJsonPrimitive || !v.asJsonPrimitive.isString || v.asString.length>max)throw LibraryFailure("invalid_backup")
        return v.asString
    }
    fun decodePost(body:String):Post=try {
        val o=JsonParser.parseString(body).asJsonObject
        val id=o.long("id",true)!!;if(id<=0)throw LibraryFailure("invalid_backup")
        if(o.text("pageUrl")?.let{it!="https://www.sakugabooru.com/post/show/$id"}==true)throw LibraryFailure("invalid_backup")
        val raw=o.getAsJsonArray("tags") ?: throw LibraryFailure("invalid_backup")
        if(raw.size()>500)throw LibraryFailure("invalid_backup")
        val tags=raw.map{val t=it.asJsonObject;val name=t.text("name",200) ?: throw LibraryFailure("invalid_backup");if(!tagName(name))throw LibraryFailure("invalid_backup");Tag(name,TagCategory.entries.firstOrNull{c->c.name==t.text("category",30)} ?: TagCategory.UNKNOWN)}.distinctBy{it.name}
        fun int(key:String)=o.long(key)?.let{if(it !in Int.MIN_VALUE..Int.MAX_VALUE)throw LibraryFailure("invalid_backup");it.toInt()}
        fun size(key:String)=int(key)?.also{if(it !in 1..100000)throw LibraryFailure("invalid_backup")}
        val bytes=o.long("fileSize")?.also{if(it<0)throw LibraryFailure("invalid_backup")}
        val duration=o.long("durationMs")?.also{if(it !in 1..86400000)throw LibraryFailure("invalid_backup")}
        Post(id,tags,int("score"),trustedMedia(o.text("previewUrl")),size("previewWidth"),size("previewHeight"),trustedMedia(o.text("fileUrl")),o.text("fileExt",16),size("width"),size("height"),o.text("source"),o.text("rating",30),o.long("createdAt"),o.text("status",100),o.text("uploader",300),bytes,duration)
    }catch(e:LibraryFailure){throw e}catch(e:Exception){throw LibraryFailure("invalid_backup")}
    const val FORMAT="sakugapp.library"
    const val SCHEMA_VERSION=3
    fun encode(value:LibraryBackup):String {
        val keys=value.folders.mapIndexed{index,folder->folder.id to "folder_$index"}.toMap()
        val o=JsonObject();o.addProperty("format",FORMAT);o.addProperty("schemaVersion",SCHEMA_VERSION)
        o.add("folders",JsonArray().apply{value.folders.forEach{folder->add(JsonObject().apply{
            addProperty("key",keys.getValue(folder.id));addProperty("kind",if(folder.id==1L)"default" else "custom")
            addProperty("name",folder.name);addProperty("position",folder.position)
            folder.coverPostId?.takeIf{cover->value.links.any{it.folderId==folder.id && it.postId==cover}}?.let{addProperty("coverPostId",it)}
        })}})
        o.add("links",JsonArray().apply{value.links.forEach{link->add(JsonObject().apply{
            addProperty("postId",link.postId);addProperty("folderKey",keys[link.folderId] ?: throw LibraryFailure("invalid_backup"));addProperty("addedAt",link.addedAt)
        })}})
        o.add("posts",JsonArray().apply{value.posts.forEach{p->add(JsonObject().apply{addProperty("savedAt",p.savedAt);add("post",JsonParser.parseString(p.snapshot))})}})
        o.add("tagFavorites",gson.toJsonTree(value.tags));o.add("tagNotes",gson.toJsonTree(value.notes))
        return gson.toJson(o).also{if(it.toByteArray(Charsets.UTF_8).size>MAX_BYTES)throw LibraryFailure("library_limit")}
    }
    fun decode(body:String):LibraryBackup=try {
        if(body.toByteArray(Charsets.UTF_8).size>MAX_BYTES)throw LibraryFailure("library_limit")
        val o=strictGson.fromJson(body.removePrefix("\uFEFF"),JsonElement::class.java).asJsonObject
        if(o.text("format")!=FORMAT)throw LibraryFailure("invalid_backup")
        val schema=o.long("schemaVersion")
        val version=if(schema!=null){
            if(schema!=SCHEMA_VERSION.toLong())throw LibraryFailure("unsupported_backup_version")
            if(o.has("version"))throw LibraryFailure("invalid_backup")
            SCHEMA_VERSION.toLong()
        }else o.long("version",true)!!.also{if(it !in setOf(1L,2L))throw LibraryFailure("unsupported_backup_version")}
        fun array(key:String,limit:Int)=o.getAsJsonArray(key)?.also{if(it.size()>limit)throw LibraryFailure("library_limit")} ?: throw LibraryFailure("invalid_backup")
        val folderKeys=mutableMapOf<String,Long>();var nextFolder=2L
        val folders=array("folders",100).mapIndexed{index,v->
            val f=v.asJsonObject
            val id=if(version==3L){
                val key=f.text("key",120) ?: throw LibraryFailure("invalid_backup")
                if(key.isBlank() || key.any{it.isISOControl()} || key in folderKeys)throw LibraryFailure("invalid_backup")
                val kind=f.text("kind",16)
                val mapped=when(kind){"default"->1L;"custom"->nextFolder++;else->throw LibraryFailure("invalid_backup")}
                if(mapped in folderKeys.values)throw LibraryFailure("invalid_backup")
                folderKeys[key]=mapped;mapped
            }else f.long("id",true)!!
            val originalName=f.text("name",80)?.trim() ?: ""
            val name=if(version<3L && id==1L)"" else originalName
            val position=f.long("position") ?: index.toLong()
            if(id<=0 || (id!=1L && name.isBlank()) || (id==1L && name.isNotEmpty()) || originalName.any{it.isISOControl()} || position !in 0..100000)throw LibraryFailure("invalid_backup")
            FavoriteFolder(id,name,position.toInt(),f.long("coverPostId"))
        }.also{if(it.map{f->f.id}.distinct().size!=it.size)throw LibraryFailure("invalid_backup")}
        val posts=array("posts",10000).map{v->val entry=v.asJsonObject;val post=decodePost(entry.get("post").toString());FavoritePost(post.id,encodePost(post),entry.long("savedAt",true)!!.also{if(it<0)throw LibraryFailure("invalid_backup")})}.distinctBy{it.id}
        val folderIds=folders.map{it.id}.toSet();val postIds=posts.map{it.id}.toSet()
        val links=array("links",50000).map{v->val l=v.asJsonObject
            val folder=if(version==3L)folderKeys[l.text("folderKey",120)] ?: throw LibraryFailure("invalid_backup") else l.long("folderId",true)!!
            FavoriteLink(l.long("postId",true)!!,folder,l.long("addedAt",true)!!.also{if(it<0)throw LibraryFailure("invalid_backup")})
        }.distinctBy{it.postId to it.folderId}
        if(links.any{it.postId !in postIds || it.folderId !in folderIds})throw LibraryFailure("invalid_backup")
        val linkedPosts=links.map{it.postId}.toSet();val memberships=links.map{it.folderId to it.postId}.toSet()
        if(posts.any{it.id !in linkedPosts})throw LibraryFailure("invalid_backup")
        val checkedFolders=folders.map{f->if(f.coverPostId!=null && (f.id to f.coverPostId) !in memberships){
            if(version==3L)throw LibraryFailure("invalid_backup")
            f.copy(coverPostId=null)
        }else f}
        // Obsolete legacy translation fields are ignored, never imported or exported.
        val tags=if(version>=2L)array("tagFavorites",PersonalRules.MAX_TAGS).map{v->val t=v.asJsonObject
            val name=t.text("name",200) ?: throw LibraryFailure("invalid_backup");if(!tagName(name))throw LibraryFailure("invalid_backup")
            SavedTag(name,PersonalRules.category(t.text("category",30).orEmpty()).name,t.long("savedAt",true)!!.also{if(it<0)throw LibraryFailure("invalid_backup")})}.distinctBy{it.name}else emptyList()
        val notes=if(version>=2L)array("tagNotes",PersonalRules.MAX_NOTES).map{v->val n=v.asJsonObject
            val name=n.text("name",200) ?: throw LibraryFailure("invalid_backup");if(!tagName(name))throw LibraryFailure("invalid_backup")
            val note=PersonalRules.note(n.text("note",4000) ?: throw LibraryFailure("invalid_backup"))
            if(note.isBlank())throw LibraryFailure("invalid_backup")
            TagNote(name,note,n.long("updatedAt",true)!!.also{if(it<0)throw LibraryFailure("invalid_backup")})}.distinctBy{it.name}else emptyList()
        LibraryBackup(checkedFolders,posts,links,tags,notes)
    }catch(e:LibraryFailure){throw e}catch(e:Exception){throw LibraryFailure("invalid_backup")}
}
