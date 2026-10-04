"""Legacy-domain API compatibility aliases for Monita.

The active implementation lives in custom_components.monita.api.  These aliases
exist only so config entries stored under the historical Home Assistant domain
can continue loading during an in-place upgrade.
"""

from custom_components.monita.api import (
    MonitaAttachment,
    MonitaAuthError,
    MonitaChannel,
    MonitaClient,
    MonitaConnectionError,
    MonitaError,
    MonitaRateLimitError,
    MonitaServerError,
)

GotifyMUError = MonitaError
GotifyMUAuthError = MonitaAuthError
GotifyMUConnectionError = MonitaConnectionError
GotifyMURateLimitError = MonitaRateLimitError
GotifyMUServerError = MonitaServerError
GotifyMUAttachment = MonitaAttachment
GotifyMUChannel = MonitaChannel
GotifyMUClient = MonitaClient

__all__ = [
    "GotifyMUAttachment",
    "GotifyMUAuthError",
    "GotifyMUChannel",
    "GotifyMUClient",
    "GotifyMUConnectionError",
    "GotifyMUError",
    "GotifyMURateLimitError",
    "GotifyMUServerError",
]
