"""Legacy-domain Repairs compatibility shim for Monita."""

from custom_components.monita.repairs import (
    async_create_native_bridge_repair_issue,
    async_delete_native_bridge_repair_issue,
    native_bridge_issue_id,
)

__all__ = [
    "async_create_native_bridge_repair_issue",
    "async_delete_native_bridge_repair_issue",
    "native_bridge_issue_id",
]
