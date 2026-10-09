# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.3.0] - 2026-10-09

### Added
- priv.packages `set_appop {package, op, mode}` and `grant_permission {package, permission}`: droidtop's Emulator setup helper uses them to give an emulator All files access (`MANAGE_EXTERNAL_STORAGE`) or a runtime permission, instead of you doing it in Android's screens. droidtop asks only after you turn on Settings > Risky actions there and confirm that one use; the plugin checks the shape of every argument and runs `appops set` or `pm grant` only. They come under the existing "priv.packages" permission, so an updated install needs nothing new from you.

## [1.2.0] - 2026-10-09

### Added
- Long-running commands with input and output (priv.shell exec_stream, stream_read, stream_write, stream_kill), at the system (adb) and root levels, for droidtop's rooted desktop and for plugins allowed them.
- Root through Shizuku: when Shizuku's server runs as root (started as root, or Sui), other plugins can run commands as root through it, as their own grant ("Run commands as root"), separate from running them as the system (adb). droidtop offers it only while Shizuku really is root; the status tile says "Running as root, allowed".

### Changed
- CI now signs the plugin bundle itself (repo secret `PLUGIN_SIGNING_KEY`, optional `PLUGIN_SIGNING_CERT` packaged as `origin.cert`) and attaches it to the release for a `plugin-v*` tag.
- While Shizuku runs as root, a plugin allowed only "Run commands as the system (adb)" is told to ask for root instead of having its command run as root: Shizuku cannot drop to the shell user without su.

## [1.1.0] - 2026-09-29

### Added

- Other plugins and droidtop itself can ask Shizuku to force-stop an app (priv.packages) or run an ADB-level shell command (priv.shell), each only after you allow it.

### Changed

- The plugin now uses the second plugin contract: it declares what it provides and what it needs up front, so droidtop shows the request on its Permissions screen.

## [1.0.0]

### Added

- Plugin wrapper exposing Shizuku status (installed, granted, or not granted) through droidtop status tile and app actions.
- Plugin plan describing native-bundle reuse, root/ADB pairing path, and shared capability for other plugins.
- CI workflow to build the plugin payload against droidtop's plugin-host API.
- License clarification and daily upstream sync workflow.

### Changed

- Plugin now runs directly in droidtop's own process; removed enginehost hosting assumption.
- Plugin framing updated from private fork to public droidtop plugin fork.
- CI checkout path fixed to run from workspace root instead of a subdirectory.
- Updated upstream base to Shizuku 13.6.0 with AGP 8.10.1, LTO, and translation updates.

### Fixed

- License exemption text corrected.
- Plugin source placement in CI build adjusted to plugin-repo path.
