#!/usr/bin/env bash
# Signs and packages an already-built plugin bundle (build.sh output:
# manifest.json + classes.jar) into droidtop.shizuku-bridge.droidplugin.tar.xz.
#
# The ONLY script in this folder that touches the plugin origin private
# key. Run this on droidtop-dev only, where
# /root/coordination/keys/droidtop-plugins/droidtop-origin-private.pem
# lives; the key is never committed to this repo and never given to CI.

set -euo pipefail
cd "$(dirname "$0")"

: "${PLUGIN_SIGNING_KEY:?set to the droidtop plugin origin EC private key PEM}"
: "${BUNDLE_DIR:=build}"

test -f "$BUNDLE_DIR/manifest.json" || { echo "missing $BUNDLE_DIR/manifest.json -- run build.sh first" >&2; exit 1; }
test -f "$BUNDLE_DIR/classes.jar" || { echo "missing $BUNDLE_DIR/classes.jar -- run build.sh first" >&2; exit 1; }

openssl dgst -sha256 -sign "$PLUGIN_SIGNING_KEY" "$BUNDLE_DIR/manifest.json" | base64 -w0 > "$BUNDLE_DIR/manifest.sig"

tar -C "$BUNDLE_DIR" --sort=name -cf - manifest.json manifest.sig classes.jar | xz -9e > droidtop.shizuku-bridge.droidplugin.tar.xz

echo "Signed droidtop.shizuku-bridge.droidplugin.tar.xz"
sha256sum droidtop.shizuku-bridge.droidplugin.tar.xz
