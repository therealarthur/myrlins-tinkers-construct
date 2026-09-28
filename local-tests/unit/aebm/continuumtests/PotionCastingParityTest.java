package aebm.continuumtests;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.casting.TipClearingCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.TippingCastingRecipe;
import slimeknights.tconstruct.library.recipe.ingredient.NoContainerIngredient;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import static org.junit.jupiter.api.Assertions.*;

final class PotionCastingParityTest {
  private static final ModifierId MODIFIER = new ModifierId("tconstruct:tipped");
  private static final Identifier OTHER = Identifier.fromNamespaceAndPath("aebm_test", "unrelated");

  @BeforeAll static void components() { ComponentTestSetup.initialize(); }

  @Test
  void tippingUsesPotionComponentsAndClearingPersistsWithoutChangingInput() {
    TippingCastingRecipe tip = new TippingCastingRecipe(
      typed(TinkerSmeltery.tableTippingRecipeSerializer.get()),
      Identifier.fromNamespaceAndPath("aebm_test", "tip"), "", Ingredient.of(Items.STICK),
      FluidIngredient.of(Fluids.WATER, 50), 5, MODIFIER);
    TipClearingCastingRecipe clear = new TipClearingCastingRecipe(
      typed(TinkerSmeltery.tableTipClearingRecipeSerializer.get()),
      Identifier.fromNamespaceAndPath("aebm_test", "clear"), "", Ingredient.of(Items.STICK),
      FluidIngredient.of(Fluids.WATER, 50), 5, MODIFIER);
    ItemStack original = new ItemStack(Items.STICK);
    ToolStack initial = ToolStack.from(original);
    initial.getPersistentData().putString(OTHER, "keep this");
    original = initial.copyStack(original);
    original.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Potion test"));
    ItemStack snapshot = original.copy();
    FluidStack fluid = new FluidStack(Fluids.WATER, 50);
    fluid.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
    ItemStack tipped = tip.assemble(container(original, fluid, null));
    assertEquals("minecraft:healing", ToolStack.from(tipped).getPersistentData().getString(MODIFIER.getId()));
    assertTrue(ItemStack.matches(snapshot, original), "tipping must not mutate the input stack");
    ItemStack tippedSnapshot = tipped.copy();
    ItemStack cleared = clear.assemble(container(tipped, new FluidStack(Fluids.WATER, 50), null));
    assertEquals("", ToolStack.from(cleared).getPersistentData().getString(MODIFIER.getId()), "clear must write the copied tool data back");
    assertEquals("keep this", ToolStack.from(cleared).getPersistentData().getString(OTHER));
    assertEquals(original.get(DataComponents.CUSTOM_NAME), cleared.get(DataComponents.CUSTOM_NAME));
    assertTrue(ItemStack.matches(tippedSnapshot, tipped), "clearing must not mutate the input stack");

    CompoundTag legacy = new CompoundTag();
    legacy.putString("Potion", "minecraft:poison");
    ItemStack legacyTipped = tip.assemble(container(original, FluidStack.EMPTY, legacy));
    assertEquals("minecraft:poison", ToolStack.from(legacyTipped).getPersistentData().getString(MODIFIER.getId()));
  }

  @Test
  void noContainerIngredientAcceptsEmptyRemaindersAndRejectsFilledContainers() {
    Ingredient ingredient = NoContainerIngredient.of(Items.STICK, Items.WATER_BUCKET, Items.BUCKET);
    assertTrue(ingredient.test(new ItemStack(Items.STICK)));
    assertTrue(ingredient.test(new ItemStack(Items.BUCKET)));
    assertFalse(ingredient.test(new ItemStack(Items.WATER_BUCKET)));
    assertFalse(ingredient.test(new ItemStack(Items.DIRT)));
    assertFalse(ingredient.test(ItemStack.EMPTY));
  }

  @Test
  void modifierDisplayPersistsCallbackDataAfterComponentCopy() {
    ItemStack input = new ItemStack(Items.STICK, 4);
    input.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keep my component"));
    ItemStack snapshot = input.copy();
    ItemStack display = slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe.withModifiers(
      input, 3, java.util.List.of(), data -> data.putString(MODIFIER.getId(), "minecraft:healing"));
    assertEquals(3, display.getCount());
    assertEquals("minecraft:healing", ToolStack.from(display).getPersistentData().getString(MODIFIER.getId()));
    assertEquals(input.get(DataComponents.CUSTOM_NAME), display.get(DataComponents.CUSTOM_NAME));
    assertTrue(ItemStack.matches(snapshot, input));
  }

  private static ICastingContainer container(ItemStack stack, FluidStack fluid, CompoundTag legacy) {
    return new ICastingContainer() {
      @Override public ItemStack getStack() { return stack; }
      @Override public Fluid getFluid() { return fluid.getFluid(); }
      @Override public FluidStack getFluidStack() { return fluid; }
      @Override public CompoundTag getFluidTag() { return legacy; }
    };
  }

  private static <T extends net.minecraft.world.item.crafting.Recipe<?>> TypeAwareRecipeSerializer<T> typed(net.minecraft.world.item.crafting.RecipeSerializer<T> serializer) {
    return new TypeAwareRecipeSerializer<>() {
      @Override public net.minecraft.world.item.crafting.RecipeType<?> getType() {
        return slimeknights.tconstruct.library.recipe.TinkerRecipeTypes.CASTING_TABLE.get();
      }
      @Override public net.minecraft.world.item.crafting.RecipeSerializer<T> getSerializer() { return serializer; }
    };
  }
}
