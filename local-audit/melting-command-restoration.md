# Melting recipe generator restoration, 2026-09-28

The disabled `GenerateMeltingRecipesCommand` is restored as `/tconstruct generate melting_recipes <recipe_type>`. It reads the original `tconstruct:command/generate_melting_recipes.json` resource, applies melt/input/ignore predicates and skipped recipe IDs, infers conservative fluid amounts, intersects duplicate recipe results, builds melting recipes including byproducts and damage scaling, and writes a generated datapack. The original source is official Tinkers' Construct `v3.12.1.231`, already hashed and matched to its tag in `PARITY-AUDIT.md`.

This follow-up does not amend the immutable baseline audit. Compilation and headless tests are coordinated by the parent task. No command, game client, server, or build was launched by this subtask. The parent confirmed clean main compilation in `build-20260928-032903.log`. Its first FML run executed all 11 generator tests: 10 passed, and the remaining assertion exposed Core's documented legacy ID fallback rather than a changed recipe payload. The corrected assertion described below awaits a rerun.

## Target contract

- Target `26.1.2` recipes use `RecipeHolder` IDs and `RecipeInput`, and no longer provide the old universal ingredient/result accessors. The command iterates `server.getRecipeManager().getRecipes()`, selecting the requested registered recipe type.
- A result must be a literal `SlotDisplay.ItemStackSlotDisplay` or `ItemSlotDisplay`; every display must describe the same item, components, and count. Missing, example, tag, composite, and ambiguous outputs are rejected. The command does not resolve demonstration displays into fabricated recipe results.
- The supported exact recipe classes are `ShapedRecipe`, `ShapelessRecipe`, `SmeltingRecipe`, `BlastingRecipe`, `SmokingRecipe`, `CampfireCookingRecipe`, and `StonecutterRecipe`. Their source establishes one consumed item per ingredient slot and a fixed result. Special recipes, subclasses, and unknown custom classes are rejected. **Generic custom-recipe generation remains a parity gap.** The full original administrative workflow is restored, with this explicit inference boundary.
- Placement is traversed using `slotsToIngredientIndex()`, preserving repeated references to a shared ingredient. Empty grid slots are skipped. `ingredients()` alone is not used as an occurrence count.
- Ingredients must be simple and have nonempty item alternatives. Every alternative must pass the input predicate. Alternatives must either all be ignorable/unmeltable or all resolve to the same fluid and components. Compatible alternatives take the smallest amount; incompatible mixtures veto that recipe. This retains the original conservative approach.
- Nondamageable outputs with component patches are excluded, corresponding to the original NBT exclusion. Damageable outputs retain the original exception and produce damage-scaled melting with the original 10 mB unit size.
- All gathered fluid amounts are divided by the output stack count using integer division. Fractions below one mB are omitted instead of creating invalid empty outputs. Compatible duplicate recipes intersect their outputs and take the minimum amount. A rejected recipe with a known literal output vetoes a previously inferred candidate for that item.

## Fluid and output API mapping

The fluid cache retains original precedence: registered Core container-transfer recipe, bucket, item capability, existing static melting lookup. Bucket content is `BucketItem.getContent()`. Item capabilities use `ItemAccess.forStack(stack).oneByOne().getCapability(Capabilities.Fluid.ITEM)`, yielding `ResourceHandler<FluidResource>`; query `size`, `getResource`, and `getAmountAsLong`, then `FluidResource.toStack`.

The command only queries that resource handler. It rejects multiple nonempty fluid tanks and amounts above `Integer.MAX_VALUE`, rather than silently picking one tank or narrowing a long. The separate Core transfer route works with a disposable item stack and temporary tank, never an inventory item owned by a player.

`FluidStack.isSameFluidSameComponents` prevents components from being conflated during intersection. Tagged outputs retain their tag and custom data where representable. Current Core `FluidOutput` JSON supports `CUSTOM_DATA` only; additional or removed component patches are rejected explicitly. Fixing that general Core serialization limit is outside this change.

