#!/usr/bin/env bash
# OWNER RUNS ONCE in a trusted terminal. This script is never invoked by CI.
set +x
set -euo pipefail
umask 077
destination="${1:?Pass a NEW private directory outside every Git checkout}"
case "$destination" in /*) ;; *) echo 'Use an absolute private directory path.' >&2; exit 1 ;; esac
if [ -e "$destination" ]; then echo 'Directory already exists. Reuse/back up an existing key; never overwrite it.' >&2; exit 1; fi
mkdir -m 700 "$destination"
if git -C "$destination" rev-parse --show-toplevel >/dev/null 2>&1; then
  rmdir "$destination"; echo 'Refusing to generate a key inside a Git checkout.' >&2; exit 1
fi
keytool -genkeypair -keystore "$destination/sakugapp-release.p12" -storetype PKCS12 \
  -alias sakugapp-release -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 \
  -dname 'CN=Sakugapp Release'
chmod 600 "$destination/sakugapp-release.p12"
echo 'Key created once. Back up this file AND its password separately before closing the environment.'
echo 'Do not upload the key as a Release asset or paste it/passwords into chat.'
