# Modifier tooltip context restoration

Baseline: official `TConstruct-1.20.1-3.12.1.231-sources.jar` and released main JAR in `local-audit/upstream`. This restores `ModifierTooltip` and `ModifierTooltipsField`, plus the original `BasicModifier`/`ComposableModifier` policy API. No modifier modules, numerical stats or gameplay conditions are changed.

Five distinct contexts are now honored: tool items, Tinker Station/worktable details, tool-part items, Part Builder details and material books. Both book rendering and HTML export apply the book policy. `TooltipBuilder` gains the original context overload while retaining its boolean overload. Existing concrete port modifiers override the boolean method on `Modifier`; the new context bridge still calls those overrides for tool/station contexts. The three material contexts retain their previous visible default for legacy modifiers. `EmptyModifier` remains hidden everywhere.

The original presets are:

| Policy | Tool | Station | Part item | Part Builder | Book |
| --- | --- | --- | --- | --- | --- |
| always | yes | yes | yes | yes | yes |
| never | no | no | no | no | no |
| parts_only | no | no | yes | yes | yes |
| bonus_slot | no | yes | yes | yes | yes |
| advanced | no | yes | no | yes | yes |

Arbitrary context arrays are also supported. Explicit `show_in_tooltips` takes precedence over legacy `tooltip_display`, including when the ignored legacy value is invalid. Legacy `always`, `tinker_station`, and `never` map respectively to `always`, `bonus_slot`, and `parts_only`. Legacy `never` did not filter material traits, so translating it to the new `never` would break compatibility. As in the original loader, legacy keys emit migration warnings. Default `always` is omitted from newly serialized JSON.

Old constructors, builder `tooltipDisplay(...)`, the protected legacy field and boolean callers remain available. The port's existing public `BasicModifier.Builder(ModuleHookMap)` constructor is preserved in addition to the original static factory. The inherited behavior matches the official API: subclasses that implement new context rules should override the context overload; existing boolean overrides on `Modifier` continue working for their two original contexts.

Comparison of shared generated modifiers found 294 semantically equivalent policies and nine differences. Only those nine provider calls and generated JSON fields change: banner, dyed, embellishment, rebalanced and trim become station-only; edible_tooltip, iron_armor and pocket become hidden in all contexts; overslime_friend becomes advanced. An authoring-time structural comparison verified every other parsed JSON value in these nine files is unchanged. Other generated legacy fields retain their compatible behavior.

`ModifierTooltipParityTest` adds six headless tests: preset/custom-set JSON and network roundtrips, modern/legacy precedence and migration, existing boolean override compatibility, old/new constructor and builder agreement, real `TooltipUtil`/`TooltipBuilder` filtering, and complete loading of the nine shipped modifier resources. The actual consumer test supplies only the tool's modifier list; production code performs filtering and display-name selection. It does not initialize screens, fonts or the Minecraft singleton.

The first coordinator run compiled the restoration and passed five of its six tests. The actual-consumer test reached the unloaded headless modifier registry through `LazyModifier`; its setup now supplies the four test modifier objects directly without faking global registry state. That correction still needs a passing run. Existing client-only book tests remain distribution-gated. Client acceptance is still needed for all five visible contexts, advanced tooltip modes, book HTML/export layout and material/recipe reloads. No graphical acceptance is claimed by common API tests.
