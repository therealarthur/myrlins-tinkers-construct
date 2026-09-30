package aebm.continuumtests;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.library.fluid.FluidActions;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;
import slimeknights.tconstruct.library.recipe.partbuilder.IPartBuilderRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.shared.TinkerAttributes;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.entity.HeaterBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.MultitankFuelModule;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity;
import slimeknights.tconstruct.tables.menu.PartBuilderContainerMenu;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.entity.ThrownTool;

/**
 * Upstream sync fixture (release arthur.9, 2026-09-29): headless checks for the upstream Continuum Construct 3.12.3 and
 * 3.12.4 fixes merged into the parity port, and for the oracle groups closed with that merge.
 *
 * <p>Command {@code aebm_continuum_upstream_sync}, run with {@code execute positioned} in an already loaded, empty
 * 7x7x7 region (origin offsets -1..5). The fuel and part builder cases place real blocks (two seared fuel tanks, a
 * part builder and a vanilla chest) inside that region and remove them again; item entities spawned there while a
 * case runs are canceled and counted as a failure. Expected output: seven {@code AEBM_UPSTREAM_SYNC_PASS} lines and
 * {@code AEBM_UPSTREAM_SYNC_SUMMARY passed=7 failed=0}.
 */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class UpstreamSyncServerFixture {
  /** Region of the running suite, used to cancel stray item drops. Server thread only. */
  private static AABB activeRegion;
  private static int unexpectedDrops;

  private UpstreamSyncServerFixture() {}

  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebm_continuum_upstream_sync")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  @SubscribeEvent
  public static void captureDrops(EntityJoinLevelEvent event) {
    AABB region = activeRegion;
    if (region != null && event.getEntity() instanceof ItemEntity item && region.intersects(item.getBoundingBox())) {
      unexpectedDrops++;
      event.setCanceled(true);
    }
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
      try {
        preflight();
      } catch (Throwable failure) {
        source.sendFailure(Component.literal("AEBM_UPSTREAM_SYNC_REFUSED " + failure.getMessage()));
        return 0;
      }
      activeRegion = new AABB(origin.getX() - 1, origin.getY() - 1, origin.getZ() - 1, origin.getX() + 6, origin.getY() + 6, origin.getZ() + 6);
      unexpectedDrops = 0;
      try {
        // upstream 3.12.4 (40a872af): liquid fuels were wiped by the shared fallback recipe id during world load
        test("liquid_fuels_survive_world_load", () -> {
          MeltingFuel lava = MeltingFuelLookup.findFuel(Fluids.LAVA);
          MeltingFuel blazing = MeltingFuelLookup.findFuel(TinkerFluids.blazingBlood.get());
          require(lava != null, "lava must be a loaded liquid fuel");
          require(blazing != null, "blazing blood must be a loaded liquid fuel");
          List<MeltingFuel> all = MeltingFuelLookup.getAll();
          require(all.contains(lava) && all.contains(blazing), "both liquid fuels must stay registered together, got " + all.size());
          require(blazing.getTemperature() > lava.getTemperature(), "blazing blood must be the hotter fuel");
        });
        // upstream 3.12.4 (40a872af): a smeltery keeps heating from a second fuel tank once the first is empty
        test("second_fuel_tank_heats_after_first_empties", () -> {
          MeltingFuel lava = MeltingFuelLookup.findFuel(Fluids.LAVA);
          require(lava != null, "lava fuel must be loaded");
          int cost = lava.getAmount(Fluids.LAVA);
          require(cost > 0, "lava fuel must cost fluid");
          BlockPos first = origin.offset(1, 0, 1);
          BlockPos second = origin.offset(3, 0, 1);
          try {
            TankBlockEntity firstTank = tank(first, cost);
            TankBlockEntity secondTank = tank(second, 1000);
            HeaterBlockEntity parent = new HeaterBlockEntity(origin.offset(2, 2, 3), TinkerSmeltery.searedHeater.get().defaultBlockState());
            parent.setLevel(level);
            MultitankFuelModule module = new MultitankFuelModule(parent, () -> List.of(first, second));
            require(module.findFuel(true) == lava.getTemperature(), "first tank must fuel the structure");
            require(firstTank.getTank().getFluidAmount() == 0, "first tank must be spent, has " + firstTank.getTank().getFluidAmount());
            require(secondTank.getTank().getFluidAmount() == 1000, "second tank must be untouched while the first had fuel");
            require(module.findFuel(true) == lava.getTemperature(), "the second tank must keep the structure heated once the first is empty");
            require(secondTank.getTank().getFluidAmount() == 1000 - cost, "second tank must pay one fuel cost, has " + secondTank.getTank().getFluidAmount());
            require(module.findFuel(false) == lava.getTemperature(), "a preview must still find the second tank");
            require(secondTank.getTank().getFluidAmount() == 1000 - cost, "a preview must not drain");
          } finally {
            clear(first);
            clear(second);
          }
        });
        // upstream 3.12.4 (50fe91ba): shift-clicking the part builder result beside a chest froze the server
        test("part_builder_shift_click_beside_chest_crafts_all", () -> {
          BlockPos table = origin.offset(1, 0, 3);
          BlockPos chest = table.east();
          FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AEBMUpstreamSync"));
          player.setPos(table.getX() + 0.5, table.getY() + 1, table.getZ() + 0.5);
          try {
            level.setBlock(table, TinkerTables.partBuilder.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            require(level.getBlockEntity(table) instanceof PartBuilderBlockEntity, "part builder block entity must exist");
            PartBuilderBlockEntity builder = (PartBuilderBlockEntity) level.getBlockEntity(table);
            builder.setItem(PartBuilderBlockEntity.PATTERN_SLOT, new ItemStack(TinkerTables.pattern.get(), 8));
            builder.setItem(PartBuilderBlockEntity.MATERIAL_SLOT, new ItemStack(Items.OAK_PLANKS, 3));
            PartBuilderContainerMenu menu = new PartBuilderContainerMenu(1, player.getInventory(), builder);
            require(!menu.subContainers.isEmpty(), "the chest beside the part builder must be a side inventory");
            List<Pattern> buttons = builder.getSortedButtons();
            int index = -1;
            for (int i = 0; i < buttons.size(); i++) {
              if ("tool_handle".equals(buttons.get(i).getPath())) {
                index = i;
              }
            }
            require(index >= 0, "oak planks must offer the tool handle pattern, got " + buttons);
            builder.selectRecipe(index);
            IPartBuilderRecipe recipe = builder.getPartRecipe();
            require(recipe != null && recipe.getCost() == 1, "tool handle must cost one wood unit");
            require(menu.getOutputSlot().getItem().is(TinkerToolParts.toolHandle.get()), "result slot must show a tool handle");
            menu.clicked(menu.getOutputSlot().index, 0, ContainerInput.QUICK_MOVE, player);
            int handles = count(player.getInventory(), TinkerToolParts.toolHandle.get()) + count((Container) level.getBlockEntity(chest), TinkerToolParts.toolHandle.get());
            require(handles == 3, "one shift-click must craft every handle the three planks pay for, got " + handles);
            require(builder.getItem(PartBuilderBlockEntity.MATERIAL_SLOT).isEmpty(), "all three planks must be used");
            require(builder.getItem(PartBuilderBlockEntity.PATTERN_SLOT).getCount() == 5, "exactly three patterns must be used, left "
              + builder.getItem(PartBuilderBlockEntity.PATTERN_SLOT).getCount());
            require(menu.getOutputSlot().getItem().isEmpty(), "no ghost result may stay once the material is gone");
          } finally {
            if (level.getBlockEntity(table) instanceof PartBuilderBlockEntity builder) {
              builder.setItem(PartBuilderBlockEntity.PATTERN_SLOT, ItemStack.EMPTY);
              builder.setItem(PartBuilderBlockEntity.MATERIAL_SLOT, ItemStack.EMPTY);
            }
            if (level.getBlockEntity(chest) instanceof Container container) {
              container.clearContent();
            }
            clear(chest);
            clear(table);
          }
        });
        // official 3.12.1 (kept over upstream's squared curve): Returning pulls like a loyalty trident, linear in the level
        test("returning_speed_scales_linearly_like_official", () -> {
          FakePlayer owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AEBMUpstreamReturn"));
          owner.setPos(origin.getX(), level.getMaxY() + 32, origin.getZ());
          double one = returnPull(owner, 1);
          double four = returnPull(owner, 4);
          require(one > 0 && four > 0, "both levels must pull toward the owner, got " + one + " and " + four);
          double ratio = four / one;
          require(ratio > 3.9 && ratio < 4.1, "Returning IV must pull 4x as hard as Returning I (vanilla loyalty), ratio " + ratio);
        });
        // upstream 3.12.4 (d15ff32c): negative incoming damage crashed the server (DamageContainer rejects negatives)
        test("negative_incoming_damage_is_clamped", () -> {
          Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
          require(zombie != null, "zombie must be constructible");
          zombie.setPos(origin.getX(), level.getMaxY() + 48, origin.getZ());
          DamageContainer container = new DamageContainer(damageSource("minecraft:generic"), -2f);
          NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(zombie, container));
          require(container.getNewDamage() == 0f, "negative damage must be clamped to zero, got " + container.getNewDamage());
        });
        // oracle group closed with arthur.9: chrysophilite and gold guard use the official 3.12.1 JSON form
        test("golden_skull_traits_use_official_json_form", () -> {
          checkGolden(ModifierIds.chrysophilite, MaterialIds.gold, "+1 ", "+2 ");
          checkGolden(ModifierIds.goldGuard, MaterialIds.roseGold, "+4 ", "+8 ");
          // attribute at level 2: the skull now counts as gold itself, so chrysophilite is 1 + 1
          ToolStack skull = helmet(MaterialIds.gold);
          skull.addModifier(ModifierIds.chrysophilite, 1);
          Zombie wearer = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
          require(wearer != null, "zombie must be constructible");
          wearer.setPos(origin.getX(), level.getMaxY() + 56, origin.getZ());
          equip(wearer, EquipmentSlot.HEAD, skull.createStack());
          AttributeInstance chrysophilite = wearer.getAttribute(TinkerAttributes.CHRYSOPHILITE);
          require(chrysophilite != null && chrysophilite.getValue() == 2, "level 2 chrysophilite skull alone must give 2, got "
            + (chrysophilite == null ? "no attribute" : chrysophilite.getValue()));
          equip(wearer, EquipmentSlot.HEAD, ItemStack.EMPTY);
          require(chrysophilite.getValue() == 0, "removing the skull must remove chrysophilite, got " + chrysophilite.getValue());
        });
        // oracle group closed with arthur.9: the skeleton skull and venom basin cast costs the official 1250 mB
        test("skull_venom_cast_costs_official_amount", () -> {
          var recipes = level.getServer().getRecipeManager();
          var id = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("tconstruct", "smeltery/casting/slime/venom/skull"));
          var holder = recipes.byKey(id);
          require(holder.isPresent(), "venom skull casting must be loaded");
          var recipe = holder.get().value();
          require(recipe instanceof slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe, "venom skull must be an item casting recipe");
          var casting = (slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe) recipe;
          int amount = casting.getFluids().stream().mapToInt(FluidStack::getAmount).max().orElse(0);
          require(amount == 1250, "skull venom amount must be the official 1250 mB, got " + amount);
          require(casting.getCoolingTime() == 107, "skull venom cooling must be the official 107 ticks, got " + casting.getCoolingTime());
        });
        require(unexpectedDrops == 0, "stray item drops in the fixture region: " + unexpectedDrops);
      } catch (Throwable failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_UPSTREAM_SYNC_FAIL cleanup " + failure));
      } finally {
        activeRegion = null;
      }
      source.sendSuccess(() -> Component.literal("AEBM_UPSTREAM_SYNC_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    /** Checks level display, the level 2 piglin neutral flag and the flat tooltip of a golden skull trait. */
    private void checkGolden(ModifierId id, MaterialVariantId skullMaterial, String levelOneTooltip, String levelTwoTooltip) {
      Modifier modifier = ModifierManager.getValue(id);
      require(!modifier.getClass().getName().contains(".traits.skull."), id + " must load from JSON, not the older Java modifier: " + modifier.getClass().getName());
      require(!modifier.getDisplayName(2).getString().equals(modifier.getDisplayName(1).getString()), id + " must show its level (official single_level display)");
      ToolStack skull = helmet(skullMaterial);
      require(skull.getModifiers().getLevel(id) == 1, id + " must be the level 1 skull trait");
      require(!ModifierUtil.checkVolatileFlag(skull.createStack(), ModifiableArmorItem.PIGLIN_NEUTRAL), id + " level 1 skull must not make piglins neutral");
      require(tooltip(skull, id).startsWith(levelOneTooltip), id + " level 1 tooltip must start with '" + levelOneTooltip + "', got '" + tooltip(skull, id) + "'");
      skull.addModifier(id, 1);
      require(skull.getModifiers().getLevel(id) == 2, id + " must reach level 2");
      require(ModifierUtil.checkVolatileFlag(skull.createStack(), ModifiableArmorItem.PIGLIN_NEUTRAL), id + " level 2 must set tconstruct:piglin_neutral");
      require(tooltip(skull, id).startsWith(levelTwoTooltip), id + " level 2 tooltip must start with '" + levelTwoTooltip + "', got '" + tooltip(skull, id) + "'");
    }

    private String tooltip(ToolStack tool, ModifierId id) {
      ModifierEntry entry = tool.getModifiers().getEntry(id);
      require(entry != null, "modifier entry missing: " + id);
      List<Component> lines = new ArrayList<>();
      entry.getHook(ModifierHooks.TOOLTIP).addTooltip(tool, entry, null, lines, TooltipKey.NORMAL, TooltipFlag.NORMAL);
      require(lines.size() == 1, id + " must add exactly one tooltip line, got " + lines.size());
      return lines.get(0).getString();
    }

    /** Velocity toward the owner after one return tick from rest, for a grounded javelin with the given Returning level. */
    private double returnPull(FakePlayer owner, int returning) throws Exception {
      ItemStack stack = ToolBuildHandler.createSingleMaterial(TinkerTools.javelin.get(), MaterialVariant.of(MaterialIds.iron, ""));
      ToolStack tool = ToolStack.copyFrom(stack);
      tool.addModifier(ModifierIds.returning, returning);
      ThrownTool thrown = new ThrownTool(level, owner, tool.createStack(), 1, 1, 0.6f);
      thrown.setPos(owner.getX() + 12, owner.getEyeY(), owner.getZ());
      thrown.setDeltaMovement(Vec3.ZERO);
      Field inGround = AbstractArrow.class.getDeclaredField("inGroundTime");
      inGround.setAccessible(true);
      inGround.setInt(thrown, 5);
      thrown.tick();
      require(thrown.isNoPhysics(), "Returning " + returning + " must start returning after the ground delay");
      // the owner is 12 blocks toward -x at eye height, so the pull is the -x velocity
      return -thrown.getDeltaMovement().x;
    }

    private TankBlockEntity tank(BlockPos pos, int lava) {
      level.setBlock(pos, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState(), Block.UPDATE_ALL);
      require(level.getBlockEntity(pos) instanceof TankBlockEntity, "seared fuel tank block entity must exist at " + pos);
      TankBlockEntity tank = (TankBlockEntity) level.getBlockEntity(pos);
      int filled = tank.getTank().fill(new FluidStack(Fluids.LAVA, lava), FluidActions.EXECUTE);
      require(filled == lava, "tank must take " + lava + " mB of lava, took " + filled);
      return tank;
    }

    /** Removes a fixture block without drops; tanks are drained first. */
    private void clear(BlockPos pos) {
      if (level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
        tank.getTank().drain(Integer.MAX_VALUE, FluidActions.EXECUTE);
      }
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void preflight() {
      for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(5, 5, 5))) {
        require(!level.isOutsideBuildHeight(pos), "fixture outside build height: " + pos);
        require(level.hasChunkAt(pos), "fixture requires already loaded chunks: " + pos);
        require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "fixture refuses occupied region: " + pos);
      }
      AABB region = new AABB(origin.getX() - 1, origin.getY() - 1, origin.getZ() - 1, origin.getX() + 6, origin.getY() + 6, origin.getZ() + 6);
      require(level.getEntitiesOfClass(Entity.class, region).stream().allMatch(Entity::isRemoved),
        "fixture refuses a region containing existing entities or players");
    }

    private DamageSource damageSource(String id) {
      ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.parse(id));
      Holder<DamageType> holder = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(key)
        .orElseThrow(() -> new AssertionError("damage type not loaded: " + id));
      return new DamageSource(holder);
    }

    private void test(String name, CheckedRunnable check) {
      try {
        check.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_UPSTREAM_SYNC_PASS " + name), false);
      } catch (Throwable failure) {
        failed++;
        Throwable cause = failure;
        while (cause.getCause() != null) {
          cause = cause.getCause();
        }
        source.sendFailure(Component.literal("AEBM_UPSTREAM_SYNC_FAIL " + name + ": " + cause));
      }
    }
  }

  /** Slime helmet with the given skull material and an earth slime skin. */
  private static ToolStack helmet(MaterialVariantId skull) {
    Item item = TinkerTools.slimesuit.get(ArmorType.HELMET);
    var variants = java.util.stream.Stream.of(skull, MaterialIds.earthslime).map(MaterialVariant::of).toList();
    return ToolStack.createTool(item, IModifiable.getToolDefinition(item), new MaterialNBT(variants));
  }

  /** Puts a stack in a slot and posts the real equipment change event, as LivingEntity.detectEquipmentUpdates does. */
  private static void equip(LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
    ItemStack previous = entity.getItemBySlot(slot).copy();
    entity.setItemSlot(slot, stack);
    NeoForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(entity, slot, previous, entity.getItemBySlot(slot)));
  }

  private static int count(Container container, Item item) {
    int total = 0;
    for (int slot = 0; slot < container.getContainerSize(); slot++) {
      ItemStack stack = container.getItem(slot);
      if (stack.is(item)) {
        total += stack.getCount();
      }
    }
    return total;
  }

  private static int count(Inventory inventory, Item item) {
    return count((Container) inventory, item);
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
