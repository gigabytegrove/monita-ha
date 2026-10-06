## 1.8.16 — 2026-10-06

### Finalized duplicate-card cleanup release

- Promotes the fully tested duplicate-card cleanup from the complete validated tree.
- Keeps the historical `gotify_mu` migration bootstrap classified as a Home Assistant `helper`, which prevents Home Assistant's Devices & services dashboard from synthesizing a second unmanaged YAML-style integration card after migration.
- Preserves the canonical `monita` integration as the only user-facing Monita card.
- Includes the migration regression test that locks `config_flow: false` and `integration_type: helper` for the legacy bootstrap.
- Built as a single atomic release commit so the published tag cannot point at an intermediate, partially validated state.

## 1.8.15 — 2026-10-06

### Remove duplicate post-migration integration card

- Marks the historical `gotify_mu` bootstrap as a Home Assistant `helper` integration instead of a normal service integration.
- Home Assistant's Devices & services dashboard synthesizes a YAML-style card for loaded, non-config-flow service integrations that have no config entry. After migration, the bootstrap is intentionally left with no config entry, which caused the second unmanaged Monita card.
- Home Assistant excludes helper integrations from that synthetic integration-card path, so the migration bootstrap can remain available for older installs without appearing as a second Monita integration after the handoff.
- The canonical `monita` integration remains the only user-facing Monita integration after migration.
- Adds regression coverage that locks the bootstrap manifest to `integration_type: helper` and `config_flow: false`.

## 1.8.14 — 2026-10-06

### Fully validated legacy-to-Monita migration

- Supersedes the incomplete 1.8.13 staging release with the complete migration implementation and regression coverage.
- Provides a self-contained historical-domain config-flow platform so Home Assistant can import `gotify_mu.config_flow` before canonical Monita exists on disk.
- Restores legacy config-entry schema migration before the domain handoff, including v1/v2 normalization and Channel selection migration.
- Exercises Home Assistant's real config-entry setup path to verify the historical config-flow platform imports successfully.
- Exercises the actual `gotify_mu` → `monita` config-entry handoff and verifies the original config-entry ID, data, options, and unique ID are preserved.
- Keeps the historical integration migration-only while canonical Monita remains the production integration.

## 1.8.13 — 2026-10-06

### Production migration bootstrap correction

- Replaces the historical-domain config flow with a self-contained migration-only registration module. Home Assistant imports `config_flow` before `async_setup_entry`, so the old flow can no longer import any canonical Monita modules before the migration payload has installed them.
- Restores v1/v2-to-v3 config-entry schema migration inside the bootstrap itself before the domain handoff.
- Keeps the legacy integration hidden from new-add flows while retaining the config-flow platform Home Assistant requires to load and migrate existing entries.
- Preserves the existing config-entry ID, entity IDs, unique IDs, options, credentials, device ownership, and entity-registry ownership through the `gotify_mu` to `monita` handoff.
- Adds regression coverage specifically for the migration bootstrap and legacy schema conversion.

## 1.8.12 — 2026-10-05

### Home Assistant compatibility fix for migration bootstrap

- Fixes startup failure on Home Assistant releases that do not expose `async_clear_custom_components_cache` from `homeassistant.loader`.
- Uses the stable loader cache keys already present in supported Home Assistant releases to invalidate custom-integration discovery after installing canonical Monita.
- Clears any stale cached lookup for the `monita` domain before scheduling canonical setup.
- Marks both the legacy migration bootstrap and canonical Monita integration for import-executor loading to avoid event-loop blocking warnings during component import.
- Keeps the self-contained legacy-to-Monita migration path introduced in 1.8.11.

## 1.8.11 — 2026-10-05

### Complete self-contained migration release

- Supersedes the incomplete 1.8.10 release, which was published before the self-contained migration payload was present.
- Ships the legacy-domain bootstrap and the complete canonical Monita payload together in the same HACS-installed historical component directory.
- Fixes startup when only `custom_components/gotify_mu` exists by removing all pre-migration imports of `custom_components.monita`.
- Installs `custom_components/monita` from the bundled payload, clears Home Assistant's custom-component loader cache, migrates the config entry domain, and schedules canonical Monita setup.
- Preserves the existing config-entry ID and migrates entity-platform ownership, device identifiers, options, credentials, selected channels, and entity IDs.
- Leaves no requirement for the user to delete/re-add the integration or manually copy files.

