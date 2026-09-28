package slimeknights.tconstruct.library.client.model.tools;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import slimeknights.tconstruct.library.client.modifiers.DyedModifierModel;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelMap;
import slimeknights.tconstruct.library.client.modifiers.model.FluidModifierModel;
import slimeknights.tconstruct.library.fluid.FluidStackNbt;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

import static org.junit.jupiter.api.Assertions.*;

/** Requires client classes, but never constructs a window, texture, renderer, or Minecraft instance. */
@EnabledIf(value = "clientDistribution", disabledReason = "Item model APIs require a client-distribution test launch.")
final class ToolModelGeometryTest {
  static boolean clientDistribution() {
    return FMLEnvironment.getDist() == Dist.CLIENT;
  }

  @Test
  void actualDyeAndConstantFluidModelKeysChangeWithPersistentData() {
    var data = new ModDataNBT();
    var tool = (IToolStackView)Proxy.newProxyInstance(IToolStackView.class.getClassLoader(), new Class<?>[] {IToolStackView.class}, (proxy, method, args) -> {
      if (method.getName().equals("getPersistentData")) {
        return data;
      }
      throw new AssertionError("Unexpected tool query: " + method);
    });
    var dye = new ModifierId("tconstruct:test_dye");
    var entry = new ModifierEntry(dye, 1);
    var material = new Material(Identifier.parse("tconstruct:test_mask"));
    var models = ModifierModelMap.create(
      Map.of("fluid", new FluidModifierModel(material, null, ToolTankHelper.TANK_HELPER)),
      Map.of(dye, new DyedModifierModel(material, null)));
    data.putInt(dye.getId(), 0x00FF00);
    var greenEmpty = ToolItemModel.modifierCacheKeys(models, tool, List.of(entry), Set.of());
    data.putInt(dye.getId(), 0xFF0000);
    var redEmpty = ToolItemModel.modifierCacheKeys(models, tool, List.of(entry), Set.of());
    assertNotEquals(greenEmpty, redEmpty, "same modifier level with different dye needs distinct geometry");
    data.put(ToolTankHelper.TANK_HELPER.getFluidKey(), FluidStackNbt.write(new FluidStack(Fluids.WATER, 90)));
    var water = ToolItemModel.modifierCacheKeys(models, tool, List.of(entry), Set.of());
    assertNotEquals(redEmpty, water, "constant overlays must participate even without an installed modifier entry");
    data.put(ToolTankHelper.TANK_HELPER.getFluidKey(), FluidStackNbt.write(new FluidStack(Fluids.LAVA, 90)));
    var lava = ToolItemModel.modifierCacheKeys(models, tool, List.of(entry), Set.of());
    assertNotEquals(water, lava, "fluid identity changes at equal amounts need distinct geometry");
    var hidden = ToolItemModel.modifierCacheKeys(models, tool, List.of(entry), Set.of(dye));
    assertFalse(hidden.containsKey("modifier:" + dye));
    assertTrue(hidden.containsKey("constant:fluid"));
    assertThrows(UnsupportedOperationException.class, () -> lava.put("mutated", "key"));
  }

  @Test
  void normalModelKeepsAllDirectionsAndGuiKeepsOnlyFrontWithLayerOrder() {
    // Material metadata is deliberately unused: this tests quad routing, not GPU material rendering.
    Collection<BakedQuad> bottom = Arrays.stream(Direction.values()).map(ToolModelGeometryTest::quad).toList();
    Collection<BakedQuad> top = List.of(quad(Direction.SOUTH));
    var normal = new QuadCollection.Builder();
    var gui = new QuadCollection.Builder();
    ToolItemModel.addLayers(normal, gui, List.of(top, bottom));
    var normalQuads = normal.build().getAll();
    assertEquals(7, normalQuads.size());
    assertEquals(bottom, normalQuads.subList(0, 6));
    assertEquals(top.iterator().next(), normalQuads.getLast());
    var guiQuads = gui.build().getAll();
    assertEquals(2, guiQuads.size());
    assertTrue(guiQuads.stream().allMatch(quad -> quad.direction() == Direction.SOUTH));
    assertSame(top.iterator().next(), guiQuads.getLast());
  }

  private static BakedQuad quad(Direction direction) {
    var point = new Vector3f();
    return new BakedQuad(point, point, point, point, 0, 0, 0, 0, direction, null);
  }
}
