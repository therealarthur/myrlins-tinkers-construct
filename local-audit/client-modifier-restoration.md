# Client modifier restoration, 2026-09-28

This is a source restoration against the official `v3.12.1.231` source JAR recorded in `PARITY-AUDIT.md`. It does not amend that immutable baseline audit or establish graphical acceptance. Target API evidence comes from cached NeoForge `26.1.2.109` sources and the patched Minecraft `26.1.2` sources (`transformSources_a939cf3bda7ecd59dab066b095c79cc14c972ed3_output.zip`).

## Restored paths

- `ToolClientEvents.clientSetupEvent` now registers `ModifierClientEvents` on the client game event bus. The class's original Forge bus annotation had been removed, and no replacement registration existed. This restores active tooltip, zoom, equipment-change, and logout handlers as well as the two previously commented rendering handlers.
- `ToolClientEvents.handleInput` retains the original attribute + held-tool-stat + deprecated armor-stat calculation, clamped to 0–1, and applies its original `speed * 5` correction to both axes via `ClientInput.moveVector`.
- `ModifierClientEvents.renderHand` retains the original ballista held-ammo duplicate-hand cancellation, invisibility exclusion, empty-offhand/chestplate rule, filled-map exclusion, and `SHOW_HAND` tag rule. It calls the actual target `ItemInHandRenderer.renderPlayerArm` with the event's `SubmitNodeCollector`, equip/swing progress, and correct arm. Pose restoration uses `finally`.
- `renderHotbar` runs after `VanillaGuiLayers.HOTBAR`, skips hidden HUDs, nonplayer cameras, absent game mode, and spectators. Shield-strap and sleeves indicators retain their original texture coordinates, handedness, empty-offhand variants, and screen offsets. Calling target `Gui.extractSlot` preserves item model context, pop animation, durability/count decorations, and seeds.
- Minimap rendering retains scale, seven-pixel border, alignment, configured offsets, and top-right effect avoidance. `DataComponents.MAP_ID` supplies the modern map ID. A new render state is extracted each frame; its local decoration `renderOnFrame` flags are enabled so the GUI extractor includes player markers, matching the original map renderer's `showOnlyFrame=false`. The saved map data is not changed.
- Item-frame rows retain original alignment, last-row alignment, configured offsets, map collision offset, and potion-effect offset. Since existing config accepts zero items per row, rendering clamps columns to at least one rather than dividing by zero.
- Logout also clears `currentSleeve`, alongside the original shield/item-frame cache clearing.

## Target API evidence and access changes

`LocalPlayer.aiStep` calls `input.tick()` immediately before `ClientHooks.onMovementInputUpdate(this, input)`. Later, `applyInput` calls `modifyInput(input.getMoveVector())`. `modifyInput` applies 0.98 damping, the current item's `UseEffects.speedMultiplier()` when using and not a passenger, the sneaking attribute, then square-movement normalization. The event therefore adjusts the input before the vanilla item-use multiplier. `UseEffects.DEFAULT.speedMultiplier()` is 0.2. An ordinary tool gets the same intended speed as the original implementation; the baseline attribute 0.2 gives a multiplier of 1 and leaves ordinary vanilla behavior unchanged.

Custom `USE_EFFECTS` values deliberately retain their relative effect: a component multiplier `u` combines with the Tinkers correction as `u * speed / 0.2` before the target's later movement normalization. This does not promise the Tinkers speed value is an absolute movement fraction for items overriding that component. A zero component multiplier remains zero; no division by the component or modification of the item is used. The modern sprinting and square-normalization rules remain vanilla. No input/physics runtime test was performed here.

Three narrowly scoped access transformers were verified against actual target class descriptors with JDK 25 `javap -s -p`:

```
public net.minecraft.client.player.ClientInput moveVector
public net.minecraft.client.renderer.ItemInHandRenderer renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V
public net.minecraft.client.gui.Gui extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V
```

The two obsolete arm/slot entries were replaced; input has no public setter. Returning projectile access entries were preserved.

The target `GuiGraphicsExtractor.map` consumes ordinary atlas decorations and names but does not call NeoForge's `MapDecorationRendererManager.render` custom 3D decoration hook. Standard map markers and NeoForge map-state extraction extensions are retained; custom decoration renderer compatibility remains an open follow-up requiring a dedicated picture-in-picture renderer if a concrete mod needs it.

## Acceptance still required

No build, Minecraft client, GUI, or server was launched by this task. `git diff --check` and source/API review passed; compilation belongs to the coordinated parent build.

Graphical acceptance must cover both main-arm settings; shield strap and sleeves with empty/occupied offhand; held ballista ammo; gloves and modifier-forced empty offhand; invisible players; map in main hand; minimap player markers, banner names, missing saved data, scale/offset settings and beneficial/harmful effect rows; item-frame partial rows and zero-column config; F1, spectator, camera changes, world logout/rejoin; FOV modifiers with FOV effects at zero and one. Movement acceptance must compare ordinary item use, Tinkers tool-use stats, armor bonuses, crouching, riding, and any custom `USE_EFFECTS` item. These are pending checks, not claimed passes.

## Read-only follow-up: melting recipe generation command

`shared/command/subcommand/GenerateMeltingRecipesCommand` is still a 33-line placeholder whose `run` sends the disabled message and returns zero. The original accepts a `recipe_type` argument and writes a generated datapack after reading `tconstruct:command/generate_melting_recipes.json`. It applies melt/input/ignore predicates and skipped recipe IDs, inspects ingredients and output stacks, resolves container or existing melting fluids, conservatively intersects alternatives and duplicate crafting results, scales by output count, emits primary/byproduct/damage-aware recipes, and reports count/time/output path. Current code also changed the configuration identifier to `tconstruct:melting_recipe_generation` and removed the argument. This is a real missing administrative feature, not proof that normal melting is broken.

Restoration requires deliberate modern `RecipeHolder`/`RecipeInput` and ingredient/result inspection, data-component-sensitive filtering, fluid capability/transfer APIs, recipe codecs/`RecipeOutput`, singular `data/.../recipe` output folders, and current pack metadata. The original intersection helper advances iterators without assigning their returned values; copying it verbatim would retain incorrect alternative intersection behavior. Cache freezing also needs `finally` cleanup around output generation. No command implementation or filesystem-generating behavior was changed in this task.