## 1.8.10 — 2026-10-05

### Self-contained legacy-to-Monita migration bootstrap

- Fixes upgrades where HACS installed only the historical `gotify_mu` component directory and the compatibility shim failed before migration with `No module named 'custom_components.monita'`.
- Bundles the complete canonical Monita integration inside the historical component as a migration payload, so migration no longer depends on a sibling `custom_components/monita` directory already existing.
- On first legacy setup, installs the canonical Monita component into `custom_components/monita`, migrates the config entry to the `monita` domain, preserves the existing config-entry ID, and immediately schedules canonical setup.
- Migrates entity-registry platform ownership from `gotify_mu` to `monita` while preserving entity IDs and unique IDs.
- Migrates device identifiers from the historical integration domain to `monita` and removes historical repair issues.
- The historical package no longer imports the canonical package before migration, eliminating the startup import failure.

## 1.8.9 — 2026-10-05

### Automatic Home Assistant domain migration

- Adds the missing automatic migration from the historical Home Assistant integration domain to the canonical `monita` domain.
- Existing installations are migrated automatically during setup; no delete/re-add, token re-entry, or manual config-entry editing is required.
- Preserves the existing Home Assistant config-entry ID during migration so device/entity registry ownership, entity identity, dashboards, and automations stay attached to the same entry.
- Re-indexes the config entry under the canonical Monita domain and immediately schedules canonical Monita setup.
- Keeps the historical component only as a one-way migration bootstrap for installations that have not migrated yet.
- Refuses to overwrite a distinct canonical Monita entry with the same unique ID, preventing destructive duplicate migration.

## 1.8.8 — 2026-10-05

### Existing-install upgrade recovery

- Restores the historical Home Assistant config-entry compatibility component required by installations whose stored domain still uses the predecessor technical identifier.
- Keeps all user-facing integration names, config flows, entities, repairs, and translations branded as **Monita for Home Assistant**.
- Restores release packaging for both the canonical `monita` component and the compatibility component so HACS upgrades can repair existing installations in place.
- Restores validation and branding generation for the compatibility component.
- No delete/re-add or credential re-entry is required for an existing installation.

## 1.8.7 — 2026-10-04

### Legacy compatibility cleanup

- Reduces the historical `custom_components/monita` implementation toward a compatibility-only bridge while keeping `custom_components/monita` canonical.
- Replaces duplicated notification, event, binary-sensor, and repair implementations with thin Monita compatibility shims.
- Preserves repair issues under the historical `monita` domain for existing config entries so repair flows remain discoverable after upgrade.
- Keeps legacy domain, manifest, config-flow, translation, service, credential, and entity compatibility required for non-destructive upgrades.

## 1.8.6

- Continued the Monita identity cleanup across the Home Assistant integration.
- Reduced the legacy `monita` compatibility component by delegating shared API, media, diagnostics, and helper logic to the canonical `monita` implementation.
- Preserved the historical Home Assistant domain only for existing config-entry compatibility.
- Corrected repair/help links to the current Monita for Home Assistant repository.
- Added identity regression coverage so stale product branding cannot return unnoticed.

# Changelog

## 1.8.5 — 2026-10-02

### Transparent icon correction

- Restored the transparent Monita for Home Assistant icon requested for all icon surfaces.
- Removed only the 512×512 gray canvas/background rectangle from the canonical HA icon SVG; all other artwork remains unchanged.
- HACS root branding plus canonical `monita` and compatibility `monita` icon aliases are regenerated from the same transparent master.
- Full Monita for Home Assistant logo artwork is unchanged.

### Compatibility

- No configuration, entity, action, token, or automation migration is required.
- Existing installations update in place.

## 1.8.4 — 2026-10-02

### Authoritative supplied branding

- Restored the full Monita for Home Assistant logo and icon directly from the supplied SVG masters.
- The icon canvas/background is preserved exactly as supplied; the branding renderer no longer removes or alters it.
- HACS root branding, the canonical `monita` component, and the existing-install `monita` compatibility component all use raster copies generated from those same supplied masters.
- Root `icon.png` is the supplied HA icon and root `logo.png` is the supplied full HA logo.
- GitHub documentation uses the supplied vector full logo directly for crisp rendering.
- No redraw, recolor, crop, trace, simplification, background removal, or geometry change is performed.

