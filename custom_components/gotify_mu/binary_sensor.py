"""Legacy-domain binary sensor compatibility shim for Monita."""

from custom_components.monita.binary_sensor import (
    MonitaConnectionBinarySensor,
    MonitaNativeBridgeBinarySensor,
    async_setup_entry,
)

__all__ = [
    "MonitaConnectionBinarySensor",
    "MonitaNativeBridgeBinarySensor",
    "async_setup_entry",
]
