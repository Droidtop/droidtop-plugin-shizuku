# Shizuku as a droidtop dependency

Private plan. Upstream: https://github.com/RikkaApps/Shizuku (Apache-2.0).
Not a content or game source -- Shizuku is infrastructure: it starts a
privileged server (via ADB pairing or root) and hands its binder to apps
that ask, so they can call system APIs without holding root themselves.

**Plugin model this plan is written against (2026-09-25):** droidtop plugins
run in **droidtop's own process/context**, never through enginehost. A
plugin is Python, a native Kotlin/`.so` bundle (arm64-v8a + x86_64), or
another kind droidtop's host adds support for. The sandbox exists for
stability, not security. Root is an optional enhancement only, never
required, never the default path.

## What it would contribute

Nothing droidtop renders directly -- no library entries, no status tile of
its own, no settings row. Its contribution is enabling *other* plugins:
ReVanced Manager's install path and MMRL's module actions both want
privileged access without demanding root outright, and Shizuku is exactly
droidtop's "root optional" story made concrete -- see both of those repos'
`PLUGIN-PLAN.md`. This repo is a private tracked mirror so the version
droidtop's plugins build against is pinned and known, not because Shizuku
itself becomes a droidtop-visible plugin.

## Reuse as a native bundle

Only the `api` module (the `moe.shizuku.api` client library + AIDL) is
relevant -- this is exactly what ReVanced Manager's own dependency graph
would use if its `feat/shizuku` branch lands, and what an MMRL plugin would
use for a non-root privileged path. `manager` (Shizuku's own app UI),
`server`/`starter`/`shell` (the privileged process itself, launched by the
separate Shizuku app the user installs from Play/GitHub) stay untouched
upstream code -- droidtop never bundles or launches Shizuku's server itself,
it only depends on the client API to talk to whatever Shizuku app is
already on the device, same as any other app that supports Shizuku today.
Since droidtop plugins run directly in droidtop's own process now, the
`api` module can be a plain dependency of any droidtop plugin (or of
droidtop's plugin host itself, exposed to plugins as a shared capability --
see below) rather than something that needs its own hosting story.

## Root

Shizuku *is* the non-root path, functionally: its server can start from
either `adb shell` (wireless debugging pairing, no root) or `su` -- from a
calling app's point of view the binder looks the same either way. So for
droidtop's purposes, "does this device have Shizuku running and granted"
is the actual optional-enhancement check other plugins gate on, and root is
just one of two ways a user might have gotten there. This matches the
project rule (root is desktop-only, handheld/launcher features never need
it) as long as droidtop never prompts a user to root a Retroid Pocket 5 to
use Shizuku -- pairing over ADB should be the documented path for handheld
users, root left implicit for anyone who already has it.

## What it needs from droidtop's plugin API

- A single shared "Shizuku available + granted" capability exposed to
  plugin authors, so ReVanced Manager's and MMRL's plugins don't each
  reimplement the pairing/permission dance against the Shizuku API
  themselves. Since plugins run in droidtop's own process, this is most
  naturally a small shared library droidtop's plugin host provides to any
  plugin that asks for it, rather than a capability call across a process
  boundary.
- The open question this shares with the MMRL and ReVanced Manager plans:
  whether a plugin running in droidtop's own process is allowed to hold a
  live Shizuku binder connection directly (a persistent IPC handle) or has
  to go through some host-mediated call shape instead. Worth settling once,
  here, rather than per-plugin, since it's the same underlying binder
  connection every consumer would share.
