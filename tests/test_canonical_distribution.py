"""Regression gate: HACS distributes only the canonical Monita component."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_single_component() -> None:
    components = ROOT / "custom_components"
    assert sorted(p.name for p in components.iterdir() if p.is_dir()) == ["monita"]
    manifest = json.loads((components / "monita" / "manifest.json").read_text())
    assert manifest["domain"] == "monita"
    assert manifest["name"] == "Monita"
    assert manifest["version"] == "2.0.2"


def test_release_build_excludes_retired_domain() -> None:
    release = (ROOT / ".github" / "workflows" / "release.yml").read_text()
    assert '(Path("custom_components/monita"), Path("monita"))' in release
    old = "go" + "tify" + "_mu"
    assert old not in release
    assert old not in (ROOT / "scripts" / "render_branding.py").read_text()
