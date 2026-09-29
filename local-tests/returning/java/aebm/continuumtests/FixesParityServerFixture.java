package aebm.continuumtests;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.gadgets.TinkerGadgets;
import slimeknights.tconstruct.gadgets.entity.FancyArmorStandEntity;
import slimeknights.tconstruct.library.recipe.entitymelting.EntityMeltingRecipe;
import slimeknights.tconstruct.library.recipe.entitymelting.EntityMeltingRecipeCache;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.EntityMeltingModule;
import slimeknights.tconstruct.smeltery.block.entity.multiblock.SmelteryMultiblock;

/**
 * Fixes-stream parity checks (2026-09-29) for the data and behavior restored from official
 * Tinkers 3.12.1.231: the entity melting tags and their recipes, the melting blacklist check,
 * the fancy armor stand loot table and the grout kiln (blasting) recipes.
 *
 * <p>Command {@code aebm_continuum_fixes_parity}, run with {@code execute positioned} in an already
 * loaded, empty 7x7x7 region (origin offsets -1..5); only the giant melting case uses it. No blocks
 * are written; the one added entity is discarded before the command returns. Expected output:
 * six {@code AEBM_FIXES_PARITY_PASS} lines and {@code AEBM_FIXES_PARITY_SUMMARY passed=6 failed=0}.
 */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class FixesParityServerFixture {
  /** Official 3.12.1.231 generated tags/entity_types/meltable/*.json (skeleton adds #minecraft:skeletons at runtime). */
  private static final Map<TagKey<EntityType<?>>, List<String>> OFFICIAL_MELTABLE = new LinkedHashMap<>();
  static {
    OFFICIAL_MELTABLE.put(TinkerTags.EntityTypes.MELTABLE_FARM_ANIMALS, List.of("minecraft:chicken", "minecraft:rabbit", "minecraft:cow",
      "minecraft:mooshroom", "minecraft:pig", "minecraft:hoglin", "minecraft:sheep", "minecraft:goat", "minecraft:cod", "minecraft:salmon",
      "minecraft:tropical_fish"));
    OFFICIAL_MELTABLE.put(TinkerTags.EntityTypes.MELTABLE_ZOMBIE, List.of("minecraft:zombie", "minecraft:husk", "minecraft:zombie_horse"));
    OFFICIAL_MELTABLE.put(TinkerTags.EntityTypes.MELTABLE_DROWNED, List.of("minecraft:drowned"));
    OFFICIAL_MELTABLE.put(TinkerTags.EntityTypes.MELTABLE_SKELETON, List.of("minecraft:skeleton_horse"));
    OFFICIAL_MELTABLE.put(TinkerTags.EntityTypes.MELTABLE_ENDER, List.of("minecraft:enderman", "minecraft:endermite", "minecraft:ender_dragon"));
    OFFICIAL_MELTABLE.put(TinkerTags.EntityTypes.MELTABLE_SLIME, List.of("minecraft:slime"));
    OFFICIAL_MELTABLE.put(TinkerTags.EntityTypes.MELTABLE_MAGMA, List.of("minecraft:magma_cube"));
  }
  /** Recipe path under tconstruct:smeltery/entity_melting/ that must melt every member of each meltable tag. */
  private static final Map<TagKey<EntityType<?>>, String> TAG_RECIPES = new LinkedHashMap<>();
  static {
    TAG_RECIPES.put(TinkerTags.EntityTypes.MELTABLE_FARM_ANIMALS, "meat_soup");
    TAG_RECIPES.put(TinkerTags.EntityTypes.MELTABLE_ZOMBIE, "zombie");
    TAG_RECIPES.put(TinkerTags.EntityTypes.MELTABLE_DROWNED, "drowned");
    TAG_RECIPES.put(TinkerTags.EntityTypes.MELTABLE_SKELETON, "skeletons");
    TAG_RECIPES.put(TinkerTags.EntityTypes.MELTABLE_ENDER, "ender");
    TAG_RECIPES.put(TinkerTags.EntityTypes.MELTABLE_SLIME, "slime");
    TAG_RECIPES.put(TinkerTags.EntityTypes.MELTABLE_MAGMA, "magma_cube");
  }

  private FixesParityServerFixture() {}

  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebm_continuum_fixes_parity")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final BlockPos origin;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
      this.origin = BlockPos.containing(source.getPosition());
    }

    private int run() {
      test("meltable_tags_match_official_members", () -> {
        for (var entry : OFFICIAL_MELTABLE.entrySet()) {
          Set<String> expected = new TreeSet<>(entry.getValue());
          if (entry.getKey() == TinkerTags.EntityTypes.MELTABLE_SKELETON) {
            Set<String> skeletons = members(EntityTypeTags.SKELETONS);
            require(skeletons.containsAll(List.of("minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton")),
              "vanilla skeletons tag must be loaded: " + skeletons);
            expected.addAll(skeletons);
          }
          Set<String> actual = members(entry.getKey());
          require(actual.equals(expected), entry.getKey().location() + " expected " + expected + " but loaded " + actual);
        }
        require(members(TinkerTags.EntityTypes.MELTING_BLACKLIST).isEmpty(), "official melting/blacklist is empty");
      });
      test("hide_in_default_keeps_official_members", () -> {
        Set<String> hidden = members(TinkerTags.EntityTypes.MELTING_HIDE);
        require(hidden.contains("minecraft:giant"), "giant must stay hidden from the default display: " + hidden);
        require(hidden.containsAll(members(TinkerTags.EntityTypes.MELTING_BLACKLIST)), "hide_in_default must include the blacklist");
        require(members(TinkerTags.EntityTypes.MELTING_SHOW).equals(new TreeSet<>(List.of("minecraft:iron_golem", "minecraft:snow_golem",
          "minecraft:villager", "minecraft:player"))), "show_in_default must stay official");
      });
      test("tagged_entity_melting_recipes_cover_every_member", () -> {
        RecipeManager recipes = level.getServer().getRecipeManager();
        boolean milk = BuiltInRegistries.FLUID.containsKey(Identifier.fromNamespaceAndPath("minecraft", "milk"));
        for (var entry : TAG_RECIPES.entrySet()) {
          ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
            Identifier.fromNamespaceAndPath("tconstruct", "smeltery/entity_melting/" + entry.getValue()));
          var holder = recipes.byKey(key);
          if (entry.getKey() == TinkerTags.EntityTypes.MELTABLE_SKELETON && !milk) {
            // The skeleton recipe is conditional on NeoForge's optional milk fluid, as before this change.
            require(holder.isEmpty(), "skeleton recipe must stay unloaded while milk fluid is absent");
            continue;
          }
          require(holder.isPresent() && holder.get().value() instanceof EntityMeltingRecipe, "missing entity melting recipe " + key.identifier());
          EntityMeltingRecipe recipe = (EntityMeltingRecipe) holder.get().value();
          Set<String> members = members(entry.getKey());
          require(!members.isEmpty(), "tag must not be empty: " + entry.getKey().location());
          for (Holder<EntityType<?>> type : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(entry.getKey())) {
            require(recipe.matches(type.value()), key.identifier() + " must match tag member " + type.getRegisteredName());
            require(EntityMeltingRecipeCache.findRecipe(recipes, type.value()) == recipe,
              "runtime lookup for " + type.getRegisteredName() + " must resolve " + key.identifier());
          }
        }
      });
      test("hidden_only_giant_still_melts_like_official", () -> {
        preflight(level, origin);
        FixtureController controller = new FixtureController(level, origin);
        LivingEntity giant = EntityType.GIANT.create(level, EntitySpawnReason.COMMAND);
        require(giant != null, "giant must be constructible");
        try {
          require(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.GIANT).is(TinkerTags.EntityTypes.MELTING_HIDE)
            && !BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.GIANT).is(TinkerTags.EntityTypes.MELTING_BLACKLIST),
            "giant must be hidden but not blacklisted");
          giant.setPos(origin.getX() + 2.5, origin.getY() + 1, origin.getZ() + 2.5);
          giant.setNoGravity(true);
          giant.setSilent(true);
          if (giant instanceof Mob mob) mob.setNoAi(true);
          require(level.addFreshEntity(giant), "giant must enter the real server entity query");
          EntityMeltingRecipe recipe = EntityMeltingRecipeCache.findRecipe(level.getServer().getRecipeManager(), EntityType.GIANT);
          FluidStack expected = recipe == null ? EntityMeltingModule.getDefaultFluid() : recipe.getOutput(giant);
          float health = giant.getHealth();
          require(controller.interact(), "official module melts entities that are only hidden from the default display");
          require(giant.getHealth() < health, "accepted melting must damage the giant");
          require(controller.getTank().getContained() == expected.getAmount()
            && FluidStack.isSameFluidSameComponents(expected, controller.getTank().getFluidInTank(0)), "giant must yield the loaded output once");
        } finally {
          giant.discard();
        }
        require(giant.isRemoved(), "fixture giant must be discarded");
      });
      test("fancy_stand_loot_table_loaded_and_empty", () -> {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("tconstruct", "entities/armor_stand"));
        require(TinkerGadgets.armorStandEntity.get().getDefaultLootTable().equals(Optional.of(key)),
          "fancy stand must use the official loot table key, got " + TinkerGadgets.armorStandEntity.get().getDefaultLootTable());
        require(level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).get(key).isPresent(),
          "tconstruct:entities/armor_stand must be loaded from data");
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        require(table != LootTable.EMPTY, "loaded table must not be the missing-table fallback");
        FancyArmorStandEntity stand = new FancyArmorStandEntity(TinkerGadgets.armorStandEntity.get(), level);
        stand.setPos(origin.getX() + 0.5, level.getMaxY() + 32, origin.getZ() + 0.5);
        LootParams params = new LootParams.Builder(level)
          .withParameter(LootContextParams.THIS_ENTITY, stand)
          .withParameter(LootContextParams.ORIGIN, stand.position())
          .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
          .create(LootContextParamSets.ENTITY);
        List<ItemStack> rolled = table.getRandomItems(params);
        require(rolled.isEmpty(), "official table is empty; stand item and equipment come from the break hooks: " + rolled);
        require(stand instanceof ArmorStand, "fancy stand remains an armor stand");
      });
      test("grout_blasting_kiln_recipes_loaded", () -> {
        RecipeManager recipes = level.getServer().getRecipeManager();
        checkKiln(recipes, "smeltery/seared/seared_brick_kiln", new ItemStack(TinkerSmeltery.grout), TinkerSmeltery.searedBrick.asItem());
        checkKiln(recipes, "smeltery/scorched/scorched_brick_kiln", new ItemStack(TinkerSmeltery.netherGrout), TinkerSmeltery.scorchedBrick.asItem());
      });
      source.sendSuccess(() -> Component.literal("AEBM_FIXES_PARITY_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private void checkKiln(RecipeManager recipes, String path, ItemStack input, net.minecraft.world.item.Item output) {
      ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("tconstruct", path));
      var holder = recipes.byKey(key);
      require(holder.isPresent(), "missing restored kiln recipe " + path);
      require(holder.get().value().getType() == RecipeType.BLASTING, path + " must be a blasting recipe");
      AbstractCookingRecipe cooking = (AbstractCookingRecipe) holder.get().value();
      require(cooking.cookingTime() == 100, path + " must keep the official 100 tick time");
      require(cooking.matches(new SingleRecipeInput(input), level), path + " must accept its grout");
      require(cooking.assemble(new SingleRecipeInput(input)).is(output), path + " must produce its brick");
      Optional<RecipeHolder<net.minecraft.world.item.crafting.BlastingRecipe>> found = recipes.getRecipeFor(RecipeType.BLASTING, new SingleRecipeInput(input), level);
      require(found.isPresent() && found.get().id().equals(key), "blast furnace lookup must find " + path);
    }

    private void test(String name, CheckedRunnable body) {
      try {
        body.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_FIXES_PARITY_PASS " + name), false);
      } catch (Throwable failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_FIXES_PARITY_FAIL " + name + " " + failure));
        LogUtils.getLogger().error("AEBM_FIXES_PARITY_TRACE {}", name, failure);
      }
    }
  }

  private static Set<String> members(TagKey<EntityType<?>> tag) {
    Set<String> names = new TreeSet<>();
    for (Holder<EntityType<?>> holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(tag)) {
      names.add(holder.getRegisteredName());
    }
    return names;
  }

  /** Same detached controller shape as EntityMeltingServerFixture: real module, tank and fuel state. */
  private static final class FixtureController extends SmelteryBlockEntity {
    private FixtureController(ServerLevel level, BlockPos origin) {
      super(origin, TinkerSmeltery.smelteryController.get().defaultBlockState());
      setLevel(level);
      setStructure(new SmelteryMultiblock(this).createClient(origin, origin.offset(4, 4, 4), List.of()));
      CompoundTag fuel = new CompoundTag();
      fuel.putInt("fuel", 400);
      fuel.putInt("temperature", 1000);
      fuel.putInt("rate", 1);
      getFuelModule().readFromTag(fuel);
      require(getFuelModule().hasFuel(), "persisted real fuel state must load");
    }

    private boolean interact() { return entityModule.interactWithEntities(); }
  }

  private static void preflight(ServerLevel level, BlockPos origin) {
    for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(5, 5, 5))) {
      require(!level.isOutsideBuildHeight(pos), "fixture outside build height: " + pos);
      require(level.hasChunkAt(pos), "fixture requires already loaded chunks: " + pos);
      require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "fixture refuses occupied region: " + pos);
    }
    AABB region = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 5, origin.getY() + 5, origin.getZ() + 5).inflate(1);
    require(level.getEntitiesOfClass(Entity.class, region).stream().allMatch(Entity::isRemoved),
      "fixture refuses a region containing existing entities or players");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  @FunctionalInterface
  private interface CheckedRunnable { void run() throws Exception; }
}
