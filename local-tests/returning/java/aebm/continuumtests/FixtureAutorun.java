package aebm.continuumtests;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Fixture-only unattended command runner. It lives in the returning fixture source set, so it is
 * never part of the Continuum release jar, and it does nothing unless the JVM was started with
 * {@code -Daebm.fixture.autorun=<plan file>}.
 *
 * <p>Why: the 12 server fixture suites used to need a person typing commands into a server console.
 * {@code <parity-workspace>\tools\run-fixture-server.ps1} writes a plan, starts a disposable
 * headless server with this property, and parses the log afterwards.
 *
 * <p>Plan format, one step per line (UTF-8):
 * <ul>
 *   <li>blank lines and lines starting with {@code #} are ignored;</li>
 *   <li>{@code !await_loaded minX minZ maxX maxZ timeoutTicks} waits until every overworld chunk
 *       covering that block rectangle is loaded at FULL status (use after {@code forceload add});</li>
 *   <li>{@code !wait ticks} idles for that many server ticks;</li>
 *   <li>anything else is a server console command, for example
 *       {@code execute positioned 8 10 8 run aebmblockwalkertest}.</li>
 * </ul>
 *
 * <p>Every step is logged with an {@code AEBM_AUTORUN_*} marker. A command that does not parse
 * (unknown command or bad argument) is not executed and is logged as
 * {@code AEBM_AUTORUN_UNKNOWN_COMMAND}; the runner treats any such line as a failed run. After the
 * last step the server is stopped through the same path as the vanilla {@code stop} command.
 * A daemon watchdog stops the server if the plan does not finish within
 * {@code aebm.fixture.autorun.timeoutSeconds} (default 900), and halts the JVM 60 seconds later
 * if a clean stop did not happen.
 */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class FixtureAutorun {
  /** System property naming the plan file. Unset means the autorun is completely inert. */
  public static final String PROPERTY = "aebm.fixture.autorun";
  /** System property for the whole-run watchdog, in seconds. */
  public static final String TIMEOUT_PROPERTY = "aebm.fixture.autorun.timeoutSeconds";
  private static final int DEFAULT_TIMEOUT_SECONDS = 900;
  private static final int HALT_GRACE_SECONDS = 60;
  private static final Logger LOGGER = LogUtils.getLogger();

  private static volatile MinecraftServer server;
  private static Runner runner;

  static {
    // Started during mod construction (class load by the subscriber scan) so a hang anywhere
    // after mod loading, including world creation, still ends the disposable server.
    if (isEnabled()) {
      startWatchdog(timeoutSeconds());
    }
  }

  private FixtureAutorun() {}

  static boolean isEnabled() {
    String plan = System.getProperty(PROPERTY);
    return plan != null && !plan.isBlank();
  }

  @SubscribeEvent
  public static void onServerStarted(ServerStartedEvent event) {
    if (!isEnabled()) {
      return;
    }
    server = event.getServer();
    Path planPath = Path.of(System.getProperty(PROPERTY));
    List<String> steps;
    try {
      steps = parsePlan(Files.readAllLines(planPath, StandardCharsets.UTF_8));
    } catch (IOException | RuntimeException failure) {
      LOGGER.error("AEBM_AUTORUN_ERROR 0 cannot read plan {}: {}", planPath, failure.toString());
      LOGGER.info("AEBM_AUTORUN_END steps=0 unknown=0 errors=1");
      event.getServer().halt(false);
      return;
    }
    runner = new Runner(event.getServer(), steps);
    // The console source origin (world spawn) is where unpositioned suites run; log it as evidence.
    LOGGER.info("AEBM_AUTORUN_BEGIN plan={} steps={} origin={}", planPath.toAbsolutePath(), steps.size(),
      BlockPos.containing(event.getServer().createCommandSourceStack().getPosition()).toShortString());
  }

  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Post event) {
    Runner active = runner;
    if (active != null && active.server == event.getServer()) {
      active.tick();
      if (active.finished) {
        runner = null;
      }
    }
  }

  @SubscribeEvent
  public static void onServerStopped(ServerStoppedEvent event) {
    if (isEnabled()) {
      LOGGER.info("AEBM_AUTORUN_STOPPED");
    }
  }

  /** Removes comments and blank lines; keeps order. Package-private for unit tests. */
  static List<String> parsePlan(List<String> lines) {
    List<String> steps = new ArrayList<>();
    for (String raw : lines) {
      String line = raw.strip();
      if (!line.isEmpty() && !line.startsWith("#")) {
        steps.add(line);
      }
    }
    return steps;
  }

  private static final class Runner {
    private final MinecraftServer server;
    private final List<String> steps;
    private int index;
    private int unknown;
    private int errors;
    private int waitTicks;
    private int awaitElapsed;
    private boolean finished;

    private Runner(MinecraftServer server, List<String> steps) {
      this.server = server;
      this.steps = steps;
    }

    /** Runs at most one step per server tick so chunk tickets and scheduled work can progress. */
    private void tick() {
      if (finished) {
        return;
      }
      if (waitTicks > 0) {
        waitTicks--;
        return;
      }
      if (index >= steps.size()) {
        finish();
        return;
      }
      String step = steps.get(index);
      int number = index + 1;
      try {
        if (step.startsWith("!")) {
          if (!directive(number, step)) {
            return; // still waiting on this directive; retry next tick
          }
        } else {
          command(number, step);
        }
      } catch (Throwable failure) {
        errors++;
        LOGGER.error("AEBM_AUTORUN_ERROR {} {} :: {}", number, step, failure.toString());
      }
      index++;
    }

    private void command(int number, String step) {
      String command = Commands.trimOptionalPrefix(step);
      CommandSourceStack source = server.createCommandSourceStack();
      ParseResults<CommandSourceStack> parse = server.getCommands().getDispatcher().parse(command, source);
      CommandSyntaxException invalid = Commands.getParseException(parse);
      if (invalid != null) {
        unknown++;
        LOGGER.error("AEBM_AUTORUN_UNKNOWN_COMMAND {} {} :: {}", number, command, invalid.getMessage());
        return;
      }
      LOGGER.info("AEBM_AUTORUN_EXEC {} {}", number, command);
      long start = System.nanoTime();
      server.getCommands().performCommand(parse, command);
      LOGGER.info("AEBM_AUTORUN_DONE {} millis={}", number, (System.nanoTime() - start) / 1_000_000L);
    }

    /** Returns true when the directive is complete and the runner may advance. */
    private boolean directive(int number, String step) {
      String[] parts = step.substring(1).trim().split("\\s+");
      String name = parts[0].toLowerCase(Locale.ROOT);
      switch (name) {
        case "wait" -> {
          require(parts.length == 2, "usage: !wait ticks");
          waitTicks = Math.max(0, Integer.parseInt(parts[1]));
          LOGGER.info("AEBM_AUTORUN_WAIT {} ticks={}", number, waitTicks);
          return true;
        }
        case "await_loaded" -> {
          require(parts.length == 6, "usage: !await_loaded minX minZ maxX maxZ timeoutTicks");
          int minX = Integer.parseInt(parts[1]);
          int minZ = Integer.parseInt(parts[2]);
          int maxX = Integer.parseInt(parts[3]);
          int maxZ = Integer.parseInt(parts[4]);
          int timeout = Integer.parseInt(parts[5]);
          ServerLevel level = server.overworld();
          int missing = 0;
          int total = 0;
          for (int cx = Math.floorDiv(minX, 16); cx <= Math.floorDiv(maxX, 16); cx++) {
            for (int cz = Math.floorDiv(minZ, 16); cz <= Math.floorDiv(maxZ, 16); cz++) {
              total++;
              if (!level.hasChunk(cx, cz) || level.getChunkSource().getChunkNow(cx, cz) == null) {
                missing++;
              }
            }
          }
          if (missing == 0) {
            LOGGER.info("AEBM_AUTORUN_LOADED {} chunks={} ticks={}", number, total, awaitElapsed);
            awaitElapsed = 0;
            return true;
          }
          if (++awaitElapsed > timeout) {
            awaitElapsed = 0;
            throw new IllegalStateException(missing + " of " + total + " chunks still not loaded after " + timeout + " ticks");
          }
          return false;
        }
        default -> throw new IllegalArgumentException("unknown directive " + parts[0]);
      }
    }

    private void finish() {
      finished = true;
      LOGGER.info("AEBM_AUTORUN_END steps={} unknown={} errors={}", steps.size(), unknown, errors);
      // Same path as the vanilla stop command: leave the tick loop, save, exit the JVM normally.
      server.halt(false);
    }
  }

  private static int timeoutSeconds() {
    try {
      return Integer.parseInt(System.getProperty(TIMEOUT_PROPERTY, Integer.toString(DEFAULT_TIMEOUT_SECONDS)).trim());
    } catch (NumberFormatException invalid) {
      return DEFAULT_TIMEOUT_SECONDS;
    }
  }

  private static void startWatchdog(int seconds) {
    Thread watchdog = new Thread(() -> {
      try {
        Thread.sleep(seconds * 1000L);
        LOGGER.error("AEBM_AUTORUN_TIMEOUT after {} seconds; stopping the fixture server", seconds);
        MinecraftServer current = server;
        if (current != null) {
          current.halt(false);
        }
        Thread.sleep(HALT_GRACE_SECONDS * 1000L);
        LOGGER.error("AEBM_AUTORUN_TIMEOUT clean stop did not finish; halting the fixture JVM");
        Runtime.getRuntime().halt(124);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }, "AEBM fixture autorun watchdog");
    watchdog.setDaemon(true);
    watchdog.start();
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
