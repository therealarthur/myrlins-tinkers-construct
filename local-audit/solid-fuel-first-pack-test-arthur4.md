# Solid-fuel burn hooks after the first pack server test

`evidence/<first pack server candidate run>/latest.log:18153-18159` records four passing solid-fuel cases and two failures for frozen Construct `.3` / fixture `.3`. The preceding Returning, crafting, persistence, armor and block-walker suites passed 35 cases. The runner stopped at the solid-fuel gate, so combat (6), recipe viewer (7), tool tinkering (9) and recipe mapper (14) were not executed. The server saved and exited normally (`exitCode=0`, `forcedStop=false`). This is 39 passing cases, two failures and 36 unexecuted cases, not whole-suite acceptance.

The failed assertions are valid. The fixture fuel's `getBurnTime` override returns 400, and its component-sensitive remainder returns three buckets. Target `ItemStackTemplate.fromNonEmptyStack` preserves the count and component patch; buckets have a maximum stack size of 16. The extracted stack's remainder API dispatches to the item override. Neither test count needs weakening.

The production lookup failed earlier: `SolidFuelModule` queried the fuel data map and called `EventHooks.getItemBurnTime` directly. That event helper does not invoke the item's burn-time override. The fixture item has no data-map fuel entry, so the module saw zero and never extracted it. `HeaterItemHandler` and REI's fuel enumerator used the same bypass. Their fallback/max operations also restored positive data-map fuel after a zero event veto or a lower event value.

The target NeoForge .109 contract is explicit in `IItemStackExtension.getBurnTime`: empty stack returns zero; the item override runs first; a negative item result is rejected; then `EventHooks.getItemBurnTime` supplies the final value. `IItemExtension.getBurnTime` documents precedence over the fuel data map. `FurnaceFuelBurnTimeEvent.setBurnTime(0)` explicitly prevents using the item as fuel. The patch uses that stack API in all three callers, keeping `/4` in module/display and the matching `>3` heater threshold. Remainder creation, extracted components and residual-only dropping are unchanged.

The six original server cases and assertions remain. Four additional controlled cases cover:

- Actual heater acceptance of the item override while the fuel data map is zero; exactly 100 fuel ticks and three named container drops.
- An event veto of positive vanilla coal fuel: heater rejects it, preview returns zero, consuming retains the item and creates no fuel or drops.
- Reduction to eight burn ticks: the actual module evaluates the event once and grants exactly two ticks.
- The three/four-tick threshold: heater and module reject three without consumption, then accept four for one tick.

The event listener is active only through a thread-local test scope, only for the TConstruct fuel recipe type, with both thread locals cleared in `finally`. It changes no global fuel map or runtime configuration. The fixture now expects ten `AEBM_SOLID_FUEL_PASS` records and `AEBM_SOLID_FUEL_SUMMARY passed=10 failed=0`; the full companion has **81 cases**, including all 77 original cases. REI uses the same corrected lookup, but its graphical behavior is still unverified.

At authoring, this is an uncompiled source fix for the first pack server. Static diff/whitespace review passed and an independent source audit confirmed the API dispatch and count preservation. No Java, Gradle or server was launched: the coordinator owns the execution slot and is reviewing the portable build runner. After slot allocation, run `compileJava`, `craftingRegression` and `returningFixtureJar`, then package committed `.4` into a new independent output directory. Recheck frozen `.2/.3` and Core hashes. The coordinator must rerun all 81 cases, preserving the 39 prior passes and executing the previously unrun 36; do not silently skip past the failed gate.

The existing Edible, recipe-browser, rendering/resource and broader official-parity work remains planned separately. No balance change or Core change is part of this fuel correction.
