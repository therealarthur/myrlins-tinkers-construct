package slimeknights.tconstruct.plugin.rei;

/**
 * Decides when the REI display cache must be dropped, rebuilt or kept, from the received recipe snapshot.
 * <p>
 * Extracted from {@code SmelteryDisplayGenerator.Recipes} so the lifecycle is tested without REI on the test classpath:
 * displays exist only inside a synchronized world, a new snapshot revision (datapack reload, late material sync or a new
 * connection) rebuilds them once, and leaving the world forgets the revision so a reconnect always rebuilds even if the
 * revision number repeats. It never touches REI's entry list or filters; {@link ClientEntryRefresh} owns those.
 */
public final class SnapshotGate {
  /** What the cache should do for this lookup. */
  public enum Action { CLEAR, REBUILD, KEEP }

  private long revision = -1;

  /**
   * @param ready     true when a world is connected, a recipe snapshot exists and materials are loaded
   * @param snapshot  revision of the current recipe snapshot
   */
  public synchronized Action next(boolean ready, long snapshot) {
    if (!ready) {
      revision = -1;
      return Action.CLEAR;
    }
    return revision == snapshot ? Action.KEEP : Action.REBUILD;
  }

  /** Records a finished rebuild for the revision. */
  public synchronized void built(long snapshot) {
    revision = snapshot;
  }

  /** Revision of the cached displays, or -1 when there are none. */
  public synchronized long revision() {
    return revision;
  }
}
