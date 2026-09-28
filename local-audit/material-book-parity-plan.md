# Material book restoration

Baseline: official `TConstruct-1.20.1-3.12.1.231` source and release archives in `local-audit/upstream`, compared with the current 26.1.2 port. The release contains the five `content/material` classes; these are missing behavior, not renamed classes.

## Scope

- Restore `SingleMaterialStatContent`, slime, ribcage, shell and laces pages, their page registrations, and material-type registrations. Single-stat pages expose one stat row, matching traits, and appropriate part icons/HTML; slime must not advertise part-builder crafting.
- Restore grouped material sections in `TierRangeMaterialSectionTransformer`, including array entries, unique group-prefixed page names and title suffixes. Retain object syntax and public compatibility overloads.
- Restore skull material names independent of casting output names, first skull ingredient as index icon, and repair-kit deduplication. Use the shared received recipe snapshot and Core's multi-recipe expansion, including on a dedicated client.
- Restore Gadgetry's five-group materials section and the Encyclopedia equivalent. Keep the existing Encyclopedia section ID and skull page names where practical so saved page positions and resource-pack links continue to work.
- Restore category-specific translation lookup with a legacy skull-key fallback. Existing port tuning remains unchanged. Released flavor text can be reused; encyclopedia effects must match the current traits. In particular, enderslime-vine laces currently have enderporting, and several probability/multiplier values differ from upstream. Do not import numerical promises without verifying them.
- Reset the six book instances on their next access after the shared recipe snapshot revision changes. This covers received recipes, material updates and disconnect without modifying the shared cache. Resource/language changes already reset books through Core.

## API and ownership

Owned source: `library/client/book`, `CustomMaterialName`'s existing material-name helper extraction, and the affected book/language resources. Core already supplies typed page registration, arbitrary section `extraData`, multi-recipe expansion from a recipe-holder stream, and `BookData.reset()`; no Core change is expected. `MaterialStatsId.getId()` unwraps to `Identifier`; `MaterialStatType.getStatsId()` retains the typed stat identifier.

Do not modify viewer/cache/overlay sources or balance data. Preserve current text-height wrapping and other modern API adaptations. A revision reset takes effect when the book is reopened; it does not replace the currently open screen during a datapack reload.

## Validation and remaining acceptance

Static/resource checks and compilation can establish registration, array syntax, material categories, translation coverage and retained page IDs. A client fixture should invoke the actual transformer and verify repeated materials produce distinct pages, titles, correct stat types and no error pages. Dedicated-client checks must cover snapshot updates, reconnect and multi-recipe skull icons.

Actual client acceptance remains required: open both books, follow all five categories and index links, hover stats/traits and recipe icons, compare slime part-builder suppression and skull icons, inspect long localized descriptions at normal and narrow GUI scales, then reload data and reopen. A server fixture cannot validate font layout, item rendering, book navigation or remote-client recipe display. Source restoration and successful compilation are not client acceptance.

## Implemented source and resource checks

The two indexes now contain all five groups. Skull entries explicitly use an empty name prefix; Gadgetry retains `materials.<material>` and Encyclopedia retains `materials_skull.<material>`. Other groups use their stat names as prefixes. The optional empty prefix extends the original array format without changing object-format page names.

All 79 released English category flavor/encyclopedia keys now exist, plus the skull title label. Released flavor lines are preserved. Detailed descriptions avoid numerical claims while the port's balance remains distinct; current stat and trait tooltips remain available. This is intentionally not a claim of matching the release's numerical prose. The enderslime-vine laces detail is empty because the port assigns Enderporting there rather than the original dodge trait; the real trait still appears above it. Existing legacy skull keys are retained, with new category-specific keys preferred and legacy keys used when no category key exists. Other languages and their legacy keys are not rewritten; complete translated category prose remains pending.

Static checks parsed both indexes and English JSON, verified five groups, retained skull link IDs, resolved suffix labels, and verified all 79 category keys. Existing duplicate `_comment`/`__comment` JSON keys were compared with HEAD and remain unchanged; no duplicate translation keys were added. `MaterialBookContentTest` adds three checks against production page registration/section parsing, single-stat page contracts, and actual shared snapshot changes resetting `BookData` on the next access. It is enabled only on a client distribution and skips on a dedicated-server test launch, without forcing client classes into that environment. It does not load a screen or claim rendered acceptance. The parent owns compilation and test execution; main-source compilation has passed after removing the obsolete package annotation.
