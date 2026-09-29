package aebm.continuumtests;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.fluid.FluidActions;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.casting.material.PartSwapCastingRecipe;
import slimeknights.tconstruct.library.recipe.material.IMaterialValue;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.partbuilder.IPartBuilderContainer;
import slimeknights.tconstruct.library.recipe.partbuilder.PartRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.inventory.TinkerStationContainerWrapper;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

/**
 * parity/materials server fixture (2026-09-29), command {@code aebmmaterialstest}.
 * <p>
 * Uses the loaded material, recipe and tool definition data with real part builder recipes, real casting table and
 * basin block entities (ticked with their server ticker) and a real tinker station container. Objects stay above
 * build height over an unloaded column and are never installed in chunks. Covers M1 (sky and ender slimeskin parts
 * from the part builder and composite casting, legacy skin migration without item loss) and the slimesuit part of
 * M1/M2 (build a slimeskull, skin swap it through sky, ender and venom slime, repair it, swap a slimecage).
 */
public final class MaterialsParityServerFixture {
  private MaterialsParityServerFixture() {}

  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmmaterialstest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final BlockPos position;
    private final FakePlayer player;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
      this.position = BlockPos.containing(source.getPosition().x, level.getMaxY() + 32, source.getPosition().z);
      this.player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "MaterialsFixture"));
      player.setPos(source.getPosition().x, level.getMaxY() + 32, source.getPosition().z);
    }

    private int run() {
      test("part_builder_makes_leather_maille", () -> {
        ItemStack maille = partBuilder(TinkerToolParts.maille.get(), new ItemStack(Items.LEATHER, 4));
        require(maille.is(TinkerToolParts.maille.get()), "part builder must output maille");
        require(material(maille).sameVariant(MaterialIds.leather), "part builder maille must be leather, got " + material(maille));
      });
      test("composite_casting_makes_sky_and_ender_slimeskin_maille", () -> {
        for (var entry : List.of(new Object[]{TinkerFluids.skySlime.get(), MaterialIds.skyslimeskin}, new Object[]{TinkerFluids.enderSlime.get(), MaterialIds.enderslimeskin})) {
          ItemStack leather = TinkerToolParts.maille.get().withMaterial(MaterialIds.leather);
          ItemStack skin = cast(false, leather, (Fluid) entry[0]);
          require(skin.is(TinkerToolParts.maille.get()) && material(skin).sameVariant((MaterialVariantId) entry[1]), "composite must make " + entry[1] + ", got " + material(skin));
          // venom cleaning returns the part to leather, so dipping is reversible without losing the part
          ItemStack cleaned = cast(false, skin, TinkerFluids.venom.get());
          require(material(cleaned).sameVariant(MaterialIds.leather), "venom cleaning must return leather, got " + material(cleaned));
        }
      });
      test("legacy_vine_skin_parts_migrate_without_loss", () -> {
        for (var entry : List.of(new Object[]{MaterialIds.skySlimeskin, TinkerFluids.skySlime.get(), MaterialIds.skyslimeskin},
                                 new Object[]{MaterialIds.enderSlimeskin, TinkerFluids.enderSlime.get(), MaterialIds.enderslimeskin})) {
          MaterialVariantId legacy = (MaterialVariantId) entry[0];
          ItemStack old = TinkerToolParts.maille.get().withMaterial(legacy);
          require(material(old).sameVariant(legacy), "legacy variant must stay on the saved part");
          require(MaterialVariant.of(legacy).get().getIdentifier().getId().equals(legacy.getId()), "legacy variant must resolve to its backing vine");
          require(MaterialRegistry.getInstance().getMaterialStats(legacy.getMaterialId(), TinkerToolParts.maille.get().getStatType()).isPresent(), "legacy maille must keep stats");
          ItemStack cleaned = cast(false, old, TinkerFluids.venom.get());
          require(material(cleaned).sameVariant(MaterialIds.leather), "legacy cleaning must return leather");
          ItemStack skin = cast(false, cleaned, (Fluid) entry[1]);
          require(material(skin).sameVariant((MaterialVariantId) entry[2]), "cleaned legacy part must accept the standalone skin");
        }
      });
      test("slimeskull_build_skin_swap_and_repair", () -> {
        // build: skeleton skull cast consumed in a basin with earth slime, extra material bone
        ItemStack helmet = cast(true, new ItemStack(Items.SKELETON_SKULL), TinkerFluids.earthSlime.get());
        require(helmet.is(TinkerTools.slimesuit.get(ArmorType.HELMET)), "skull casting must build a slime helmet");
        requireMaterials(helmet, MaterialIds.bone, MaterialIds.earthslime);
        // skin swaps through the loaded basin recipes keep the skull and replace the slime
        for (var entry : List.of(new Object[]{TinkerFluids.skySlime.get(), MaterialIds.skyslime}, new Object[]{TinkerFluids.enderSlime.get(), MaterialIds.enderslime},
                                 new Object[]{TinkerFluids.venom.get(), MaterialIds.venom})) {
          helmet = cast(true, helmet, (Fluid) entry[0]);
          requireMaterials(helmet, MaterialIds.bone, (MaterialVariantId) entry[1]);
        }
        require(ToolStack.from(helmet).getModifiers().getLevel(ModifierIds.magicProtection) >= 1, "venom skin must grant its magic protection trait");
        // the restored official recipe on its own matches and swaps index 1
        var swap = recipe("tools/armor/slime_skull/swapping/slime", PartSwapCastingRecipe.class);
        ItemStack venomSkull = helmet.copy();
        var container = new FixedCastingContainer(venomSkull, TinkerFluids.skySlime.get());
        require(swap.matches(container, level), "slime_skull/swapping/slime must match a slime helmet and sky slime");
        requireMaterials(swap.assemble(container), MaterialIds.bone, MaterialIds.skyslime);
        // repair: damage the venom skull and repair it with spider eyes in a tinker station
        ToolStack damaged = ToolStack.from(helmet.copy());
        damaged.setDamage(damaged.getStats().getInt(slimeknights.tconstruct.library.tools.stat.ToolStats.DURABILITY) / 2);
        int before = damaged.getDamage();
        var repair = recipe("tables/tinker_station_repair", ITinkerStationRecipe.class);
        var inv = station(damaged, new ItemStack(Items.SPIDER_EYE, 16));
        require(repair.matches(inv, level), "spider eyes must repair a venom slimeskull");
        var result = repair.getValidatedResult(inv, level.registryAccess());
        require(result.isSuccess(), "repair must validate");
        int after = result.getResult().getTool().getDamage();
        require(after < before, "repair must reduce damage: " + before + " -> " + after);
        requireMaterials(result.getResult().getTool().createStack(), MaterialIds.bone, MaterialIds.venom);
        repair.updateInputs(result.getResult(), inv, true);
        require(inv.getInput(0).getCount() < 16 && inv.getInput(0).getCount() >= 0, "repair must consume spider eyes");
        // vanilla repairs (mending, grindstone style resets) go through ItemStack.setDamageValue and the item's setDamage
        ItemStack vanilla = result.getResult().getTool().createStack();
        vanilla.setDamageValue(7);
        require(ToolStack.from(vanilla).getDamage() == 7 && vanilla.getDamageValue() == 7, "setDamageValue must persist on slimesuit armor, got " + ToolStack.from(vanilla).getDamage());
      });
      test("slimecage_skin_swap_keeps_ribcage", () -> {
        ItemStack chest = tool(TinkerTools.slimesuit.get(ArmorType.CHESTPLATE), MaterialIds.bone, MaterialIds.earthslime).createStack();
        for (var entry : List.of(new Object[]{TinkerFluids.skySlime.get(), MaterialIds.skyslime}, new Object[]{TinkerFluids.venom.get(), MaterialIds.venom})) {
          chest = cast(true, chest, (Fluid) entry[0]);
          requireMaterials(chest, MaterialIds.bone, (MaterialVariantId) entry[1]);
        }
        require(chest.is(TinkerTools.slimesuit.get(ArmorType.CHESTPLATE)), "swapping must keep the item");
      });
      source.sendSuccess(() -> Component.literal("AEBM_MATERIALS_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    /** Runs the loaded part builder recipe for the given part with a real material recipe lookup. */
    private ItemStack partBuilder(Item part, ItemStack materialStack) {
      MaterialRecipe material = MaterialRecipeCache.findRecipe(materialStack);
      require(material != MaterialRecipe.EMPTY, "no material recipe for " + materialStack);
      var inv = new PartInput(materialStack, new ItemStack(TinkerTables.pattern.get()), material);
      for (RecipeHolder<?> holder : level.getServer().getRecipeManager().recipeMap().byType(TinkerRecipeTypes.PART_BUILDER.get())) {
        if (holder.value() instanceof PartRecipe recipe && recipe.getRecipeOutput(MaterialIds.leather, 1).is(part) && recipe.partialMatch(inv) && recipe.matches(inv, level)) {
          return recipe.assemble(inv);
        }
      }
      throw new AssertionError("no loaded part builder recipe for " + part);
    }

    /** Fills a real casting table or basin with enough fluid and ticks it until the output appears. */
    private ItemStack cast(boolean basin, ItemStack input, Fluid fluid) {
      BlockState state = basin ? TinkerSmeltery.searedBasin.get().defaultBlockState() : TinkerSmeltery.searedTable.get().defaultBlockState();
      CastingBlockEntity casting = basin ? new CastingBlockEntity.Basin(position, state) : new CastingBlockEntity.Table(position, state);
      casting.setItem(CastingBlockEntity.INPUT, input.copy());
      casting.setLevel(level);
      int accepted = casting.getTank().fill(new FluidStack(fluid, 8000), FluidActions.EXECUTE);
      require(accepted > 0, "no loaded casting recipe accepted " + fluid + " on " + input);
      require(accepted == casting.getTank().getCapacity(), "tank must hold exactly the recipe amount");
      for (int tick = 0; tick < 2000 && casting.getItem(CastingBlockEntity.OUTPUT).isEmpty(); tick++) {
        CastingBlockEntity.SERVER_TICKER.tick(level, position, casting.getBlockState(), casting);
      }
      ItemStack output = casting.getItem(CastingBlockEntity.OUTPUT);
      require(!output.isEmpty() && output.getCount() == 1, "casting must finish with one output");
      require(casting.getTank().isEmpty(), "casting must consume the fluid");
      require(casting.getItem(CastingBlockEntity.INPUT).isEmpty() || !casting.getItem(CastingBlockEntity.INPUT).is(output.getItem()), "the cast input must be used up, not duplicated");
      return output.copy();
    }

    private <T> T recipe(String path, Class<T> type) {
      var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("tconstruct", path));
      var value = level.getServer().getRecipeManager().byKey(key).orElseThrow(() -> new AssertionError("recipe not loaded: " + path)).value();
      require(type.isInstance(value), path + " has unexpected type " + value.getClass().getName());
      return type.cast(value);
    }

    private TinkerStationContainerWrapper station(ToolStack tool, ItemStack... inputs) {
      var station = new TinkerStationBlockEntity(position, TinkerTables.tinkerStation.get().defaultBlockState(), 5);
      station.setLevel(level);
      station.setItem(TinkerStationBlockEntity.TINKER_SLOT, tool.createStack());
      for (int i = 0; i < inputs.length; i++) station.setItem(TinkerStationBlockEntity.INPUT_SLOT + i, inputs[i]);
      var wrapper = new TinkerStationContainerWrapper(station);
      wrapper.setPlayer(player);
      return wrapper;
    }

    private void test(String name, CheckedRunnable test) {
      try {
        test.run(); passed++;
        source.sendSuccess(() -> Component.literal("AEBM_MATERIALS_PASS " + name), false);
      } catch (Exception | AssertionError failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_MATERIALS_FAIL " + name + " " + failure));
      }
    }
  }

  /** Part builder inventory holding a material stack, a blank pattern and the looked up material value. */
  private record PartInput(ItemStack stack, ItemStack pattern, IMaterialValue material) implements IPartBuilderContainer {
    @Override public ItemStack getStack() { return stack; }
    @Override public ItemStack getPatternStack() { return pattern; }
    @Nullable @Override public IMaterialValue getMaterial() { return material; }
  }

  /** Casting inventory for calling a single loaded casting recipe directly. */
  private record FixedCastingContainer(ItemStack stack, Fluid fluid) implements ICastingContainer {
    @Override public ItemStack getStack() { return stack; }
    @Override public Fluid getFluid() { return fluid; }
  }

  private static MaterialVariantId material(ItemStack stack) {
    return IMaterialItem.getMaterialFromStack(stack);
  }

  private static void requireMaterials(ItemStack stack, MaterialVariantId first, MaterialVariantId second) {
    MaterialNBT materials = ToolStack.from(stack).getMaterials();
    require(materials.size() == 2, "expected two materials, got " + materials);
    require(materials.get(0).sameVariant(first) && materials.get(1).sameVariant(second), "expected [" + first + ", " + second + "], got " + materials);
  }

  private static ToolStack tool(Item item, MaterialVariantId... materials) {
    var variants = java.util.Arrays.stream(materials).map(MaterialVariant::of).toList();
    return ToolStack.createTool(item, IModifiable.getToolDefinition(item), new MaterialNBT(variants));
  }

  private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
  @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }
}
