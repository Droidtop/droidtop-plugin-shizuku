<h1 align="center">Shizuku for droidtop</h1>

<p align="center">
  <b>System-level access for droidtop without root.</b><br>
  A status tile for the Shizuku app, and privilege for other plugins, each only after you allow it.
</p>

<p align="center">
  <a href="https://github.com/Droidtop/droidtop-plugin-shizuku/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/Droidtop/droidtop-plugin-shizuku?label=release&include_prereleases&style=flat-square"></a>
  <a href="https://github.com/Droidtop/droidtop-plugin-shizuku/actions/workflows/plugin-bundle.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/Droidtop/droidtop-plugin-shizuku/plugin-bundle.yml?branch=main&label=build&style=flat-square"></a>
  <img alt="For droidtop" src="https://img.shields.io/badge/for-droidtop-2f7bf5?style=flat-square">
  <a href="LICENSE"><img alt="License: Apache-2.0" src="https://img.shields.io/badge/license-Apache--2.0-blue?style=flat-square"></a>
</p>

<p align="center">
  <img src="docs/images/quick-menu-panel.webp" alt="The Shizuku entry in droidtop's Quick Menu plugins panel, with an App status row" width="35%">
</p>

<p align="center">
  <a href="#what-the-droidtop-plugin-does">What it does</a> ·
  <a href="#install">Install</a> ·
  <a href="#status">Status</a> ·
  <a href="#licence">Licence</a> ·
  <a href="#upstream-readme">Upstream README</a>
</p>

