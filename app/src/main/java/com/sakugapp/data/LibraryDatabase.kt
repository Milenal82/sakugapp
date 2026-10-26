// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="favorite_posts")
data class FavoritePost(@PrimaryKey val id:Long,val snapshot:String,val savedAt:Long)
@Entity(tableName="favorite_folders")
data class FavoriteFolder(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String,val position:Int,val coverPostId:Long?=null)
@Entity(tableName="favorite_links",primaryKeys=["postId","folderId"],
    foreignKeys=[ForeignKey(entity=FavoritePost::class,parentColumns=["id"],childColumns=["postId"],onDelete=ForeignKey.CASCADE),ForeignKey(entity=FavoriteFolder::class,parentColumns=["id"],childColumns=["folderId"],onDelete=ForeignKey.CASCADE)],indices=[Index("folderId")])
data class FavoriteLink(val postId:Long,val folderId:Long,val addedAt:Long)
@Entity(tableName="tag_translations")
data class LocalTagTranslation(@PrimaryKey val original:String,val translation:String)
@Entity(tableName="media_facts")
data class MediaFact(@PrimaryKey val url:String,val durationMs:Long,val touchedAt:Long)

@Dao interface LibraryDao {
    @Query("SELECT * FROM favorite_posts") fun posts():Flow<List<FavoritePost>>
    @Query("SELECT * FROM favorite_folders ORDER BY position,id") fun folders():Flow<List<FavoriteFolder>>
    @Query("SELECT * FROM favorite_links") fun links():Flow<List<FavoriteLink>>
    @Query("SELECT * FROM tag_translations") fun translations():Flow<List<LocalTagTranslation>>
    @Query("SELECT * FROM media_facts") fun facts():Flow<List<MediaFact>>
    @Query("SELECT * FROM favorite_folders ORDER BY position,id") suspend fun foldersOnce():List<FavoriteFolder>
    @Query("SELECT * FROM favorite_posts") suspend fun postsOnce():List<FavoritePost>
    @Query("SELECT * FROM favorite_links") suspend fun linksOnce():List<FavoriteLink>
    @Query("SELECT * FROM tag_translations") suspend fun translationsOnce():List<LocalTagTranslation>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun folder(value:FavoriteFolder):Long
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun post(value:FavoritePost):Long
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun link(value:FavoriteLink)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun translation(value:LocalTagTranslation)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun fact(value:MediaFact)
    @Query("UPDATE favorite_folders SET name=:name WHERE id=:id") suspend fun rename(id:Long,name:String)
    @Query("UPDATE favorite_folders SET position=:position WHERE id=:id") suspend fun position(id:Long,position:Int)
    @Query("UPDATE favorite_folders SET coverPostId=:postId WHERE id=:id") suspend fun cover(id:Long,postId:Long?)
    @Query("DELETE FROM favorite_folders WHERE id=:id") suspend fun deleteFolder(id:Long)
    @Query("DELETE FROM favorite_links WHERE postId=:postId AND folderId=:folderId") suspend fun unlink(postId:Long,folderId:Long)
    @Query("DELETE FROM favorite_posts WHERE id NOT IN (SELECT postId FROM favorite_links)") suspend fun pruneOrphans()
    @Query("DELETE FROM tag_translations WHERE original=:original") suspend fun deleteTranslation(original:String)
    @Query("DELETE FROM media_facts WHERE url NOT IN (SELECT url FROM media_facts ORDER BY touchedAt DESC LIMIT 1000)") suspend fun trimFacts()
}
@Database(entities=[FavoritePost::class,FavoriteFolder::class,FavoriteLink::class,LocalTagTranslation::class,MediaFact::class],version=1,exportSchema=true)
abstract class LibraryDatabase:RoomDatabase(){
    abstract fun library():LibraryDao
    companion object {
        @Volatile private var instance:LibraryDatabase?=null
        fun get(context:Context)=instance ?: synchronized(this){instance ?: Room.databaseBuilder(context.applicationContext,LibraryDatabase::class.java,"local-library-v1.db").build().also{instance=it}}
    }
}
