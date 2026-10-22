#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
destination="${1:?Pass an absolute ZIP output path outside the checkout}"
case "$destination" in /*) ;; *) echo 'Output path must be absolute' >&2; exit 1 ;; esac
mkdir -p "$(dirname "$destination")"
git archive --format=zip --prefix=Sakugapp/ -o "$destination" HEAD
python3 scripts/verify-source-zip.py "$destination"
