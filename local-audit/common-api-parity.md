# Original common APIs and component-storage corrections

Reference: official Tinkers Construct `v3.12.1.231`, source archive pinned in
`PARITY-AUDIT.md`. This is a follow-up to the frozen baseline, not a replacement
for its inventory or a claim of complete API compatibility.

## Restored original behavior

- Registered original `fluid_amount` and `tank_capacity` tool variables. Both
  use actual `ToolTankHelper` storage and optional fluid predicates.
- Registered original `fluid_as_capacity` and `fluid_predicate_as_capacity`
  modules. Predicate modules can drain, not generate an unspecified fluid.
  On the component-based target, amount changes preserve fluid components and
  nonpositive amounts empty the tank; foreign fluid cannot be replaced.
- Registered original `tool_action` predicate against NeoForge `ItemAbility`.
  Actual definition and modifier action hooks still determine the result, and
  broken tools do not expose actions.
- Restored `DURABILITY_CHANGED` and `ToolDurabilityChangedHook`. Notifications
  run after accepted mutations with the actual clamped damage or repair amount;
  all nested observers run. Existing stack writeback remains after damage hooks.
- Restored the `IMaterialUser` contract on material items and stat types, plus
  original integer/percentage display stat classes. Durability, fishing luck,
  lure and water inertia use those formatters. This does not retune their values.
- Capacity values are displayed as whole units, matching stored capacity.

## Additional defects exposed by the comparison

`LazyMaterial` instances constructed from IDs no longer retain old material
objects or `UNKNOWN` across reloads. Explicit `IMaterial` instances remain
pinned for data generation. During a partial three-packet synchronization the
ID lookup returns `UNKNOWN`. No global logout reset was added while an integrated
server may still use the common registry. `MaterialManager` publishes completion
after both tags and material definitions have been replaced.

Potion tipping reads modern fluid potion components before the legacy container
tag. Tip clearing now writes the copied `ToolStack` back to its output stack;
previously it removed the string from a detached copy and returned unchanged
data. The input stack and unrelated components are preserved. Potion display
expansions invalidate with the received recipe/material revision.

`NoContainerIngredient` now accepts both empty and null crafting remainders.
Its old null-only check rejected ordinary items under the current item-stack API.

The modifier-display helper now attaches persistent data before copying NBT into
the immutable custom-data component. Its callback's potion or other preview data
previously disappeared during that copy.

## Horn instrument variants

Restored the eight original instrument-specific material recipes, the conditional
fallback, and the `variant_horns` instrument tag. The generic recipe was removed
so it cannot compete with these variants. `InstrumentIngredient` matches the
current `InstrumentComponent`; its explicit registry lookup produces display
stacks carrying that same component. Recipe and tag providers produce the new
resources. Material value remains four per horn.

`InstrumentIngredientTest` checks all 64 instrument/recipe combinations through
the registered ingredient codec, JSON roundtrips, component-bearing display
stacks, and actual bound-holder tag inclusion/exclusion. The fallback test binds
tags on isolated lookup holders and restores their previous (possibly unbound)
state. Independent review also found item-only material recipe caching and an
explicit-registry display lookup issue; those corrections and tests are owned by
the recipe-viewer follow-up.

## Verification scope

`ToolApiParityTest` exercises registered loaders, actual tool/tank storage,
capacity clamping, component preservation, foreign-fluid rejection, accepted and
cancelled durability changes, merged observers and tool actions. Its hook/tag
test adapter delegates state and mutations to a real `ToolStack`; it supplies
only isolated test hooks and tag answers.

`MaterialReloadTest` exercises the real three synchronization callbacks in all
six orders across missing, added, replaced, redirected and removed materials.
It restores the original static registry state after the test.

`PotionCastingParityTest` exercises the actual tipping and clearing recipes with
modern components and legacy tags, checking output and input data independently,
and checks ordinary versus filled-container ingredient matching.

The completed headless run `build/local-evidence/build-20260928-043419.log`
passed all 59 applicable cases, including the common APIs, material reload,
three potion/display cases and both horn tests. Five client-only cases were
skipped. The fallback fixture uses nondefault isolated holders because target
component equality otherwise collapses an equal-key value to the item's default
holder; identity assertions and tag changes on the unchanged stack verify the
intended tag-based behavior. XML copies and hashes are retained in the final
verification manifest. These tests do not establish
client rendering, server packet delivery, natural gameplay or world restart
behavior.

## Mapped paths and remaining work

The two old TConstruct loot helper classes have active counterparts in Core:
`mantle:has_context_set` and `mantle:tag_preference`. Current generated loot
resources use those IDs. The old `tconstruct` aliases are a separate compatibility
gap, not absent current loot mechanics.

`ModifierPotionCastingRecipe` was split into inline code in the two current
potion recipe classes. Its class absence did not mean potion casting was absent;
the concrete component/writeback defects are addressed above. Original
conditional-stat declarations and the remaining modular edible behavior require
separate closure. Detailed modifier tooltip contexts are covered by
`modifier-tooltip-parity.md`.
