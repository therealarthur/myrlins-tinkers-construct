# Combat result and secondary-hit regression

For the separate `arthur.2` source / `0.0.2` fixture candidate. Command: `aebmcombattest`. Require six `AEBM_COMBAT_PASS` lines, no `AEBM_COMBAT_FAIL`, and `AEBM_COMBAT_SUMMARY passed=6 failed=0`.

The shipped port called the modern void `Entity.hurt` and then returned `damage > 0` from the main tool attack, secondary attack, deprecated default-damage helper and `MeleeHitToolHook`. The original Tinkers implementations use the actual boolean damage result. Positive requested damage can still be rejected by invulnerability, immunity, cooldown or event cancellation. Treating it as success incorrectly enables successful-hit hooks and durability consumption.

The target vanilla player uses `Entity.hurtOrSimulate`, which returns `hurtServer` on the server and the target's simulation result on the client. All four affected Tinkers entry points now use that real result. Secondary attacks also restore the original accumulated `LivingEntity.lastHurt` state through a named access transformer while preserving the old invulnerability timer. Rejected hits retain the old threshold rather than doubling it; timer and temporary knockback resistance cleanup use `finally`.

The six server cases cover:

1. Actual deprecated default damage against an invulnerable and ordinary detached cow, with return value and health checks.
2. Accepted secondary damage during cooldown: health decreases by three, previous threshold four becomes seven, timer 17 survives.
3. Invulnerable secondary damage: health, previous threshold and timer stay unchanged and the result is false.
4. Actual NeoForge incoming-damage cancellation: one scoped event fires; false result, health/threshold/timer preservation and temporary knockback resistance cleanup.
5. Actual loaded iron sword and full `performAttack`: rejected hit does not cost durability; accepted hit damages the target and tool.
6. Actual `MeleeHitToolHook.dealDamage`: a test-only view delegates to the actual tool except for an observable post-hit callback. A rejected hit calls it zero times; an accepted hit calls it once.

Cows and fake players are detached, silent and above maximum build height. No entities or blocks are added to the world. The cancellation listener is unregistered in `finally`. This does not validate natural player input, every modifier, armor mitigation, multiplayer rendering, or projectile collision physics. At source creation these cases have not yet been compiled or executed.
