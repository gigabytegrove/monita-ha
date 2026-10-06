"""Legacy-domain helper compatibility wrapper for Monita."""

from custom_components.monita.helpers import (
    channel_unique_id,
    fallback_unique_id,
    normalize_server_url,
    server_unique_id,
)

__all__ = [
    "channel_unique_id",
    "fallback_unique_id",
    "normalize_server_url",
    "server_unique_id",
]
