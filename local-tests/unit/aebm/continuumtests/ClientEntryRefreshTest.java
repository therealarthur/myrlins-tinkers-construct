package aebm.continuumtests;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.plugin.rei.ClientEntryRefresh;

import static org.junit.jupiter.api.Assertions.*;

final class ClientEntryRefreshTest {
  @Test
  void asynchronousReloadDefersPendingRevisionAndDisconnectUntilCachesAreReady() {
    ClientEntryRefresh refresh = new ClientEntryRefresh();
    List<String> calls = new ArrayList<>();
    Runnable remove = () -> calls.add("remove");
    Runnable add = () -> calls.add("add");
    Runnable filter = () -> calls.add("filter");
    refresh.refresh(true, true, 1, remove, add, filter);
    assertTrue(calls.isEmpty(), "First synchronized recipe snapshot can arrive before REI finishes reloading");
    refresh.refresh(false, true, 1, remove, add, filter);
    assertEquals(List.of("remove", "add", "filter"), calls);
    calls.clear();
    refresh.refresh(true, true, 2, remove, add, filter);
    refresh.refresh(true, false, 3, remove, add, filter);
    assertTrue(calls.isEmpty(), "Reload must also defer cleanup, preserving installed state");
    refresh.refresh(false, true, 2, remove, add, filter);
    assertEquals(List.of("remove", "add", "filter"), calls, "Pending revision must be applied when REI is ready");
    calls.clear();
    refresh.refresh(false, true, 2, remove, add, filter);
    assertTrue(calls.isEmpty(), "A completed revision must not be applied twice");
    refresh.refresh(true, false, 3, remove, add, filter);
    assertTrue(calls.isEmpty());
    refresh.refresh(false, false, 3, remove, add, filter);
    assertEquals(List.of("remove"), calls, "Deferred logout still cleans up after reload completes");
  }

  @Test
  void titleAndPartialSynchronizationNeverInvokeViewerFiltering() {
    ClientEntryRefresh refresh = new ClientEntryRefresh();
    Runnable unavailable = () -> fail("Viewer mutation before synchronized world access");
    refresh.refresh(false, false, 0, unavailable, unavailable, unavailable);
    refresh.refresh(false, false, 1, unavailable, unavailable, unavailable);
    refresh.reset();
    refresh.refresh(false, false, 2, unavailable, unavailable, unavailable);
  }

  @Test
  void connectedRevisionRefreshesOnceAndDisconnectOnlyRemovesOwnedEntries() {
    ClientEntryRefresh refresh = new ClientEntryRefresh();
    List<String> calls = new ArrayList<>();
    Runnable remove = () -> calls.add("remove");
    Runnable add = () -> calls.add("add");
    Runnable filter = () -> calls.add("filter");
    refresh.refresh(false, true, 1, remove, add, filter);
    refresh.refresh(false, true, 1, remove, add, filter);
    assertEquals(List.of("remove", "add", "filter"), calls);
    refresh.refresh(false, true, 2, remove, add, filter);
    assertEquals(6, calls.size());
    calls.clear();
    refresh.refresh(false, false, 3, remove, () -> fail("Entries added after logout"), () -> fail("Filtering accessed registry after logout"));
    refresh.refresh(false, false, 4, remove, add, filter);
    assertEquals(List.of("remove"), calls);
    calls.clear();
    refresh.refresh(false, true, 2, remove, add, filter);
    assertEquals(List.of("remove", "add", "filter"), calls, "Reconnect must refresh even when a revision number repeats");
  }

  @Test
  void pluginReloadInvalidatesCompletedRevision() {
    ClientEntryRefresh refresh = new ClientEntryRefresh();
    List<String> calls = new ArrayList<>();
    refresh.refresh(false, true, 5, () -> {}, () -> {}, () -> calls.add("filter"));
    refresh.reset();
    refresh.refresh(false, true, 5, () -> {}, () -> {}, () -> calls.add("filter"));
    assertEquals(List.of("filter", "filter"), calls);
  }
}
