// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

/** Counts include the default folder and each distinct many-to-many membership. */
data class BackupCounts(val folders:Int,val posts:Int,val relationships:Int,val tags:Int,val notes:Int)
class BackupPreview internal constructor(internal val backup:LibraryBackup,val counts:BackupCounts,val folderConflicts:Int,val noteConflicts:Int)
enum class FolderConflictPolicy { MERGE_BY_NAME, KEEP_BOTH }
enum class NoteConflictPolicy { KEEP_EXISTING, REPLACE }
data class BackupImportResult(val added:Int,val skipped:Int,val failed:Int=0)

/** A pure plan is fully checked before the transaction writes any rows. Database IDs never cross the file boundary. */
internal data class BackupMergePlan(
    val folders:List<FavoriteFolder>,val posts:List<FavoritePost>,val links:List<FavoriteLink>,
    val tags:List<SavedTag>,val notes:List<TagNote>,val result:BackupImportResult
)
internal object LibraryMerge {
    fun counts(value:LibraryBackup)=BackupCounts(value.folders.size,value.posts.size,value.links.size,value.tags.size,value.notes.size)
    fun preview(incoming:LibraryBackup,old:LibraryBackup):BackupPreview {
        val oldNotes=old.notes.associateBy{it.name}
        val available=old.folders.filter{it.id!=1L}.toMutableList()
        val conflicts=incoming.folders.sortedBy{it.position}.count{folder->
            if(folder.id==1L)false else {
                val index=available.indexOfFirst{it.name.equals(folder.name,true)}
                if(index<0)false else {available.removeAt(index);true}
            }
        }
        return BackupPreview(incoming,counts(incoming),conflicts,
            incoming.notes.count{n->oldNotes[n.name]?.let{it.note!=n.note}==true})
    }

    fun plan(incoming:LibraryBackup,old:LibraryBackup,folderPolicy:FolderConflictPolicy,notePolicy:NoteConflictPolicy):BackupMergePlan {
        val pristine=old.posts.isEmpty() && old.links.isEmpty() && old.folders.all{it.id==1L && it.name.isEmpty()}
        val folders=old.folders.toMutableList()
        if(folders.none{it.id==1L})folders.add(FavoriteFolder(1,"",0))
        var nextId=(folders.maxOfOrNull{it.id} ?: 1L)+1
        var nextPosition=(folders.maxOfOrNull{it.position} ?: -1)+1
        // Match only original local folders, once per occurrence. Distinct source keys must
        // remain distinct even when an older app allowed duplicate names in the same backup.
        val available=old.folders.filter{it.id!=1L}.toMutableList()
        val mapped=mutableMapOf<Long,Long>();var added=0;var skipped=0
        fun uniqueName(original:String):String {
            var index=2
            while(true){val suffix=" ($index)";val value=original.take(80-suffix.length)+suffix
                if(folders.none{it.name.equals(value,true)})return value
                index++
            }
        }
        incoming.folders.sortedBy{it.position}.forEachIndexed{index,folder->
            val existing=if(folder.id==1L)folders.first{it.id==1L} else {
                val match=available.indexOfFirst{it.name.equals(folder.name,true)}
                if(match<0)null else available.removeAt(match)
            }
            val target=if(existing!=null && (folder.id==1L || folderPolicy==FolderConflictPolicy.MERGE_BY_NAME)){
                skipped++;existing
            }else{
                if(folders.size>=100 || nextId<=1)throw LibraryFailure("library_limit")
                FavoriteFolder(nextId++,if(folderPolicy==FolderConflictPolicy.KEEP_BOTH && folders.any{it.name.equals(folder.name,true)})uniqueName(folder.name) else folder.name,nextPosition++).also{folders.add(it);added++}
            }
            mapped[folder.id]=target.id
            val position=if(pristine)index+(if(incoming.folders.any{it.id==1L})0 else 1) else target.position
            val cover=target.coverPostId ?: folder.coverPostId
            folders[folders.indexOfFirst{it.id==target.id}]=target.copy(position=position,coverPostId=cover)
        }
        val posts=old.posts.associateBy{it.id}.toMutableMap()
        val newPosts=incoming.posts.filter{if(it.id in posts){skipped++;false}else{posts[it.id]=it;added++;true}}
        if(posts.size>10000 || posts.values.sumOf{it.snapshot.toByteArray(Charsets.UTF_8).size.toLong()}>8*1024*1024)throw LibraryFailure("library_limit")
        val links=old.links.associateBy{it.postId to it.folderId}.toMutableMap()
        val newLinks=incoming.links.map{it.copy(folderId=mapped.getValue(it.folderId))}.filter{
            val key=it.postId to it.folderId
            if(key in links){skipped++;false}else{links[key]=it;added++;true}
        }
        if(links.size>50000)throw LibraryFailure("library_limit")
        val tags=old.tags.associateBy{it.name}.toMutableMap()
        val newTags=incoming.tags.filter{tag->
            val existing=tags[tag.name]
            if(existing==null || (existing.category==TagCategory.UNKNOWN.name && tag.category!=TagCategory.UNKNOWN.name)){
                tags[tag.name]=tag;added++;true
            }else{skipped++;false}
        }
        val notes=old.notes.associateBy{it.name}.toMutableMap()
        val newNotes=incoming.notes.filter{note->
            val existing=notes[note.name]
            if(existing==null || (notePolicy==NoteConflictPolicy.REPLACE && existing.note!=note.note)){
                notes[note.name]=note;added++;true
            }else{skipped++;false}
        }
        if(tags.size>PersonalRules.MAX_TAGS || notes.size>PersonalRules.MAX_NOTES)throw LibraryFailure("library_limit")
        return BackupMergePlan(folders,newPosts,newLinks,newTags,newNotes,BackupImportResult(added,skipped))
    }
}
