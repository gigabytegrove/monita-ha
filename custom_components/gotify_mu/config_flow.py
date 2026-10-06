"""Config-flow registration for historical Monita config entries.

This module is intentionally self-contained. Home Assistant imports an
integration's config_flow platform before it calls async_setup_entry(), including
for existing entries. Therefore this compatibility module must never import the
canonical Monita package: that package may not exist yet on an upgrade where the
legacy bootstrap is responsible for installing it.
"""

from __future__ import annotations

from homeassistant import config_entries

DOMAIN = "gotify_mu"


class LegacyMonitaMigrationFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """Register the legacy config-entry schema for automatic migration only."""

    VERSION = 3
    MINOR_VERSION = 0
