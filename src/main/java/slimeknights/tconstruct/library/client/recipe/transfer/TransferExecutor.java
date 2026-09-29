package slimeknights.tconstruct.library.client.recipe.transfer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Click;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Sends a planned transfer one click at a time and stops the moment the menu disagrees with the plan.
 * <p>
 * The client applies each click to its own menu before sending it (vanilla prediction), so after every click the real
 * slot and cursor are compared with a {@link ClickModel} running the same plan. On any difference no further planned
 * click is sent; the executor only tries to put a held cursor stack back into an empty or matching slot of the
 * player's inventory. Items are never lost by stopping early: whatever is still on the cursor stays in the menu's
 * carried slot, which vanilla returns to the inventory (or drops at the player) when the menu closes.
 */
public final class TransferExecutor {
  private TransferExecutor() {}

  /** Sends one PICKUP click on the menu; production sends it through the game mode so the server receives it. */
  @FunctionalInterface
  public interface ClickSink {
    void click(int slot, int button);
  }

  /**
   * @param complete    true when every planned click was sent and matched
   * @param sent        clicks sent, including recovery clicks
   * @param divergedAt  index of the first planned click whose result differed, or -1
   */
  public record Outcome(boolean complete, int sent, int divergedAt) {}

  /**
   * Executes the clicks.
   * @param recovery  slots that may receive a held stack after a divergence (the player's inventory)
   */
  public static Outcome execute(AbstractContainerMenu menu, @Nullable Player player, List<Click> clicks, List<Integer> recovery, ClickSink sink) {
    ClickModel model = new ClickModel(menu::getSlot, player, menu.getCarried());
    int sent = 0;
    for (int i = 0; i < clicks.size(); i++) {
      Click click = clicks.get(i);
      model.click(click.slot(), click.button());
      sink.click(click.slot(), click.button());
      sent++;
      ItemStack real = menu.getSlot(click.slot()).getItem();
      if (!ItemStack.matches(real, model.get(click.slot())) || !ItemStack.matches(menu.getCarried(), model.carried())) {
        return new Outcome(false, sent + recover(menu, recovery, sink), i);
      }
    }
    return new Outcome(true, sent, -1);
  }

  /** Puts a held stack into matching or empty recovery slots, checking the real result of each click. */
  private static int recover(AbstractContainerMenu menu, List<Integer> recovery, ClickSink sink) {
    int sent = 0;
    for (int pass = 0; pass < 2 && !menu.getCarried().isEmpty(); pass++) {
      for (int slot : recovery) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) break;
        ItemStack current = menu.getSlot(slot).getItem();
        boolean fits = pass == 0
          ? !current.isEmpty() && ItemStack.isSameItemSameComponents(current, carried) && current.getCount() < menu.getSlot(slot).getMaxStackSize(current)
          : current.isEmpty();
        if (fits && menu.getSlot(slot).mayPlace(carried)) {
          sink.click(slot, 0);
          sent++;
        }
      }
    }
    return sent;
  }
}
