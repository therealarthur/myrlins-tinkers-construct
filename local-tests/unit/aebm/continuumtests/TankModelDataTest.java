package aebm.continuumtests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.client.model.ModelProperties;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.CastingTankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.FluidCannonBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.ITankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.AlloyerBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;

import static org.junit.jupiter.api.Assertions.*;

/** Actual provider snapshots and shipped model dispatch, without a level or graphics context. */
final class TankModelDataTest {
  @Test
  void controllerAndCastingProvidersExposeFluidCapacityAndIsolatedSnapshots() {
    List<BlockEntity> entities = List.of(
      new CastingTankBlockEntity(BlockPos.ZERO, TinkerSmeltery.searedCastingTank.get().defaultBlockState()),
      new MelterBlockEntity(BlockPos.ZERO, TinkerSmeltery.searedMelter.get().defaultBlockState()),
      new AlloyerBlockEntity(BlockPos.ZERO, TinkerSmeltery.scorchedAlloyer.get().defaultBlockState()));
    for (BlockEntity entity : entities) {
      var tank = ((ITankBlockEntity)entity).getTank();
      assertTrue(entity.getModelData().get(ModelProperties.FLUID_STACK).isEmpty());
      tank.setFluid(new FluidStack(Fluids.WATER, 500));
      var published = entity.getModelData();
      assertEquals(tank.getCapacity(), published.get(ModelProperties.TANK_CAPACITY));
      assertEquals(500, published.get(ModelProperties.FLUID_STACK).getAmount());
      tank.getFluid().setAmount(750);
      assertEquals(500, published.get(ModelProperties.FLUID_STACK).getAmount(), "published model data must not alias the live tank");
      assertEquals(750, entity.getModelData().get(ModelProperties.FLUID_STACK).getAmount());
      tank.setFluid(FluidStack.EMPTY);
      assertTrue(entity.getModelData().get(ModelProperties.FLUID_STACK).isEmpty());
    }
  }

  @Test
  void cannonVariantsAlreadyInheritFluidAndCapacityProvider() {
    for (var block : List.of(TinkerSmeltery.searedFluidCannon.get(), TinkerSmeltery.scorchedFluidCannon.get(), TinkerSmeltery.endFluidCannon.get())) {
      var entity = new FluidCannonBlockEntity(BlockPos.ZERO, block.defaultBlockState());
      entity.getTank().setFluid(new FluidStack(Fluids.LAVA, 250));
      var data = entity.getModelData();
      assertEquals(Fluids.LAVA, data.get(ModelProperties.FLUID_STACK).getFluid());
      assertEquals(250, data.get(ModelProperties.FLUID_STACK).getAmount());
      assertEquals(entity.getTank().getCapacity(), data.get(ModelProperties.TANK_CAPACITY));
    }
  }

  @Test
  void everyControllerAndCannonStateReachesActualTankGeometry() throws Exception {
    for (String block : List.of("seared_casting_tank", "seared_melter", "scorched_alloyer", "seared_fluid_cannon", "scorched_fluid_cannon", "end_fluid_cannon")) {
      var variants = resource("blockstates/" + block + ".json").getAsJsonObject("variants");
      assertFalse(variants.isEmpty());
      for (var entry : variants.entrySet()) {
        var variant = entry.getValue().getAsJsonObject();
        assertEquals("tconstruct:tank", variant.get("type").getAsString(), block + ":" + entry.getKey());
        String model = variant.get("model").getAsString();
        var visited = new HashSet<String>();
        while (true) {
          assertTrue(visited.add(model), "cyclic model parents: " + model);
          assertTrue(model.startsWith("tconstruct:"));
          var json = resource("models/" + model.substring("tconstruct:".length()) + ".json");
          if (json.has("loader")) {
            assertEquals("tconstruct:tank", json.get("loader").getAsString());
            assertTrue(json.has("fluid"));
            assertTrue(json.getAsJsonObject("fluid").get("increments").getAsInt() > 0);
            break;
          }
          assertTrue(json.has("parent"), "no tank geometry found for " + model);
          model = json.get("parent").getAsString();
        }
      }
    }
  }

  @Test
  void proxyGuiSelectorPreservesExactOriginalAlternateGeometry() throws Exception {
    var selector = resource("items/scorched_proxy_tank.json").getAsJsonObject("model");
    assertEquals("minecraft:select", selector.get("type").getAsString());
    assertEquals("minecraft:display_context", selector.get("property").getAsString());
    var cases = selector.getAsJsonArray("cases");
    assertEquals(1, cases.size());
    assertEquals("gui", cases.get(0).getAsJsonObject().get("when").getAsString());
    assertEquals("tconstruct:block/foundry/proxy_tank_gui", cases.get(0).getAsJsonObject().getAsJsonObject("model").get("model").getAsString());
    assertEquals("tconstruct:item/scorched_proxy_tank", selector.getAsJsonObject("fallback").get("model").getAsString());
    var original = resource("models/block/foundry/proxy_tank.json");
    var gui = resource("models/block/foundry/proxy_tank_gui.json");
    assertEquals("tconstruct:block/foundry/proxy_tank", gui.get("parent").getAsString());
    assertEquals(original.getAsJsonObject("gui").get("elements"), gui.get("elements"));
    assertNotEquals(original.get("elements"), gui.get("elements"));
  }

  private static JsonObject resource(String path) throws Exception {
    try (var stream = TankModelDataTest.class.getResourceAsStream("/assets/tconstruct/" + path)) {
      assertNotNull(stream, path);
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }
}
