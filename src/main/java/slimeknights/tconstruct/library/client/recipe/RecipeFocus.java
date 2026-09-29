package slimeknights.tconstruct.library.client.recipe;

import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.EntityValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.MaterialValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ModifierValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.PatternValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.SlotValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.Value;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Focus rules for Tinkers' own recipe viewer entries, matching official 3.12.1 JEI ingredient identities:
 * a material focus is its exact variant (official {@code MaterialIngredientHelper} unique IDs are variant IDs),
 * a modifier focus is its modifier ID with any level (official {@code ModifierIngredientHelper}), a slot focus is its
 * slot type, a pattern its ID and an entity its type. Amounts never take part, so "2 x oak" still finds "1 x oak".
 * <p>
 * Viewer-neutral: the REI generator applies {@link #matches} to entry values, and the display-level helpers below
 * give tests the exact recipe and use sets for a focus.
 */
public final class RecipeFocus {
  private RecipeFocus() {}

  /** True when the candidate value is the same focus identity as the focus value. */
  public static boolean matches(Value focus, Value candidate) {
    return switch (focus) {
      case MaterialValue material -> candidate instanceof MaterialValue other && material.material().equals(other.material());
      case ModifierValue modifier -> candidate instanceof ModifierValue other && modifier.modifier().getId().equals(other.modifier().getId());
      case SlotValue slot -> candidate instanceof SlotValue other && slot.slots().type().equals(other.slots().type());
      case PatternValue pattern -> candidate instanceof PatternValue other && pattern.pattern().getId().equals(other.pattern().getId());
      case EntityValue entity -> candidate instanceof EntityValue other && entity.entity() == other.entity();
      default -> false;
    };
  }

  /** True when any alternative in any of the slots matches the focus. */
  public static boolean anyMatch(List<List<Value>> slots, Value focus) {
    return slots.stream().flatMap(List::stream).anyMatch(value -> matches(focus, value));
  }

  /** Displays producing the focus: the "recipes for" set. */
  public static List<RecipeDisplayData> recipesFor(Collection<RecipeDisplayData> displays, Value focus) {
    return displays.stream().filter(display -> anyMatch(display.outputs(), focus)).toList();
  }

  /** Displays consuming or requiring the focus, including lookup-only inputs: the "uses of" set. */
  public static List<RecipeDisplayData> usesOf(Collection<RecipeDisplayData> displays, Value focus) {
    return displays.stream().filter(display -> anyMatch(display.inputs(), focus) || anyMatch(display.catalysts(), focus)
      || display.lookupInputs().stream().anyMatch(value -> matches(focus, value))).toList();
  }

  /** Every display a focus opens, recipes first, like a viewer's combined focus view. */
  public static List<RecipeDisplayData> all(Collection<RecipeDisplayData> displays, Value focus) {
    return Stream.concat(recipesFor(displays, focus).stream(), usesOf(displays, focus).stream()).distinct().toList();
  }
}
