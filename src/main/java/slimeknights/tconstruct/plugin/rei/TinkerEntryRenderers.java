package slimeknights.tconstruct.plugin.rei;

import java.util.ArrayList;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.network.chat.Component;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.GuiUtil;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.*;

/** Name-based material/modifier/entity entries and the existing pattern/slot artwork. */
final class TinkerEntryRenderers {
  private TinkerEntryRenderers() {}

  /** Repair kit per material, built once (official MaterialIconIngredientRenderer caches the same way) */
  private static final java.util.Map<slimeknights.tconstruct.library.materials.definition.MaterialVariantId, net.minecraft.world.item.ItemStack> MATERIAL_ICONS =
    new java.util.concurrent.ConcurrentHashMap<>();

  static <T extends Value> EntryRenderer<T> renderer() {
    return new EntryRenderer<>() {
      @Override
      public void render(EntryStack<T> entry, GuiGraphics graphics, Rectangle bounds, int mouseX, int mouseY, float delta) {
        Value value = entry.getValue();
        if (value instanceof PatternValue pattern) {
          GuiUtil.renderPattern(graphics, pattern.pattern(), bounds.x, bounds.y);
          return;
        }
        if (value instanceof SlotValue slot) {
          String name = slot.slots().type().getName();
          String texture = switch (name) {
            case "abilities" -> "item/slot/ability";
            case "upgrades" -> "item/slot/upgrade";
            case "souls" -> "item/materials/hollow_gem";
            default -> "item/slot/" + name;
          };
          var sprite = Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_ITEMS, TConstruct.getResource(texture)));
          graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, bounds.x, bounds.y, 16, 16);
          graphics.drawString(Minecraft.getInstance().font, Integer.toString(slot.slots().count()), bounds.x + 17, bounds.y + 4, -1, true);
          return;
        }
        if (value instanceof ModifierValue modifier && bounds.width <= 18) {
          // official JEI's bookmark renderer draws the modifier icon in item-sized cells (sidebar, worktable)
          slimeknights.tconstruct.library.client.modifiers.ModifierIconManager.renderIcon(graphics, modifier.modifier().getModifier(), bounds.x, bounds.y, 100, 16);
          return;
        }
        // myrlin.2: item-sized cells (bookmarks, focus) drew these as bare text ("1 x", "Bla")
        if (value instanceof MaterialValue material && bounds.width <= 18) {
          // official MaterialIconIngredientRenderer: the repair kit made of the material
          graphics.item(MATERIAL_ICONS.computeIfAbsent(material.material(),
            id -> slimeknights.tconstruct.tools.TinkerToolParts.repairKit.get().withMaterialForDisplay(id)), bounds.x, bounds.y);
          return;
        }
        if (value instanceof EntityValue entity && bounds.width <= 18) {
          // the spawn egg, as the port's entity melting layout shows it (official draws a live entity there)
          var egg = net.minecraft.world.item.SpawnEggItem.byId(entity.entity());
          if (egg.isPresent()) {
            graphics.item(new net.minecraft.world.item.ItemStack(egg.get()), bounds.x, bounds.y);
            return;
          }
        }
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, font.plainSubstrByWidth(TinkerEntryTypes.name(value).getString(), bounds.width), bounds.x, bounds.y + 4, -1, true);
      }

      @Override
      public Tooltip getTooltip(EntryStack<T> entry, TooltipContext context) {
        var lines = new ArrayList<Component>();
        lines.add(TinkerEntryTypes.name(entry.getValue()));
        if (entry.getValue() instanceof ModifierValue modifier) lines.addAll(modifier.modifier().getModifier().getDescriptionList(modifier.modifier().getLevel()));
        if (entry.getValue() instanceof MaterialValue material && material.material().hasVariant()) lines.add(Component.literal(material.material().toString()));
        if (context.getFlag().isAdvanced()) lines.add(Component.literal(entry.getIdentifier().toString()));
        return Tooltip.create(context.getPoint(), lines);
      }
    };
  }
}
