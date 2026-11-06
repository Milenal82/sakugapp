// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.media.MediaMuxer
import android.media.MediaCodec
import androidx.media3.common.*
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.transformer.Muxer
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/** Media3 has an MP4 muxer; platform WebM writing preserves VP8/VP9/Vorbis/Opus samples. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class WebmClipMuxer(path:String):Muxer{
    private val muxer=MediaMuxer(path,MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
    private var started=false
    private val mimes=mutableMapOf<Int,String?>()
    override fun addTrack(format:Format):Int=muxer.addTrack(MediaFormatUtil.createMediaFormatFromFormat(format)).also{mimes[it]=format.sampleMimeType}
    override fun writeSampleData(trackId:Int,data:ByteBuffer,info:MediaCodec.BufferInfo){
        // WebM cannot represent negative preroll packet timestamps. Keep only complete nonnegative packets.
        if(info.presentationTimeUs<0 || info.size==0)return
        val size=info.size-(if(mimes[trackId]==MimeTypes.AUDIO_VORBIS)4 else 0)
        require(size>0 && info.offset>=0 && info.offset+size<=data.limit())
        val slice=data.duplicate().apply{position(info.offset);limit(info.offset+size)}.slice()
        val normalized=MediaCodec.BufferInfo().apply{set(0,size,info.presentationTimeUs,info.flags)}
        if(!started){muxer.start();started=true};muxer.writeSampleData(trackId,slice,normalized)
    }
    override fun addMetadataEntry(entry:Metadata.Entry){}
    override fun close(){try{if(started)muxer.stop()}finally{muxer.release()}}
    class Factory:Muxer.Factory{
        override fun create(path:String):Muxer=WebmClipMuxer(path)
        override fun getSupportedSampleMimeTypes(type:Int):ImmutableList<String> = ImmutableList.copyOf(if(type==C.TRACK_TYPE_VIDEO)listOf(MimeTypes.VIDEO_VP8,MimeTypes.VIDEO_VP9)else listOf(MimeTypes.AUDIO_VORBIS,MimeTypes.AUDIO_OPUS))
    }
}
