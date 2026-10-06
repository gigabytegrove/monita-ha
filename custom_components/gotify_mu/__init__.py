"""Migration bootstrap from the historical integration domain to Monita."""

from __future__ import annotations

import importlib
import logging
import shutil
from pathlib import Path
from typing import Any

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers import device_registry as dr
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers import issue_registry as ir
from homeassistant.loader import DATA_CUSTOM_COMPONENTS, DATA_INTEGRATIONS

_LOGGER = logging.getLogger(__name__)

LEGACY_DOMAIN = "gotify_mu"
CANONICAL_DOMAIN = "monita"
_PAYLOAD_DIR = "_monita_payload"


async def async_setup(hass: HomeAssistant, config: dict[str, Any]) -> bool:
    """Load the historical domain only long enough to migrate existing installs."""
    return True


def _copy_payload(source: Path, destination: Path) -> None:
    """Atomically install the bundled canonical Monita component."""
    if not source.is_dir():
        raise FileNotFoundError(f"Monita migration payload is missing: {source}")

    temp = destination.with_name(f".{destination.name}.migration")
    if temp.exists():
        shutil.rmtree(temp)
    shutil.copytree(source, temp)

    if destination.exists():
        shutil.rmtree(destination)
    temp.replace(destination)


def _clone_entry_for_monita(entry: ConfigEntry) -> ConfigEntry:
    """Clone a historical config entry into the canonical Monita domain."""
    subentries_data = [
        {
            "subentry_id": subentry.subentry_id,
            "subentry_type": subentry.subentry_type,
            "title": subentry.title,
            "unique_id": subentry.unique_id,
            "data": dict(subentry.data),
        }
        for subentry in entry.subentries.values()
    ]

    return ConfigEntry(
        created_at=entry.created_at,
        data=dict(entry.data),
        disabled_by=entry.disabled_by,
        discovery_keys=entry.discovery_keys,
        domain=CANONICAL_DOMAIN,
        entry_id=entry.entry_id,
        minor_version=entry.minor_version,
        modified_at=entry.modified_at,
        options=dict(entry.options),
        pref_disable_new_entities=entry.pref_disable_new_entities,
        pref_disable_polling=entry.pref_disable_polling,
        source=entry.source,
        subentries_data=subentries_data,
        title=entry.title,
        unique_id=entry.unique_id,
        version=entry.version,
    )


def _adopt_entity_registry(hass: HomeAssistant, entry_id: str) -> None:
    """Move registry entities from the historical platform to Monita."""
    registry = er.async_get(hass)
    for entity in list(er.async_entries_for_config_entry(registry, entry_id)):
        if entity.platform != LEGACY_DOMAIN:
            continue

        if existing_entity_id := registry.async_get_entity_id(
            entity.domain,
            CANONICAL_DOMAIN,
            entity.unique_id,
        ):
            if existing_entity_id != entity.entity_id:
                _LOGGER.warning(
                    "Keeping existing Monita entity %s and removing duplicate legacy entity %s",
                    existing_entity_id,
                    entity.entity_id,
                )
                registry.async_remove(entity.entity_id)
                continue

        registry.async_update_entity_platform(
            entity.entity_id,
            CANONICAL_DOMAIN,
            new_config_entry_id=entry_id,
        )


def _adopt_device_registry(hass: HomeAssistant, entry_id: str) -> None:
    """Move historical Monita device identifiers to the canonical domain."""
    registry = dr.async_get(hass)
    for device in list(dr.async_entries_for_config_entry(registry, entry_id)):
        identifiers = set(device.identifiers)
        if not any(domain == LEGACY_DOMAIN for domain, _ in identifiers):
            continue

        new_identifiers = {
            (CANONICAL_DOMAIN if domain == LEGACY_DOMAIN else domain, identifier)
            for domain, identifier in identifiers
        }
        registry.async_update_device(device.id, new_identifiers=new_identifiers)


def _remove_legacy_issues(hass: HomeAssistant) -> None:
    """Remove repair issues registered under the historical domain."""
    registry = ir.async_get(hass)
    legacy_issue_ids = [
        issue_id
        for domain, issue_id in list(registry.issues)
        if domain == LEGACY_DOMAIN
    ]
    for issue_id in legacy_issue_ids:
        ir.async_delete_issue(hass, LEGACY_DOMAIN, issue_id)


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Install canonical Monita and migrate this historical config entry."""
    integration_dir = Path(__file__).resolve().parent
    payload_dir = integration_dir / _PAYLOAD_DIR
    canonical_dir = integration_dir.parent / CANONICAL_DOMAIN

    try:
        await hass.async_add_executor_job(
            _copy_payload,
            payload_dir,
            canonical_dir,
        )
    except (OSError, FileNotFoundError) as err:
        _LOGGER.exception("Unable to install the bundled Monita migration payload")
        raise RuntimeError(
            "Unable to install the bundled Monita integration during migration"
        ) from err

    importlib.invalidate_caches()

    # Home Assistant primes custom integration discovery during startup. Older
    # supported HA releases do not expose async_clear_custom_components_cache(),
    # so invalidate the same caches directly using the long-standing loader keys.
    # This keeps the migration compatible across HA releases and makes the newly
    # installed canonical Monita component discoverable immediately.
    hass.data.pop(DATA_CUSTOM_COMPONENTS, None)
    integration_cache = hass.data.get(DATA_INTEGRATIONS)
    if isinstance(integration_cache, dict):
        integration_cache.pop(CANONICAL_DOMAIN, None)

    duplicate = next(
        (
            candidate
            for candidate in hass.config_entries.async_entries(CANONICAL_DOMAIN)
            if candidate.entry_id != entry.entry_id
            and entry.unique_id is not None
            and candidate.unique_id == entry.unique_id
        ),
        None,
    )
    if duplicate is not None:
        raise RuntimeError(
            "A different Monita config entry already has the same unique ID; "
            "automatic migration stopped to avoid overwriting it"
        )

    migrated = _clone_entry_for_monita(entry)

    _adopt_entity_registry(hass, entry.entry_id)
    _adopt_device_registry(hass, entry.entry_id)
    _remove_legacy_issues(hass)

    hass.config_entries._entries[entry.entry_id] = migrated  # noqa: SLF001
    hass.config_entries._async_schedule_save()  # noqa: SLF001
    hass.config_entries.async_update_issues()

    _LOGGER.warning(
        "Migrated Home Assistant config entry %s from %s to %s",
        entry.entry_id,
        LEGACY_DOMAIN,
        CANONICAL_DOMAIN,
    )

    hass.async_create_task(
        hass.config_entries.async_setup(migrated.entry_id),
        f"Set up migrated Monita entry {migrated.entry_id}",
    )
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Historical entries do not keep runtime resources after migration."""
    return True
