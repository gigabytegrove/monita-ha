# Monita Identity

Monita for Home Assistant uses the canonical `monita` integration domain and Monita-native protocol identifiers.

The retired compatibility identity is no longer supported. Current installations, automations, services, documentation, tests, and release packages use Monita naming exclusively.

## Canonical identifiers

- Integration domain: `monita`
- Service action: `monita.send`
- Integration origin marker: `homeassistant::monita`
- Authentication header: `X-Monita-Key`
- Display extras: `monita::display`

See [FEATURES.md](FEATURES.md) for current behavior and configuration.
