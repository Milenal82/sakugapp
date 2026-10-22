#!/usr/bin/env python3
"""Create a synthetic decoder-only VFR fixture. It is never used as website/post content."""
import json, pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[1]
colors=[(230,20,20),(20,230,20),(20,20,230),(230,200,20),(180,20,200),(20,190,210),(140,80,20),(20,80,140),(150,150,150),(80,180,100),(200,80,130),(90,60,200)]
out=root/'app/src/androidTest/assets'
out.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory() as directory:
 raw=pathlib.Path(directory)/'colors.rgb'
 raw.write_bytes(b''.join(bytes(c)*(96*64) for c in colors))
 subprocess.run(['ffmpeg','-y','-v','error','-f','rawvideo','-pixel_format','rgb24','-video_size','96x64','-framerate','10','-i',str(raw),'-vf',r'settb=expr=1/1000,setpts=if(lt(N\,6)\,N*200\,1200+(N-6)*100)','-fps_mode','passthrough','-enc_time_base','1/1000','-frames:v','12','-c:v','libx264','-pix_fmt','yuv420p','-g','12','-bf','2','-movflags','+faststart','-use_editlist','0','-avoid_negative_ts','make_zero',str(out/'frame-index-vfr.mp4')],check=True)
(out/'frame-index-colors.json').write_text(json.dumps(colors)+'\n')
