package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.MaterialValue;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tables.TinkerTables;

import java.util.ArrayList;
import java.util.List;

/**
 * Materials, official {@code material/MaterialCategory} (132 by 36): the items or fluid that make the material, its
 * value or per-unit fluid amount, craftable marker, leftover, composite base, and example parts and tools.
 * <p>
 * Official draws the part builder and casting table markers from icons in its {@code melting.png}; this pack's copy
 * lacks those two icons, so the block items are drawn instead.
 */
public class MaterialCategory extends TinkerDisplayCategory {
  public MaterialCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, Renderer icon) {
    super(id, title, icon, 132, 36);
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    EntryIngredient source = get(display.inputs(), 0);
    boolean fluid = !source.isEmpty() && source.get(0).getType().equals(VanillaEntryTypes.FLUID);
    boolean items = !source.isEmpty() && !fluid;
    boolean leftover = RecipeLayout.getBoolean(layout, RecipeLayout.LEFTOVER);
    boolean composite = RecipeLayout.getBoolean(layout, RecipeLayout.COMPOSITE);
    boolean craftable = RecipeLayout.getBoolean(layout, RecipeLayout.CRAFTABLE);
    int value = RecipeLayout.getInt(layout, RecipeLayout.VALUE, 0);
    Font font = Minecraft.getInstance().font;

    if (items) {
      widgets.add(ReiLayout.slot(origin, 2, 9, source, true).markInput());
      if (leftover) {
        EntryIngredient change = ReiLayout.withTooltip(get(display.outputs(), 1),
          List.of(Component.translatable("jei.tconstruct.materials.leftover").withStyle(ChatFormatting.GRAY)));
        widgets.add(ReiLayout.slot(origin, 39, 20, change, true).markOutput());
      }
      // craftable marker; the pack's texture lacks official's icons, so draw the blocks
      ItemStack marker = new ItemStack(craftable ? TinkerTables.partBuilder.asItem() : TinkerSmeltery.searedTable.asItem());
      widgets.add(ReiLayout.item(origin, marker, 21, 20));
      widgets.add(ReiLayout.tooltip(origin, 21, 20, 16, 16, List.of(Component.translatable(craftable ? "jei.tconstruct.materials.craftable" : "jei.tconstruct.materials.uncraftable"))));
    }
    Slot fluidSlot = null;
    if (fluid) {
      widgets.add(ReiLayout.texture(origin, ReiLayout.MELTING, 3, 1, 3, 3, 14, 34));
      fluidSlot = ReiLayout.tank(origin, 4, 2, 12, 32, source, 100).markInput();
      widgets.add(fluidSlot);
    }

    // material name and composite base
    EntryIngredient material = get(display.outputs(), 0);
    widgets.add(ReiLayout.area(origin, 21, 0, 95, 10, TitleRenderer.titles(material, false)).markOutput());
    if (composite) {
      int x = 21 + (items ? (leftover ? 36 : 18) : 0);
      EntryIngredient base = ReiLayout.withTooltip(get(display.inputs(), display.inputs().size() - 1),
        List.of(Component.translatable("jei.tconstruct.materials.composite").withStyle(ChatFormatting.GRAY)));
      widgets.add(ReiLayout.slot(origin, x, 20, base, true).markInput());
    }

    // value, or the per-unit amount of the displayed fluid
    if (value > 0) {
      widgets.add(ReiLayout.text(origin, 21, 11, Component.translatable("jei.tconstruct.materials.value", value), ReiLayout.GRAY, false)
        .tooltip(Component.translatable("jei.tconstruct.materials.value.tooltip")));
    } else if (fluidSlot != null) {
      Slot shown = fluidSlot;
      widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
        EntryStack<?> current = shown.getCurrentEntry();
        if (current != null && current.getType().equals(VanillaEntryTypes.FLUID)) {
          long amount = current.<dev.architectury.fluid.FluidStack>castValue().getAmount();
          graphics.drawString(font, Component.translatable("jei.tconstruct.materials.amount", amount), origin.x() + 21, origin.y() + 11, ReiLayout.GRAY, false);
        }
      }));
      widgets.add(ReiLayout.tooltip(origin, 21, 11, 111, 9, List.of(Component.translatable("jei.tconstruct.materials.amount.tooltip"))));
    }

    // example parts and tools of this material, render-only in official
    if (!material.isEmpty() && material.get(0).getValue() instanceof MaterialValue value1) {
      widgets.add(ReiLayout.slot(origin, 116, 0, EntryIngredients.ofItemStacks(parts(value1.material())), false));
      widgets.add(ReiLayout.slot(origin, 116, 20, EntryIngredients.ofItemStacks(tools(value1.material())), false));
    }
  }

  /** Every tool part that accepts the material, with the material applied. */
  static List<ItemStack> parts(MaterialVariantId material) {
    List<ItemStack> parts = new ArrayList<>();
    for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TinkerTags.Items.TOOL_PARTS)) {
      if (holder.value() instanceof IMaterialItem part && part.canUseMaterial(material.getMaterialId())) {
        parts.add(part.withMaterialForDisplay(material));
      }
    }
    return parts;
  }

  /** Every multipart tool built entirely from the material where its parts allow it. */
  static List<ItemStack> tools(MaterialVariantId material) {
    List<ItemStack> tools = new ArrayList<>();
    MaterialVariant variant = MaterialVariant.of(material);
    for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TinkerTags.Items.MULTIPART_TOOL)) {
      if (holder.value() instanceof IModifiable modifiable) {
        try {
          ItemStack tool = ToolBuildHandler.createSingleMaterial(modifiable, variant);
          if (!tool.isEmpty()) tools.add(tool);
        } catch (RuntimeException ignored) {
          // a tool definition without data must not break the materials page
        }
      }
    }
    return tools;
  }
}
