package aebm.continuumtests;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.plugin.rei.ClientEntryRefresh;

import static org.junit.jupiter.api.Assertions.*;

final class ClientEntryRefreshTest {
  @Test
  void titleAndPartialSynchronizationNeverInvokeViewerFiltering() {
    ClientEntryRefresh refresh = new ClientEntryRefresh();
    Runnable unavailable = () -> fail("Viewer mutation before synchronized world access");
    refresh.refresh(false, 0, unavailable, unavailable, unavailable);
    refresh.refresh(false, 1, unavailable, unavailable, unavailable);
    refresh.reset();
    refresh.refresh(false, 2, unavailable, unavailable, unavailable);
  }

  @Test
  void connectedRevisionRefreshesOnceAndDisconnectOnlyRemovesOwnedEntries() {
    ClientEntryRefresh refresh = new ClientEntryRefresh();
    List<String> calls = new ArrayList<>();
    Runnable remove = () -> calls.add("remove");
    Runnable add = () -> calls.add("add");
    Runnable filter = () -> calls.add("filter");
    refresh.refresh(true, 1, remove, add, filter);
    refresh.refresh(true, 1, remove, add, filter);
    assertEquals(List.of("remove", "add", "filter"), calls);
    refresh.refresh(true, 2, remove, add, filter);
    assertEquals(6, calls.size());
    calls.clear();
    refresh.refresh(false, 3, remove, () -> fail("Entries added after logout"), () -> fail("Filtering accessed registry after logout"));
    refresh.refresh(false, 4, remove, add, filter);
    assertEquals(List.of("remove"), calls);
    calls.clear();
    refresh.refresh(true, 2, remove, add, filter);
    assertEquals(List.of("remove", "add", "filter"), calls, "Reconnect must refresh even when a revision number repeats");
  }

  @Test
  void pluginReloadInvalidatesCompletedRevision() {
    ClientEntryRefresh refresh = new ClientEntryRefresh();
    List<String> calls = new ArrayList<>();
    refresh.refresh(true, 5, () -> {}, () -> {}, () -> calls.add("filter"));
    refresh.reset();
    refresh.refresh(true, 5, () -> {}, () -> {}, () -> calls.add("filter"));
    assertEquals(List.of("filter", "filter"), calls);
  }
}
