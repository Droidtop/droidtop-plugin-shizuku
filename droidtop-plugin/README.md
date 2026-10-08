# droidtop-plugin/

The droidtop `native_bundle` plugin built from this repo (see `PLUGIN-PLAN.md`):
Shizuku for droidtop. Installed through droidtop's Settings > Accounts and
sources > Plugins like any other plugin bundle. It is a contract 2 plugin
(`droidtop/docs/plugin-api.md`).

## What it does

- **Provides ADB-level privilege** to other plugins and to droidtop itself,
  through two standard interfaces that droidtop's broker mediates:
  - `priv.packages@1`, op `force_stop {package} -> {stopped: true}`. This is
    what lets droidtop's Quit to Library end an emulator's process on Android
    13, where a non-privileged app has no way to end another app's game.
  - `priv.shell@1` (attribute `level` = `adb`), op
    `exec {argv: [string], timeoutMs?} -> {exit, stdout, stderr}`. `argv` runs
    directly, never through a shell; each stream keeps its first 64 KiB.
  Both run one command through Shizuku's server as the ADB shell user. The
  broker has checked the caller's own grant for the op's permission
  (`priv.packages`, `priv.shell.adb`, both critical) before this plugin sees the
  call. When this bundle updates an older installed one, its two exports are new
  and stay off until you allow them under Permissions. The plugin adds only what
  the broker cannot know: whether Shizuku is running and whether droidtop is
  allowed in it, and it says so in words when not.
- **Shows Shizuku itself:** a status tile ("Not installed", "Installed, not
  allowed", "Running, allowed") and an app page with actions to open Shizuku's
  own screen and to ask it to allow droidtop.

It declares `host.full_trust`: Shizuku allows droidtop's UID, and this plugin
runs in droidtop's own process, so it cannot be contained. That is also why it
is only ever a provider: nothing depends on it, and a plugin that wants
privilege declares an optional `requires` on `priv.*`.

## How the binder gets here

Shizuku's server pushes its binder to a content provider named
`<droidtop's applicationId>.shizuku`. droidtop's `:plugin-host` declares
Shizuku's own `ShizukuProvider` under that name in `:pluginhost` (the process
plugins run in) and carries Shizuku's client library. This plugin's class
loader delegates to the host's first, so `rikka.shizuku.Shizuku` here is the
copy that holds the binder; the plugin bundles no Shizuku classes. Shizuku
13 made `Shizuku.newProcess` private, so it is reached by reflection.

## Files

- `src/` -- `ShizukuPlugin.kt`, implementing `dev.droidtop.pluginhost.DroidtopPlugin`
  (`invoke` for the tile and app page, `handle` for the two exported APIs).
- `manifest.template.json` -- everything about the manifest except `payload`
  (filled in by `build.sh` once `classes.jar`'s real hash is known). `origin` is
  `"droidtop"`, signed with the official plugin origin key.
- `build.sh` -- compiles, dexes and hashes: produces `build/classes.jar` and
  `build/manifest.json`. Needs `PLUGIN_HOST_CLASSPATH` (droidtop's `:plugin-host`
  classes), `ANDROID_JAR`, and `SHIZUKU_CLASSPATH` (Shizuku's `api`, `aidl` and
  `shared` jars, the version pinned in droidtop's `gradle/libs.versions.toml`),
  the last only to compile against. It touches no private key, so it runs in CI
  (`.github/workflows/plugin-bundle.yml`).
- `sign.sh` -- the only script here that touches the real droidtop plugin origin
  key (`/root/coordination/keys/droidtop-plugins/droidtop-origin-private.pem`);
  signs `build/manifest.json` and packages
  `droidtop.shizuku-bridge.droidplugin.tar.xz`. CI runs it with the `PLUGIN_SIGNING_KEY` repo secret
  (optional `PLUGIN_SIGNING_CERT` becomes `origin.cert`); locally run it on
  droidtop-dev. The key is never committed to this repo.

## Trying it

Install the signed bundle and approve it (if it replaced version 1.0, open
Permissions and allow "Offer priv.packages to other plugins" and "Offer
priv.shell to other plugins"). Start Shizuku (its own pairing over wireless
debugging) and allow droidtop in it. Then, in Gaming, launch a console game, press Home, open the
Quick Menu > Game > Quit to Library: the emulator's process should be gone, not
just droidtop's bookkeeping. Nothing here has been run on a device yet.
