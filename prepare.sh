#!/usr/bin/env bash
# Run by the shared droidtop-plugin-bundle workflow in the build job, from this
# directory, after kotlinc is installed and before build.sh: fetches Shizuku's
# client library for the compile classpath (it is compiled against and never
# bundled) and hands it to build.sh through SHIZUKU_CLASSPATH. DROIDTOP_DIR is
# the Droidtop/droidtop checkout; the version is the one its :plugin-host
# carries (the "shizuku" entry of gradle/libs.versions.toml).
set -euo pipefail
SHIZUKU_VERSION=$(grep '^shizuku = ' "$DROIDTOP_DIR/gradle/libs.versions.toml" | cut -d '"' -f2)
echo "Using Shizuku client $SHIZUKU_VERSION"
mkdir -p "$RUNNER_TEMP/shizuku"
JARS=""
for module in api aidl shared; do
  curl -sfL "https://repo1.maven.org/maven2/dev/rikka/shizuku/${module}/${SHIZUKU_VERSION}/${module}-${SHIZUKU_VERSION}.aar" -o "$RUNNER_TEMP/shizuku/${module}.aar"
  unzip -q -o "$RUNNER_TEMP/shizuku/${module}.aar" classes.jar -d "$RUNNER_TEMP/shizuku/${module}"
  JARS="${JARS:+$JARS:}$RUNNER_TEMP/shizuku/${module}/classes.jar"
done
echo "SHIZUKU_CLASSPATH=$JARS" >> "$GITHUB_ENV"
