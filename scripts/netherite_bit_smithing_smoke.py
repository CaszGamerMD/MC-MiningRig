#!/usr/bin/env python3
"""Check Netherite drill bit uses normal smithing, not the old ingot-only craft."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
recipe=json.loads((root/"src/main/resources/data/miningrig/recipe/netherite_bit.json").read_text())
assert recipe["type"]=="minecraft:smithing_transform",recipe
assert recipe["template"]=="minecraft:netherite_upgrade_smithing_template"
assert recipe["base"]=="miningrig:diamond_bit"
assert recipe["addition"]=="minecraft:netherite_ingot"
assert recipe["result"]=={"id":"miningrig:netherite_bit"}
assert "pattern" not in recipe and "key" not in recipe
# Vanilla smithing_transform merges source bit's enchantment/component patch
# into a new item; do not set result.components to avoid overwriting enchants.
assert "components" not in recipe["result"]
diamond=json.loads((root/"src/main/resources/data/miningrig/recipe/diamond_bit.json").read_text())
assert diamond["result"]["id"]=="miningrig:diamond_bit"
registrations=(root/"src/main/java/dev/casz/miningrig/MiningRig.java").read_text()
assert 'bit("diamond_bit"' in registrations
assert 'bit("netherite_bit"' in registrations
print("PASS: Netherite Drill Bit is a 3-input vanilla smithing upgrade; no legacy shaped recipe.")
