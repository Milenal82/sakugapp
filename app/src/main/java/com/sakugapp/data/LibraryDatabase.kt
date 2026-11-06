// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="favorite_posts")
data class FavoritePost(@PrimaryKey val id:Long,val snapshot:String,val savedAt:Long)
@Entity(tableName="favorite_folders")
data class FavoriteFolder(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String,val position:Int,val coverPostId:Long?=null)
@Entity(tableName="favorite_links",primaryKeys=["postId","folderId"],
    foreignKeys=[ForeignKey(entity=FavoritePost::class,parentColumns=["id"],childColumns=["postId"],onDelete=ForeignKey.CASCADE),ForeignKey(entity=FavoriteFolder::class,parentColumns=["id"],childColumns=["folderId"],onDelete=ForeignKey.CASCADE)],indices=[Index("folderId")])
data class FavoriteLink(val postId:Long,val folderId:Long,val addedAt:Long)
@Entity(tableName="favorite_subfolders",foreignKeys=[ForeignKey(entity=FavoriteFolder::class,parentColumns=["id"],childColumns=["folderId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("folderId"),Index(value=["id","folderId"],unique=true)])
data class FavoriteSubfolder(@PrimaryKey(autoGenerate=true) val id:Long=0,val folderId:Long,val name:String,val position:Int)
@Entity(tableName="favorite_subfolder_links",primaryKeys=["postId","subfolderId"],foreignKeys=[
    ForeignKey(entity=FavoriteLink::class,parentColumns=["postId","folderId"],childColumns=["postId","folderId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=FavoriteSubfolder::class,parentColumns=["id","folderId"],childColumns=["subfolderId","folderId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index(value=["postId","folderId"]),Index(value=["subfolderId","folderId"])])
data class FavoriteSubfolderLink(val postId:Long,val subfolderId:Long,val folderId:Long)
@Entity(tableName="media_facts")
data class MediaFact(@PrimaryKey val url:String,val durationMs:Long,val touchedAt:Long)

@Dao interface LibraryDao {
    @Query("SELECT * FROM favorite_posts") fun posts():Flow<List<FavoritePost>>
    @Query("SELECT * FROM favorite_folders ORDER BY position,id") fun folders():Flow<List<FavoriteFolder>>
    @Query("SELECT * FROM favorite_links") fun links():Flow<List<FavoriteLink>>
    @Query("SELECT * FROM media_facts") fun facts():Flow<List<MediaFact>>
    @Query("SELECT * FROM favorite_folders ORDER BY position,id") suspend fun foldersOnce():List<FavoriteFolder>
    @Query("SELECT * FROM favorite_posts") suspend fun postsOnce():List<FavoritePost>
    @Query("SELECT * FROM favorite_links") suspend fun linksOnce():List<FavoriteLink>
    @Query("SELECT * FROM favorite_subfolders ORDER BY position,id") suspend fun subfoldersOnce():List<FavoriteSubfolder>
    @Query("SELECT * FROM favorite_subfolder_links") suspend fun subfolderLinksOnce():List<FavoriteSubfolderLink>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun subfolder(value:FavoriteSubfolder):Long
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun subfolderLink(value:FavoriteSubfolderLink)
    @Query("UPDATE favorite_subfolders SET name=:name WHERE id=:id") suspend fun renameSubfolder(id:Long,name:String)
    @Query("UPDATE favorite_subfolders SET position=:position WHERE id=:id") suspend fun subfolderPosition(id:Long,position:Int)
    @Query("DELETE FROM favorite_subfolders WHERE id=:id") suspend fun deleteSubfolder(id:Long)
    @Query("DELETE FROM favorite_subfolder_links WHERE postId=:postId AND subfolderId=:subfolderId") suspend fun unclassify(postId:Long,subfolderId:Long)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun folder(value:FavoriteFolder):Long
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun post(value:FavoritePost):Long
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun link(value:FavoriteLink)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun fact(value:MediaFact)
    @Query("UPDATE favorite_posts SET snapshot=:snapshot WHERE id=:id") suspend fun snapshot(id:Long,snapshot:String)
    @Query("UPDATE favorite_folders SET name=:name WHERE id=:id") suspend fun rename(id:Long,name:String)
    @Query("UPDATE favorite_folders SET position=:position WHERE id=:id") suspend fun position(id:Long,position:Int)
    @Query("UPDATE favorite_folders SET coverPostId=:postId WHERE id=:id") suspend fun cover(id:Long,postId:Long?)
    @Query("DELETE FROM favorite_folders WHERE id=:id") suspend fun deleteFolder(id:Long)
    @Query("DELETE FROM favorite_links WHERE postId=:postId AND folderId=:folderId") suspend fun unlink(postId:Long,folderId:Long)
    @Query("DELETE FROM favorite_posts WHERE id NOT IN (SELECT postId FROM favorite_links)") suspend fun pruneOrphans()
    @Query("DELETE FROM media_facts WHERE url NOT IN (SELECT url FROM media_facts ORDER BY touchedAt DESC LIMIT 1000)") suspend fun trimFacts()
}
@Database(entities=[FavoritePost::class,FavoriteFolder::class,FavoriteLink::class,FavoriteSubfolder::class,FavoriteSubfolderLink::class,MediaFact::class,SavedTag::class,TagNote::class,ViewedPost::class,HistoryPolicy::class],version=4,exportSchema=true)
abstract class LibraryDatabase:RoomDatabase(){
    abstract fun library():LibraryDao
    abstract fun personal():PersonalDao
    companion object {
        // Only the removed feature's data is discarded; collection and media tables are untouched.
        val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("DROP TABLE IF EXISTS tag_translations")}}
        val MIGRATION_2_3=object:Migration(2,3){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("CREATE TABLE IF NOT EXISTS saved_tags (name TEXT NOT NULL, category TEXT NOT NULL, savedAt INTEGER NOT NULL, PRIMARY KEY(name))")
            db.execSQL("CREATE TABLE IF NOT EXISTS tag_notes (name TEXT NOT NULL, note TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(name))")
            db.execSQL("CREATE TABLE IF NOT EXISTS viewed_posts (id INTEGER NOT NULL, snapshot TEXT NOT NULL, viewedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS history_policy (id INTEGER NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(id))")
        }}
        val MIGRATION_3_4=object:Migration(3,4){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("CREATE TABLE IF NOT EXISTS favorite_subfolders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, folderId INTEGER NOT NULL, name TEXT NOT NULL, position INTEGER NOT NULL, FOREIGN KEY(folderId) REFERENCES favorite_folders(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_subfolders_folderId ON favorite_subfolders(folderId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_subfolders_id_folderId ON favorite_subfolders(id,folderId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS favorite_subfolder_links (postId INTEGER NOT NULL, subfolderId INTEGER NOT NULL, folderId INTEGER NOT NULL, PRIMARY KEY(postId,subfolderId), FOREIGN KEY(postId,folderId) REFERENCES favorite_links(postId,folderId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(subfolderId,folderId) REFERENCES favorite_subfolders(id,folderId) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_subfolder_links_postId_folderId ON favorite_subfolder_links(postId,folderId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_subfolder_links_subfolderId_folderId ON favorite_subfolder_links(subfolderId,folderId)")
        }}
        @Volatile private var instance:LibraryDatabase?=null
        fun get(context:Context)=instance ?: synchronized(this){instance ?: Room.databaseBuilder(context.applicationContext,LibraryDatabase::class.java,"local-library-v1.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build().also{instance=it}}
    }
}
