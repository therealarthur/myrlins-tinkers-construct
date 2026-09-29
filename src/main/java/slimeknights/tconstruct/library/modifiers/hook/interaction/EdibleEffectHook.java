package slimeknights.tconstruct.library.modifiers.hook.interaction;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.modules.interaction.edible.EdibleModule;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.Collection;
import java.util.List;

/**
 * Hook called by {@link EdibleModule} to apply effects after eating other than restoring hunger and saturation.
 * Effects may include durability usage, clearing effects, and representative items.
 * Port of the official Tinkers' Construct 3.12.1 hook (parity oracle fixes).
 */
public interface EdibleEffectHook {
  /**
   * Runs effects upon eating food with this modifier.
   * @param tool                  Tool being eaten.
   * @param modifier              Modifier running the hook.
   * @param player                Player eating.
   * @param eatenSlot             Slot holding the eaten tool.
   * @param hunger                Hunger restored.
   * @param saturation            Saturation restored.
   * @param representativeItems   List of representative items to fill with item stacks for food mods such as Diet.
   */
  void onToolEaten(IToolStackView tool, ModifierEntry modifier, Player player, EquipmentSlot eatenSlot, int hunger, float saturation, List<ItemStack> representativeItems);

  /** Merger running all nested modules */
  record AllMerger(Collection<EdibleEffectHook> modules) implements EdibleEffectHook {
    @Override
    public void onToolEaten(IToolStackView tool, ModifierEntry modifier, Player player, EquipmentSlot eatenSlot, int hunger, float saturation, List<ItemStack> representativeItems) {
      for (EdibleEffectHook module : modules) {
        module.onToolEaten(tool, modifier, player, eatenSlot, hunger, saturation, representativeItems);
      }
    }
  }
}
