"""Outbound Monita event filtering and image source allowlist tests."""
from types import SimpleNamespace
import pytest

from custom_components.monita.security import (
    DEFAULT_NATIVE_EVENTS, parse_native_events, parse_image_hosts,
    sanitize_event_data, validate_image_url,
)

def test_event_names_are_explicit_bounded():
    assert "state_changed" in parse_native_events(DEFAULT_NATIVE_EVENTS)
    assert parse_native_events("state_changed,state_changed") == ("state_changed",)
    for raw in ("*", "state_changed,*", "ha-event", ",".join(["event"] * 17)):
        with pytest.raises(ValueError):
            parse_native_events(raw)

def test_images_require_explicit_trusted_exact_hosts():
    hosts = parse_image_hosts("camera.example.test,172.20.0.55")
    validate_image_url("https://camera.example.test/current.jpg?token=private", hosts)
    validate_image_url("http://172.20.0.55/snapshot.jpg", hosts)
    for url in ("http://169.254.169.254/latest/meta-data", "http://127.0.0.1/image.jpg", "https://evil.example.test/image.jpg", "https://camera.example.test@evil.example.test/image.jpg"):
        with pytest.raises(ValueError):
            validate_image_url(url, hosts)

def test_secret_location_keys_removed_and_state_objects_minimized():
    previous = SimpleNamespace(entity_id="binary_sensor.front_door", state="off")
    obj = {"old_state": previous, "token": "SECRET", "gps_latitude": 11, "details": {"password": "SECRET", "kind": "doorbell"}}
    cleaned = sanitize_event_data(obj)
    assert cleaned["old_state"] == {"entity_id": "binary_sensor.front_door", "state": "off"}
    assert "token" not in cleaned and "gps_latitude" not in cleaned
    assert "password" not in cleaned["details"]
