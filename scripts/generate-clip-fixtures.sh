#!/usr/bin/env bash
# Synthetic media-engine fixtures only. Never displayed as website posts.
set -euo pipefail
cd "$(dirname "$0")/.."
assets=app/src/androidTest/assets
ffmpeg -y -v error -f lavfi -i 'testsrc2=size=96x64:rate=10:duration=4' -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=4' -c:v libx264 -pix_fmt yuv420p -g 10 -keyint_min 10 -sc_threshold 0 -bf 2 -flags +cgop -c:a aac -movflags +faststart "$assets/clip-gop.mp4"
ffmpeg -y -v error -i "$assets/clip-gop.mp4" -c copy "$assets/clip-gop.mkv"
ffmpeg -y -v error -i "$assets/clip-gop.mp4" -c:v libvpx-vp9 -g 10 -b:v 100k -c:a libvorbis "$assets/clip-gop.webm"
python3 - <<'PY'
import json,pathlib,tempfile,subprocess
assets=pathlib.Path('app/src/androidTest/assets');colors=json.loads((assets/'frame-index-colors.json').read_text())
with tempfile.TemporaryDirectory() as folder:
    raw=pathlib.Path(folder)/'colors.rgb';raw.write_bytes(b''.join(bytes(c)*(96*64) for c in colors))
    subprocess.run(['ffmpeg','-y','-v','error','-f','rawvideo','-pixel_format','rgb24','-video_size','96x64','-framerate','10','-i',str(raw),'-c:v','libx264','-pix_fmt','yuv420p','-g','12','-bf','2','-movflags','+faststart',str(assets/'clip-colors.mp4')],check=True)
PY
