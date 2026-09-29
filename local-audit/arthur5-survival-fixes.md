# Arthur .5 survival fixes

This candidate starts at `d3576350dcdfd2ab776cfc4043a39d8b1733f774` and uses main version `3.12.2-arthur.5` and development fixture version `0.0.5`. Frozen Arthur .4 source/artifacts are separate. No assets, recipes or balance values change.

Entity melting now fills the tank only when the real server damage call returns true. Rejected vanilla armor-stand damage and canceled living-damage events must not grant fluid. Fuel checks remain before damage; a full tank still permits accepted damage and discards excess output as in the original.

Fancy armor stands replace the inherited player-break item drop through `brokenByPlayer`, then run the inherited equipment-drop path once. The previous death-loot override is removed. Two standard access-transformer entries widen the parent methods. This avoids an extra vanilla stand on large variants and the wrong vanilla stand on small variants. Explosion and creative behavior follow the inherited routes.

The test JVM always receives `aebm.continuum.projectRoot` from the actual checkout. This fixes the existing recipe-audit lookup when FML uses an isolated working directory. No assertions were weakened.

## Required validation

Source review is complete; this candidate has not yet been compiled or executed. Do not call it accepted until the coordinated build and server checks finish.

1. Build with the new access transformer applied, compile the existing FML tests and fixture source, and package main/fixture separately. Do not reuse .4 compiled classes for this build. Check source revision, main/fixture version, AT entries and production/test separation in the JARs.
2. Run the existing 64-case headless FML suite. Its expected result remains 59 passed and five specifically named client-only skips, with no failure/error. Client cases need separate graphical acceptance.
3. Run the existing 81 server cases plus the two new commands below in an authorized disposable server with the actual candidate JARs. Refusal, missing labels, duplicated labels, failure or contradictory summaries is not a pass.

The development-only fixture automatically registers:

- `aebm_continuum_entity_melting`: seven cases; expected marker `AEBM_ENTITY_MELTING_SUMMARY passed=7 failed=0`. Choose an already loaded empty region covering origin offsets -1 through +5 on each axis, then use `execute positioned <x> <y> <z> run aebm_continuum_entity_melting`. It uses actual world entities, the controller-owned module, real tank, and real fuel predicate. Structure metadata and already-burning fuel are seeded on a detached controller. Physical multiblock discovery, fuel-recipe acquisition, tick cadence and real-player creative immunity are not covered.
- `aebm_continuum_fancy_stand`: 19 cases; expected marker `AEBM_FANCY_STAND_SUMMARY passed=19 failed=0`. Choose an already loaded empty region covering origin offsets -2..+2 horizontally and -1..+3 vertically. The fixture calls real inherited `hurtServer`, observes actual item admission and checks exact item/count/components, all four variants, wobble timing, duplicate prevention, explosion, game rules, enchantment exclusions and rejection paths. It restores game rules and removes only tracked fixture entities.

Both commands require game-master permission, refuse occupied/unloaded regions, write no blocks, and must be run separately at the selected position. The stand fixture briefly changes global drop rules synchronously and restores them; use the disposable acceptance server, not a live customer world. Fixture source and successful compilation do not prove access-transformer dispatch or gameplay acceptance.

The fixture JAR must be excluded from the release pack. Completing these checks addresses these two defects, not the entire original Tinkers parity backlog or all modpack compatibility.
