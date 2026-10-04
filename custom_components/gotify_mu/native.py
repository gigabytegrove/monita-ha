"""Legacy-domain native bridge compatibility shim for Monita."""

from custom_components.monita.native import (
    MonitaNativeBridge,
    MonitaNativeError,
    MonitaNativePairingError,
    NativePairingResult,
    PendingNativeWebhook,
    async_handle_native_webhook,
    async_pair_native,
    async_revoke_native,
    native_pairing_data,
    native_pairing_is_configured,
    remove_native_pairing_data,
)

__all__ = [
    "MonitaNativeBridge",
    "MonitaNativeError",
    "MonitaNativePairingError",
    "NativePairingResult",
    "PendingNativeWebhook",
    "async_handle_native_webhook",
    "async_pair_native",
    "async_revoke_native",
    "native_pairing_data",
    "native_pairing_is_configured",
    "remove_native_pairing_data",
]