### Compatibility

- No configuration, entity, action, token, or automation migration is required.
- Existing `monita` and compatibility `monita` installations update in place.

## 1.8.3 — 2026-10-01

### Transparent icon background

- Removed only the 512×512 gray background rectangle from the canonical Monita for Home Assistant icon SVG.
- Preserved all Home Assistant icon artwork, gradients, embedded image data, geometry, colors, clipping, and the original vector canvas unchanged.
- HACS/root branding and both canonical and compatibility component icon PNGs are regenerated from the transparent master at full 512×512 quality.

### Compatibility

- No configuration migration is required.
- Existing `monita` and compatibility `monita` installations update in place.

## 1.8.2 - 2026-10-01

### Canonical branding refresh

- Replaced the Home Assistant integration, HACS repository card, release package, documentation, canonical domain, and existing-install compatibility branding with raster copies rendered directly from the exact SVG masters supplied on 2026-10-01.
- Added the supplied full Monita for Home Assistant logo and standalone Home Assistant icon as the canonical vector source files.
- The canonical `monita` and compatibility `monita` component directories now receive byte-identical logo/icon aliases generated from the same masters.
- Root-level HACS `logo.png` and `icon.png` are generated from the same approved artwork.
- CI locks the supplied SVG masters by Git blob hash and verifies all generated compatibility aliases.

### Compatibility

- No configuration, entity, action, token, or automation migration is required from 1.8.1.
- Existing `monita` installations remain supported while new integrations can use the canonical `monita` domain.

## 1.8.1 - 2026-10-01

### HACS branding
- Added canonical root-level `logo.png` and `icon.png` assets so HACS can render Monita branding in the Downloaded repositories card.
- Root HACS branding reuses the exact locked Monita integration artwork; no resampling or alternate logo was introduced.
- HACS validation, hassfest, static checks, and integration tests remain required for the release.

### Compatibility
- No configuration or entity migration is required from 1.8.0.
- Existing `monita` installations remain supported while new integrations can use the canonical `monita` domain.

## 1.8.0 - 2026-09-30

### Canonical Monita identity
- Added the canonical Home Assistant integration domain `monita` under `custom_components/monita`.
- New installations use `monita` and the canonical `monita.send` action.
- Retained the historical integration domain only as an existing-install compatibility component so previously stored config entries, entity identities, credentials, and automations remain loadable.
- The canonical and compatibility components resolve Monita Channel targets across both domains, allowing mixed transition states without binding the action to whichever component loads first.
- Canonical source classes use Monita naming; the predecessor platform terminology remains only where it describes the compatible wire protocol or the historical compatibility layer.

### Messaging
- Added `@username` mention support to the current Monita server/Web clients and Android conversation rendering.
- Home Assistant message-control metadata remains compatible with Monita 1.2.0.

## 1.7.0 - 2026-09-30

### Per-message controls

- Added a Message controls multi-select to `monita.send`.
- Home Assistant can enable Assign to Me, Resolve, and Attach independently for each message.
- Messages without selected controls remain normal notifications without workflow buttons.

### Image delivery

- Refreshes Monita server capabilities before falling back to text-only when an image is requested, so an upgraded Monita server can accept images without first reloading the Home Assistant integration.
- Camera/image entity delivery remains supported on Notification and Chat Channels.

## 1.6.0 - 2026-09-30

### Monita-native automation namespace

- Added `monita.send` as the canonical Home Assistant action for Monita automations.
- Kept the historical integration service namespace as a compatibility alias so existing automations do not break during the transition.
- New documentation and examples use Monita terminology and the `monita.send` action.

### Notification Channel images

- Added direct camera/image delivery to Monita Notification Channels when the server advertises `notificationImages` support (Monita 1.1.9+).
- Preserved direct Chat image delivery through the existing `chatImages` capability.
- Older Monita servers no longer cause the entire alert to fail when an image is requested for a Notification Channel; Home Assistant sends the text notification and logs that the image was omitted.
- Image sends now preserve an explicit notification title through the multipart Monita image route.

