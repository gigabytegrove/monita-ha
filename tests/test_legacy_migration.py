"""Regression tests for the one-way historical-domain migration bootstrap."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock, patch

from pytest_homeassistant_custom_component.common import MockConfigEntry

from custom_components.gotify_mu import async_migrate_entry, async_setup_entry
from custom_components.gotify_mu.config_flow import LegacyMonitaMigrationFlow


def test_legacy_config_flow_is_migration_only_and_self_contained() -> None:
    """The flow HA imports before setup must not require canonical Monita."""
    assert LegacyMonitaMigrationFlow.VERSION == 3
    assert LegacyMonitaMigrationFlow.MINOR_VERSION == 0

    source = Path("custom_components/gotify_mu/config_flow.py").read_text()
    assert "custom_components.monita" not in source
    assert "from .api" not in source
    assert "from .helpers" not in source
    assert "from .native" not in source
    assert "from .repairs" not in source


async def test_v1_legacy_entry_migrates_to_v3_without_canonical_imports() -> None:
    """A v1 per-Channel entry is normalized before the domain handoff."""
    update = MagicMock()
    hass = SimpleNamespace(
        config_entries=SimpleNamespace(async_update_entry=update),
    )
    entry = SimpleNamespace(
        version=1,
        minor_version=0,
        entry_id="legacy-v1",
        data={
            "server_url": "https://monita.example/",
            "app_token": "secret-app-token",
            "channel_id": "7",
        },
        options={},
        unique_id=None,
    )

    assert await async_migrate_entry(hass, entry) is True

    update.assert_called_once()
    kwargs = update.call_args.kwargs
    assert kwargs["version"] == 3
    assert kwargs["minor_version"] == 0
    assert kwargs["data"]["server_url"] == "https://monita.example"
    assert kwargs["options"]["channel_ids"] == [7]
    assert kwargs["unique_id"] == "https://monita.example|channel:7"


async def test_v2_legacy_entry_preserves_unique_id_and_adds_channel_selection() -> None:
    """A v2 entry keeps its identity and gains the v3 Channel selection option."""
    update = MagicMock()
    hass = SimpleNamespace(
        config_entries=SimpleNamespace(async_update_entry=update),
    )
    entry = SimpleNamespace(
        version=2,
        minor_version=0,
        entry_id="legacy-v2",
        data={
            "server_url": "https://monita.example",
            "channel_id": 11,
        },
        options={},
        unique_id="existing-stable-id",
    )

    assert await async_migrate_entry(hass, entry) is True

    kwargs = update.call_args.kwargs
    assert kwargs["unique_id"] == "existing-stable-id"
    assert kwargs["options"]["channel_ids"] == [11]
    assert kwargs["version"] == 3


async def test_v3_legacy_entry_needs_no_schema_rewrite() -> None:
    """A current legacy schema passes straight through to domain migration."""
    update = MagicMock()
    hass = SimpleNamespace(
        config_entries=SimpleNamespace(async_update_entry=update),
    )
    entry = SimpleNamespace(
        version=3,
        minor_version=0,
        entry_id="legacy-v3",
        data={"server_url": "https://monita.example"},
        options={},
        unique_id="stable-id",
    )

    assert await async_migrate_entry(hass, entry) is True
    update.assert_not_called()


async def test_unsupported_future_legacy_schema_is_not_modified() -> None:
    """Unknown future schemas stop safely rather than corrupting stored data."""
    update = MagicMock()
    hass = SimpleNamespace(
        config_entries=SimpleNamespace(async_update_entry=update),
    )
    entry = SimpleNamespace(
        version=4,
        minor_version=0,
        entry_id="future",
        data={"server_url": "https://monita.example"},
        options={},
        unique_id="stable-id",
    )

    assert await async_migrate_entry(hass, entry) is False
    update.assert_not_called()


def test_legacy_config_flow_imports_when_canonical_package_is_unavailable() -> None:
    """Reproduce HA's pre-setup config-flow import with Monita not installed yet."""
    script = r"""
import builtins

real_import = builtins.__import__

def guarded_import(name, globals=None, locals=None, fromlist=(), level=0):
    if name == "custom_components.monita" or name.startswith("custom_components.monita."):
        raise ModuleNotFoundError("canonical Monita intentionally unavailable")
    return real_import(name, globals, locals, fromlist, level)

builtins.__import__ = guarded_import
import custom_components.gotify_mu.config_flow  # noqa: F401
"""
    result = subprocess.run(
        [sys.executable, "-c", script],
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr


async def test_home_assistant_can_import_legacy_config_flow_before_setup(hass) -> None:
    """Exercise the same HA setup phase that produced 'config_flow not found'."""
    entry = MockConfigEntry(
        domain="gotify_mu",
        title="Legacy Monita",
        data={"server_url": "https://monita.example"},
        options={},
        version=3,
        minor_version=0,
        unique_id="https://monita.example|server",
    )
    entry.add_to_hass(hass)

    with patch(
        "custom_components.gotify_mu.async_setup_entry",
        new=AsyncMock(return_value=True),
    ):
        assert await hass.config_entries.async_setup(entry.entry_id) is True


async def test_bootstrap_replaces_legacy_entry_with_canonical_domain(hass) -> None:
    """Exercise the actual domain handoff while preserving the config-entry ID."""
    entry = MockConfigEntry(
        domain="gotify_mu",
        title="Legacy Monita",
        data={"server_url": "https://monita.example"},
        options={},
        version=3,
        minor_version=0,
        unique_id="https://monita.example|server",
    )
    entry.add_to_hass(hass)

    created_tasks = []

    def capture_task(coro, *args, **kwargs):
        created_tasks.append((coro, args, kwargs))
        coro.close()
        return MagicMock()

    with (
        patch("custom_components.gotify_mu._copy_payload"),
        patch.object(hass, "async_create_task", side_effect=capture_task),
    ):
        assert await async_setup_entry(hass, entry) is True

    migrated = hass.config_entries.async_get_entry(entry.entry_id)
    assert migrated is not None
    assert migrated.entry_id == entry.entry_id
    assert migrated.domain == "monita"
    assert migrated.unique_id == entry.unique_id
    assert dict(migrated.data) == dict(entry.data)
    assert dict(migrated.options) == dict(entry.options)
    assert created_tasks
