package slimeknights.tconstruct.plugin.rei;

/** Keeps viewer filtering inside the connected, synchronized client lifecycle. */
public final class ClientEntryRefresh {
  private long revision = -1;
  private boolean installed;

  public synchronized void reset() {
    revision = -1;
    installed = false;
  }

  public synchronized void refresh(boolean ready, long nextRevision, Runnable removeEntries, Runnable addEntries, Runnable refilter) {
    if (!ready) {
      if (installed) removeEntries.run();
      reset();
      return;
    }
    if (installed && revision == nextRevision) return;
    removeEntries.run();
    addEntries.run();
    refilter.run();
    revision = nextRevision;
    installed = true;
  }
}
