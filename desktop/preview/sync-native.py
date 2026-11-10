"""Copy native catalog/guide strings and transparent asset bounds into the preview."""
import json
import re
from pathlib import Path

from PIL import Image

directory = Path(__file__).resolve().parent
source = (directory.parent / "TungTungClient.cs").read_text()
quoted = r'"((?:[^"\\]|\\.)*)"'


def decode(value):
    return json.loads('"' + value + '"')


pattern = r'Add\(' + quoted + r',\s*' + quoted + r',\s*' + quoted
pattern += r',\s*(true|false),\s*' + quoted + r',\s*' + quoted + r'\);'
catalog = []
for match in re.finditer(pattern, source):
    identifier, name, category, available, note, status = match.groups()
    catalog.append(dict(id=decode(identifier), name=decode(name),
                        category=decode(category), available=available == "true",
                        note=decode(note), status=decode(status)))
assert len(catalog) == 27, f"Unexpected native module count: {len(catalog)}"
(directory / "catalog.js").write_text("const MODULES = " + json.dumps(catalog, ensure_ascii=False, indent=2) + ";\n")

guide_pattern = r'case\s+' + quoted + r':\s*return new Guide\(\s*' + quoted
guide_pattern += r',\s*' + quoted + r',\s*' + quoted + r'\);'
guides = {}
for match in re.finditer(guide_pattern, source):
    identifier, summary, steps, limit = map(decode, match.groups())
    guides[identifier] = dict(how=summary, start=steps, limit=limit)
assert len(guides) == 18, f"Unexpected native guide count: {len(guides)}"
(directory / "tutorials-data.js").write_text("const GUIDES = " + json.dumps(guides, ensure_ascii=False, indent=2) + ";\n")

finder_values = []
for name in ("finderKeys", "finderNames"):
    match = re.search(r"\b" + name + r"\s*=\s*new string\[\]\s*\{([^}]+)\}", source)
    assert match is not None, f"Native finder array missing: {name}"
    finder_values.append([decode(value) for value in re.findall(quoted, match.group(1))])
assert len(finder_values[0]) == len(finder_values[1]) == 12
finder_signals = list(zip(*finder_values))
(directory / "finder-signals.js").write_text("const FINDER_SIGNALS = " + json.dumps(finder_signals, ensure_ascii=False, indent=2) + ";\n")

assets = directory.parent / "assets"
bounds = {}
paths = [assets / name for name in ("logo.png", "dancer.png", "world-mascot.png", "pvp-mascot.png", "findings-mascot.png")]
paths += [assets / "items" / (module["id"] + ".png") for module in catalog]
paths += sorted((assets / "items").glob("finder_*.png"))
for path in paths:
    with Image.open(path).convert("RGBA") as image:
        box = image.getchannel("A").getbbox()
        assert box is not None, f"Empty transparent asset: {path}"
        x, y, right, bottom = box
        bounds[path.relative_to(assets).as_posix()] = dict(width=image.width, height=image.height,
                                                        x=x, y=y, w=right - x, h=bottom - y)
(directory / "asset-bounds.js").write_text("const ASSET_BOUNDS = " + json.dumps(bounds, separators=(",", ":")) + ";\n")
print(f"Synced {len(catalog)} modules, {len(guides)} guides, {len(finder_signals)} finder signals and {len(bounds)} asset bounds.")
