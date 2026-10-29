// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import com.google.gson.*
import java.net.URI

class LibraryFailure(val code:String):Exception(code)
data class LibraryBackup(val folders:List<FavoriteFolder>,val posts:List<FavoritePost>,val links:List<FavoriteLink>,val tags:List<SavedTag> = emptyList(),val notes:List<TagNote> = emptyList())
object LibraryCodec {
    const val MAX_BYTES=20*1024*1024
    private val gson=Gson()
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
    fun encode(value:LibraryBackup):String {
        val o=JsonObject();o.addProperty("format","sakugapp.library");o.addProperty("version",2)
        o.add("folders",gson.toJsonTree(value.folders));o.add("links",gson.toJsonTree(value.links))
        o.add("posts",JsonArray().apply{value.posts.forEach{p->add(JsonObject().apply{addProperty("savedAt",p.savedAt);add("post",JsonParser.parseString(p.snapshot))})}})
        o.add("tagFavorites",gson.toJsonTree(value.tags));o.add("tagNotes",gson.toJsonTree(value.notes))
        return gson.toJson(o).also{if(it.toByteArray().size>MAX_BYTES)throw LibraryFailure("library_limit")}
    }
    fun decode(body:String):LibraryBackup=try {
        if(body.toByteArray().size>MAX_BYTES)throw LibraryFailure("library_limit")
        val o=JsonParser.parseString(body).asJsonObject
        if(o.text("format")!="sakugapp.library" || o.long("version") !in setOf(1L,2L))throw LibraryFailure("invalid_backup")
        fun array(key:String,limit:Int)=o.getAsJsonArray(key)?.also{if(it.size()>limit)throw LibraryFailure("library_limit")} ?: throw LibraryFailure("invalid_backup")
        val folders=array("folders",100).map{v->val f=v.asJsonObject;val id=f.long("id",true)!!;val name=f.text("name",80)?.trim() ?: "";if(id<=0 || (id!=1L && name.isBlank()) || name.any{it.isISOControl()})throw LibraryFailure("invalid_backup");FavoriteFolder(id,name,0,f.long("coverPostId"))}.distinctBy{it.id}
        val posts=array("posts",10000).map{v->val entry=v.asJsonObject;val post=decodePost(entry.get("post").toString());FavoritePost(post.id,encodePost(post),entry.long("savedAt",true)!!.also{if(it<0)throw LibraryFailure("invalid_backup")})}.distinctBy{it.id}
        val folderIds=folders.map{it.id}.toSet();val postIds=posts.map{it.id}.toSet()
        val links=array("links",50000).map{v->val l=v.asJsonObject;FavoriteLink(l.long("postId",true)!!,l.long("folderId",true)!!,l.long("addedAt",true)!!.also{if(it<0)throw LibraryFailure("invalid_backup")})}.distinctBy{it.postId to it.folderId}
        if(links.any{it.postId !in postIds || it.folderId !in folderIds})throw LibraryFailure("invalid_backup")
        // Legacy v1 backup translation fields are ignored, never read into the database.
        val tags=if(o.long("version")==2L)array("tagFavorites",PersonalRules.MAX_TAGS).map{v->val t=v.asJsonObject
            val name=t.text("name",200) ?: throw LibraryFailure("invalid_backup");if(!tagName(name))throw LibraryFailure("invalid_backup")
            SavedTag(name,PersonalRules.category(t.text("category",30).orEmpty()).name,t.long("savedAt",true)!!.also{if(it<0)throw LibraryFailure("invalid_backup")})}.distinctBy{it.name}else emptyList()
        val notes=if(o.long("version")==2L)array("tagNotes",PersonalRules.MAX_NOTES).map{v->val n=v.asJsonObject
            val name=n.text("name",200) ?: throw LibraryFailure("invalid_backup");if(!tagName(name))throw LibraryFailure("invalid_backup")
            val note=PersonalRules.note(n.text("note",4000) ?: throw LibraryFailure("invalid_backup"))
            if(note.isBlank())throw LibraryFailure("invalid_backup")
            TagNote(name,note,n.long("updatedAt",true)!!.also{if(it<0)throw LibraryFailure("invalid_backup")})}.distinctBy{it.name}else emptyList()
        LibraryBackup(folders,posts,links,tags,notes)
    }catch(e:LibraryFailure){throw e}catch(e:Exception){throw LibraryFailure("invalid_backup")}
}
