# Rendering parity follow-up for arthur.3

This batch restores the specific dispatch, data, cache, geometry, and tooltip omissions identified after arthur.2. It does not modify Core, default client settings, or the frozen arthur.2 artifacts. The immutable upstream inventory remains `PARITY-AUDIT.md`; `render-dispatch-restoration.md` records the preceding source audit. These changes do not establish complete graphical parity.

## Tank model fluid

`CastingTankBlockEntity`, `MelterBlockEntity`, and `AlloyerBlockEntity` now provide `ModelProperties.FLUID_STACK` and `TANK_CAPACITY` through the live NeoForge `ModelData` API. Fluids are copied so published model data does not alias the tank's mutable stack. The casting tank and melter had commented providers; the alloyer also needed a provider for its existing tank geometry. Cannons already inherit a provider from `TankBlockEntity` and were not given a duplicate implementation.

All variants in five blockstate files now use `type: tconstruct:tank`: `seared_melter`, `scorched_alloyer`, `end_fluid_cannon`, `seared_fluid_cannon`, and `scorched_fluid_cannon`. The seared casting tank already had the correct type. The model references, facing rotations, UV-lock flags, and geometry are retained. Melter and alloyer BEs do not implement retexturing; replacing their ineffective `mantle:retextured_block` wrapper does not remove a supported texture selection path. The target `TankModel.Unbaked` follows the existing model parent chain and bakes the selected geometry with those rotations.

A direct resource walk verified all **46 variants across these six block families** reach a `tconstruct:tank` loader with a positive fluid increment count: casting tank 4, melter 12, alloyer 12, and three cannons with 6 each. The default `tankFluidModel=false` remains unchanged. The existing BE renderers handle fluid in that mode; the restored providers and dispatch supply model fluid when true. Existing packet/update behavior remains in use.

Remaining limits: `TankModel` and `FluidTextureModel` have not been rewritten for original fluid tint, emissive light, UV-lock/color metadata, or stack-sensitive sprite parity. These are separate from supplying a missing fluid/model. The existing `SafeClient.updateFluidModel` only requests a redraw when the amount changes; replacing fluid identity at exactly the same amount through that packet route can retain stale chunk geometry. This behavior exists in the pinned original too and is not changed in this batch. Configuration toggling and resource reload must be checked in a client; no pixels were inspected.

## Tool models

`ToolItemModel` now includes each visible modifier model's `getCacheKey(tool, entry)` and each constant model's key in the outer geometry cache. Keys are associated with their modifier ID or constant name, so unrelated conditional keys cannot collapse into the same unlabeled sequence. Existing materials, modifier entries, hidden IDs, display size, hand, and ammunition remain part of the outer key. A changed dye, potion, fluid, tank fill state, trim, or other data represented by a registered model's key can now cause a new bake without requiring a modifier-level change.

The modifier map is resolved lazily once for each baked item-model instance and shared by key calculation and quad generation. Resource model replacement produces new instances. Legacy modifier roots also remain usable when no explicit modifier-map list was supplied, instead of being skipped by an early return. This does not redefine a modifier model's own cache-key contract or prove arbitrary third-party model implementations correct.

`ToolItemModel.addLayers` now retains every quad direction in the normal model, matching the original tool builder. GUI continues to receive only SOUTH-facing quads, with the existing layer order. `MaterialItemModel` also retains the material sprite's extruded edge quads rather than discarding every direction except NORTH/SOUTH. No sprite coordinates, large-tool tuning, or hand transforms changed. Visual inspection is still required for edge thickness, depth overlap, transparency, and custom display contexts.

## Proxy-tank GUI geometry

The original `models/block/foundry/proxy_tank.json` embeds a separate `gui.elements` list, but the passthrough loader ignores it. A new `proxy_tank_gui.json` model inherits that original model's textures/transforms and contains the exact alternate elements. The existing `scorched_proxy_tank` item definition now uses Minecraft's `display_context` selector: GUI selects this new model, and other contexts retain the original item model. Target `SelectItemModel.SwitchCase` uses the compact-list codec for `when`, so the single string `gui` is valid.

This repairs the shipped proxy-tank asset without changing legacy loader registration. It does not implement arbitrary resource packs' `tconstruct:gui` loader extensions, nor other tank models' embedded GUI submodels; those remain separate compatibility work. The original outer model and its embedded alternate geometry are retained.

## Book tooltips

Three overrides are restored using current `GuiGraphicsExtractor` signatures and existing Core helpers:

- `TinkerItemElement` honors `noTooltip` and uses the normal item font when no custom tooltip is supplied.
- `FluidItemElement` shows the actual fluid tooltip, including the supplied fluid amount, instead of inheriting only the representative container item's tooltip.
- `CycleRecipeElement` shows the translated recipe-cycle hover label. Its click and sound behavior is unchanged.

Correction to the preceding audit: **`PageIconLinkElement` is entirely commented out**, including the apparent tooltip method. The earlier description of that method as live was incorrect. It is deprecated legacy code, not one of the three restored active element classes; the only reference outside its source is another deprecated-class comment in `ContentPageIconList`, with no active callers found. The material-book content restoration is owned separately and unchanged here.

## Verification and boundaries

`TankModelDataTest` prepares four tests for the parent's FML launcher: real BE fluid/capacity snapshots and mutation isolation; the existing cannon provider for all three blocks; every affected blockstate variant's complete parent chain; and the proxy GUI selector with exact original alternate geometry. They require no world or renderer.

`ToolModelGeometryTest` prepares two tests using client classes without opening a window: actual dyed/constant fluid model keys across persistent-data changes and hidden modifiers; and quad routing across all six directions with retained GUI ordering. It is explicitly gated to a client distribution. A skip under server FML is not a pass. The tests do not bake GPU textures or assert rendered appearance.

The direct JSON/resource checks passed and `git diff --check` found no whitespace errors in the owned source changes. Main compilation and test execution are performed only by the parent task. At authoring time those .3 checks are pending; no build, server, game client, or GUI was launched by this subtask.

Separately, the parent reported that arthur.2's restored melting command passed all 11 prepared tests after correcting the assertion for Core's documented legacy constructor ID. This supersedes the pending-rerun wording in `melting-command-restoration.md`; no Core serializer behavior changed.
