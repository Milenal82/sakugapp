// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import com.sakugapp.ui.PreviewLabels
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class PreviewNamingTest {
    private val artist=Tag("yutaka_nakamura",TagCategory.ARTIST)
    private val work=Tag("mob_psycho_100",TagCategory.COPYRIGHT)
    private fun name(tag:Tag)=SakugabotNameParser.parse(javaClass.getResource("/sakugabot/${tag.name}.json")!!.readText(),tag)!!
    private fun post()=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first()

    @Test fun previewsUseOneCopyrightNameAndAlwaysKeepTheArtistRaw(){
        val names=mapOf(artist to name(artist),work to name(work))
        assertEquals("灵能百分百",TranslationLabels.preview(work,true,names))
        assertEquals(artist.name,TranslationLabels.preview(artist,true,names))
        for(tag in listOf(artist,work))assertEquals(tag.name,TranslationLabels.preview(tag,false,names))
        assertEquals(work.name,TranslationLabels.preview(work,true,emptyMap()))
        assertEquals(work.name,TranslationLabels.preview(work,true,mapOf(work to name(artist))))
        val unknown=work.copy(category=TagCategory.UNKNOWN)
        assertEquals(work.name,TranslationLabels.preview(unknown,true,mapOf(unknown to name(work))))
    }
    @Test fun unrelatedCacheUpdatesReuseExactlyTheSamePerCardLabels(){
        val labels=PreviewLabels();val names=mapOf(work to name(work))
        val first=labels.present(listOf(work,artist),true,names)
        assertEquals(mapOf(work.name to "灵能百分百"),first)
        assertSame(first,labels.present(listOf(work,artist),true,names+(artist to name(artist))))
        assertTrue(labels.present(listOf(work,artist),false,names).isEmpty())
    }
    @Test fun downloadNamesReadOnlyCachedVerifiedNamesAndNeverFetch(){
        var reads=0
        val source=object:DetailTranslationSource{
            override val cachedNames=MutableStateFlow(mapOf(artist to name(artist),work to name(work)))
            override suspend fun lookup(tag:Tag):ChineseTagName?=error("A download must not request translations")
            override fun cached(tag:Tag):ChineseTagName?{reads++;return cachedNames.value[tag]}
        }
        val tags=listOf(artist,work,artist,Tag("source",TagCategory.UNKNOWN))
        assertTrue(TranslationLabels.download(tags,false,source).isEmpty());assertEquals(0,reads)
        assertEquals(mapOf(artist to "中村丰",work to "灵能百分百"),TranslationLabels.download(tags,true,source))
        source.cachedNames.value=mapOf(work to name(artist))
        assertTrue(TranslationLabels.download(tags,true,source).isEmpty())
    }
    @Test fun exampleAndRawFallbackUseArtistSourceCopyrightIdInThatOrder(){
        val copyright=Tag("gachiakuta",TagCategory.COPYRIGHT)
        val value=post().copy(id=295427,tags=listOf(copyright,artist),source="21")
        assertEquals("#中村丰#21#gachiakuta#295427.mp4",MediaFileNames.forPost(value,"mp4",mapOf(artist to name(artist).chinese)))
        assertEquals("#yutaka_nakamura#21#gachiakuta#295427.webm",MediaFileNames.forPost(value,"webm"))
        assertEquals("#yutaka_nakamura#gachiakuta#295427.mkv",MediaFileNames.forPost(value.copy(source=null),"mkv"))
        assertEquals("#295427.mp4",MediaFileNames.forPost(value.copy(source=" ",tags=emptyList()),"MP4"))
    }
    @Test fun categoriesStayInStableOriginalOrderWithoutDuplicatedOrEmptySegments(){
        val first=artist.copy(name="first_artist");val second=artist.copy(name="second_artist")
        val other=work.copy(name="other_show")
        val value=post().copy(id=42,source="Series #12",tags=listOf(other,first,work,second,first,Tag("guess",TagCategory.UNKNOWN)))
        assertEquals("#first_artist#second_artist#Series_12#other_show#mob_psycho_100#42.mp4",MediaFileNames.forPost(value,"mp4"))
        val unsafe=value.copy(tags=listOf(artist.copy(name="/:*?\"<>|\\\u0000#")),source="/#\\\u0000")
        assertEquals("#42.mp4",MediaFileNames.forPost(unsafe,"mp4"))
    }
    @Test fun unicodeNamesCollisionsAndClipsKeepTheIdAndRealExtension(){
        val value=post().copy(id=295427,tags=listOf(artist,work),source="long source ".repeat(40))
        for(ext in listOf("mp4","webm","mkv")){
            val name=MediaFileNames.forPost(value,ext,mapOf(artist to "中村丰😀".repeat(80),work to "灵能百分百".repeat(80)))
            val result=MediaFileNames.unique(name){it==name}
            assertTrue(name.toByteArray().size<=MediaFileNames.MAX_BYTES)
            assertTrue(result.toByteArray().size<=MediaFileNames.MAX_BYTES)
            assertTrue(result.endsWith("#295427 (2).$ext"));assertFalse(result.contains("##"))
            assertFalse(result.contains('\uFFFD'));assertFalse(result.any{it.isISOControl() || it in "/\\:*?\"<>|"})
        }
        val clip=MediaFileNames.forClip(value,"webm",ClipRange(600,1400),ClipMode.PRECISE)
        assertTrue(clip.contains("#295427_AB_600-1400_precise"))
        assertTrue(MediaFileNames.unique(clip){it==clip}.endsWith("#295427_AB_600-1400_precise (2).webm"))
    }
    @Test fun sourceIsNeverTreatedAsATranslatedTagOrCredentialBearingFilename(){
        val value=post().copy(id=42,tags=listOf(artist,work),source="Mob Psycho 100 #12")
        val translations=mapOf(artist to "中村丰",work to "灵能百分百")
        assertEquals("#中村丰#Mob_Psycho_100_12#灵能百分百#42.mp4",MediaFileNames.forPost(value,"mp4",translations))
        val url=value.copy(source="https://username:SECRET@www.example.com/private/TOKEN?token=PRIVATE#signed")
        assertEquals("#中村丰#example.com#灵能百分百#42.mp4",MediaFileNames.forPost(url,"mp4",translations))
    }
    @Test fun offlineMediaIdentityDoesNotTraverseTagsOrGenerateNames(){
        var reads=0
        val tags=object:AbstractList<Tag>(){
            override val size=2
            override fun get(index:Int):Tag{reads++;return listOf(artist,work)[index]}
        }
        val value=post().copy(tags=tags,source="Series #12")
        repeat(1000){VideoDownloadPlan.media(value)}
        assertEquals("Receipt/cache checks must not build filenames",0,reads)
        val plan=VideoDownloadPlan.from(value)
        assertTrue(reads>0);assertTrue(plan.filename.contains("#yutaka_nakamura#Series_12#mob_psycho_100#"))
    }
}
