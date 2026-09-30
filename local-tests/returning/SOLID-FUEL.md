# Solid fuel remainder fixture

The development mod registers `aebmsolidfueltest` and a test-only fuel item with a stack-sensitive crafting remainder. For fixture 0.0.4, expected output is ten `AEBM_SOLID_FUEL_PASS` lines and `AEBM_SOLID_FUEL_SUMMARY passed=10 failed=0`. The six original assertions remain; four new burn-hook controls are described below.

The fixture calls the real private `SolidFuelModule.trySolidFuel` method through reflection. It verifies simulation, lava-bucket replacement, the actual heater's rejection/ejection of an empty bucket, a remainder derived from the extracted stack's components, partial insertion of a three-item remainder, and failed extraction. Fuel values and hook processing execute normally. No replacement model of the fuel algorithm is used.

For partial insertion, two buckets fit and the remaining one must be the stack passed to the actual ItemEntity spawn path. A synchronous thread-local EntityJoinLevelEvent handler captures that stack and cancels the fixture spawn, then removes its capture state in a finally block. Heater objects are above build height and never installed into chunks. This tests the production spawn request, not later world pickup or entity persistence. The deliberate rejected-extraction case logs the module's existing invalid-handler error; it must create neither fuel nor items.

The production fix restores original stack-aware crafting remainder behavior through the modern ItemStackTemplate API. It also drops `notInserted` instead of the full `container`: the original code's full-stack drop would duplicate the portion already inserted. Fuel amounts, temperature, rate and priority are unchanged. No build or server execution was performed by this subagent.


## First pack server .4 burn-hook regression

The frozen .3 companion ran on the first pack server: four cases passed, while the extracted-component and partial-insertion cases failed before extraction because the production fuel lookup bypassed the item's `getBurnTime` override. Bucket count three is valid; those assertions are unchanged. See `../../local-audit/solid-fuel-first-pack-test-arthur4.md` for target API evidence and the exact observed run.

Four additional cases check `heater_accepts_item_burn_time_override`, `burn_event_zero_veto_is_authoritative`, `burn_event_reduction_applies_once`, and `heater_and_module_share_three_four_tick_boundary`. The real heater accepts the custom 400-tick item absent from the data map and ejects three named buckets after burning. An isolated thread-local event callback sets coal to zero, eight, three or four ticks; both acceptance and actual fuel consumption must obey the final event result. Callback state is removed in `finally`. Nothing modifies the global fuel data map.

The full fixture now contains 81 cases; 39 cases passed in the initial .3 run, two failed and 36 later cases were unexecuted. New source/tests are prepared only until a new compile and coordinator run are recorded. No .4 artifact is built by editing these files.