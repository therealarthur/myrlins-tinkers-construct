package slimeknights.tconstruct.library.materials;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.LazyMaterial;
import slimeknights.tconstruct.library.materials.definition.Material;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.UpdateMaterialsPacket;
import slimeknights.tconstruct.library.materials.stats.UpdateMaterialStatsPacket;
import slimeknights.tconstruct.library.materials.traits.UpdateMaterialTraitsPacket;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise real synchronization callbacks across missing, changed, redirected and removed materials. */
final class MaterialReloadTest {
  @Test
  void lazyIdsFollowCompletedReloadsInEveryPacketOrder() throws Exception {
    Map<Field,Object> saved = new HashMap<>();
    for (String name : new String[]{"INSTANCE", "fullyLoaded", "materialsLoaded", "statsLoaded", "traitsLoaded", "FIRST_MATERIALS"}) {
      Field field = MaterialRegistry.class.getDeclaredField(name);
      field.setAccessible(true);
      Object value = field.get(null);
      saved.put(field, value instanceof Map<?,?> map ? new HashMap<>(map) : value);
    }
    try {
      MaterialRegistry.INSTANCE = new MaterialRegistry();
      resetFlags();
      MaterialId id = new MaterialId("aebm_test:reload_material");
      MaterialId redirect = new MaterialId("aebm_test:old_material");
      LazyMaterial lazy = LazyMaterial.of(id);
      Material first = new Material(id.getId(), 1, 100, Rarity.COMMON, true, false);
      Material replacement = new Material(id.getId(), 4, 200, Rarity.EPIC, false, true);
      LazyMaterial pinned = LazyMaterial.of(first);
      assertSame(IMaterial.UNKNOWN, lazy.get());
      for (int[] order : new int[][]{{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}}) {
        sync(Map.of(), Map.of(), order, lazy);
        assertSame(IMaterial.UNKNOWN, lazy.get());
        sync(Map.of(id, first), Map.of(redirect, id), order, lazy);
        assertSame(first, lazy.get(), "previously missing IDs must resolve after synchronization");
        assertEquals(id, MaterialRegistry.getInstance().resolve(redirect));
        assertSame(first, MaterialRegistry.getMaterial(MaterialRegistry.getInstance().resolve(redirect)));
        sync(Map.of(id, replacement), Map.of(redirect, id), order, lazy);
        assertSame(replacement, lazy.get(), "recipes must not retain pre-reload material instances");
        assertSame(replacement, MaterialRegistry.getMaterial(MaterialRegistry.getInstance().resolve(redirect)));
        assertSame(first, pinned.get(), "explicit instances used by datagen remain explicit");
        sync(Map.of(), Map.of(), order, lazy);
        assertSame(IMaterial.UNKNOWN, lazy.get());
        assertEquals(redirect, MaterialRegistry.getInstance().resolve(redirect));
        assertSame(IMaterial.UNKNOWN, MaterialRegistry.getMaterial(redirect));
      }
    } finally {
      for (var entry : saved.entrySet()) {
        if (entry.getKey().getName().equals("FIRST_MATERIALS")) {
          @SuppressWarnings("unchecked") Map<Object,Object> map = (Map<Object,Object>) entry.getKey().get(null);
          map.clear();
          map.putAll((Map<?,?>) entry.getValue());
        } else entry.getKey().set(null, entry.getValue());
      }
    }
  }

  private static void resetFlags() throws Exception {
    for (String name : new String[]{"fullyLoaded", "materialsLoaded", "statsLoaded", "traitsLoaded"}) {
      Field field = MaterialRegistry.class.getDeclaredField(name);
      field.setAccessible(true);
      field.setBoolean(null, false);
    }
  }

  private static void sync(Map<MaterialId,IMaterial> materials, Map<MaterialId,MaterialId> redirects, int[] order, LazyMaterial lazy) {
    for (int index = 0; index < order.length; index++) {
      switch (order[index]) {
        case 0 -> MaterialRegistry.updateMaterialsFromServer(new UpdateMaterialsPacket(materials, redirects, Map.of()));
        case 1 -> MaterialRegistry.updateMaterialStatsFromServer(new UpdateMaterialStatsPacket(Map.of()));
        case 2 -> MaterialRegistry.updateMaterialTraitsFromServer(new UpdateMaterialTraitsPacket(Map.of()));
      }
      assertEquals(index == 2, MaterialRegistry.isFullyLoaded());
      if (index < 2) assertSame(IMaterial.UNKNOWN, lazy.get(), "no stale material during partial synchronization");
    }
  }
}
