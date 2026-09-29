package slimeknights.tconstruct.library.client.recipe.transfer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * A copy of the menu contents that the transfer planner changes instead of the real menu.
 * <p>
 * {@link #click} reproduces the {@code PICKUP} branch of vanilla {@code AbstractContainerMenu.doClick} for left
 * (button 0) and right (button 1) clicks, reading the real slot's {@code mayPickup}, {@code mayPlace},
 * {@code allowModification} and {@code getMaxStackSize} rules but never changing the real slot. Every plan is
 * replayed on this model before a single real click is sent, and the executor compares each real click's result
 * with the model, so a slot that behaves differently stops the transfer instead of moving items unexpectedly.
 * <p>
 * Item override behaviors (bundles and the NeoForge stacked-on event) are not modeled; the planner never uses items
 * that have them, and the executor's comparison catches any other divergence.
 */
public final class ClickModel {
  private final IntFunction<Slot> slots;
  @Nullable
  private final Player player;
  private final Map<Integer, ItemStack> contents = new HashMap<>();
  private ItemStack carried;

  /**
   * @param slots    menu slot lookup by menu index
   * @param player   player for slot permission checks, may be null in tests of slots that ignore it
   * @param carried  current cursor stack
   */
  public ClickModel(IntFunction<Slot> slots, @Nullable Player player, ItemStack carried) {
    this.slots = slots;
    this.player = player;
    this.carried = carried.copy();
  }

  /** Stack currently modeled in the slot. */
  public ItemStack get(int slot) {
    return contents.computeIfAbsent(slot, index -> slots.apply(index).getItem().copy());
  }

  /** Modeled cursor stack. */
  public ItemStack carried() {
    return carried;
  }

  /** Real slot for rules. */
  public Slot slot(int slot) {
    return slots.apply(slot);
  }

  /**
   * True when a left click can take the slot's whole stack. Vanilla {@code Slot.tryRemove} only checks
   * {@code allowModification} for partial takes, so a whole-stack pickup needs just {@code mayPickup}; a slot that then
   * refuses the put-back has the rest moved to the player's inventory by the planner.
   */
  public boolean canTake(int slot) {
    return slots.apply(slot).mayPickup(player);
  }

  /** Applies a PICKUP click with vanilla semantics. */
  public void click(int slotIndex, int button) {
    Slot real = slots.apply(slotIndex);
    ItemStack clicked = get(slotIndex);
    boolean primary = button == 0;
    if (clicked.isEmpty()) {
      if (!carried.isEmpty()) {
        int amount = primary ? carried.getCount() : 1;
        carried = safeInsert(slotIndex, real, carried, amount);
      }
    } else if (real.mayPickup(player)) {
      if (carried.isEmpty()) {
        int amount = primary ? clicked.getCount() : (clicked.getCount() + 1) / 2;
        carried = tryRemove(slotIndex, real, amount, Integer.MAX_VALUE);
      } else if (real.mayPlace(carried)) {
        if (ItemStack.isSameItemSameComponents(clicked, carried)) {
          int amount = primary ? carried.getCount() : 1;
          carried = safeInsert(slotIndex, real, carried, amount);
        } else if (carried.getCount() <= real.getMaxStackSize(carried)) {
          contents.put(slotIndex, carried);
          carried = clicked;
        }
      } else if (ItemStack.isSameItemSameComponents(clicked, carried)) {
        ItemStack taken = tryRemove(slotIndex, real, clicked.getCount(), carried.getMaxStackSize() - carried.getCount());
        carried.grow(taken.getCount());
      }
    }
  }

  /** Vanilla Slot.safeInsert on the model. */
  private ItemStack safeInsert(int slotIndex, Slot real, ItemStack stack, int increment) {
    if (!stack.isEmpty() && real.mayPlace(stack)) {
      ItemStack current = get(slotIndex);
      int place = Math.min(Math.min(increment, stack.getCount()), real.getMaxStackSize(stack) - current.getCount());
      if (place <= 0) {
        return stack;
      }
      if (current.isEmpty()) {
        contents.put(slotIndex, stack.split(place));
      } else if (ItemStack.isSameItemSameComponents(current, stack)) {
        stack.shrink(place);
        current.grow(place);
      }
    }
    return stack;
  }

  /** Vanilla Slot.tryRemove on the model; returns the taken stack or empty. */
  private ItemStack tryRemove(int slotIndex, Slot real, int count, int decrement) {
    ItemStack current = get(slotIndex);
    if (!real.mayPickup(player)) return ItemStack.EMPTY;
    if (!real.allowModification(player) && decrement < current.getCount()) return ItemStack.EMPTY;
    int take = Math.min(count, decrement);
    ItemStack taken = current.split(take);
    if (current.isEmpty()) contents.put(slotIndex, ItemStack.EMPTY);
    return taken;
  }
}
