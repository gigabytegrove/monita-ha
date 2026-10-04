"""Legacy-domain image helper compatibility aliases for Monita."""

from custom_components.monita.media import (
    IMAGE_DOWNLOAD_TIMEOUT_SECONDS,
    MAX_IMAGE_BYTES,
    MonitaImage,
    async_acquire_entity_image,
    async_acquire_url_image,
)

GotifyMUImage = MonitaImage

__all__ = [
    "GotifyMUImage",
    "IMAGE_DOWNLOAD_TIMEOUT_SECONDS",
    "MAX_IMAGE_BYTES",
    "async_acquire_entity_image",
    "async_acquire_url_image",
]
