package slimeknights.tconstruct.tools.logic;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack.LoadVerifyResult;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Re-verifies saved Tinkers tools after they are loaded (release arthur.8).
 * <p>
 * Official TConstruct 1.20.1 rebuilt a tool's derived stats every time the stack was read from NBT
 * ({@code Item#verifyTagAfterLoad}), so material, modifier, tool definition and balance changes reached tools saved
 * under older data. The NeoForge 26.1 port lost that hook: 26.1 decodes stacks through the plain ItemStack constructor
 * and offers no item callback after load. Without it, tools saved under arthur.4 or arthur.7 would keep their old
 * stats until something rebuilt them (tinker station, modifier, repair).
 * <p>
 * A stack decoded from disk is a new ItemStack instance, and ItemStack uses identity equality, so "verify once per
 * instance" is the 26.1 equivalent of "verify on load". Instances are tracked weakly and are forgotten when collected.
 * Verification runs, server side only, when:
 * <ul>
 *   <li>a player logs in: every stack in the inventory (main, armor, offhand) and the ender chest;</li>
 *   <li>a living entity is loaded from disk: its equipment (mobs, armor stands, fancy armor stands);</li>
 *   <li>a tool ticks in an inventory ({@code inventoryTick} of every modifiable item class), which covers tools that
 *       come out of chests, item frames or the ground.</li>
 * </ul>
 * The work itself, and the rules that keep materials, upgrades, persistent data, damage and broken state intact, are in
 * {@link ToolStack#verifyStackAfterLoad(ItemStack)}.
 */
public final class ToolLoadVerification {
  private ToolLoadVerification() {}

  /** Stack instances already verified this server session. Weak keys, identity semantics (ItemStack has no equals). */
  private static final Map<ItemStack, Boolean> VERIFIED = Collections.synchronizedMap(new WeakHashMap<>());

  /** Registers the event listeners, called from TinkerTools common setup. */
  public static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerEvent.PlayerLoggedInEvent.class, ToolLoadVerification::onPlayerLoggedIn);
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, EntityJoinLevelEvent.class, ToolLoadVerification::onEntityJoinLevel);
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, event -> VERIFIED.clear());
  }

  /**
   * Verifies the stack unless this exact instance was already verified. Safe to call every tick; only the first call
   * per instance does work. Stacks seen before datapack data finished loading are retried on the next call.
   * @param stack  Stack to check, modified in place when its derived data was stale
   * @return  Result of the verification, or {@link LoadVerifyResult#SKIPPED} if the instance was already verified
   */
  public static LoadVerifyResult verifyOnce(ItemStack stack) {
    if (stack.isEmpty() || !(stack.getItem() instanceof IModifiable) || VERIFIED.containsKey(stack)) {
      return LoadVerifyResult.SKIPPED;
    }
    LoadVerifyResult result = ToolStack.verifyStackAfterLoad(stack);
    if (result != LoadVerifyResult.NOT_READY) {
      VERIFIED.put(stack, Boolean.TRUE);
    }
    return result;
  }

  /** Verifies every stack in a container, returning how many were updated. */
  public static int verifyContainer(Container container) {
    int updated = 0;
    for (int i = 0; i < container.getContainerSize(); i++) {
      if (verifyOnce(container.getItem(i)) == LoadVerifyResult.UPDATED) {
        updated++;
      }
    }
    return updated;
  }

  /** Verifies every equipment slot of a living entity, returning how many were updated. */
  public static int verifyEquipment(LivingEntity living) {
    int updated = 0;
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      if (verifyOnce(living.getItemBySlot(slot)) == LoadVerifyResult.UPDATED) {
        updated++;
      }
    }
    return updated;
  }

  /** Verifies a player's inventory and ender chest, returning how many stacks were updated. */
  public static int verifyPlayer(Player player) {
    return verifyContainer(player.getInventory()) + verifyContainer(player.getEnderChestInventory());
  }

  /** Player data is read from disk right before login. */
  private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
    Player player = event.getEntity();
    if (!player.level().isClientSide()) {
      verifyPlayer(player);
    }
  }

  /** Entities read from chunk storage; players are covered by the login listener. */
  private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
    if (event.loadedFromDisk() && !event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living && !(living instanceof Player)) {
      verifyEquipment(living);
    }
  }
}
