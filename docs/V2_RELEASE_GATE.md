# Monita 2.0.0 — coordinated release gates

**Status: NOT RELEASED.** This document tracks the work required to publish the same `v2.0.0` version for **Monita Server**, **Monita for Home Assistant**, and **Monita for Android**. Do not tag, advertise or publish a stable 2.0.0 component until all three have passed the gates below.

## Published baseline (verified 2026-10-14)

| Component | Published release | Repository |
| --- | --- | --- |
| Server | 1.3.9 stable | [Monita](https://github.com/gigabytegrove/monita) |
| Home Assistant | 1.8.16 | [Monita HA](https://github.com/gigabytegrove/monita-ha) |
| Android | 0.3.17 signed phone-test APK | [Monita Android](https://github.com/gigabytegrove/monita-android) |

Android 0.3.19 is **source-only/development** at this checkpoint; the repository's build jobs fail before executing build steps. A source version or README claim is not proof that a verified APK exists.

## Required 2.0.0 compatibility contract

- **Versioning:** use exactly `2.0.0` for all three *stable published* GitHub releases; corresponding server VERSION, Home Assistant manifest version, Android versionName and user-facing About screens must agree.
- **Upgrade order:** Server first, Home Assistant second, Android third; verify the older installed companions can communicate throughout the rolling upgrade.
- **Existing installations:** preserve Monita DB/data, Channels, message history within each Channel's retention policy, membership, credentials, HA entities/automations/config-entry identifiers, and Android signed-package upgrade identity.
- **API:** native `/api/monita/v1/*` first, with narrowly scoped legacy protocol fallbacks retained for older installations and servers. Never expose secrets in URLs or logs.
- **Delivery:** single-container in-app server updates, platform-agnostic Docker Compose, HACS install/upgrade package and a downloadable, verifiably signed, non-debuggable Android APK. No additional updater container or deployment-platform dependency.
- **Verification:** build and test actual release artifacts and record checksums, CI run URLs, version/build numbers, compatibility results, and a tested rollback method. No documentation-only substitution for end-to-end tests.

## Server release gates

- [ ] Verify every current-code message workflow API and Web UI control is opt-in per message and disabled actions are forbidden server-side.
- [ ] Verify 24-hour Notification Channel retention for new/existing Channels, archived messages, exact timing and repeat-safe migration; Chat history indefinite by default.
- [ ] Verify attachment upload, authorized access, permanent message delete, retention cleanup, filesystem orphan cleanup, and migration/backup preservation.
- [ ] Run Go race/coverage, UI tests, security and dependency checks against the final release commit. Resolve the broken Makefile recipe and verify the subsequent CI run.
- [ ] Verify responsive Web/PWA, Settings, update-channel selection, correct in-app progress, and restart/data preservation.
- [ ] Build, checksum and exercise both amd64 and arm64 server runtime packages and install/update flows.

## Home Assistant release gates

- [ ] Exercise fresh HACS installation and *in-place* upgrade of a historical-domain installation; verify config entry, entity IDs, device IDs, automations, secrets and Channel settings survive without a duplicate card.
- [ ] Keep the migration bootstrap until a safe, tested removal path exists. Do not delete a compatibility component simply for branding.
- [ ] Verify native pairing, repair, revoke, both event directions, reconnect/re-auth, LLT-free mode, diagnostics and redaction against the final server.
- [ ] Verify `monita.send` message controls, image entity/camera/URL uploads (real bytes), server capability refresh and legacy fallback.
- [ ] Pass integration tests and HACS package installation using the final release zip.

## Android release gates

- [ ] Restore working build infrastructure and produce a **real** 2.0.0 APK; CI workflows that fail before runner assignment do **not** satisfy this requirement.
- [ ] Confirm APK package identity `com.gigabytegrove.monita`, monotonically increasing versionCode and production signing certificate. Production signing is distinct from the public phone-test signing identity, so document/verify the one-time migration consequences.
- [ ] Test Wi-Fi-to-mobile/default-network changes, revoked vs valid tokens, reconnect timing and absence of duplicate callbacks and self-notifications on devices.
- [ ] Verify per-Channel sounds, explicit Chat mention routing/wording/sounds, Android permissions, notification deep links, view refresh, scroll preservation, images and protected Channels.
- [ ] Run Kotlin/Android tests, lint, JS/WebView checks, identity audit, actual-device acceptance tests, APK signing validation and hash verification.

## Coordinated publication/rollback

1. Complete and merge verified source changes in all three repositories. Preserve PR reviews, tests, and release evidence.
2. Take a database backup and capture Home Assistant/Android upgrade test baselines.
3. Tag `v2.0.0` for server; verify prebuilt artifacts and actual in-app upgrade first.
4. Publish matching HA `v2.0.0`, install through HACS and exercise persisted migrated entries.
5. Publish matching Android `v2.0.0` with signed APK, checksum, tested installation behavior and explicit test-to-production signing limitations.
6. Update all documentation and in-app version references, inspect all three releases together, then announce 2.0.0 as stable.
7. If a gate fails, leave the affected version unreleased, retain previous stable artifacts, and document rollback/data compatibility. Do not invent successful tests.

## Known limitations at kickoff

Android's October 8 GitHub Actions runs failed before normal job steps executed, and its latest published APK is 0.3.17, not its 0.3.19 development version. This must be resolved before 2.0.0 can be called complete.
