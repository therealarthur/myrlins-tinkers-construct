package aebm.continuumtests;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.EntityValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ItemValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.MaterialValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ModifierValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.Value;
import slimeknights.tconstruct.library.client.recipe.RecipeFocus;
import slimeknights.tconstruct.library.client.recipe.RecipeWorkstations;
import slimeknights.tconstruct.library.client.recipe.RecipeWorkstations.Station;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focus identities and tool workstation rules for the REI adapter, without either viewer.
 * The display lists here are hand-built facts; the real recipe sets for a focus are checked by the server fixtures.
 */
final class RecipeFocusWorkstationTest {
  private static final MaterialVariantId OAK = MaterialVariantId.tryParse("tconstruct:wood#oak");
  private static final MaterialVariantId BIRCH = MaterialVariantId.tryParse("tconstruct:wood#birch");
  private static final MaterialVariantId WOOD = MaterialVariantId.tryParse("tconstruct:wood");

  @BeforeAll
  static void components() {
    ComponentTestSetup.initialize();
  }

  @Test
  void materialFocusIsTheExactVariantAndIgnoresAmounts() {
    var oakRecipe = display("materials", List.of(List.of(item(Items.OAK_PLANKS))), List.of(List.of(new MaterialValue(OAK, 1))));
    var birchRecipe = display("materials", List.of(List.of(item(Items.BIRCH_PLANKS))), List.of(List.of(new MaterialValue(BIRCH, 1))));
    var oakPart = display("part_builder", List.of(List.of(item(Items.OAK_PLANKS)), List.of(new MaterialValue(OAK, 2))), List.of(List.of(item(Items.STICK))));
    var woodPart = display("part_builder", List.of(List.of(item(Items.STICK)), List.of(new MaterialValue(WOOD, 2))), List.of(List.of(item(Items.STICK))));
    var composite = display("materials", List.of(List.of(item(Items.WATER_BUCKET)), List.of(new MaterialValue(OAK, 1))), List.of(List.of(new MaterialValue(BIRCH, 1))));
    List<RecipeDisplayData> all = List.of(oakRecipe, birchRecipe, oakPart, woodPart, composite);

    Value focus = new MaterialValue(OAK, 5);
    assertEquals(List.of(oakRecipe), RecipeFocus.recipesFor(all, focus), "recipes for oak are the oak material recipes only");
    assertEquals(List.of(oakPart, composite), RecipeFocus.usesOf(all, focus), "uses of oak are its part builder recipes and composites built on it");
    assertTrue(RecipeFocus.recipesFor(all, new MaterialValue(WOOD, 1)).isEmpty(), "the base material is its own identity, not a wildcard for variants");
    assertEquals(List.of(woodPart), RecipeFocus.usesOf(all, new MaterialValue(WOOD, 1)));
  }

  @Test
  void modifierFocusIgnoresLevelAndOtherKinds() {
    ModifierId haste = new ModifierId("tconstruct", "haste");
    ModifierId luck = new ModifierId("tconstruct", "luck");
    Value level1 = new ModifierValue(new ModifierEntry(haste, 1));
    Value level3 = new ModifierValue(new ModifierEntry(haste, 3));
    assertTrue(RecipeFocus.matches(level3, level1), "official modifier identity ignores the level");
    assertFalse(RecipeFocus.matches(level1, new ModifierValue(new ModifierEntry(luck, 1))));
    assertFalse(RecipeFocus.matches(level1, new MaterialValue(OAK, 1)), "different entry kinds never match");
    assertTrue(RecipeFocus.matches(new EntityValue(EntityType.ZOMBIE, false), new EntityValue(EntityType.ZOMBIE, true)), "baby and adult share the entity focus");
    assertFalse(RecipeFocus.matches(new EntityValue(EntityType.ZOMBIE, false), new EntityValue(EntityType.HUSK, false)));
  }

  @Test
  void meltingToolIsAWorkstationForMeltingAndEntityMeltingOnlyWhenMelee() {
    Set<TagKey<Modifier>> melting = Set.of(TinkerTags.Modifiers.MELTING);
    assertEquals(EnumSet.of(Station.MELTING, Station.ENTITY_MELTING), RecipeWorkstations.forTraits(melting::contains, true));
    assertEquals(EnumSet.of(Station.MELTING), RecipeWorkstations.forTraits(melting::contains, false), "a non-melee melting tool cannot melt entities");
    assertEquals(EnumSet.noneOf(Station.class), RecipeWorkstations.forTraits(tag -> false, true), "a tool without the modifiers is no workstation");
    Set<TagKey<Modifier>> several = Set.of(TinkerTags.Modifiers.CRAFTING, TinkerTags.Modifiers.SMELTING, TinkerTags.Modifiers.SEVERING);
    assertEquals(EnumSet.of(Station.CRAFTING, Station.SMELTING, Station.SEVERING), RecipeWorkstations.forTraits(several::contains, true));
  }

  @Test
  void itemsThatAreNotToolsAreNeverWorkstations() {
    assertTrue(RecipeWorkstations.forItem(Items.DIAMOND_PICKAXE).isEmpty(), "vanilla tools have no Tinkers traits");
    assertTrue(RecipeWorkstations.forItem(Items.STICK).isEmpty());
  }

  @Test
  void workstationTagsUseOfficialIds() {
    assertEquals(Identifier.fromNamespaceAndPath("tconstruct", "jei/crafting"), TinkerTags.Modifiers.CRAFTING.location());
    assertEquals(Identifier.fromNamespaceAndPath("tconstruct", "jei/smelting"), TinkerTags.Modifiers.SMELTING.location());
    assertEquals(Identifier.fromNamespaceAndPath("tconstruct", "jei/melting"), TinkerTags.Modifiers.MELTING.location());
    assertEquals(Identifier.fromNamespaceAndPath("tconstruct", "jei/severing"), TinkerTags.Modifiers.SEVERING.location());
    assertEquals(Identifier.fromNamespaceAndPath("tconstruct", "fuel_examples"), TinkerTags.Items.FUEL_EXAMPLES.location());
  }

  private static ItemValue item(net.minecraft.world.item.Item item) {
    return new ItemValue(new ItemStack(item));
  }

  private static RecipeDisplayData display(String category, List<List<Value>> inputs, List<List<Value>> outputs) {
    Identifier id = Identifier.fromNamespaceAndPath("continuum_test", category);
    return new RecipeDisplayData(Identifier.fromNamespaceAndPath("tconstruct", category), id, inputs, outputs, List.of(), List.of(), List.of());
  }
}
