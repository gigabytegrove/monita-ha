"""Legacy-domain API compatibility re-export for Monita.

The active implementation lives in custom_components.monita.api. This module
exists only so Home Assistant config entries stored under the historical
integration domain continue loading during an in-place upgrade.
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

__all__ = [
    "MonitaAttachment",
    "MonitaAuthError",
    "MonitaChannel",
    "MonitaClient",
    "MonitaConnectionError",
    "MonitaError",
    "MonitaRateLimitError",
    "MonitaServerError",
]
