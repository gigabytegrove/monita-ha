"""Legacy-domain Repairs compatibility shim for Monita."""

from __future__ import annotations

from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import issue_registry as ir

from custom_components.monita.repairs import (
    NATIVE_BRIDGE_AUTH_ISSUE,
    native_bridge_issue_id,
)

from .const import DOMAIN


@callback
def async_create_native_bridge_repair_issue(
    hass: HomeAssistant,
    *,
    entry_id: str,
    name: str,
) -> None:
    """Create a repair issue under the historical integration domain."""
    ir.async_create_issue(
        hass,
        DOMAIN,
        native_bridge_issue_id(entry_id),
        is_fixable=False,
        is_persistent=True,
        issue_domain=DOMAIN,
        severity=ir.IssueSeverity.ERROR,
        translation_key=NATIVE_BRIDGE_AUTH_ISSUE,
        translation_placeholders={"name": name},
        learn_more_url=(
            "https://github.com/gigabytegrove/monita-ha"
            "#native-monita-pairing"
        ),
    )


@callback
def async_delete_native_bridge_repair_issue(
    hass: HomeAssistant,
    entry_id: str,
) -> None:
    """Delete a legacy-domain native bridge repair issue if it exists."""
    ir.async_delete_issue(hass, DOMAIN, native_bridge_issue_id(entry_id))


__all__ = [
    "async_create_native_bridge_repair_issue",
    "async_delete_native_bridge_repair_issue",
    "native_bridge_issue_id",
]