This is a fork of [Shizuku](https://github.com/RikkaApps/Shizuku) with a [droidtop](https://github.com/Droidtop/droidtop) plugin added in `droidtop-plugin/`. droidtop never starts or bundles Shizuku's server. It talks to the Shizuku app you already installed.

## What the droidtop plugin does

- **Status tile and actions.** Shows "Not installed", "Installed, not allowed" or "Running, allowed", and opens Shizuku or asks it to allow droidtop.
- **Privilege for other plugins and droidtop itself**, through two interfaces droidtop checks on every call:
  - ending another app's game, so Quit to Library works on Android 13;
  - granting an emulator access you approve (an app-op, or a permission) for droidtop's emulator setup helper, only after you turn on Risky actions and confirm that use;
  - running one command as the ADB shell user, and reading and writing files in shared storage.
- **Root when Shizuku runs as root**, still only after you allow it.

It is a provider only. Nothing depends on it, and a plugin that wants privilege asks for it as optional. Handheld users can start Shizuku over wireless debugging, so no root is needed.

## Install

You need [droidtop](https://github.com/Droidtop/droidtop). You also need the [Shizuku app](https://shizuku.rikka.app/) installed and started.

1. In droidtop open **Settings > Plugins > Add**. droidtop's own catalog is always there.
2. Pick **Shizuku** and press **Install**.
3. Read the permissions it asks for and press **Accept**. Nothing runs until you do.

You can also browse the catalog at [droidtop.github.io](https://droidtop.github.io/), or open this plugin's install page directly: [Install in droidtop](https://droidtop.github.io/install-plugin?catalog=https%3A%2F%2Fraw.githubusercontent.com%2Fdroidtop%2Fdroidtop-platforms%2Fmain%2Fdroidtop-plugins%2Findex.json&id=droidtop.shizuku-bridge). That link only opens a review inside droidtop.

## Status

Released as `plugin-v1.4.0`, and listed in droidtop's official catalog. The code is in `droidtop-plugin/`, and the plan it follows is in `PLUGIN-PLAN.md`.

## Licence

The fork and the plugin are Apache-2.0, like upstream Shizuku, see [LICENSE](LICENSE).

---

## Upstream README

The rest of this page is the README of [Shizuku](https://github.com/RikkaApps/Shizuku), kept as it is.

---

# Shizuku

## Background

When developing apps that requires root, the most common method is to run some commands in the su shell. For example, there is an app that uses the `pm enable/disable` command to enable/disable components.

This method has very big disadvantages:

1. **Extremely slow** (Multiple process creation)
2. Needs to process texts (**Super unreliable**)
3. The possibility is limited to available commands
4. Even if ADB has sufficient permissions, the app requires root privileges to run

Shizuku uses a completely different way. See detailed description below.

## User guide & Download

<https://shizuku.rikka.app/>

## How does Shizuku work?

First, we need to talk about how app use system APIs. For example, if the app wants to get installed apps, we all know we should use `PackageManager#getInstalledPackages()`. This is actually an interprocess communication (IPC) process of the app process and system server process, just the Android framework did the inner works for us.

Android uses `binder` to do this type of IPC. `Binder` allows the server-side to learn the uid and pid of the client-side, so that the system server can check if the app has the permission to do the operation.

Usually, if there is a "manager" (e.g., `PackageManager`) for apps to use, there should be a "service" (e.g., `PackageManagerService`) in the system server process. We can simply think if the app holds the `binder` of the "service", it can communicate with the "service". The app process will receive binders of system services on start.

Shizuku guides users to run a process, Shizuku server, with root or ADB first. When the app starts, the `binder` to Shizuku server will also be sent to the app.

The most important feature Shizuku provides is something like be a middle man to receive requests from the app, sent them to the system server, and send back the results. You can see the `transactRemote` method in `rikka.shizuku.server.ShizukuService` class, and `moe.shizuku.api.ShizukuBinderWrapper` class for the detail.

So, we reached our goal, to use system APIs with higher permission. And to the app, it is almost identical to the use of system APIs directly.

## Developer guide

### API & sample

https://github.com/RikkaApps/Shizuku-API

### Migrating from pre-v11

> Existing applications still works, of course.

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### Attention

1. ADB permissions are limited

   ADB has limited permissions and different on various system versions. You can see permissions granted to ADB [here](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml).

   Before calling the API, you can use `ShizukuService#getUid` to check if Shizuku is running user ADB, or use `ShizukuService#checkPermission` to check if the server has sufficient permissions.

2. Hidden API limitation from Android 9

   As of Android 9, the usage of the hidden APIs is limited for normal apps. Please use other methods (such as <https://github.com/LSPosed/AndroidHiddenApiBypass>).

3. Android 8.0 & ADB

   At present, the way Shizuku service gets the app process is to combine `IActivityManager#registerProcessObserver` and `IActivityManager#registerUidObserver` (26+) to ensure that the app process will be sent when the app starts. However, on API 26, ADB lacks permissions to use `registerUidObserver`, so if you need to use Shizuku in a process that might not be started by an Activity, it is recommended to trigger the send binder by starting a transparent activity.

4. Direct use of `transactRemote` requires attention

   * The API may be different under different Android versions, please be sure to check it carefully. Also, the `android.app.IActivityManager` has the aidl form in API 26 and later, and `android.app.IActivityManager$Stub` exists only on API 26.

   * `SystemServiceHelper.getTransactionCode` may not get the correct transaction code, such as `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` does not exist on API 25 and there is `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47` (this situation has been dealt with, but it is not excluded that there may be other circumstances). This problem is not encountered with the `ShizukuBinderWrapper` method.

## Developing Shizuku itself

### Build

- Clone with `git clone --recurse-submodules`
- Run gradle task `:manager:assembleDebug` or `:manager:assembleRelease`

The `:manager:assembleDebug` task generates a debuggable server. You can attach a debugger to `shizuku_server` to debug the server. Be aware that, in Android Studio, "Run/Debug configurations" - "Always install with package manager" should be checked, so that the server will use the latest code.

## License

All code files in this project are licensed under Apache 2.0

Under Apache 2.0 section 6, specifically:

* You are **FORBIDDEN** to use `manager/src/main/res/mipmap*/ic_launcher*.png` image files, unless for displaying Shizuku itself.

* You are **FORBIDDEN** to use `Shizuku` as app name or use `moe.shizuku.privileged.api` as application id or declare `moe.shizuku.manager.permission.*` permission.

## Fork status

This repo mirrors upstream [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)
for droidtop plugin work (see `PLUGIN-PLAN.md`). It is an official droidtop
plugin fork, kept in sync with upstream, not a place for droidtop's own
patches beyond the `droidtop-plugin/` wrapper.

Branches:
- `upstream-main` tracks upstream's `master` exactly (fast-forward only,
  synced daily by `.github/workflows/sync-upstream.yml`).
- `main` is where plugin/integration work happens; the sync workflow merges
  `upstream-main` into it automatically when there's no conflict, and opens
  an issue here instead of forcing anything when there is one.

### Licence

Shizuku is Apache-2.0 (see `LICENSE`). Apache-2.0 permits modification,
use, and redistribution with attribution and a copy of the licence; the only
real requirement here is what's already true — `LICENSE` and
NOTICE-equivalent copyright text stay intact. No copyleft, no obligation to
share changes.
