# Solid fuel remainder fixture

The development mod registers `aebmsolidfueltest` and a test-only fuel item with a stack-sensitive crafting remainder. Expected output is six `AEBM_SOLID_FUEL_PASS` lines and `AEBM_SOLID_FUEL_SUMMARY passed=6 failed=0`.

The fixture calls the real private `SolidFuelModule.trySolidFuel` method through reflection. It verifies simulation, lava-bucket replacement, the actual heater's rejection/ejection of an empty bucket, a remainder derived from the extracted stack's components, partial insertion of a three-item remainder, and failed extraction. Fuel values and hook processing execute normally. No replacement model of the fuel algorithm is used.

For partial insertion, two buckets fit and the remaining one must be the stack passed to the actual ItemEntity spawn path. A synchronous thread-local EntityJoinLevelEvent handler captures that stack and cancels the fixture spawn, then removes its capture state in a finally block. Heater objects are above build height and never installed into chunks. This tests the production spawn request, not later world pickup or entity persistence. The deliberate rejected-extraction case logs the module's existing invalid-handler error; it must create neither fuel nor items.

The production fix restores original stack-aware crafting remainder behavior through the modern ItemStackTemplate API. It also drops `notInserted` instead of the full `container`: the original code's full-stack drop would duplicate the portion already inserted. Fuel amounts, temperature, rate and priority are unchanged. No build or server execution was performed by this subagent.
