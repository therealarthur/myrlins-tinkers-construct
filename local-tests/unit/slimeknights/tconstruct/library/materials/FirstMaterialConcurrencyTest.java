package slimeknights.tconstruct.library.materials;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;

import static org.junit.jupiter.api.Assertions.*;

final class FirstMaterialConcurrencyTest {
  @Test
  void parallelCreativeCollectionSerializesCachePopulation() throws Exception {
    Field field = MaterialRegistry.class.getDeclaredField("FIRST_MATERIALS");
    field.setAccessible(true);
    @SuppressWarnings("unchecked") Map<MaterialStatsId,IMaterial> cache = (Map<MaterialStatsId,IMaterial>) field.get(null);
    Map<MaterialStatsId,IMaterial> saved = new HashMap<>(cache);
    MaterialRegistry previous = MaterialRegistry.INSTANCE;
    CountDownLatch firstEntered = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch secondStarted = new CountDownLatch(1);
    AtomicInteger loads = new AtomicInteger();
    IMaterialRegistry registry = (IMaterialRegistry) Proxy.newProxyInstance(IMaterialRegistry.class.getClassLoader(), new Class<?>[]{IMaterialRegistry.class}, (proxy, method, args) -> {
      if (method.getName().equals("getVisibleMaterials")) {
        if (loads.incrementAndGet() == 1) {
          firstEntered.countDown();
          if (!releaseFirst.await(5, TimeUnit.SECONDS)) throw new AssertionError("Lookup was not released");
        }
        return List.of();
      }
      throw new AssertionError("Unexpected registry operation: " + method.getName());
    });
    var workers = Executors.newFixedThreadPool(2);
    try {
      cache.clear();
      MaterialRegistry.INSTANCE = new MaterialRegistry(registry);
      var first = workers.submit(() -> MaterialRegistry.firstWithStatType(new MaterialStatsId("aebm_test:first")));
      assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
      var second = workers.submit(() -> {
        secondStarted.countDown();
        return MaterialRegistry.firstWithStatType(new MaterialStatsId("aebm_test:second"));
      });
      assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> second.get(150, TimeUnit.MILLISECONDS), "A second lookup must not mutate HashMap during the first computeIfAbsent");
      assertEquals(1, loads.get());
      releaseFirst.countDown();
      assertSame(IMaterial.UNKNOWN, first.get(5, TimeUnit.SECONDS));
      assertSame(IMaterial.UNKNOWN, second.get(5, TimeUnit.SECONDS));
      assertEquals(2, loads.get());
      assertEquals(2, cache.size());
    } finally {
      releaseFirst.countDown();
      workers.shutdownNow();
      assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
      MaterialRegistry.INSTANCE = previous;
      cache.clear();
      cache.putAll(saved);
    }
  }
}
