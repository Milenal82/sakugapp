#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
destination="${1:?Pass an absolute ZIP output path outside the checkout}"
case "$destination" in /*) ;; *) echo 'Output path must be absolute' >&2; exit 1 ;; esac
mkdir -p "$(dirname "$destination")"
git archive --format=zip --prefix=Sakugapp/ -o "$destination" HEAD
python3 - "$destination" <<'PY'
import pathlib, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as archive:
    for name in archive.namelist():
        path = pathlib.PurePosixPath(name)
        if any(p in {'.git', '.gradle', '.kotlin', '.idea', 'build'} for p in path.parts):
            raise SystemExit('Unexpected cache/config directory in source archive: ' + name)
        if path.name in {'local.properties', '.env'} or path.suffix in {'.jks', '.keystore', '.pem', '.key'}:
            raise SystemExit('Unexpected local/credential file in source archive: ' + name)
    if 'Sakugapp/gradle/wrapper/gradle-wrapper.jar' not in archive.namelist():
        raise SystemExit('Source ZIP must include the Gradle Wrapper JAR')
print('Source ZIP verified:', sys.argv[1])
PY
