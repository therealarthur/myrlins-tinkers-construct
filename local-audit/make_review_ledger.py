"""Produce an exhaustive file-level review queue from compare_upstream.py output.

Classifications deliberately separate missing paths from missing mechanics.
Manual feature findings are documented in PARITY-AUDIT.md.
"""
import collections
import json
from pathlib import Path
import re

BASE = Path(__file__).resolve().parent


def read(name):
    return json.loads((BASE / name).read_text(encoding="utf-8-sig"))


def dump(name, obj):
    (BASE / name).write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def classify_resource(row):
    path = row["path"]
    if row["status"] in {"byte_equal", "newline_equal", "json_equal"}:
        return "content_matches_behavior_unverified"
    if row["status"] == "continuum_only":
        return "port_addition_or_replacement_review"
    if row.get("same_content_other_paths"):
        return "same_content_elsewhere_mapping_review"
    if path.startswith("data/forge/"):
        return "common_tag_namespace_membership_review"
    if "/book/" in path:
        return "book_translation_or_layout_review"
    if path.startswith("assets/"):
        return "client_asset_or_resource_schema_review"
    if "/smeltery/melting/metal/" in path:
        return "optional_metal_recipe_or_schema_review"
    if "/tools/materials/horn/" in path:
        return "instrument_ingredient_refactor_review"
    if "/tinkering/materials/" in path:
        return "material_gameplay_review"
    if "/tinkering/tool_definitions/" in path:
        return "tool_gameplay_review"
    if "/tinkering/modifiers/" in path:
        return "modifier_gameplay_or_static_replacement_review"
    if "/recipe/" in path:
        return "recipe_gameplay_or_schema_review"
    if "/worldgen/" in path or "/structure/" in path or "/neoforge/biome_modifier/" in path:
        return "world_generation_or_migration_review"
    return "unreviewed"


def classify_source(row):
    path = row["path"]
    if row["status"] in {"byte_equal", "newline_equal"}:
        return "source_matches_runtime_unverified"
    if row["status"] == "continuum_only":
        return "port_addition_or_replacement_review"
    if row["status"] == "source_changed":
        return "implementation_behavior_review"
    if row.get("same_filename_candidates"):
        return "same_classname_elsewhere_mapping_review"
    if path.endswith("package-info.java"):
        return "package_metadata_review"
    if "/plugin/jei/" in path or "/recipe/display/" in path:
        return "viewer_feature_or_api_review"
    if "/client/" in path:
        return "rendering_or_book_migration_review"
    if path.endswith(("DietPlugin.java", "DummmmmmyPlugin.java", "ImmersiveEngineeringPlugin.java")):
        return "optional_external_integration_review"
    return "unreviewed_source_absence"


def main():
    queue = []
    groups = collections.defaultdict(collections.Counter)
    for kind, filename, classify in [("resource", "resource-inventory.json", classify_resource),
                                     ("source", "source-inventory.json", classify_source)]:
        for row in read(filename):
            label = classify(row)
            groups[kind + ":" + row["domain"]][label] += 1
            # This is a review queue, not a completed assessment. Every changed,
            # missing or added file is represented, including migrations.
            if label not in {"content_matches_behavior_unverified", "source_matches_runtime_unverified"}:
                queue.append({"kind": kind, "path": row["path"], "comparison_status": row["status"],
                              "review_category": label, "review_status": "open"})
    dump("review-queue.json", queue)
    dump("feature-domain-coverage.json", dict(sorted(groups.items())))
    release = read("upstream-release.json")
    section, checklist = "Introduction", []
    for line in release["body"].splitlines():
        if line.startswith("#"):
            section = line.lstrip("# ")
        if line.strip().startswith("* "):
            checklist.append({"id": f"upstream-3.12.1-{len(checklist)+1:03}", "section": section,
                              "text": line.strip()[2:], "status": "unverified_in_continuum",
                              "source": release["html_url"]})
    dump("upstream-release-checklist.json", checklist)
    print(json.dumps({"review_queue": len(queue), "feature_domains": len(groups), "release_checklist": len(checklist)}, indent=2))


if __name__ == "__main__":
    main()
