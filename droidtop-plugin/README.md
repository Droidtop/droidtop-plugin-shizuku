# droidtop-plugin/

The droidtop `native_bundle` plugin built from this repo (see
`PLUGIN-PLAN.md`): a `status_tile` + `app_status` surface for Shizuku,
installed through droidtop's Settings -> App integrations -> Plugins
screen like any other plugin bundle.

- `src/` -- `ShizukuPlugin.kt`, implementing `dev.droidtop.pluginhost.DroidtopPlugin`.
  Depends only on the `plugin-host` API compiled from `Droidtop/droidtop`
  (public repo) -- nothing in this folder depends on Shizuku's own `api`/
  `common` modules, since a status tile only needs to know "installed"
  and "granted", both already exposed generically by droidtop's plugin
  host (`PluginContext.isAppInstalled`/`hasShizukuAccess`).
- `manifest.template.json` -- everything about the manifest except
  `payload` (filled in by `build.sh` once `classes.jar`'s real hash is
  known). `origin` is `"droidtop"`: this bundle is signed with the same
  origin key as droidtop's own sample plugin, since droidtop's public
  repo only pins that one origin today (see
  `Droidtop/droidtop`'s `PluginOriginKeys`) -- there is no separate
  "shizuku" origin pinned anywhere, by design.
- `build.sh` -- compiles, dexes and hashes: produces `build/classes.jar`
  and `build/manifest.json`. Touches no private key, so it runs in CI
  (`.github/workflows/plugin-bundle.yml`, which checks out
  `Droidtop/droidtop` to build `:plugin-host` for the classpath) as well
  as locally.
- `sign.sh` -- the only script here that touches the real droidtop
  plugin origin key
  (`/root/coordination/keys/droidtop-plugins/droidtop-origin-private.pem`);
  signs `build/manifest.json` and packages
  `droidtop.shizuku-bridge.droidplugin.tar.xz`. Run on droidtop-dev only;
  never in CI, never committed to this repo.

To produce an installable bundle: download the `plugin-bundle` CI
artifact's `build/manifest.json` + `build/classes.jar` (or run
`build.sh` locally against a `:plugin-host:assembleDebug` classpath from
a `Droidtop/droidtop` checkout), then run
`PLUGIN_SIGNING_KEY=/root/coordination/keys/droidtop-plugins/droidtop-origin-private.pem ./sign.sh`.
