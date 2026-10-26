#!/usr/bin/env python3
"""Read background samples from Android's raw screencap; test tooling only."""
import struct

def edge_background(raw):
    if len(raw)<12:
        raise ValueError('Missing screencap header')
    width,height,format_id=struct.unpack_from('<III',raw)
    bpp={1:4,2:4,3:3,5:4}.get(format_id) # RGBA, RGBX, RGB, BGRA
    if not bpp or not (16<=width<=10000 and 16<=height<=10000):
        raise ValueError('Unsupported screencap dimensions or pixel format')
    offset=len(raw)-width*height*bpp
    if offset not in (12,16):
        raise ValueError('Incomplete screencap pixels')
    pixels=[]
    for part in range(2,8):
        y=height*part//10
        for x in (width-8,width-12):
            start=offset+(y*width+x)*bpp
            color=tuple(raw[start:start+3])
            pixels.append(color[::-1] if format_id==5 else color)
    return pixels
