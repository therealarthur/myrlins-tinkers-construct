package aebm.continuumtests;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.plugin.rei.ClientEntryRefresh;
import slimeknights.tconstruct.plugin.rei.SnapshotGate;
import slimeknights.tconstruct.plugin.rei.SnapshotGate.Action;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reload safety of the REI display cache: displays rebuild once per synchronized snapshot, after a datapack reload
 * and after a reconnect, and nothing is rebuilt or refiltered while no world is synchronized. Pairs the display cache
 * gate with the shipped entry refresh gate (arthur.6 and .7), both without REI on the classpath.
 */
final class SnapshotGateTest {
  @Test
  void rebuildsOncePerRevisionAndAfterReloadAndReconnect() {
    SnapshotGate gate = new SnapshotGate();
    assertEquals(Action.CLEAR, gate.next(false, 0), "title screen: no displays");
    assertEquals(Action.REBUILD, gate.next(true, 1), "first synchronized snapshot builds");
    gate.built(1);
    assertEquals(Action.KEEP, gate.next(true, 1), "same snapshot is reused");
    assertEquals(Action.REBUILD, gate.next(true, 2), "datapack reload or late material sync rebuilds");
    gate.built(2);
    assertEquals(Action.KEEP, gate.next(true, 2));
    assertEquals(Action.CLEAR, gate.next(false, 3), "logout drops displays");
    assertEquals(-1, gate.revision());
    assertEquals(Action.REBUILD, gate.next(true, 2), "reconnect rebuilds even when a revision number repeats");
  }

  @Test
  void displayRebuildsAndEntryRefreshStayInsideTheSynchronizedWorld() {
    SnapshotGate gate = new SnapshotGate();
    ClientEntryRefresh refresh = new ClientEntryRefresh();
    List<String> calls = new ArrayList<>();
    Runnable remove = () -> calls.add("remove");
    Runnable add = () -> calls.add("add");
    Runnable filter = () -> calls.add("filter");

    // title screen and partial sync: neither cache work nor viewer filtering
    step(gate, refresh, false, false, 0, remove, add, filter, calls);
    assertTrue(calls.isEmpty(), "no viewer mutation before a synchronized world");

    // join: REI still reloading defers the entry refresh, the display cache may build
    step(gate, refresh, true, true, 1, remove, add, filter, calls);
    assertEquals(List.of("build 1"), calls, "reload busy: entries wait for REI");
    calls.clear();
    step(gate, refresh, true, false, 1, remove, add, filter, calls);
    assertEquals(List.of("remove", "add", "filter"), calls, "entries installed once after REI finishes");
    calls.clear();

    // datapack reload
    step(gate, refresh, true, false, 2, remove, add, filter, calls);
    assertEquals(List.of("build 2", "remove", "add", "filter"), calls, "reload rebuilds displays and entries once");
    calls.clear();
    step(gate, refresh, true, false, 2, remove, add, filter, calls);
    assertTrue(calls.isEmpty(), "steady state does nothing");

    // logout then reconnect
    step(gate, refresh, false, false, 3, remove, add, filter, calls);
    assertEquals(List.of("remove"), calls, "logout only removes owned entries");
    calls.clear();
    step(gate, refresh, true, false, 2, remove, add, filter, calls);
    assertEquals(List.of("build 2", "remove", "add", "filter"), calls, "reconnect rebuilds and refreshes");
  }

  /** One client tick: the display cache is consulted, then the entry refresh runs with REI's reload state. */
  private static void step(SnapshotGate gate, ClientEntryRefresh refresh, boolean ready, boolean reloadBusy, long revision,
                           Runnable remove, Runnable add, Runnable filter, List<String> calls) {
    if (gate.next(ready, revision) == Action.REBUILD) {
      calls.add("build " + revision);
      gate.built(revision);
    }
    refresh.refresh(reloadBusy, ready, revision, remove, add, filter);
  }
}
