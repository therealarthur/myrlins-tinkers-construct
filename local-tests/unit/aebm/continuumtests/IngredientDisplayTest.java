package aebm.continuumtests;

import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;

import static org.junit.jupiter.api.Assertions.*;

final class IngredientDisplayTest {
  @Test
  void customIngredientDisplayRetainsRequiredComponents() {
    var lookup = VanillaRegistries.createLookup();
    CommonHooks.markComponentClassAsValid(lookup.lookupOrThrow(Registries.ITEM).getOrThrow(ItemTags.SWORDS).getClass());
    BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
      .forEach(DataComponentInitializers.PendingComponents::apply);
    Component name = Component.literal("Required ingredient name");
    var ingredient = DataComponentIngredient.of(DataComponents.CUSTOM_NAME, name, Items.IRON_INGOT);
    assertFalse(ingredient.test(new ItemStack(Items.IRON_INGOT)), "plain item identity is not an accepted input");
    var displayed = MaterialRecipeCache.getDisplayItems(ingredient);
    assertEquals(1, displayed.size());
    assertEquals(name, displayed.getFirst().get(DataComponents.CUSTOM_NAME));
    assertTrue(ingredient.test(displayed.getFirst()), "displayed stack must satisfy the real ingredient");
  }
}
