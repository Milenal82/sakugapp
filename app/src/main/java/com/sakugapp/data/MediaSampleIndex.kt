// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import androidx.media3.common.*
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.*
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile

/** Uses the player's edit-list/VFR interpretation. Skips encoded payload without preloading it. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object MediaSampleIndex {
    data class Sample(val timeUs:Long,val key:Boolean)
    data class Track(val type:Int,var format:Format?=null,val samples:MutableList<Sample> = mutableListOf())
    data class Index(val tracks:List<Track>,val durationUs:Long){val video get()=tracks.single{it.type==C.TRACK_TYPE_VIDEO}}
    fun read(file:File,checkActive:()->Unit={}):Index=RandomAccessFile(file,"r").use{r->read(r.length(),checkActive){p,b,o,n->r.seek(p);r.read(b,o,n)}}
    fun read(length:Long,checkActive:()->Unit={},readAt:(Long,ByteArray,Int,Int)->Int):Index{
        val input=Input(length){p,b,o,n->checkActive();readAt(p,b,o,n)}
        val candidates=DefaultExtractorsFactory().createExtractors()
        val extractor=candidates.firstOrNull{e->try{input.p=0;input.resetPeekPosition();e.sniff(input)}catch(_:EOFException){false}}
        candidates.filter{it!==extractor}.forEach{it.release()}
        checkNotNull(extractor){"clip_unsupported"}
        val tracks=linkedMapOf<Int,Track>();var duration=C.TIME_UNSET
        try{
            input.p=0;input.resetPeekPosition()
            extractor.init(object:ExtractorOutput{
                override fun endTracks(){}
                override fun seekMap(map:SeekMap){duration=map.durationUs}
                override fun track(id:Int,type:Int):TrackOutput{
                    val track=tracks.getOrPut(id){Track(type)}
                    return object:TrackOutput{
                        override fun format(format:Format){track.format=format}
                        override fun sampleData(reader:DataReader,n:Int,allowEnd:Boolean,part:Int):Int {
                            check(reader is ExtractorInput);return if(reader.skipFully(n,allowEnd))n else C.RESULT_END_OF_INPUT
                        }
                        override fun sampleData(data:ParsableByteArray,n:Int,part:Int){data.skipBytes(n)}
                        override fun sampleMetadata(timeUs:Long,flags:Int,size:Int,offset:Int,crypto:TrackOutput.CryptoData?){
                            check(crypto==null){"clip_unsupported"}
                            if(size>0){check(track.samples.size<400000){"clip_unsupported"};track.samples+=Sample(timeUs,flags and C.BUFFER_FLAG_KEY_FRAME!=0)}
                        }
                    }
                }
            })
            val position=PositionHolder()
            while(true){checkActive();if(Thread.currentThread().isInterrupted)throw java.io.InterruptedIOException();when(extractor.read(input,position)){Extractor.RESULT_END_OF_INPUT->break;Extractor.RESULT_SEEK->{input.p=position.position;input.resetPeekPosition()}}}
            val list=tracks.values.filter{it.type in listOf(C.TRACK_TYPE_VIDEO,C.TRACK_TYPE_AUDIO)}
            check(list.count{it.type==C.TRACK_TYPE_VIDEO}==1){"clip_unsupported"}
            return Index(list,duration)
        }finally{extractor.release()}
    }
    private class Input(private val size:Long,private val at:(Long,ByteArray,Int,Int)->Int):ExtractorInput{
        var p=0L;private var peek=0L
        override fun getPosition()=p
        override fun getPeekPosition()=peek
        override fun getLength()=size
        override fun resetPeekPosition(){peek=p}
        private fun readFrom(pos:Long,b:ByteArray,o:Int,n:Int):Int=if(n==0)0 else if(pos>=size)-1 else at(pos,b,o,minOf(n.toLong(),size-pos).toInt())
        override fun read(b:ByteArray,o:Int,n:Int):Int{val count=readFrom(p,b,o,n);if(count>0){p+=count;peek=p};return count}
        override fun peek(b:ByteArray,o:Int,n:Int):Int{val count=readFrom(peek,b,o,n);if(count>0)peek+=count;return count}
        private fun fully(b:ByteArray,o:Int,n:Int,allow:Boolean,peeking:Boolean):Boolean{
            var done=0;while(done<n){val count=if(peeking)peek(b,o+done,n-done)else read(b,o+done,n-done);if(count<0 && done==0 && allow)return false;if(count<=0)throw EOFException();done+=count};return true
        }
        override fun readFully(b:ByteArray,o:Int,n:Int,allow:Boolean)=fully(b,o,n,allow,false)
        override fun readFully(b:ByteArray,o:Int,n:Int){readFully(b,o,n,false)}
        override fun peekFully(b:ByteArray,o:Int,n:Int,allow:Boolean)=fully(b,o,n,allow,true)
        override fun peekFully(b:ByteArray,o:Int,n:Int){peekFully(b,o,n,false)}
        override fun skip(n:Int):Int{val count=minOf(n.toLong(),size-p).coerceAtLeast(0).toInt();p+=count;peek=p;return count}
        override fun skipFully(n:Int,allow:Boolean):Boolean{if(p==size && allow && n>0)return false;if(skip(n)!=n)throw EOFException();return true}
        override fun skipFully(n:Int){skipFully(n,false)}
        override fun advancePeekPosition(n:Int,allow:Boolean):Boolean{if(peek==size && allow && n>0)return false;if(peek+n>size)throw EOFException();peek+=n;return true}
        override fun advancePeekPosition(n:Int){advancePeekPosition(n,false)}
        override fun <E:Throwable?> setRetryPosition(position:Long,e:E){p=position;peek=p;throw e!!}
    }
}
