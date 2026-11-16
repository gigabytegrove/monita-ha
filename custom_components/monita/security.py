"""Strict outbound event and inbound image policy for Monita."""
from __future__ import annotations

from ipaddress import ip_address
import math
import re
from typing import Any
from urllib.parse import urlsplit

DEFAULT_NATIVE_EVENTS = "state_changed,automation_triggered,script_started"
MAX_NATIVE_EVENTS = 16
MAX_IMAGE_HOSTS = 16
MAX_EVENT_BODY_BYTES = 65536
_EVENT_NAME = re.compile(r"[a-z][a-z0-9_]{0,79}\Z")
_HOST_NAME = re.compile(r"[a-z0-9][a-z0-9.-]{0,252}\Z")
_REDACT = (
    "password", "secret", "token", "cookie", "authorization", "credential",
    "latitude", "longitude", "gps", "location", "coordinate", "address",
    "email", "vin", "private_key", "access_key", "api_key",
)


def parse_native_events(raw: str) -> tuple[str, ...]:
    """Require at most sixteen exact event names, never a wildcard."""
    if not isinstance(raw, str) or len(raw) > 1400:
        raise ValueError("Invalid native event allowlist")
    names = [name.strip().lower() for name in raw.split(",") if name.strip()]
    if len(names) > MAX_NATIVE_EVENTS or any(_EVENT_NAME.fullmatch(name) is None for name in names):
        raise ValueError("Invalid native event allowlist")
    return tuple(dict.fromkeys(names))


def parse_image_hosts(raw: str) -> frozenset[str]:
    """Exact approved hostnames; an empty set disables image URL downloads."""
    if not isinstance(raw, str) or len(raw) > 4200:
        raise ValueError("Invalid image host allowlist")
    names = [part.strip().lower().rstrip(".") for part in raw.split(",") if part.strip()]
    if len(names) > MAX_IMAGE_HOSTS or any(_HOST_NAME.fullmatch(name) is None for name in names):
        raise ValueError("Invalid image host allowlist")
    return frozenset(names)


def validate_image_url(url: str, approved: frozenset[str]) -> None:
    """Refuse unapproved hosts, user info, loopback, and metadata addresses."""
    parsed = urlsplit(url)
    if parsed.scheme.lower() not in ("http", "https") or not parsed.hostname:
        raise ValueError("Image URL must use HTTP or HTTPS")
    if parsed.username or parsed.password or parsed.fragment:
        raise ValueError("Image URL cannot contain user info or a fragment")
    try:
        _ = parsed.port
    except ValueError as exc:
        raise ValueError("Invalid image URL port") from exc
    host = parsed.hostname.lower().rstrip(".")
    if host not in approved:
        raise ValueError("Image URL host is not approved in Monita options")
    if host == "localhost" or host.endswith(".localhost"):
        raise ValueError("Loopback image URLs are prohibited")
    try:
        addr = ip_address(host)
    except ValueError:
        return
    if addr.is_loopback or addr.is_link_local or addr.is_multicast or addr.is_unspecified or addr.is_reserved:
        raise ValueError("Unsafe image URL address")


def sanitize_event_data(value: Any, *, depth: int = 0) -> Any:
    """Only bounded, privacy-minimized event content is eligible for transport."""
    if depth > 4:
        return None
    if hasattr(value, "entity_id") and hasattr(value, "state"):
        return {"entity_id": str(value.entity_id)[:256], "state": str(value.state)[:256]}
    if isinstance(value, dict):
        result: dict[str, Any] = {}
        for key, child in list(value.items())[:48]:
            label = str(key)[:100]
            if any(term in label.lower() for term in _REDACT):
                continue
            clean = sanitize_event_data(child, depth=depth + 1)
            if clean is not None:
                result[label] = clean
        return result
    if isinstance(value, (list, tuple)):
        sanitized = [sanitize_event_data(v, depth=depth + 1) for v in value[:32]]
        return [v for v in sanitized if v is not None]
    if isinstance(value, str):
        return value[:512]
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value if math.isfinite(value) else None
    return None