### Compatibility

- Existing config entries, selected Channels, notification entities, native pairing, and stored credentials remain in place.
- Existing automations using the historical service alias continue to work, while new automations should use `monita.send`.

## 1.5.1 - 2026-09-29

### Maintenance

- Prepared the integration metadata for the HACS-ready 1.5.1 release.
- Added repository validation for HACS default-repository requirements.
- Removed stale Home Assistant development branches after their work was incorporated into `main`.
- Synchronized the README and release documentation with the manifest version.

### Compatibility

- No user-facing configuration migration is required from 1.5.0.
- The compatibility-safe technical domain remains `monita`, and existing config entries, selected Channels, entities, automations, credentials, and the `monita.send` action remain valid.

## 1.5.0 - 2026-09-28

### Added

- Direct Home Assistant camera/image delivery into Monita Chat Channels on Monita 1.1.7+ using the server-centric client token.
- Capability discovery for Monita's `chatImages` feature.
- Multipart Chat-image publishing that preserves priority, Markdown display metadata, caller extras, and the Home Assistant origin marker.
- Regression coverage for capability discovery, direct Chat image publishing, supported Chat routing, and failure-closed behavior on non-Chat Channels.

### Compatibility

- Existing application-token staged-image workflows remain unchanged.
- Server-centric image requests use the direct Chat-image route only when the selected Channel is a Chat and the server advertises `chatImages: true`.
- Image requests to unsupported servers or Notification Channels fail clearly instead of silently sending text without the requested image.

## 1.4.0 - 2026-09-27

### Added

- Server-centric setup: add one Monita server with a client token, query its accessible Channels, and select multiple Channels in one integration entry.
- **Manage Channels** in the Home Assistant options UI. Reopening it queries Monita live so newly created or removed Channels can be reflected without adding another integration entry.
- One Home Assistant notification entity per selected Channel that the configured Monita account is allowed to post to.
- Selected read-only Channels remain eligible for inbound-message filtering without being exposed as push-capable notification entities.
- **Push Message** as the user-facing Home Assistant action name for the compatibility-safe `monita.send` service.
- Channel targeting in Push Message through a Monita notification-entity picker.
- Channel name in inbound Home Assistant message-event data and selected-Channel details in diagnostics.

### Changed

- New installs use a Monita client token as the server credential instead of requiring one application token per Channel.
- Text and Markdown publishing can use one client token plus Monita's `appid` routing to reach any selected Channel the account is permitted to post to.
- Realtime inbound streaming now filters one server WebSocket to the complete selected Channel set.
- Legacy per-Channel application-token entries remain supported and migrate to config-entry version 3 without changing their existing entity identity.

### Compatibility

- The technical Home Assistant domain remains `monita` and the existing `monita.send` action ID remains valid.
- Existing `entry_id` Push Message automations continue to work. New automations should use the Channel picker.
- Existing application-token image workflows remain supported. Server-credential per-Channel staged image upload requires corresponding Monita server support and fails clearly rather than silently dropping the requested image.


## 1.3.0 - 2026-09-27

### Rebranded

- Renamed the user-facing integration to **Monita for Home Assistant**, formerly Monita for Home Assistant.
- Updated the Home Assistant manifest, HACS display name, config flows, options, services UI, entities, diagnostics terminology, Repairs text, release workflow titles, and user-facing runtime messages to the Monita brand.
- Adopted the approved Monita palette: Primary `#2563EB`, Blue `#3B82F6`, Cyan `#06B6D4`, Slate `#0F172A`, and Gray `#9CA3B8`.
- Added explicit rebrand/upgrade documentation so existing users understand that the name change does not require reconfiguration.

### Compatibility

- Preserved the Home Assistant integration domain `monita`, component directory `custom_components/monita`, and action `monita.send` so existing installations and automations continue to work.
- Preserved existing config-entry versions, entity unique IDs, token storage, API routes, WebSocket behavior, origin extras, and pairing contracts.
- Internal Python class names that contain `the predecessor platformMU` remain implementation details for this compatibility release and do not change the user-facing Monita identity.
- the predecessor platform protocol terminology remains only where it describes API compatibility or an established wire-format field.

### Branding transition

