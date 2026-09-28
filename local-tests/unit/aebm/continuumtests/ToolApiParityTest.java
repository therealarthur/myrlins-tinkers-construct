package aebm.continuumtests;

import com.google.gson.JsonParser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.library.json.predicate.tool.ToolStackPredicate;
import slimeknights.tconstruct.library.json.variable.tool.ToolVariable;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.behavior.ToolDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.behavior.ToolDurabilityChangedHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.capacity.FluidAsCapacityModule;
import slimeknights.tconstruct.library.modifiers.modules.capacity.FluidPredicateAsCapacityModule;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolActionToolHook;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import static org.junit.jupiter.api.Assertions.*;

/** Original datapack IDs through registered loaders and real tool storage/damage code. */
final class ToolApiParityTest {
  private static final ToolTankHelper TANK = ToolTankHelper.TANK_HELPER;

  @BeforeAll
  static void components() { ComponentTestSetup.initialize(); }

  private static ToolStack tool() {
    CompoundTag data = new CompoundTag();
    data.put("tic_stats", StatsNBT.builder().set(ToolStats.DURABILITY, 100f)
      .set(ToolTankHelper.CAPACITY_STAT, 1000f).build().serializeToNBT());
    return ToolStack.from(Items.STICK, ToolDefinition.EMPTY, data);
  }

  @Test
  void fluidVariablesReadStoredFluidAndRespectFilters() {
    ToolStack tool = tool();
    ToolVariable amount = variable("fluid_amount", "minecraft:water");
    ToolVariable capacity = variable("tank_capacity", "minecraft:water");
    assertEquals(0, amount.getValue(tool));
    assertEquals(1000, capacity.getValue(tool));
    TANK.setFluid(tool, new FluidStack(Fluids.WATER, 425));
    assertEquals(425, amount.getValue(tool));
    assertEquals(1000, capacity.getValue(tool));
    TANK.setFluid(tool, new FluidStack(Fluids.LAVA, 300));
    assertEquals(0, amount.getValue(tool));
    assertEquals(0, capacity.getValue(tool));
    ToolVariable any = ToolVariable.LOADER.convert(JsonParser.parseString(
      "{\"type\":\"tconstruct:tank_capacity\"}"), "test", TypedMap.EMPTY);
    assertEquals(1000, any.getValue(tool));
    assertEquals(ToolVariable.LOADER.serialize(amount),
      ToolVariable.LOADER.serialize(ToolVariable.LOADER.convert(ToolVariable.LOADER.serialize(amount), "roundtrip", TypedMap.EMPTY)));
  }

  private static ToolVariable variable(String type, String fluid) {
    return ToolVariable.LOADER.convert(JsonParser.parseString(
      "{\"type\":\"tconstruct:" + type + "\",\"fluid\":{\"type\":\"mantle:set\",\"fluids\":[\"" + fluid + "\"]}}"), "test", TypedMap.EMPTY);
  }

  @Test
  void capacityModulesPreserveComponentsAndCannotReplaceForeignFluid() {
    ToolStack tool = tool();
    FluidAsCapacityModule fixed = (FluidAsCapacityModule) ModifierModule.LOADER.convert(JsonParser.parseString(
      "{\"type\":\"tconstruct:fluid_as_capacity\",\"fluid\":\"minecraft:water\"}"), "test", TypedMap.EMPTY);
    FluidPredicateAsCapacityModule drain = (FluidPredicateAsCapacityModule) ModifierModule.LOADER.convert(JsonParser.parseString(
      "{\"type\":\"tconstruct:fluid_predicate_as_capacity\",\"fluid\":{\"type\":\"mantle:set\",\"fluids\":[\"minecraft:water\"]}}"), "test", TypedMap.EMPTY);
    FluidStack water = new FluidStack(Fluids.WATER, 300);
    water.set(DataComponents.CUSTOM_NAME, Component.literal("component survives amount changes"));
    TANK.setFluid(tool, water);
    fixed.addAmount(tool, ModifierEntry.EMPTY, 150);
    assertEquals(450, TANK.getFluid(tool).getAmount());
    assertEquals(water.get(DataComponents.CUSTOM_NAME), TANK.getFluid(tool).get(DataComponents.CUSTOM_NAME));
    drain.setAmount(tool, ModifierEntry.EMPTY, 900);
    assertEquals(450, TANK.getFluid(tool).getAmount(), "predicate modules cannot create fluid");
    drain.setAmount(tool, ModifierEntry.EMPTY, 200);
    assertEquals(200, TANK.getFluid(tool).getAmount());
    assertEquals(water.get(DataComponents.CUSTOM_NAME), TANK.getFluid(tool).get(DataComponents.CUSTOM_NAME));
    fixed.setAmount(tool, ModifierEntry.EMPTY, 2000);
    assertEquals(1000, TANK.getFluid(tool).getAmount(), "real tank capacity clamps writes");
    fixed.removeAmount(tool, ModifierEntry.EMPTY, 2000);
    assertTrue(TANK.getFluid(tool).isEmpty());
    fixed.addAmount(tool, ModifierEntry.EMPTY, 75);
    assertEquals(75, TANK.getFluid(tool).getAmount());
    TANK.setFluid(tool, new FluidStack(Fluids.LAVA, 320));
    fixed.setAmount(tool, ModifierEntry.EMPTY, 100);
    fixed.removeAmount(tool, ModifierEntry.EMPTY, 320);
    drain.setAmount(tool, ModifierEntry.EMPTY, 0);
    assertEquals(Fluids.LAVA, TANK.getFluid(tool).getFluid());
    assertEquals(320, TANK.getFluid(tool).getAmount());
    assertEquals(0, fixed.getCapacity(tool, ModifierEntry.EMPTY));
    assertEquals(0, drain.getAmount(tool));
  }

