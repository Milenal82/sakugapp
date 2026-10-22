#!/usr/bin/env bash
# OWNER RUNS in a trusted terminal after making encrypted backups. No secret arguments or stdout.
set +x
set -euo pipefail
umask 077
keystore="${1:?Pass the existing official keystore path}"
alias_name="${2:-sakugapp-release}"
repository="${3:-Milenal82/sakugapp}"
test -f "$keystore"
gh api "repos/$repository/environments/release-signing" >/dev/null
read -r -s -p 'Existing keystore password (hidden): ' SAKUGAPP_SIGN_STORE_PASSWORD
echo
read -r -s -p 'Key password (hidden; Enter = same as store password for PKCS12): ' SAKUGAPP_SIGN_KEY_PASSWORD
echo
SAKUGAPP_SIGN_KEY_PASSWORD="${SAKUGAPP_SIGN_KEY_PASSWORD:-$SAKUGAPP_SIGN_STORE_PASSWORD}"
export SAKUGAPP_SIGN_STORE_PASSWORD
certificate_file="$(mktemp)"
trap 'rm -f "$certificate_file"; unset SAKUGAPP_SIGN_STORE_PASSWORD SAKUGAPP_SIGN_KEY_PASSWORD' EXIT
keytool -exportcert -keystore "$keystore" -alias "$alias_name" -storepass:env SAKUGAPP_SIGN_STORE_PASSWORD > "$certificate_file"
fingerprint="$(python3 - "$certificate_file" <<'PY'
import hashlib,sys
print(hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest())
PY
)"
base64 < "$keystore" | tr -d '\r\n' | gh secret set ANDROID_RELEASE_KEYSTORE_BASE64 --repo "$repository" --env release-signing
printf '%s' "$SAKUGAPP_SIGN_STORE_PASSWORD" | gh secret set ANDROID_RELEASE_STORE_PASSWORD --repo "$repository" --env release-signing
printf '%s' "$alias_name" | gh secret set ANDROID_RELEASE_KEY_ALIAS --repo "$repository" --env release-signing
printf '%s' "$SAKUGAPP_SIGN_KEY_PASSWORD" | gh secret set ANDROID_RELEASE_KEY_PASSWORD --repo "$repository" --env release-signing
printf '%s' "$fingerprint" | gh secret set ANDROID_RELEASE_CERT_SHA256 --repo "$repository" --env release-signing
echo "Protected signing Secrets configured. PUBLIC certificate SHA-256: $fingerprint"
