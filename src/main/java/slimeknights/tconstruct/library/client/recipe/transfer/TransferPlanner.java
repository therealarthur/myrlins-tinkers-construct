package slimeknights.tconstruct.library.client.recipe.transfer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Plans a recipe viewer "+" transfer as ordinary left and right clicks on the open menu.
 * <p>
 * Why clicks: REI's own transfer runs REI's server-side slot crafter, which writes stacks through raw slot getters
 * and drops an item it already took when the target slot refuses it; with Tinkers' copy-returning side inventory
 * slots that is the same lost-write path as the shift-click item loss (#29). Plain clicks go through the server's
 * normal {@code AbstractContainerMenu.clicked} handling, exactly as if the player moved each item by hand, so the
 * server's own slot rules decide every move and the transfer needs no server-side REI support.
 * <p>
 * The plan is complete before any click is sent: it first returns everything in the target slots to the player's
 * inventory, then moves the recipe's items from the source slots, and finally replays itself on a {@link ClickModel}
 * and checks that every item identity and count is conserved. A plan that cannot be completed is never started.
 * Viewer-neutral and headless-testable.
 */
public final class TransferPlanner {
  private TransferPlanner() {}

  /** Default click budget; a max transfer shrinks its craft count to stay within it. */
  public static final int DEFAULT_CLICK_BUDGET = 512;
  /** Most crafts a max transfer attempts, one stack. */
  public static final int MAX_CRAFTS = 64;

  /** One target slot and the item alternatives it accepts; each alternative's count is the amount per craft. */
  public record Requirement(int slot, List<ItemStack> alternatives) {}

  /** A PICKUP click on a menu slot: button 0 is a left click, 1 a right click. */
  public record Click(int slot, int button) {}

  /**
   * @param requirements  targets to fill; requirements with no alternatives are left empty
   * @param clear         target slots emptied before filling (the whole grid or station input area)
   * @param sources       slots to take items from, in preference order
   * @param dump          slots that receive cleared items and leftovers, in order (the player's inventory)
   * @param max           true for a "craft as many as possible" transfer
   * @param clickBudget   largest number of clicks to send
   */
  public record Request(List<Requirement> requirements, List<Integer> clear, List<Integer> sources, List<Integer> dump, boolean max, int clickBudget) {}

  public sealed interface Plan permits Ready, Missing, Blocked {}
  /** Clicks to send and the number of crafts they prepare. */
  public record Ready(List<Click> clicks, int crafts) implements Plan {}
  /** Indices into the requirement list that the sources cannot satisfy even once. */
  public record Missing(List<Integer> requirements) implements Plan {}
  /** A reason the transfer cannot start, as a translation key suffix under {@code rei.tconstruct.transfer.}. */
  public record Blocked(String reason) implements Plan {}

  /** Plans the transfer against the current contents of the slots. */
  public static Plan plan(IntFunction<Slot> slots, @Nullable Player player, ItemStack carried, Request request) {
    if (!carried.isEmpty()) return new Blocked("cursor");
    for (Requirement requirement : request.requirements()) {
      for (ItemStack alternative : requirement.alternatives()) {
        if (alternative.getItem() instanceof BundleItem) return new Blocked("unsupported_item");
      }
    }

    // 1. clear the target area into the dump slots
    ClickModel cleared = new ClickModel(slots, player, carried);
    List<Click> clearClicks = new ArrayList<>();
    for (int slot : request.clear()) {
      if (cleared.get(slot).isEmpty()) continue;
      if (!cleared.canTake(slot)) return new Blocked("locked_input");
      clearClicks.add(click(cleared, slot, 0));
      if (!dumpCarried(cleared, request.dump(), clearClicks)) return new Blocked("no_room");
    }

    // 2. allocate and place, shrinking a max transfer until it fits the click budget
    int upper = request.max() ? MAX_CRAFTS : 1;
    List<Integer> missing = List.of();
    for (int crafts = upper; crafts >= 1; crafts--) {
      Map<Integer, ItemStack> snapshot = new HashMap<>();
      ClickModel model = replay(slots, player, carried, clearClicks, snapshot);
      Allocation allocation = allocate(model, request, crafts);
      if (!allocation.missing.isEmpty()) {
        if (crafts == 1) missing = allocation.missing;
        continue;
      }
      List<Click> clicks = new ArrayList<>(clearClicks);
      if (!place(model, allocation, request.dump(), clicks)) continue;
      if (clicks.size() > request.clickBudget()) continue;
      String problem = verify(slots, player, carried, clicks, request, allocation, crafts);
      if (problem != null) return new Blocked(problem);
      return new Ready(List.copyOf(clicks), crafts);
    }
    if (!missing.isEmpty()) return new Missing(missing);
    return new Blocked("too_many_clicks");
  }

  /** Applies the click to the model and returns it. */
  private static Click click(ClickModel model, int slot, int button) {
    model.click(slot, button);
    return new Click(slot, button);
  }

  /** Rebuilds a model with the given clicks already applied. */
  private static ClickModel replay(IntFunction<Slot> slots, @Nullable Player player, ItemStack carried, List<Click> clicks, Map<Integer, ItemStack> unused) {
    ClickModel model = new ClickModel(slots, player, carried);
    for (Click click : clicks) model.click(click.slot(), click.button());
    return model;
  }

  /** Puts the carried stack into dump slots, same items first, then empty slots. False when some remains. */
  private static boolean dumpCarried(ClickModel model, List<Integer> dump, List<Click> clicks) {
    for (int pass = 0; pass < 2 && !model.carried().isEmpty(); pass++) {
      for (int slot : dump) {
        if (model.carried().isEmpty()) break;
        ItemStack current = model.get(slot);
        boolean same = !current.isEmpty() && ItemStack.isSameItemSameComponents(current, model.carried());
        boolean room = same && current.getCount() < model.slot(slot).getMaxStackSize(current);
        if (pass == 0 ? room : current.isEmpty()) {
          int before = model.carried().getCount();
          model.click(slot, 0);
          if (model.carried().getCount() != before) clicks.add(new Click(slot, 0));
        }
      }
    }
    return model.carried().isEmpty();
  }

  /** Where each requirement takes its items from. */
  private record Allocation(Map<Integer, ItemStack> chosen, Map<Integer, Map<Integer, Integer>> bySource, List<Integer> missing) {}

  /** Greedy allocation for the craft count; narrow requirements first so broad ones do not starve them. */
  private static Allocation allocate(ClickModel model, Request request, int crafts) {
    Map<Integer, Integer> remaining = new LinkedHashMap<>();
    for (int source : request.sources()) {
      ItemStack stack = model.get(source);
      if (!stack.isEmpty() && !(stack.getItem() instanceof BundleItem) && model.canTake(source)) {
        remaining.put(source, stack.getCount());
      }
    }
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < request.requirements().size(); i++) {
      if (!request.requirements().get(i).alternatives().isEmpty()) order.add(i);
    }
    order.sort(Comparator.comparingInt(i -> request.requirements().get(i).alternatives().size()));

    Map<Integer, ItemStack> chosen = new LinkedHashMap<>();
    Map<Integer, Map<Integer, Integer>> bySource = new LinkedHashMap<>();
    List<Integer> missing = new ArrayList<>();
    for (int index : order) {
      Requirement requirement = request.requirements().get(index);
      Slot target = model.slot(requirement.slot());
      boolean found = false;
      for (ItemStack alternative : requirement.alternatives()) {
        if (alternative.isEmpty()) continue;
        int need = crafts * alternative.getCount();
        if (need > alternative.getMaxStackSize() || need > target.getMaxStackSize(alternative) || !target.mayPlace(alternative)) continue;
        int total = 0;
        for (var entry : remaining.entrySet()) {
          if (ItemStack.isSameItemSameComponents(model.get(entry.getKey()), alternative)) total += entry.getValue();
        }
        if (total < need) continue;
        int left = need;
        for (var entry : remaining.entrySet()) {
          if (left == 0) break;
          if (!ItemStack.isSameItemSameComponents(model.get(entry.getKey()), alternative) || entry.getValue() == 0) continue;
          int take = Math.min(left, entry.getValue());
          entry.setValue(entry.getValue() - take);
          bySource.computeIfAbsent(entry.getKey(), key -> new LinkedHashMap<>()).merge(requirement.slot(), take, Integer::sum);
          left -= take;
        }
        chosen.put(requirement.slot(), alternative.copyWithCount(need));
        found = true;
        break;
      }
      if (!found) missing.add(index);
    }
    missing.sort(Integer::compareTo);
    return new Allocation(chosen, bySource, missing);
  }

  /** Emits pickup, place and put-back clicks for every source. False if the model rejects a move. */
  private static boolean place(ClickModel model, Allocation allocation, List<Integer> dump, List<Click> clicks) {
    // follow source preference order, which is the insertion order of the allocation map
    for (var sourceEntry : allocation.bySource().entrySet()) {
      int source = sourceEntry.getKey();
      clicks.add(click(model, source, 0));
      for (var target : sourceEntry.getValue().entrySet()) {
        int amount = target.getValue();
        int before = model.get(target.getKey()).getCount();
        if (model.carried().getCount() == amount) {
          clicks.add(click(model, target.getKey(), 0));
        } else {
          for (int i = 0; i < amount; i++) clicks.add(click(model, target.getKey(), 1));
        }
        if (model.get(target.getKey()).getCount() != before + amount) return false;
      }
      if (!model.carried().isEmpty()) {
        // put the rest back where it came from; if that slot refuses it, it goes to the inventory instead
        int before = model.carried().getCount();
        model.click(source, 0);
        if (model.carried().getCount() != before) clicks.add(new Click(source, 0));
        if (!dumpCarried(model, dump, clicks)) return false;
      }
    }
    return model.carried().isEmpty();
  }

  /** Replays the plan from scratch and checks targets and conservation. Returns a problem key or null. */
  @Nullable
  private static String verify(IntFunction<Slot> slots, @Nullable Player player, ItemStack carried, List<Click> clicks, Request request,
                               Allocation allocation, int crafts) {
    List<Integer> involved = new ArrayList<>();
    involved.addAll(request.clear());
    involved.addAll(request.sources());
    involved.addAll(request.dump());
    for (Requirement requirement : request.requirements()) involved.add(requirement.slot());
    for (Click click : clicks) involved.add(click.slot());
    List<Integer> distinct = involved.stream().distinct().toList();

    ClickModel before = new ClickModel(slots, player, carried);
    List<ItemStack> start = census(before, distinct);
    ClickModel after = new ClickModel(slots, player, carried);
    for (Click click : clicks) after.click(click.slot(), click.button());
    if (!after.carried().isEmpty()) return "internal";
    if (!sameCensus(start, census(after, distinct))) return "internal";
    for (var entry : allocation.chosen().entrySet()) {
      ItemStack result = after.get(entry.getKey());
      if (!ItemStack.isSameItemSameComponents(result, entry.getValue()) || result.getCount() != entry.getValue().getCount()) return "internal";
    }
    return null;
  }

  /** Totals every item identity (item plus components) in the slots and the cursor, one stack per identity. */
  public static List<ItemStack> census(ClickModel model, List<Integer> slots) {
    List<ItemStack> totals = new ArrayList<>();
    for (int slot : slots) add(totals, model.get(slot));
    add(totals, model.carried());
    return totals;
  }

  /** Totals for the given stacks, merging equal identities; counts may exceed a stack size. */
  public static List<ItemStack> census(List<ItemStack> stacks) {
    List<ItemStack> totals = new ArrayList<>();
    for (ItemStack stack : stacks) add(totals, stack);
    return totals;
  }

  /** True when both censuses hold the same identities with the same totals. */
  public static boolean sameCensus(List<ItemStack> first, List<ItemStack> second) {
    if (first.size() != second.size()) return false;
    for (ItemStack stack : first) {
      boolean matched = false;
      for (ItemStack other : second) {
        if (ItemStack.isSameItemSameComponents(stack, other)) {
          matched = stack.getCount() == other.getCount();
          break;
        }
      }
      if (!matched) return false;
    }
    return true;
  }

  private static void add(List<ItemStack> totals, ItemStack stack) {
    if (stack.isEmpty()) return;
    for (ItemStack total : totals) {
      if (ItemStack.isSameItemSameComponents(total, stack)) {
        // counts can exceed the item's stack size here; setCount does not clamp
        total.setCount(total.getCount() + stack.getCount());
        return;
      }
    }
    totals.add(stack.copy());
  }
}
