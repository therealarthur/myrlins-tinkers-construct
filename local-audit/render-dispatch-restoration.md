# Model dispatch audit and focused restoration, 2026-09-28

Two active omissions are restored: tank-inventory item submission, and drain/duct fluid model data. Modern material/tool item models and copper-can fluid rendering already provide replacements for several legacy loader registrations. Other visual gaps remain; this is not a rendering-parity claim.

This follow-up leaves the immutable `PARITY-AUDIT.md` baseline unchanged. It compares the current source with the already verified official `TConstruct-1.20.1-3.12.1.231-sources.jar`, target NeoForge `26.1.2.109` sources, and the installed companion Core `1.12.0` API. No build, client, server, screenshot, or GUI run was launched by this subtask. Compilation and graphical acceptance belong to the parent task. The parent reported that the preceding AOE overlay compiled successfully; the new changes below await its next compile.

## Restored paths

`smeltery/client/render/TankInventoryBlockEntityRenderer.java` previously extracted `ItemStackRenderState` values but left the submission loop empty. It now extracts each configured slot with `RenderItem.getTransform()` and submits through Core's existing `RenderingHelper.renderItem(PoseStack, SubmitNodeCollector, ItemStackRenderState, RenderItem, int)` overload. The actual released Core JAR exposes this overload, verified with `javap`. The helper applies center, scale, X/Y rotation, light, and the target item-state submission call, while skipping hidden/empty items. Slot count is bounded by both inventory and configured render items.

`SmelteryClientEvents.registerRenderers` assigns this renderer to the melter, casting tank, and all three fluid-cannon block variants through their shared block entity type. The current `src/generated/client/assets/tconstruct/mantle/model/item_lists/` contains `seared_melter.json`, `seared_casting_tank.json`, and `end_fluid_cannon.json`, `scorched_fluid_cannon.json`, `seared_fluid_cannon.json`. The melter declares the custom `tconstruct:melter` display context; using `NONE` during extraction would lose that context even after repairing the loop. Existing fluid submission, block facing, and model-fluid configuration branches remain intact.

`smeltery/block/entity/component/DrainBlockEntity.java` and `DuctBlockEntity.java` now override the live NeoForge `getModelData()` API and combine the retained retexture property with a copied `ModelProperties.FLUID_STACK`. The drain uses the notified display fluid; the duct uses its filter's fluid. Their existing notification methods already request model-data refresh and send block updates. The copy prevents a later fluid mutation from changing an already published model-data value. Existing fluid transfer and filter mechanics are unchanged.

Actual dispatch is `assets/tconstruct/blockstates/{seared,scorched}_{drain,duct}.json` -> `type: mantle:retextured_block` -> each active IO model -> parent `models/block/template/io_fluid.json` (`loader: tconstruct:fluid_texture`). Core's `RetexturedBlockStateModel.Unbaked.bake` detects `FluidTextureModel` and invokes its `bakeDynamic` method. That model reads `level.getModelData(pos).get(ModelProperties.FLUID_STACK)`. Before this restoration, the IO superclass supplied only the retexture property, so the dynamic path received an empty fluid. There is no alternate drain/duct block-entity renderer registered to supply the missing fluid display.

## Legacy registrations and real modern dispatch

All asset inspections include the three resource roots from `build.gradle`: `src/main/resources`, `src/generated/resources`, and `src/generated/client`. In particular, omitting the generated-client root would incorrectly classify most item replacements as absent. These are source asset counts, not loaded-game or pixel-comparison evidence.

| Legacy registration | Actual packaged asset route | Finding |
| --- | --- | --- |
| `tconstruct:material` -> `LegacyPassthroughModelLoader` | 29 item definitions use modern `type: tconstruct:material`; registered `MaterialItemModel.Unbaked.MAP_CODEC` reads stack material IDs and bakes material quads | Active replacement exists. The legacy loader is not the active dynamic material-item implementation. |
| `tconstruct:tool` -> passthrough | 122 item-definition files, including state variants, contain modern `type: tconstruct:tool`; `ToolItemModel` performs materials, modifiers, large/small context, handedness, and ammunition composition | Active replacement exists, with the cache/geometry gaps below. Some generated variant item definitions still use `minecraft:model`; their existence alone does not prove a registered root item dispatches through them. |
| `tconstruct:fluid_container` -> passthrough | Copper-can definition uses `type: neoforge:fluid_container`; 81 bucket definitions use that type too | Active replacement exists. Target `DynamicFluidContainerModel.update` queries `FluidUtil.getFirstStackContained`; `TinkerSmeltery` registers copper-can `Capabilities.Fluid.ITEM` with `CopperCanFluidHandler`. It is not an always-empty static can. |
| `tconstruct:gui` -> passthrough | Only `models/block/foundry/proxy_tank.json` declares this loader; `items/scorched_proxy_tank.json` -> item model -> this parent, using `minecraft:model` | Real gap: the `gui` submodel is ignored. Original `UniqueGuiModel` selected separate geometry for GUI; current `UniqueGuiModel` is an empty class. The normal block model still has geometry. |
| Material block ModelData comment | `blockstates/fake_storage_block.json` uses `type: tconstruct:material_block`; registered `MaterialBlockModel` reads `MaterialBlockEntity.getMaterial()` directly from the render view | Valid replacement for material block texture lookup. The stale comment does not establish a missing material block texture. Its particle material remains the generic fallback. |

