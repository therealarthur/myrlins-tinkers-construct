package aebm.continuumtests;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.RegisterEvent;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.HeaterBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.SolidFuelModule;

/** Calls the production fuel method; captures and cancels only synchronous fixture item spawns. */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class SolidFuelServerFixture {
  private static final Identifier FUEL = Identifier.fromNamespaceAndPath("aebmcontinuumtests", "container_fuel");
  private static final ThreadLocal<List<ItemStack>> DROPS = new ThreadLocal<>();
  private SolidFuelServerFixture() {}

  @SubscribeEvent
  public static void registerItems(RegisterEvent event) {
    event.register(Registries.ITEM, FUEL, () -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, FUEL))) {
      @Override
      public int getBurnTime(ItemStack stack, RecipeType<?> recipeType, FuelValues fuelValues) { return 400; }

      @Override
      public ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        ItemStack remainder = new ItemStack(Items.BUCKET, 3);
        Component name = instance.get(DataComponents.CUSTOM_NAME);
        if (name != null) remainder.set(DataComponents.CUSTOM_NAME, name);
        return ItemStackTemplate.fromNonEmptyStack(remainder);
      }
    });
  }

  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmsolidfueltest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  @SubscribeEvent
  public static void captureDrop(EntityJoinLevelEvent event) {
    List<ItemStack> drops = DROPS.get();
    if (drops != null && event.getEntity() instanceof ItemEntity item) {
      drops.add(item.getItem().copy());
      event.setCanceled(true);
    }
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) { this.source = source; this.level = source.getLevel(); }

    private int run() {
      test("preview_does_not_consume", () -> {
        var handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET));
        var fixture = fixture();
        require(invoke(fixture.module, handler, false, fixture.drops) == MeltingFuelLookup.getSolid().getTemperature(), "preview must report loaded solid fuel temperature");
        require(handler.getStackInSlot(0).is(Items.LAVA_BUCKET) && fixture.module.getFuel() == 0 && fixture.drops.isEmpty(), "preview must preserve fuel and containers");
      });
      test("lava_bucket_returns_bucket", () -> {
        var handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET));
        var fixture = fixture();
        invoke(fixture.module, handler, true, fixture.drops);
        require(handler.getStackInSlot(0).is(Items.BUCKET) && handler.getStackInSlot(0).getCount() == 1, "one consumed lava bucket must return one bucket");
        require(fixture.module.getFuel() > 0 && fixture.drops.isEmpty(), "accepted bucket must not also drop");
      });
      test("heater_rejects_bucket_and_drops_once", () -> {
        var fixture = fixture();
        var handler = fixture.parent.getItemCapability();
        require(handler.insertItem(0, new ItemStack(Items.LAVA_BUCKET), false).isEmpty(), "real heater must accept fuel");
        invoke(fixture.module, handler, true, fixture.drops);
        require(handler.getStackInSlot(0).isEmpty(), "heater must consume its fuel");
        require(total(fixture.drops) == 1 && fixture.drops.getFirst().is(Items.BUCKET), "real heater must eject exactly one nonfuel bucket");
      });
      test("remainder_uses_extracted_components", () -> {
        var handler = new ItemStackHandler(1) {
          @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack actual = super.extractItem(slot, amount, simulate);
            if (!actual.isEmpty()) actual.set(DataComponents.CUSTOM_NAME, Component.literal("extracted component"));
            return actual;
          }
        };
        ItemStack fuel = fuel();
        fuel.set(DataComponents.CUSTOM_NAME, Component.literal("slot component"));
        handler.setStackInSlot(0, fuel);
        var fixture = fixture();
        invoke(fixture.module, handler, true, fixture.drops);
        ItemStack remainder = handler.getStackInSlot(0);
        require(remainder.is(Items.BUCKET) && remainder.getCount() == 3, "dynamic remainder must preserve its full count");
        require(Component.literal("extracted component").equals(remainder.get(DataComponents.CUSTOM_NAME)), "remainder must use the actual extracted stack's components");
        require(fixture.module.getFuel() == 100 && fixture.drops.isEmpty(), "fuel hook must supply exactly 400/4 ticks");
      });
      test("partial_insertion_drops_only_residual", () -> {
        var handler = new ItemStackHandler(1) {
          @Override public int getSlotLimit(int slot) { return 2; }
        };
        handler.setStackInSlot(0, fuel());
        var fixture = fixture();
        invoke(fixture.module, handler, true, fixture.drops);
        require(handler.getStackInSlot(0).is(Items.BUCKET) && handler.getStackInSlot(0).getCount() == 2, "two containers must fit");
        require(total(fixture.drops) == 1 && fixture.drops.getFirst().is(Items.BUCKET), "only the uninserted third container may drop");
        require(handler.getStackInSlot(0).getCount() + total(fixture.drops) == 3, "partial insertion must conserve remainder count");
      });
      test("rejected_extraction_creates_nothing", () -> {
        var handler = new ItemStackHandler(1) {
          @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        };
        handler.setStackInSlot(0, fuel());
        var fixture = fixture();
        invoke(fixture.module, handler, true, fixture.drops);
        require(handler.getStackInSlot(0).is(fuelItem()) && fixture.module.getFuel() == 0 && fixture.drops.isEmpty(), "failed extraction must not grant fuel or a remainder");
      });
      source.sendSuccess(() -> Component.literal("AEBM_SOLID_FUEL_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private Fixture fixture() {
      BlockPos pos = BlockPos.containing(source.getPosition().x, level.getMaxY() + 32, source.getPosition().z);
      HeaterBlockEntity parent = new HeaterBlockEntity(pos, TinkerSmeltery.searedHeater.get().defaultBlockState());
      parent.setLevel(level);
      return new Fixture(parent, new SolidFuelModule(parent, pos), new ArrayList<>());
    }

    private void test(String name, CheckedRunnable runnable) {
      try {
        runnable.run(); passed++;
        source.sendSuccess(() -> Component.literal("AEBM_SOLID_FUEL_PASS " + name), false);
      } catch (Exception | AssertionError failure) {
        failed++;
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        source.sendFailure(Component.literal("AEBM_SOLID_FUEL_FAIL " + name + " " + cause));
      }
    }
  }

  private record Fixture(HeaterBlockEntity parent, SolidFuelModule module, List<ItemStack> drops) {}

  private static int invoke(SolidFuelModule module, IItemHandler handler, boolean consume, List<ItemStack> drops) throws Exception {
    Method method = SolidFuelModule.class.getDeclaredMethod("trySolidFuel", IItemHandler.class, boolean.class);
    method.setAccessible(true);
    DROPS.set(drops);
    try { return (int)method.invoke(module, handler, consume); }
    finally { DROPS.remove(); }
  }
  private static Item fuelItem() { return BuiltInRegistries.ITEM.getValue(FUEL); }
  private static ItemStack fuel() { return new ItemStack(fuelItem()); }
  private static int total(List<ItemStack> drops) { return drops.stream().mapToInt(ItemStack::getCount).sum(); }
  private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
  @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }
}
