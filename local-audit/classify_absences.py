"""Triage every official-only source path; no filename-only equivalence claims."""
import collections
import json
from pathlib import Path

BASE = Path(__file__).resolve().parent
source = json.loads((BASE / "source-inventory.json").read_text())
records = []
for row in source:
    if row["status"] != "upstream_only":
        continue
    path, name = row["path"], Path(row["path"]).name
    candidates = row.get("same_filename_candidates", [])
    if name == "package-info.java":
        category, reason = "package_metadata", "Package annotations/documentation absence is not itself a missing runtime feature; retain nullability/API review."
    elif candidates:
        category, reason = "relocated_class_candidate", "A same-named class exists at the listed path. Compare implementation; relocation alone does not establish equivalent behavior."
    elif name == "JEIPlugin.java":
        category, reason = "renamed_viewer_entrypoint", "Port has plugin/jei/TConstructJEIPlugin.java with @JeiPlugin. New official viewer categories and behavior are still missing."
        candidates = ["slimeknights/tconstruct/plugin/jei/TConstructJEIPlugin.java"]
    elif name in {"DietPlugin.java", "DummmmmmyPlugin.java", "ImmersiveEngineeringPlugin.java"}:
        category, reason = "optional_external_integration_absent", "External-mod integration class is absent. Keep original capability tracked without installing that external mod merely to match the source tree."
    elif name in {"MaterialIndexSwappingRecipe.java", "MaterialValueSwappingRecipe.java", "RemappingMaterialsModule.java"}:
        category, reason = "material_swapping_or_migration_gap", "Absent source corresponds to absent Travelers swapping recipes or slimeskull remapping data; target-compatible implementation required."
    elif "/plugin/jei/" in path or "/recipe/display/" in path or "Display" in name:
        category, reason = "viewer_display_api_gap", "Latest official display/category/focusing support is absent at this path; newer display behavior must be restored or mapped explicitly."
    elif "/client/book/content/material/" in path:
        category, reason = "material_book_content_gap", "Latest official material-stat book implementation is absent; existing book renderer needs behavior comparison."
    elif "/modules/interaction/edible/" in path or name in {"EdibleEffectHook.java", "FluidAsCapacityModule.java", "FluidPredicateAsCapacityModule.java"}:
        category, reason = "modular_behavior_refactor_review", "Compare newer composition/hooks against port's monolithic behavior/EdibleModule and existing capacity code. Edible gameplay is not wholly absent."
    elif name in {"ModifierTooltip.java", "ModifierTooltipsField.java"}:
        category, reason = "tooltip_policy_api_gap", "Latest official five-context modifier tooltip control is absent; older tooltip_display policy remains in resources."
    elif name == "GoldenAttributeModule.java":
        category, reason = "gold_modifier_implementation_replacement", "Port registers concrete GoldGuardModifier and ChrysophiliteModifier rather than latest data-driven files. Behavior and balance remain unverified."
    elif "/client/" in path:
        category, reason = "client_api_or_rendering_replacement_review", "Modern item/model/render APIs differ; inspect registered replacement paths and user-operated visuals. Class absence alone is insufficient."
    elif "/data/" in path or name == "LoadableFinishedRecipe.java":
        category, reason = "datageneration_api_or_content_review", "Generation APIs differ by Minecraft version. Compare actual produced resources and provider behavior before accepting this omission."
    elif name == "AncientToolItemListing.java":
        category, reason = "villager_trade_migration_review", "Port adds villager_trade data, but the original trade helper's behavior has not been matched."
    else:
        category, reason = "unresolved_common_api_absence", "No concrete equivalent was established in this pass; inspect callers/registrations and preserve in the parity queue."
    records.append({"path": path, "category": category, "rationale": reason, "related_port_paths": candidates,
                    "parity_status": "unverified", "upstream_sha256": row["upstream_sha256"]})
assert len(records) == 92
(BASE / "source-absence-classification.json").write_text(json.dumps(records, indent=2) + "\n")
print(json.dumps(dict(collections.Counter(r["category"] for r in records)), indent=2))
