#!/usr/bin/env bash
# Compiles, dexes and hashes droidtop.shizuku-bridge's payload from this
# folder's source, in the exact shape PluginBundleInstaller.install()
# expects (Droidtop/droidtop docs/SPEC.md 12a) -- same build.sh shape as
# droidtop's own samples/plugin-sample-statustile, adapted only in where
# the :plugin-host classpath comes from: this repo has no droidtop
# checkout of its own, so CI (.github/workflows/plugin-bundle.yml)
# checks out Droidtop/droidtop separately and builds :plugin-host there.
#
# Split from signing (see sign.sh) on purpose: this script never touches
# the plugin origin's private key and is safe to run in CI. Signing is the separate sign.sh step: CI runs it with the
# PLUGIN_SIGNING_KEY repo secret, droidtop-dev runs it locally with the origin key.
#
# Prerequisites:
#   - kotlinc on PATH (matching Droidtop/droidtop's gradle/libs.versions.toml
#     "kotlin" entry)
#   - d8 on PATH (Android SDK build-tools)
#   - PLUGIN_HOST_CLASSPATH pointing at a compiled :plugin-host classes.jar
#     (from Droidtop/droidtop, e.g. its plugin-host-debug.aar's classes.jar)
#   - ANDROID_JAR pointing at android.jar for :plugin-host's compileSdk
#   - SHIZUKU_CLASSPATH: Shizuku's client jars (api, aidl and shared, dev.rikka.shizuku 13.x,
#     colon-separated), for compiling only. The plugin never bundles them: droidtop's
#     :plugin-host carries the same library and the plugin's class loader delegates to it.
#
# Optionally, for a one-shot local build+sign (droidtop-dev only): also set
# PLUGIN_SIGNING_KEY and this script calls sign.sh itself at the end.

set -euo pipefail
cd "$(dirname "$0")"

: "${PLUGIN_HOST_CLASSPATH:?set to a jar/dir containing dev.droidtop.pluginhost.* compiled classes}"
: "${ANDROID_JAR:?set ANDROID_JAR to android.jar for the target compileSdk}"
: "${SHIZUKU_CLASSPATH:?set SHIZUKU_CLASSPATH to the Shizuku api, aidl and shared jars, colon-separated}"

rm -rf build
mkdir -p build/classes

kotlinc -cp "$PLUGIN_HOST_CLASSPATH:$SHIZUKU_CLASSPATH:$ANDROID_JAR" -d build/classes src/dev/droidtop/plugins/shizuku/ShizukuPlugin.kt

# The host's and Shizuku's classes are only on the classpath so d8 can desugar against them; they are not in the output.
CLASSPATH_ARGS=()
IFS=':' read -ra CP_ITEMS <<< "$PLUGIN_HOST_CLASSPATH:$SHIZUKU_CLASSPATH"
for item in "${CP_ITEMS[@]}"; do CLASSPATH_ARGS+=(--classpath "$item"); done
d8 --output build --lib "$ANDROID_JAR" "${CLASSPATH_ARGS[@]}" \
  $(find build/classes -name '*.class')

# classes.jar is a zip containing classes.dex at its root -- what
# DexClassLoader (PluginRuntimeService) expects.
(cd build && zip -q classes.jar classes.dex)

CLASSES_SHA=$(sha256sum build/classes.jar | cut -d' ' -f1)

python3 - "$CLASSES_SHA" <<'PY'
import json, sys
sha = sys.argv[1]
manifest = json.load(open("manifest.template.json"))
manifest["payload"] = [{"path": "classes.jar", "sha256": sha}]
json.dump(manifest, open("build/manifest.json", "w"), indent=2, sort_keys=True)
PY

echo "Built build/classes.jar and build/manifest.json (unsigned)"
sha256sum build/classes.jar

if [ -n "${PLUGIN_SIGNING_KEY:-}" ]; then
  PLUGIN_SIGNING_KEY="$PLUGIN_SIGNING_KEY" ./sign.sh
else
  echo "PLUGIN_SIGNING_KEY not set -- stopping here, unsigned."
  echo "Run ./sign.sh with PLUGIN_SIGNING_KEY set (CI does this with the PLUGIN_SIGNING_KEY repo secret) to produce droidtop.shizuku-bridge.droidplugin.tar.xz."
fi
