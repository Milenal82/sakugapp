// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="saved_tags")
data class SavedTag(@PrimaryKey val name:String,val category:String,val savedAt:Long)
@Entity(tableName="tag_notes")
data class TagNote(@PrimaryKey val name:String,val note:String,val updatedAt:Long)
@Entity(tableName="viewed_posts")
data class ViewedPost(@PrimaryKey val id:Long,val snapshot:String,val viewedAt:Long)
@Entity(tableName="history_policy")
data class HistoryPolicy(@PrimaryKey val id:Int=1,val enabled:Boolean=true)

@Dao interface PersonalDao {
    @Query("SELECT * FROM saved_tags ORDER BY savedAt DESC,name") fun tags():Flow<List<SavedTag>>
    @Query("SELECT * FROM tag_notes") fun notes():Flow<List<TagNote>>
    @Query("SELECT * FROM viewed_posts ORDER BY viewedAt DESC,id DESC") fun history():Flow<List<ViewedPost>>
    @Query("SELECT * FROM history_policy WHERE id=1") fun policy():Flow<HistoryPolicy?>
    @Query("SELECT * FROM saved_tags") suspend fun tagsOnce():List<SavedTag>
    @Query("SELECT * FROM tag_notes") suspend fun notesOnce():List<TagNote>
    @Query("SELECT * FROM viewed_posts ORDER BY viewedAt DESC,id DESC") suspend fun historyOnce():List<ViewedPost>
    @Query("SELECT * FROM history_policy WHERE id=1") suspend fun policyOnce():HistoryPolicy?
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun tag(tag:SavedTag)
    @Query("UPDATE saved_tags SET category=:category WHERE name=:name AND category='UNKNOWN'") suspend fun category(name:String,category:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun note(note:TagNote)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun viewed(post:ViewedPost)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun policy(policy:HistoryPolicy)
    @Query("DELETE FROM saved_tags WHERE name=:name") suspend fun removeTag(name:String)
    @Query("DELETE FROM tag_notes WHERE name=:name") suspend fun removeNote(name:String)
    @Query("DELETE FROM viewed_posts WHERE id=:id") suspend fun removeView(id:Long)
    @Query("DELETE FROM viewed_posts") suspend fun clearViews()
}

object PersonalRules {
    const val MAX_TAGS=5000
    const val MAX_NOTES=5000
    const val NOTE_LENGTH=500
    const val HISTORY_CAPACITY=500
    const val HISTORY_BYTES=8*1024*1024
    fun note(value:String):String {
        val normalized=value.replace("\r\n","\n").replace('\r','\n').replace('\t',' ')
            .filter{it=='\n' || (!it.isISOControl() && it.code !in 0x202a..0x202e && it.code !in 0x2066..0x2069 && it.code!=0xfeff)}.trim()
        if(normalized.codePointCount(0,normalized.length)>NOTE_LENGTH || normalized.count{it=='\n'}>7)throw LibraryFailure("note_length")
        return normalized
    }
    fun category(value:String)=TagCategory.entries.firstOrNull{it.name==value} ?: TagCategory.UNKNOWN
    fun searchable(tags:List<SavedTag>,notes:List<TagNote>,query:String,sort:Int):List<SavedTag>{
        val byName=notes.associate{it.name to it.note};val value=query.trim()
        val filtered=tags.filter{value.isBlank() || it.name.contains(value,true) || byName[it.name].orEmpty().contains(value,true)}
        return when(sort){1->filtered.sortedBy{it.name.lowercase()};2->filtered.sortedBy{it.savedAt};else->filtered.sortedByDescending{it.savedAt}}
    }
    fun historyKeep(rows:List<ViewedPost>):List<ViewedPost>{
        var bytes=0L
        return rows.sortedWith(compareByDescending<ViewedPost>{it.viewedAt}.thenByDescending{it.id}).distinctBy{it.id}.take(HISTORY_CAPACITY).takeWhile{
            bytes+=it.snapshot.toByteArray().size;bytes<=HISTORY_BYTES
        }
    }
    fun queryTags(query:String,posts:List<Post>):List<Tag> {
        val known=posts.flatMap{it.tags}.groupBy{it.name}
        return query.trim().split(Regex("\\s+")).map{it.removePrefix("~").removePrefix("-")}
            .filter{LibraryCodec.tagName(it) && !it.contains(':') && !it.contains('*')}.distinct().map{name->
                val categories=known[name].orEmpty().map{it.category}.filter{it!=TagCategory.UNKNOWN}.distinct()
                Tag(name,categories.singleOrNull() ?: TagCategory.UNKNOWN)
            }
    }
}
