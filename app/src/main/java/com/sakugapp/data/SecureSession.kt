// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only cookies and server-confirmed identity are persisted. Passwords/pass_hash are never saved. */
class SecureSession(context: Context) : CookieJar {
    private val file = AtomicFile(File(context.noBackupFilesDir,"account-session.aes"))
    private var cookies = mutableListOf<Cookie>()
    private var account: Account? = null
    private val origin = "https://www.sakugabooru.com/".toHttpUrl()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("sakugapp.session.v1",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("sakugapp.session.v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized fun restore(): Account? {
        if(!file.baseFile.exists())return null
        return try {
            val bytes=file.openRead().use { it.readBytes() }
            require(bytes.size>13 && bytes[0].toInt()==1)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,13)))
            val o=CommunityParser.obj(String(cipher.doFinal(bytes.copyOfRange(13,bytes.size)),Charsets.UTF_8))
            cookies=o.getAsJsonArray("cookies").mapNotNull { Cookie.parse(origin,it.asString) }.filter { it.expiresAt>System.currentTimeMillis() }.toMutableList()
            account=if(cookies.isNotEmpty() && o.has("id")) Account(o["id"].asLong,o["name"].asString) else null
            account
        } catch(e: Exception) { cookies.clear();account=null;file.delete();null }
    }
    @Synchronized fun confirm(value: Account) { account=value;persist() }
    @Synchronized fun clear() { cookies.clear();account=null;file.delete() }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> = if(url.host==origin.host && url.isHttps)
        cookies.filter { it.expiresAt>System.currentTimeMillis() && it.matches(url) } else emptyList()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if(url.host!=origin.host || !url.isHttps)return
        for(c in cookies) { this.cookies.removeAll { it.name==c.name && it.path==c.path && it.domain==c.domain };if(c.expiresAt>System.currentTimeMillis())this.cookies.add(c) }
        persist()
    }
    @Synchronized fun importBrowser(header:String?) {
        val old=cookies.associateBy{it.name}
        cookies=header.orEmpty().split(';').mapNotNull { entry ->
            val parts=entry.trim().split('=',limit=2)
            if(parts.size!=2)return@mapNotNull null
            val previous=old[parts[0]]
            runCatching { Cookie.Builder().name(parts[0]).value(parts[1]).hostOnlyDomain(origin.host).path(previous?.path ?: "/").secure().apply{
                if(previous?.httpOnly==true || (previous==null && parts[0] !in setOf("locale","user_info")))httpOnly()
                if(previous?.persistent==true)expiresAt(previous.expiresAt)
            }.build() }.getOrNull()
        }.toMutableList()
        account=null;persist()
    }
    @Synchronized fun browserCookies(): List<String> = cookies.filter { it.expiresAt>System.currentTimeMillis() }.map { Cookie.Builder().name(it.name).value(it.value).hostOnlyDomain(origin.host).path(it.path).apply { if(it.secure)secure();if(it.httpOnly)httpOnly();if(it.persistent)expiresAt(it.expiresAt) }.build().toString() }
    @Synchronized private fun persist() {
        val o=JsonObject();account?.let { o.addProperty("id",it.id);o.addProperty("name",it.name) }
        o.add("cookies",JsonArray().apply { cookies.forEach { add(it.toString()) } })
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=byteArrayOf(1)+cipher.iv+cipher.doFinal(o.toString().toByteArray(Charsets.UTF_8))
        val stream=file.startWrite()
        try { stream.write(bytes);file.finishWrite(stream) } catch(e: Exception) { file.failWrite(stream);throw e }
    }
}
