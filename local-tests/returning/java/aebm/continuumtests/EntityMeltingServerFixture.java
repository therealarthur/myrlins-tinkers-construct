package aebm.continuumtests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.common.TinkerDamageTypes;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.fluid.FluidActions;
import slimeknights.tconstruct.library.recipe.entitymelting.EntityMeltingRecipe;
import slimeknights.tconstruct.library.recipe.entitymelting.EntityMeltingRecipeCache;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.EntityMeltingModule;
import slimeknights.tconstruct.smeltery.block.entity.multiblock.SmelteryMultiblock;

/**
 * Real controller-owned module, fuel state, tank, ServerLevel query, and entity damage.
 * The detached controller is given structure metadata and persisted burning fuel;
 * this does not test physical multiblock detection, fuel recipes, or tick scheduling.
 * Run positioned with origin offsets -1..5 in an already loaded empty 7x7x7 region. No blocks or gamerules
 * change. Every added entity is discarded before the synchronous command returns.
 */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class EntityMeltingServerFixture {
  private static final ThreadLocal<Fixture> ACTIVE = new ThreadLocal<>();

  private EntityMeltingServerFixture() {}

  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebm_continuum_entity_melting")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  /** Prevent unexpected fixture death drops from remaining if another mod changes damage. */
  @SubscribeEvent
  public static void captureUnexpectedDrops(EntityJoinLevelEvent event) {
    Fixture active = ACTIVE.get();
    Entity entity = event.getEntity();
    if (active != null && entity.level() == active.level
        && (entity instanceof ItemEntity || entity instanceof ExperienceOrb)
        && active.region.intersects(entity.getBoundingBox())) {
      active.unexpectedDrops++;
      event.setCanceled(true);
    }
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final BlockPos origin;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
      this.origin = BlockPos.containing(source.getPosition());
    }

    private int run() {
      try {
        preflight(level, origin);
      } catch (Throwable failure) {
        source.sendFailure(Component.literal("AEBM_ENTITY_MELTING_REFUSED " + failure.getMessage()));
        return 0;
      }

      test("armor_stand_rejection_never_creates_fluid", () -> {
        try (Fixture f = new Fixture(level, origin, true)) {
          var stand = f.spawn(EntityType.ARMOR_STAND);
          require(!stand.fireImmune(), "ordinary vanilla stand must use smeltery heat");
          DamageSource heat = TinkerDamageTypes.source(level.registryAccess(), TinkerDamageTypes.SMELTERY_HEAT);
          require(!stand.isInvulnerableTo(level, heat), "stand must pass the module immunity prefilter");
          float health = stand.getHealth();
          require(!stand.hurtServer(level, heat, 2), "real vanilla stand damage must be rejected by loaded tags");
          require(stand.isAlive() && stand.getHealth() == health, "direct rejected hit must preserve stand");
          for (int attempt = 0; attempt < 3; attempt++) {
            require(!f.controller.interact(), "rejected stand must not report successful melting, attempt " + attempt);
            require(f.controller.getTank().getContained() == 0, "rejected stand must not create fluid");
            require(stand.isAlive() && stand.getHealth() == health, "repeated rejection must preserve stand");
          }
        }
      });
      test("accepted_living_damage_matches_loaded_output", () -> {
        try (Fixture f = new Fixture(level, origin, true)) {
          var cow = f.spawn(EntityType.COW);
          FluidStack expected = expectedOutput(level, cow);
          float health = cow.getHealth();
          require(f.controller.interact(), "ordinary living target must report accepted melting");
          require(cow.isAlive() && cow.getHealth() < health, "real damage must lower living target health");
          require(f.controller.getTank().getContained() == expected.getAmount(), "exact loaded output amount must be filled once");
          sameFluid(expected, f.controller.getTank().getFluidInTank(0));
        }
      });
      test("canceled_incoming_damage_never_creates_fluid", () -> {
        try (Fixture f = new Fixture(level, origin, true)) {
          var cow = f.spawn(EntityType.COW);
          expectedOutput(level, cow);
          float health = cow.getHealth();
          AtomicInteger calls = new AtomicInteger();
          Consumer<LivingIncomingDamageEvent> cancel = event -> {
            if (event.getEntity() == cow) {
              calls.incrementAndGet();
              event.setCanceled(true);
            }
          };
          NeoForge.EVENT_BUS.addListener(cancel);
          try {
            require(!f.controller.interact(), "canceled real damage must not report successful melting");
          } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
          }
          require(calls.get() == 1, "real incoming-damage event must be reached exactly once");
          require(cow.getHealth() == health, "canceled incoming damage must preserve health");
          require(f.controller.getTank().getContained() == 0, "canceled incoming damage must not fill tank");
        }
      });
      test("empty_fuel_prevents_damage_and_fluid", () -> {
        try (Fixture f = new Fixture(level, origin, false)) {
          var cow = f.spawn(EntityType.COW);
          float health = cow.getHealth();
          require(!f.controller.getFuelModule().hasFuel(), "actual fuel module must start empty");
          require(!f.controller.interact(), "empty fuel and no fuel tanks must reject melting");
          require(cow.getHealth() == health && f.controller.getTank().getContained() == 0,
            "fuel rejection must preserve both health and tank");
        }
      });
      test("fire_resistance_prevents_damage_and_fluid", () -> {
        try (Fixture f = new Fixture(level, origin, true)) {
          var cow = f.spawn(EntityType.COW);
          require(cow.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0)), "effect must be applied");
          require(cow.hasEffect(MobEffects.FIRE_RESISTANCE), "actual fire resistance must be active");
          float health = cow.getHealth();
          require(!f.controller.interact(), "fire-resistant target must be excluded");
          require(cow.getHealth() == health && f.controller.getTank().getContained() == 0,
            "effect guard must preserve both health and tank");
        }
      });
      test("invulnerable_entity_prevents_damage_and_fluid", () -> {
        try (Fixture f = new Fixture(level, origin, true)) {
          var cow = f.spawn(EntityType.COW);
          cow.setInvulnerable(true);
          float health = cow.getHealth();
          require(!f.controller.interact(), "invulnerable target must be excluded");
          require(cow.getHealth() == health && f.controller.getTank().getContained() == 0,
            "invulnerability guard must preserve both health and tank");
        }
      });
      test("full_tank_still_takes_accepted_damage", () -> {
        try (Fixture f = new Fixture(level, origin, true)) {
          var cow = f.spawn(EntityType.COW);
          FluidStack expected = expectedOutput(level, cow);
          int capacity = f.controller.getTank().getCapacity();
          require(capacity > expected.getAmount(), "real smeltery capacity must exceed one output");
          require(f.controller.getTank().fill(expected.copyWithAmount(capacity), FluidActions.EXECUTE) == capacity,
            "real tank must be filled completely");
          float health = cow.getHealth();
          require(f.controller.interact(), "accepted damage still reports melting with full tank");
          require(cow.isAlive() && cow.getHealth() < health, "full tank must retain original accepted damage behavior");
          require(f.controller.getTank().getContained() == capacity, "full tank must neither overflow nor lose stored fluid");
          sameFluid(expected, f.controller.getTank().getFluidInTank(0));
        }
      });

      source.sendSuccess(() -> Component.literal("AEBM_ENTITY_MELTING_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private void test(String name, CheckedRunnable check) {
      try {
        check.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_ENTITY_MELTING_PASS " + name), false);
      } catch (Throwable failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_ENTITY_MELTING_FAIL " + name + ": " + failure));
      }
    }
  }

  /** Only exposes the existing module; neither damage nor output logic is overridden. */
  private static final class FixtureController extends SmelteryBlockEntity {
    private FixtureController(ServerLevel level, BlockPos origin, boolean fueled) {
      super(origin, TinkerSmeltery.smelteryController.get().defaultBlockState());
      setLevel(level);
      setStructure(new SmelteryMultiblock(this).createClient(origin, origin.offset(4, 4, 4), List.of()));
      CompoundTag fuel = new CompoundTag();
      fuel.putInt("fuel", fueled ? 400 : 0);
      fuel.putInt("temperature", fueled ? 1000 : 0);
      fuel.putInt("rate", fueled ? 1 : 0);
      getFuelModule().readFromTag(fuel);
      require(getFuelModule().hasFuel() == fueled, "persisted real fuel state must load");
      require(getTank().getCapacity() > 0 && getTank().getContained() == 0, "real structure must allocate empty tank");
    }

    private boolean interact() { return entityModule.interactWithEntities(); }
  }

  private static final class Fixture implements AutoCloseable {
    private final ServerLevel level;
    private final BlockPos origin;
    private final AABB region;
    private final FixtureController controller;
    private final List<LivingEntity> entities = new ArrayList<>();
    private int unexpectedDrops;

    private Fixture(ServerLevel level, BlockPos origin, boolean fueled) {
      require(ACTIVE.get() == null, "entity-melting fixture cannot be nested");
      preflight(level, origin);
      this.level = level;
      this.origin = origin;
      this.region = region(origin);
      this.controller = new FixtureController(level, origin, fueled);
      ACTIVE.set(this);
    }

    private <T extends LivingEntity> T spawn(EntityType<T> type) {
      require(!BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(TinkerTags.EntityTypes.MELTING_HIDE),
        "fixture target must not be hidden from entity melting");
      T entity = type.create(level, EntitySpawnReason.COMMAND);
      require(entity != null, "fixture entity must be constructible");
      entities.add(entity);
      entity.setPos(origin.getX() + 2.5, origin.getY() + 1, origin.getZ() + 2.5);
      entity.setNoGravity(true);
      entity.setSilent(true);
      if (entity instanceof Mob mob) mob.setNoAi(true);
      require(level.addFreshEntity(entity), "fixture entity must enter the real server entity query");
      require(level.getEntitiesOfClass(Entity.class, controller.getStructure().getBounds()).contains(entity),
        "controller bounds must discover the actual added entity");
      return entity;
    }

    @Override
    public void close() {
      try {
        for (LivingEntity entity : entities) entity.discard();
      } finally {
        ACTIVE.remove();
      }
      require(entities.stream().allMatch(Entity::isRemoved), "all fixture entities must be discarded");
      require(unexpectedDrops == 0, "unexpected fixture item/XP drops were canceled: " + unexpectedDrops);
    }
  }

  private static AABB region(BlockPos origin) {
    return new AABB(origin.getX(), origin.getY(), origin.getZ(),
      origin.getX() + 5, origin.getY() + 5, origin.getZ() + 5).inflate(1);
  }

  private static void preflight(ServerLevel level, BlockPos origin) {
    for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(5, 5, 5))) {
      require(!level.isOutsideBuildHeight(pos), "fixture outside build height: " + pos);
      require(level.hasChunkAt(pos), "fixture requires already loaded chunks: " + pos);
      require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null,
        "fixture refuses occupied region: " + pos);
    }
    require(level.getEntitiesOfClass(Entity.class, region(origin)).stream().allMatch(Entity::isRemoved),
      "fixture refuses a region containing existing entities or players");
  }

  private static FluidStack expectedOutput(ServerLevel level, LivingEntity entity) {
    EntityMeltingRecipe recipe = EntityMeltingRecipeCache.findRecipe(level.getServer().getRecipeManager(), entity.getType());
    FluidStack expected = recipe == null ? EntityMeltingModule.getDefaultFluid() : recipe.getOutput(entity);
    int damage = recipe == null ? 2 : recipe.getDamage();
    require(!expected.isEmpty() && expected.getAmount() > 0, "loaded entity output must be nonempty");
    require(damage > 0 && damage < entity.getHealth(), "fixture requires one nonlethal ordinary recipe hit");
    return expected.copy();
  }

  private static void sameFluid(FluidStack expected, FluidStack actual) {
    require(FluidStack.isSameFluidSameComponents(expected, actual), "fluid identity and components must match loaded output");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  @FunctionalInterface
  private interface CheckedRunnable { void run() throws Exception; }
}
