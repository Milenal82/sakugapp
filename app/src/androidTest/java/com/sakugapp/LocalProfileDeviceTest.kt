// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.graphics.*
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class LocalProfileDeviceTest {
 @Test fun copiedAvatarSurvivesSourceDeletionAndCorruptionKeepsOldAvatar()=runBlocking{
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val store=LocalProfileStore(context)
  val file=File(context.cacheDir,"screenshots/profile-test.jpg").apply{parentFile!!.mkdirs()}
  val bitmap=Bitmap.createBitmap(2400,1200,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
  android.media.ExifInterface(file.path).apply{setAttribute(android.media.ExifInterface.TAG_ORIENTATION,android.media.ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
  try{
   store.avatar(FileProvider.getUriForFile(context,context.packageName+".files",file));val saved=store.profile.first().avatar!!
   assertTrue(File(saved).exists());val image=BitmapFactory.decodeFile(saved);assertTrue(image.width<=512 && image.height<=512);assertTrue(image.height>image.width);image.recycle();file.delete();assertTrue(File(saved).exists())
   file.writeText("not an image");try{store.avatar(FileProvider.getUriForFile(context,context.packageName+".files",file));fail("Corrupt image must fail")}catch(_:IllegalStateException){}
   assertEquals(saved,store.profile.first().avatar)
   store.nickname(" ローカル🙂 ");assertEquals("ローカル🙂",LocalProfileStore(context).profile.first().nickname)
  }finally{file.delete();store.resetAvatar();store.nickname(null)}
 }
}