Builders feed modern `RecipeOutput.accept(ResourceKey<Recipe<?>>, Recipe<?>, AdvancementHolder, ICondition...)`. Recipes are encoded by the actual registered `Recipe.CODEC` using level registry serialization context, before any files are written. No advancement is required for these generated recipes.

Core 1.12.0 `LoadableRecipeSerializer.fallbackIdContext` supplies `mantle:loadable_recipe` to legacy recipe constructors during JSON decode; Minecraft's `RecipeHolder` owns the actual resource key. This was verified in both the companion source and released JAR bytecode. The codec test now checks the exact fallback, the generated holder key, and equality of every remaining recipe JSON field after re-encoding. No Core serializer was changed. Code elsewhere that relies on a decoded recipe's legacy `getId()` instead of its holder remains a separate compatibility risk; actual datapack reload identity is not proven by this unit test.

Generated JSON uses `data/tinkers_generated/recipe/melting/<item namespace>/<item path>.json`, with singular `recipe`. The normalized path must stay inside its namespace recipe directory. Core's existing pack helper writes only `pack_format`, which the target rejects for modern data formats above 81 without `min_format`/`max_format`. This command therefore encodes `PackMetadataSection.SERVER_TYPE` with the current exact pack version; Core itself remains unchanged. The default config's `forge:books` and `forge:dyes` tags migrate to `c:books` and `c:dyes`.

Files are written to the existing Core-generated pack location. The command does not call `/reload` automatically. Failed writes are counted separately, invalid config produces an error, and unsupported recipe counts are reported with grouped reasons. Recipe IDs and inspection failures are available in the log. An empty inference result creates no pack files. Existing generated files from earlier invocations are not deleted.

## Original defects corrected

- The original sorted intersection loop called `iterator.next()` without assigning the returned values. Direct pair matching now consumes matched elements and handles more than the first fluid, including different component variants with the same registry name.
- Melting recipe construction registers lookup entries. The lookup freeze is now enclosed by `try/finally`, so a failing builder/codec cannot leave future recipe reloads frozen.
- Adding multiple ingredient amounts detects integer overflow.
- Per-output rounding does not pass empty fluids into a required-output codec.
- Success elapsed time uses seconds, matching the existing English translation; the original value was milliseconds.

## Regression coverage prepared

`local-tests/unit/slimeknights/tconstruct/shared/command/subcommand/GenerateMeltingRecipesCommandTest.java` contains 11 tests, using the actual target classes and registered recipe codecs under the parent FML unit-test launcher:

1. Literal output count and rejection of ambiguous, missing, tag, and composite displays.
2. Shared placement index multiplicity, empty grid slots, and output-count division.
3. Minimum alternative amount and rejection of mixed/different-fluid alternatives, excluded inputs, and nonplaceable recipes.
4. Ignored slots and sub-mB rounding.
5. Multi-fluid duplicate-recipe intersection, reversed order, omission, minima, and no cached-fluid mutation.
6. Distinct custom data, unsupported component serialization, and integer overflow.
7. Tag preservation, different-tag separation, and exact/tag simplification.
8. Actual ResourceHandler query contract, bucket lookup, long overflow, and multi-tank rejection; any insertion/extraction call fails the test.
9. Freeze cleanup when the output callback throws.
10. Damage-aware builder, primary-fluid ordering, byproduct amounts, and Recipe.CODEC encode/decode/re-encode.
11. Current pack metadata decode, singular recipe path, and traversal rejection.

The production scan, command permission/syntax, resource lookup, real datapack writes, and `/reload` acceptance have not been run here. Runtime acceptance should use a disposable fixture datapack with known static crafting recipes, duplicate alternatives, excluded/unsupported recipes, and a bounded output folder. No claim of generic modpack-wide inference or full feature parity follows from this source work.
