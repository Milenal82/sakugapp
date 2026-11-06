// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import coil.request.*
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

private val Context.profilePreferences by preferencesDataStore("local-profile-v1")
data class LocalProfile(val nickname:String?=null,val avatar:String?=null)
object ProfileRules {
    fun nickname(input:String):String {
        require(input.none{it.isISOControl() || it=='\u2028' || it=='\u2029' || (Character.getType(it)==Character.FORMAT.toInt() && it!='\u200d')})
        return input.trim().also{require(it.isNotBlank() && it.codePointCount(0,it.length)<=20)}
    }
}
class LocalProfileStore(private val context:Context){
    private val name=stringPreferencesKey("nickname");private val avatar=stringPreferencesKey("avatar");private val lock=Mutex()
    val profile=context.profilePreferences.data.map{p->LocalProfile(p[name],p[avatar]?.takeIf{it.matches(Regex("avatar-[a-f0-9-]+\\.jpg"))}?.let{File(context.filesDir,it).takeIf(File::isFile)?.path})}
    suspend fun nickname(value:String?){val clean=value?.let(ProfileRules::nickname);context.profilePreferences.edit{if(clean==null)it.remove(name)else it[name]=clean}}
    suspend fun resetAvatar()=lock.withLock{val old=context.profilePreferences.data.first()[avatar];context.profilePreferences.edit{it.remove(avatar)};old?.let{File(context.filesDir,it).delete()}}
    suspend fun avatar(uri:Uri)=lock.withLock{withContext(Dispatchers.IO){
        val staged=File(context.cacheDir,"profile-${UUID.randomUUID()}.image");val dest=File(context.filesDir,"avatar-${UUID.randomUUID()}.jpg")
        var committed=false
        try{
            val type=context.contentResolver.getType(uri);require(type in setOf("image/jpeg","image/png","image/webp","image/heic","image/heif"))
            context.contentResolver.openInputStream(uri)!!.use{input->staged.outputStream().use{out->val b=ByteArray(32768);var total=0;while(true){ensureActive();val n=input.read(b);if(n<0)break;total+=n;require(total<=20*1024*1024);out.write(b,0,n)}}}
            val result=coil.Coil.imageLoader(context).execute(ImageRequest.Builder(context).data(staged).size(512).allowHardware(false).memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build())
            check(result is SuccessResult)
            val w=result.drawable.intrinsicWidth.coerceAtLeast(1);val h=result.drawable.intrinsicHeight.coerceAtLeast(1);val scale=minOf(1.0,512.0/maxOf(w,h));val bitmap=result.drawable.toBitmap((w*scale).toInt().coerceAtLeast(1),(h*scale).toInt().coerceAtLeast(1));try{dest.outputStream().use{check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it))}}finally{bitmap.recycle()}
            ensureActive();withContext(NonCancellable){val old=context.profilePreferences.data.first()[avatar];context.profilePreferences.edit{it[avatar]=dest.name};committed=true;old?.let{File(context.filesDir,it).delete()}}
        }catch(e:Throwable){if(!committed)dest.delete();throw e}finally{staged.delete()}
    }}
}
