# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- CI now signs the plugin bundle itself (repo secret `PLUGIN_SIGNING_KEY`, optional `PLUGIN_SIGNING_CERT` packaged as `origin.cert`) and attaches it to the release for a `plugin-v*` tag. No change to the plugin itself.

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
