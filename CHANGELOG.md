# Changelog

All notable changes in the `szanko02/kcik-tv-app` fork are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- **Resizable Side Chat:** Configurable side chat width (`15%–70%`) in landscape with a draggable edge handle (`sideChatDividerHandle`), live percentage badge, and double-tap reset to default (`35%`).
- **Chat Message Scale & Spacing:** Message scaling (`50%–200%`), vertical spacing (`0–16 dp`), and two-finger pinch-to-resize gesture directly inside chat (`ChatPinchResizeHelper`).
- **Fullscreen Sensor Rotation:** Option to rotate between landscape orientations via device sensor in fullscreen even when system auto-rotate is disabled.
- **Fullscreen Display Cutout & Video Scaling:** Draw fullscreen landscape video under the camera cutout (`draw_under_cutout`, cutout-safe overlay/side-chat insets) plus `Fit` / `Zoom (Fill Cutout)` / `Stretch` scaling via Player Settings, top-bar scale button, and two-finger pinch gesture (`VideoCutoutScalingPolicy`).
- **Dual Update Source:** Choose between `Fork (szanko02)` and `Upstream (xacnio)` for update checks and the "What's New" sheet, with clean empty-state messaging when no releases exist on the selected source.
- **Unit Tests:** Pure-Kotlin JUnit 4 test suites for `SideChatWidthUtils`, `ChatSizingMath`, `VodChatReplayBuffer`, `PlaybackRefreshPolicy`, `UpdateReleaseSelector`, `VideoCutoutScalingPolicy`, and `ChatInputBarSizer`.
- **ReTwitch Feature Catalog:** Added `RETWITCH_FEATURES.md` documenting ReTwitch capabilities and their implementation/porting status for Kick.

### Changed
- **Mobile-Only Focus:** Completely removed Android TV / Leanback UI (`tv/PlayerActivity`, TV layouts/resources, `androidx.leanback` dependency, `uiMode` switching) and unused funding/landing workflows.
- **Release & CI Workflows:** Updated `ci.yml` and `release.yml` to run translation checks and unit tests, and publish signed release APKs (or debug APKs when signing secrets are not yet configured) on `v*` tags or `workflow_dispatch`.
- **Branding:** Updated developer attribution, repository URLs, and privacy policy link to `szanko02/kcik-tv-app`; removed BuyMeACoffee links.

### Fixed
- **VOD Chat Replay (`VodChatReplayBuffer`):** Replaced burst/cursor-drifting chat fetching with a strict 5-second page stride, 15s ahead buffer (20s target burst), empty-page advancement, seek backfill (`-2.5s`) with seekbar-sliding suppression, generation tokens to discard stale requests, sliding 24-page memory pruning, and exponential backoff.
- **VOD/Clip Refresh:** Refreshing playback during a VOD or clip now reloads the current VOD (preserving playback position and speed) or clip instead of falling back to live channel loading and showing "Stream Offline".
- **VOD Chat Bottom Gap:** Removed the extra `50dp` bottom padding applied to `chatRecyclerView` in VOD replay mode and preserved baseline XML paddings across Live/VOD/Clip transitions.
- **Narrow Landscape Side Chat Input:** Made the live chat input bar responsive (`ChatInputBarSizer` + `ChatInputResponsiveController`) across compact/minimal/ultra-minimal widths so the message input and login overlay stay usable even at 15%–25% side chat width.
- **Version Normalization:** Fixed update tag normalization so inner `v` characters in git commit hashes (e.g., `2.4.5-beta-1v2b3c4`) are preserved.