- The supplied **Monita for Home Assistant** brand sheet is the authoritative design source for the new visual identity.
- Legacy Monita artwork remains transitional until the approved Monita icon/logo/banner files replace the active compatibility aliases and the branding lock is updated.
- The visual asset replacement is intentionally separated from textual/runtime rebranding so an incomplete or regenerated logo cannot silently become the canonical artwork.


## 1.2.0 - 2026-09-27

### Added

- Real image notifications for `monita.send` using `camera.*`, `image.*`, or advanced HTTP/HTTPS image sources.
- Secure in-Home-Assistant image retrieval followed by multipart staging through `POST /application/current/attachment` with the configured Monita application token.
- Staged attachment IDs on the normal `POST /message` payload while preserving priority, Markdown, caller extras, and the Home Assistant origin marker.
- Bounded image downloads, raster MIME/signature validation, and source-URL secret redaction behavior.
- Service UI selectors and doorbell/camera documentation for image notifications.
- Regression coverage for capture, download, staging, authentication, oversized/invalid content, failure-closed delivery, and recoverable staged orphans.

### Changed

- Standard Home Assistant `notify.send_message` remains the stable text/title path; image notifications use `monita.send`.
- Caller-supplied the predecessor platform extras are merged with the integration origin marker instead of being replaced.

### Documentation

- Added a canonical complete feature and usage guide covering every user-facing integration capability, credential role, option, entity, health signal, repair path, security behavior, and compatibility requirement.
- Expanded image-notification documentation with practical doorbell/camera guidance, source selection, supported formats, the 10 MiB limit, remote/mobile behavior, and failure semantics.
- Added a README documentation map so every feature is directly discoverable.


## 1.1.0 - 2026-09-26

### Added

- Native Home Assistant Repairs issue when Monita rejects the stored native bridge credential.
- Automatic repair-issue cleanup after successful bridge recovery, successful re-pairing, pairing removal, or integration removal.
- Regression coverage for the notify entity, `monita.send`, inbound WebSocket channel/self-loop filtering, inbound event entity payloads, and both connection binary sensors.
- Repairs regression coverage proving rejected native credentials create an actionable Home Assistant issue and successful recovery clears it.

### Changed

- Native bridge instances are now tied to their Home Assistant config-entry ID so repair issues remain unique across multiple Monita entries.


## 1.0.3 - 2026-09-26

### Hardened

- Locked all six approved Monita for Home Assistant branding assets to their exact Git blob hashes and dimensions.
- Added CI validation that fails if any canonical branding asset changes unexpectedly.
- Added CI validation that requires `banner.png`, `logo.png`, and `icon.png` to remain byte-for-byte aliases of the approved canonical artwork.
- Added regression coverage confirming the options flow remains based on Home Assistant's `OptionsFlowWithReload`, so changes such as enabling or disabling inbound messages reload the integration and take effect.


## 1.0.2 - 2026-09-26

### Fixed

- Replaced the incorrect/stale Home Assistant branding aliases with the exact user-supplied canonical Monita for Home Assistant logo and icon.
- Added the canonical Monita for Home Assistant banner and updated the GitHub README to use it.
- Preserved all six supplied branding files in the repository and documented them as immutable project branding.
- Updated Home Assistant compatibility aliases (`logo.png`, `icon.png`, and `banner.png`) to the canonical non-`-q` artwork.


## 1.0.1 - 2026-09-26

### Fixed

- Restored the valid Monita + Home Assistant logo and icon assets after the 1.0.0 branding PNGs were found to be truncated/corrupt.
- Updated README image references to use the raw repository assets directly so GitHub renders the complete branding reliably.


## 1.0.0 - 2026-09-26

Stable tandem release for Monita 1.0. This promotes the validated native bridge baseline with application-token notifications, optional client-token inbound messages, native no-LLT pairing, authenticated bidirectional events, non-destructive repair, authenticated remote revoke, bridge health visibility, bounded delivery retry, reauthentication/reconfiguration, diagnostics redaction, and full Home Assistant validation coverage.


## 1.0.0-rc1 - 2026-09-26

Release candidate for the tandem Monita 1.0 launch. Functionally identical to the validated 0.4.0 pre-1.0 hardening baseline, with versioning promoted for final cross-project compatibility validation against Monita `release/v1.0.0-rc1`.