  @Test
  void toolActionPredicateUsesActualDefinitionHookAndBrokenState() {
    ToolStack stored = tool();
    IToolStackView tool = view(stored, ModifierNBT.EMPTY, false);
    var predicate = ToolStackPredicate.LOADER.convert(JsonParser.parseString(
      "{\"type\":\"tconstruct:tool_action\",\"action\":\"shield_block\"}"), "test", TypedMap.EMPTY);
    assertTrue(predicate.matches(tool));
    var other = ToolStackPredicate.LOADER.convert(JsonParser.parseString(
      "{\"type\":\"tconstruct:tool_action\",\"action\":\"axe_strip\"}"), "test", TypedMap.EMPTY);
    assertFalse(other.matches(tool));
    stored.setDamage(100);
    assertFalse(predicate.matches(tool), "broken tools cannot expose actions");
  }

  @Test
  void durabilityObserversSeeActualClampedChangeAfterMutationAndEveryModuleRuns() {
    ToolStack stored = tool();
    List<String> observations = new ArrayList<>();
    ModuleHookMap hooks = ModuleHookMap.builder()
      .addHook(observer("first", observations), ModifierHooks.DURABILITY_CHANGED)
      .addHook(observer("second", observations), ModifierHooks.DURABILITY_CHANGED).build();
    IToolStackView tool = view(stored, modifiers(hooks), true);
    assertFalse(ToolDamageUtil.damage(tool, 25, null, null));
    assertEquals(List.of("first:damage:25:25", "second:damage:25:25"), observations);
    observations.clear();
    assertTrue(ToolDamageUtil.directDamage(tool, 200, null, null));
    assertEquals(List.of("first:damage:75:100", "second:damage:75:100"), observations);
    observations.clear();
    ToolDamageUtil.repair(tool, 500);
    assertEquals(List.of("first:repair:100:0", "second:repair:100:0"), observations);
    observations.clear();
    ToolDamageUtil.repair(tool, 5);
    ToolDamageUtil.directDamage(tool, 0, null, null);
    ToolDamageUtil.directDamage(tool, -5, null, null);
    assertTrue(observations.isEmpty(), "no notification without a durability change");
  }

  @Test
  void rejectedDamageDoesNotNotifyDurabilityObservers() {
    List<String> observations = new ArrayList<>();
    ToolDamageModifierHook cancel = (tool, modifier, amount, holder) -> 0;
    ModuleHookMap hooks = ModuleHookMap.builder()
      .addHook(cancel, ModifierHooks.TOOL_DAMAGE)
      .addHook(observer("cancelled", observations), ModifierHooks.DURABILITY_CHANGED).build();
    IToolStackView tool = view(tool(), modifiers(hooks), true);
    assertFalse(ToolDamageUtil.damage(tool, 25, null, null));
    assertEquals(0, tool.getDamage());
    assertTrue(observations.isEmpty());
  }

  private static ToolDurabilityChangedHook observer(String name, List<String> observations) {
    return new ToolDurabilityChangedHook() {
      @Override public void afterDamageTool(IToolStackView tool, ModifierEntry modifier, int amount, LivingEntity holder, ItemStack stack) {
        assertSame(ItemStack.EMPTY, stack);
        observations.add(name + ":damage:" + amount + ":" + tool.getDamage());
      }
      @Override public void afterRepairTool(IToolStackView tool, ModifierEntry modifier, int amount) {
        observations.add(name + ":repair:" + amount + ":" + tool.getDamage());
      }
    };
  }

  private static ModifierNBT modifiers(ModuleHookMap hooks) {
    Modifier modifier = new Modifier(hooks) {
      @Override public ModifierId getId() { return new ModifierId("aebm_test:durability_observer"); }
    };
    // Isolated supplied modifier, without claiming a datapack modifier registry was loaded.
    var bound = new slimeknights.tconstruct.library.modifiers.util.LazyModifier(modifier) {
      @Override public Modifier get() { return modifier; }
    };
    return new ModifierNBT(List.of(new ModifierEntry(bound, 1)));
  }

  /** Supply only registry-independent test hooks/tags; all state and mutations use the real ToolStack. */
  private static IToolStackView view(ToolStack stored, ModifierNBT modifiers, boolean durabilityTag) {
    return (IToolStackView) Proxy.newProxyInstance(IToolStackView.class.getClassLoader(), new Class<?>[]{IToolStackView.class},
      (proxy, method, args) -> {
        if (method.getName().equals("getModifiers")) return modifiers;
        if (method.getName().equals("getModifierList")) return modifiers.getModifiers();
        if (method.getName().equals("hasTag")) return durabilityTag;
        if (method.getName().equals("getHook") && args[0] == ToolHooks.TOOL_ACTION) {
          return (ToolActionToolHook) (tool, action) -> action == ItemAbility.get("shield_block");
        }
        try { return method.invoke(stored, args); }
        catch (InvocationTargetException exception) { throw exception.getCause(); }
      });
  }
}
