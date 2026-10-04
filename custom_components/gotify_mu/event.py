"""Legacy-domain event platform compatibility shim for Monita."""

from custom_components.monita.event import (
    MonitaMessageEventEntity,
    async_setup_entry,
)

__all__ = [
    "MonitaMessageEventEntity",
    "async_setup_entry",
]
