# Block-walker placement cancellation regression

Source restoration for the `.2` fixture/source iteration. The original audit and `.1` artifacts remain unchanged.

Official Tinkers `v3.12.1.231` calls `ForgeEventFactory.onBlockPlace(living, BlockSnapshot.create(world.dimension(), world, belowFeet), Direction.UP)` after target/survival/collision checks, before mutation and scheduling. A canceled event skips the replacement. Continuum's pinned implementation had commented that guard out.

The cached NeoForge **26.1.2.109** sources expose the matching `EventHooks.onBlockPlace(Entity, BlockSnapshot, Direction)` signature. It constructs `BlockEvent.EntityPlaceEvent`, posts to `NeoForge.EVENT_BUS`, and returns its cancellation flag. `BlockSnapshot.create` retains the original block state and position. The focused module change restores that direct guard. It does not move mutation before the event, change the replacement predicate, or change delay/cost/balance behavior.

The new `BlockWalkerPlacementServerFixture.registerCommands` is intended for the coordinator's fixture mod entrypoint. Command: `aebmblockwalkertest`, run via `execute positioned <x> <y> <z>` in a coordinator-selected disposable, already loaded, empty area. It does not request chunks or create a world. It refuses occupied cells, block entities, unloaded neighbor chunks, out-of-bounds positions, and a pre-existing ice tick before any write. It uses five bases at x offsets 0, 2, 4, 6, and 8; each base's y-1 through y+2 must initially be air. Neighbor chunk coverage is checked from offset (-1,-1,-1) through (9,2,1).

The five cases call the real module and NeoForge event bus:

1. A scoped listener cancels placement: source water remains and no ice tick is scheduled.
2. The listener allows placement: source water becomes frosted ice and an ice tick is scheduled.
3. A nonmatching stone target is not overwritten and posts no placement event.
4. A stone block above the water prevents replacement; both blocks remain unchanged and no event is posted.
5. An unmatched modifier-level range preserves water and posts no event.

Eligible cases also verify one event, the actor/position scope, original-water snapshot, world state still water during the event, and the UP face through a distinct stone support block. The tool remains usable and the original continue-walking return value is retained.

The listener is unregistered in `finally`; all touched states are restored in a suite `finally`, including after a failed assertion. The fake player is never added to the world or player list. Scheduled water/ice ticks may remain until they fire; the corresponding blocks are restored to their original air states, so those ticks are inert. A repeated immediate run can refuse a still-pending ice tick; use another allocated empty area or wait for it to expire.

This fixture is source-only at handoff: **not compiled or run by this workstream**. The coordinator owns compilation and any dedicated-server execution. The test establishes the event contract once it passes; actual third-party claim/protection integration remains a separate pack acceptance check. No graphical client is needed for this fixture.