The copper-can adaptation is not exact original parity: the original fluid-container model defaulted `flip_gas` to true, while the current item JSON omits the target model's false-by-default `flip_gas`; target cache keys use fluid identity rather than the original whole fluid-stack key. Target `FluidContentsTint` supplies fluid tint, but stack-sensitive sprite/light behavior from the original specialized loader has not been proved equivalent. No copper-can model change is included here.

## Model-fluid configuration gaps

`Config.CLIENT.tankFluidModel` defaults to false. Both tank BE renderers suppress their fluid geometry when it is true, relying on block-state models instead.

| Block(s) | Current model path | Remaining issue |
| --- | --- | --- |
| Seared/scorched fuel/ingot tanks and gauges, seared/scorched lanterns | Eleven blockstate files including the casting tank use `type: tconstruct:tank`; ordinary tank/lantern BEs expose fluid and capacity model data | Real modern tank model exists. Four gauge type changes from the released JAR were already preserved separately. |
| Seared casting tank | `type: tconstruct:tank`, real `TankModel`, but `CastingTankBlockEntity.getModelData` is commented | At `tankFluidModel=true`, no provider supplies this model's fluid/capacity; the BE renderer also suppresses fluid. Default false uses the real BE fluid renderer. |
| Seared melter | `type: mantle:retextured_block`, model chain reaches `template/half_tank` with legacy `tconstruct:tank` geometry | Core's wrapper dispatches fluid-texture/retexture models, not `TankModel.bakeDynamic`. The melter's fluid/capacity ModelData override is also commented. Merely uncommenting that override would not repair this dispatch. Default false uses the BE fluid renderer. |
| Scorched alloyer | `type: mantle:retextured_block`, model chain reaches `foundry/controller/alloyer` with `tconstruct:tank` | Same unsupported wrapper dispatch for model fluid; its TankBlockEntityRenderer suppresses fluid under the true configuration. |
| End/seared/scorched fluid cannons | Ordinary model variants reach `end_fluid_cannon/base` or `template/half_tank` | These blockstates lack `type: tconstruct:tank`; static geometry does not replace BE fluid when the model-fluid configuration suppresses it. |

`TankModel` and `FluidTextureModel` also select the fluid model's still/flowing material without applying its fluid tint source or stack light level to the baked quads. `FluidTextureModel` parses `ColorData` but does not use it. The IO/controller templates do not carry fluid tint indices. Restoring the drain/duct provider therefore restores fluid sprite selection, not verified tint/emission/UV parity. This remains a distinct model-level review item.

## Tool model review findings

`library/client/model/tools/ToolItemModel.buildCacheKey` includes materials, modifier entries, hidden modifier IDs, large/small, handedness, and ammunition. It never incorporates the registered modifier models' `getCacheKey(tool, entry)` values. The official `ToolModel` explicitly did. Present fluid/tank, dyed, potion, banner, trim, and conditional modifier models expose keys based on additional tool data. Thus two tools with the same outer key can reuse geometry baked from the first tool's fluid/dye/other state, and an in-place change can retain the previous model. This is a concrete source gap; no cache rewrite is included here.

Both `ToolItemModel.addLayers` and `MaterialItemModel` retain only NORTH/SOUTH quads for the non-GUI model. The original tool builder retained all quads in its normal model and selected only SOUTH for GUI. This can remove extruded sprite edges when viewing a tool or part from the side. GUI SOUTH-only filtering is intentional; applying NORTH/SOUTH filtering to the full model is not established original behavior. No geometry rewrite or visual acceptance is included here.

## Book element tooltips

These three classes retain commented legacy `drawOverlay` overrides, while current Core `ItemElement`/book elements already use `GuiGraphicsExtractor`:

- `TinkerItemElement`: `noTooltip` suppression and the normal-item-font override are disabled. Inherited item tooltip behavior still exists, so this is not a claim that all book item tooltips are missing.
- `FluidItemElement`: fluid-specific tooltip data/amount is disabled; it inherits the representative bucket/can item's tooltip instead.
- `CycleRecipeElement`: the recipe-cycle hover label is disabled; its existing click/sound behavior remains active.

`PageIconLinkElement` already has a live tooltip override. This audit does not alter book elements or the separately owned material-book restoration.

## Acceptance still required

Compile the three source changes against the pinned target. In a client, inspect configured item positions and custom contexts on a melter, casting tank, and each cannon orientation; exercise empty/filled and hidden slots. Inspect active/inactive, retextured, and filtered drains/ducts while changing fluids. Distinguish the restored provider/submission behavior from the remaining fluid tint and true-model-fluid configuration gaps above. No headless test can establish the final pixels, and no whole-rendering parity is claimed.
