"""Verify every split melting recipe against the pinned original's ordered branches.

Run from the Continuum directory. --apply changes only neoforge:conditions;
the default is read-only validation. --report writes the auditable truth table.
No Minecraft, Gradle, downloads, or extracted archive copies are needed.
"""
import argparse
import copy
import hashlib
import itertools
import json
from pathlib import Path
import re
import zipfile


ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = ROOT / "local-audit/upstream/TConstruct-1.20.1-3.12.1.231.jar"
RECIPES = ROOT / "src/generated/resources/data/tconstruct/recipe"
REPORT = ROOT / "local-audit/optional-melting-branch-validation.json"


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def migrate(value):
    """Only migrations required by the actual pinned conditional recipes."""
    if isinstance(value, str):
        if value == "forge:true":
            return "neoforge:always"
        return "c:" + value[6:] if value.startswith("forge:") else value
    if isinstance(value, list):
        return [migrate(entry) for entry in value]
    if isinstance(value, dict):
        return {key: [migrate(entry)] if key == "match" and isinstance(entry, str)
                else migrate(entry) for key, entry in value.items()}
    return value


def evaluate(condition, tags):
    kind = condition["type"]
    if kind == "mantle:tag_filled":
        return tags[condition["tag"]]
    if kind == "neoforge:always":
        return True
    if kind == "neoforge:not":
        return not evaluate(condition["value"], tags)
    raise AssertionError(f"Unexpected byproduct condition: {condition}")


def audit(apply):
    groups = {}
    for path in RECIPES.rglob("*.json"):
        match = re.fullmatch(r"(.*)_(?:byproduct_(\d+)|(fallback))\.json", path.as_posix())
        if match:
            groups.setdefault(match[1], []).append((int(match[2]) if match[2] else 999, path))
    assert len(groups) == 61, f"Re-audit changed recipe families: {len(groups)}"
    result = []
    restored = []
    changed = 0
    with zipfile.ZipFile(UPSTREAM) as original:
        for base, files in sorted(groups.items()):
            files.sort()
            resource = "data/tconstruct/recipes/" + base.split("/recipe/", 1)[1] + ".json"
            old = json.loads(original.read(resource))
            assert old["type"] == "forge:conditional"
            assert len(files) == len(old["recipes"])
            outer = migrate(old.get("conditions", []))
            previous = []
            branch_rows = []
            alternatives = []
            fixed_alternatives = []
            for (_, path), branch in zip(files, old["recipes"]):
                raw = path.read_bytes()
                current = json.loads(raw.decode("utf-8-sig"))
                eligibility = migrate(branch["conditions"])
                assert len(eligibility) == 1
                own = [entry for entry in eligibility if entry["type"] != "neoforge:always"]
                legacy = outer + (eligibility if path.name.endswith("_fallback.json") else own)
                fixed = outer + copy.deepcopy(previous) + own
                assert current.get("neoforge:conditions") in (legacy, fixed), path

                # Preserve every payload field, including ingredient, IDs, amounts and timing.
                payload = {key: value for key, value in current.items() if key != "neoforge:conditions"}
                upstream_payload = migrate(branch["recipe"])
                for key in ("type", "byproducts", "rate", "result", "temperature", "time"):
                    assert payload.get(key) == upstream_payload.get(key), (path, key)
                updated = dict(current)
                updated["neoforge:conditions"] = fixed
                assert payload == {key: value for key, value in updated.items() if key != "neoforge:conditions"}
                if updated != current:
                    assert apply, f"Not mutually exclusive: {path} (run --apply once)"
                    path.write_text(json.dumps(updated, indent=2) + "\n", encoding="utf-8")
                    changed += 1
                branch_rows.append({
                    "path": path.relative_to(ROOT).as_posix(),
                    "payload_sha256": digest(payload),
                    "original_eligibility": eligibility,
                    "fixed_conditions": fixed,
                    "original_amounts_rates_temperature_time_preserved": True,
                })
                alternatives.append(eligibility)
                fixed_alternatives.append(copy.deepcopy(previous) + own)
                if own:
                    previous.append({"type": "neoforge:not", "value": own[0]})
                else:
                    assert path == files[-1][1], "Original unconditional branch must be last"

            tags = sorted({entry["tag"] for branch in alternatives for entry in branch
                           if entry["type"] == "mantle:tag_filled"})
            truth = []
            for bits in itertools.product((False, True), repeat=len(tags)):
                state = dict(zip(tags, bits))
                expected = next(index for index, branch in enumerate(alternatives)
                                if all(evaluate(entry, state) for entry in branch))
                selected = [index for index, branch in enumerate(fixed_alternatives)
                            if all(evaluate(entry, state) for entry in branch)]
                assert selected == [expected], (base, state, expected, selected)
                for outer_enabled in (False, True):
                    truth.append({"tags": state, "outer_conditions_pass": outer_enabled,
                                  "original_first_match": expected if outer_enabled else None,
                                  "converted_enabled_branches": selected if outer_enabled else []})
            result.append({"original_resource": resource, "outer_conditions_unchanged": outer,
                           "branches": branch_rows, "truth_table": truth})
        for recipe in ("tools/materials/honey_block", "tools/materials/composite/magma",
                       "tools/modifiers/tasty_crafting_table"):
            resource = "data/tconstruct/recipes/" + recipe + ".json"
            upstream_payload = json.loads(original.read(resource))
            if "ingredient" in upstream_payload:
                assert set(upstream_payload["ingredient"]) == {"item"}
                upstream_payload["ingredient"] = upstream_payload["ingredient"]["item"]
            path = RECIPES / (recipe + ".json")
            current = json.loads(path.read_text(encoding="utf-8-sig"))
            assert current.pop("id") == "tconstruct:" + recipe
            assert current == upstream_payload, path
            restored.append({"original_resource": resource, "path": path.relative_to(ROOT).as_posix(),
                             "payload_sha256": digest(current),
                             "complete_payload_equals_original_after_ingredient_syntax_migration": True})
    return {
        "upstream": {"path": UPSTREAM.relative_to(ROOT).as_posix(),
                     "sha256": hashlib.sha256(UPSTREAM.read_bytes()).hexdigest(),
                     "version": "TConstruct 1.20.1-3.12.1.231"},
        "scope": "All generated recipe paths ending in _byproduct_N or _fallback; 61 original ordered conditional recipe families.",
        "limits": "Exhaustive byproduct-ingot tag presence combinations, each with outer eligibility false and true. Outer ingredient tag intersections/differences are byte-for-byte semantically unchanged; this static table does not simulate tag item membership or world melting.",
        "family_count": len(result),
        "branch_count": sum(len(group["branches"]) for group in result),
        "truth_row_count": sum(len(group["truth_table"]) for group in result),
        "groups": result,
        "restored_recipes": restored,
    }, changed


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--report", action="store_true")
    options = parser.parse_args()
    report, changed = audit(options.apply)
    if options.report:
        REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"families": report["family_count"], "branches": report["branch_count"],
                      "truth_rows": report["truth_row_count"], "files_changed": changed}))
