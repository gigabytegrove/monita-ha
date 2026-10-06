"""Legacy-domain notify platform compatibility shim for Monita."""

from custom_components.monita.notify import (
    MonitaNotifyEntity,
    async_setup_entry,
)

__all__ = [
    "MonitaNotifyEntity",
    "async_setup_entry",
]
