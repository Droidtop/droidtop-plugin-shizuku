# Shizuku as a droidtop dependency

Private plan. Upstream: https://github.com/RikkaApps/Shizuku (Apache-2.0).
Not a content or game source — Shizuku is infrastructure: it starts a
privileged server (via ADB pairing or root) and hands its binder to apps
that ask, so they can call system APIs without holding root themselves.

## What it would contribute

Nothing droidtop renders directly — no library entries, no status tile of
its own, no settings row. Its contribution is enabling *other* plugins:
ReVanced Manager's install path and MMRL's module actions both want
privileged access without demanding root outright, and Shizuku is exactly
droidtop's "root optional" story made concrete — see both of those repos'
`PLUGIN-PLAN.md`. This repo is a private tracked mirror so the version
droidtop/enginehost builds against is pinned and known, not because Shizuku
itself becomes a droidtop-visible plugin.

## Reuse as a native bundle

Only the `api` module (the `moe.shizuku.api` client library + AIDL) is
relevant — this is exactly what ReVanced Manager's own dependency graph
would use if its `feat/shizuku` branch lands. `manager` (Shizuku's own app
UI), `server`/`starter`/`shell` (the privileged process itself, launched by
the separate Shizuku app the user installs from Play/GitHub) stay untouched
upstream code — droidtop never bundles or launches Shizuku's server itself,
it only depends on the client API to talk to whatever Shizuku app is already
on the device, same as any other app that supports Shizuku today.

## Root

Shizuku *is* the non-root path, functionally: its server can start from
either `adb shell` (wireless debugging pairing, no root) or `su` — from a
calling app's point of view the binder looks the same either way. So for
droidtop's purposes, "does this device have Shizuku running and granted"
is the actual optional-enhancement check other plugins gate on, and root is
just one of two ways a user might have gotten there. This matches the
project rule (root is desktop-only, handheld/launcher features never need
it) as long as droidtop never prompts a user to root a Retroid Pocket 5 to
use Shizuku — pairing over ADB should be the documented path for handheld
users, root left implicit for anyone who already has it.

## What it needs from droidtop's plugin API

- Not a subplugin capability at all — Shizuku isn't something enginehost
  loads as a bundle, it's a binder client other subplugins link against.
  The open question for the plugins agent is whether a subplugin running
  inside enginehost's process is even allowed to hold a live Shizuku binder
  connection (a persistent IPC handle, not a request/response call through
  the capabilities provider) — if not, ReVanced Manager's and MMRL's plans
  above need a different privileged-access story, and that should be
  settled once, here, rather than per-plugin.
- A single shared "Shizuku available + granted" check exposed to plugin
  authors, so ReVanced Manager's and MMRL's subplugins don't each
  reimplement the pairing/permission dance against the Shizuku API
  themselves.