## 0.4.0 - 2026-09-26

Pre-1.0 native bridge hardening release.

### Added

- Native bridge connectivity binary sensor with Paired, Connected, Degraded, and Repair required health.
- Last sent/received timestamps, queue depth, retry count, dropped-event count, and last native delivery error.
- Bounded exponential retry for transient Home Assistant → Monita event delivery failures.
- Authenticated remote native bridge revoke during unpair.
- Force-local-remove recovery path when remote revocation cannot be completed.
- Manual Home Assistant callback URL override even when Home Assistant auto-detects a URL.

### Fixed

- Treats Monita HTTP 401/403/404 pairing responses as invalid pairing codes instead of generic pairing failures.
- Native repair replaces only the native bridge credentials and leaves application-token notifications and optional client-token inbound messages intact.
- Invalid inbound Bearer probes do not falsely mark a healthy bridge as repair-required.
- Diagnostics include non-secret native health information while continuing to redact the shared secret and private webhook details.

### Testing

- Adds real Home Assistant event-bus → Monita delivery coverage.
- Adds pairing 401 coverage, manual callback override coverage, repair coverage, remote revoke/removal coverage, repair-required health coverage, and diagnostics redaction coverage.

## 0.3.0 - 2026-09-26

### Added

- Native Monita ↔ Home Assistant pairing using one-time Monita pairing codes.
- Random private Home Assistant webhook registration for Monita → Home Assistant events.
- Bearer-secret validation for native inbound events.
- Home Assistant event forwarding to the paired Monita native event endpoint.
- Native pairing management in the integration options, including clear Paired / Not paired state, repair, and removal without deleting the normal Monita integration.
- Automatic Home Assistant webhook URL discovery with a manual reachable-URL fallback only when Home Assistant cannot determine one.
- Native pairing and bridge coverage for successful pairing, invalid/expired/failed codes, invalid Bearer credentials, outbound event posting, and inbound event receipt.

### Changed

- Monita options are split into notification/inbound-message settings and native Home Assistant pairing management.
- The webhook component is now an integration dependency.
- Integration version is now 0.3.0.

### Security

- Native shared secrets and private webhook identifiers/URLs are redacted from diagnostics.
- One-time pairing codes are never persisted.
- Native Monita payloads can fire Home Assistant events only; they are never interpreted as service calls.
- Native webhook Bearer credentials are compared using constant-time secret comparison.

## 0.2.0 - 2026-09-26

### Added

- Dedicated Monita HA integration logo and transparent Home Assistant connector icon based on the Monita mascot.
- Exact application-token validation and Channel identity using the Monita application identity endpoint when available.
- Safe legacy token-validation fallback that does not create a notification.
- Stable Channel-ID-based config-entry identity when supported by the server.
- Config-entry `runtime_data` architecture.
- Reauthentication flow for revoked or replaced credentials.
- Reconfiguration flow for server URL, Channel display name, TLS validation, and optional client-token replacement/removal.
- Optional realtime inbound Monita messages over the client-token WebSocket stream.
- Home Assistant Event entity for inbound Channel messages.
- Inbound WebSocket connection-status binary sensor.
- Automatic stream reconnect with bounded exponential backoff.
- Loop prevention for messages sent by the same Home Assistant config entry.
- Explicit handling for authentication failures, rate limiting, server failures, connection errors, and request timeouts.
- Redacted config-entry diagnostics.
- Migration path from v0.1 config entries.
- Hassfest, Ruff, JSON, Python compile, and pytest validation workflow.

### Changed

- `monita.send` is registered at integration setup rather than per config entry.
- Notification entities advertise title support using Home Assistant's current `NotifyEntity` API.
- IoT classification is `local_push` for the direct self-hosted connection model.
- Optional client token is used only for Channel discovery/inbound subscription; application tokens remain the outbound publishing credential.

### Security

- Application/client tokens are redacted from diagnostics.
- Raw application tokens are never used as config-entry unique IDs.
- Inbound message text is exposed as event data only and is never executed automatically.

## 0.1.0 - 2026-09-26

- Initial Home Assistant custom integration.
- UI config flow.
- Monita notification entity.
- `monita.send` action.
- Multiple config entries.
- HACS-compatible structure.
