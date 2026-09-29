package slimeknights.tconstruct.plugin.rei.transfer;

import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.InputIngredient;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.plugin.common.displays.crafting.CraftingDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.library.client.recipe.transfer.MenuTransferSlots;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferExecutor;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Request;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * REI "+" transfer for the menus official 3.12.1 JEI supports: vanilla crafting recipes in the crafting station and in
 * a tool's inventory grid, and modifier and tool building recipes in the tinker station and both anvils.
 * <p>
 * The move itself is planned by {@link TransferPlanner} and sent as ordinary clicks by {@link TransferExecutor}, so
 * the server applies its normal menu rules to every item (see those classes for why REI's server crafter is not
 * used). Planning runs for the button state as well; nothing is sent until the player actually clicks.
 */
public final class TinkerTransferHandler implements TransferHandler {
  private final Set<CategoryIdentifier<?>> stationCategories;
  private final CategoryIdentifier<?> modifiers;

  /**
   * @param modifiers           modifier category; its station inputs follow the display's station slot facts
   * @param stationCategories   categories transferred into the tinker station (modifiers and tool building)
   */
  public TinkerTransferHandler(CategoryIdentifier<?> modifiers, Set<CategoryIdentifier<?>> stationCategories) {
    this.modifiers = modifiers;
    this.stationCategories = stationCategories;
  }

  @Override
  public ApplicabilityResult checkApplicable(Context context) {
    AbstractContainerMenu menu = context.getMenu();
    Display display = context.getDisplay();
    if (menu == null || display == null || context.getContainerScreen() == null) return ApplicabilityResult.createNotApplicable();
    if (display instanceof CraftingDisplay crafting) {
      if (menu instanceof CraftingStationContainerMenu) return ApplicabilityResult.createApplicable();
      if (menu instanceof ToolContainerMenu tool) {
        int width = MenuTransferSlots.toolGridWidth(tool);
        if (width == 0) return ApplicabilityResult.createNotApplicable();
        if (width == 2 && (crafting.getInputWidth(2, 2) > 2 || crafting.getInputHeight(2, 2) > 2)) {
          return ApplicabilityResult.createApplicableWithError(Component.translatable("error.rei.transfer.too_small", 2, 2));
        }
        return ApplicabilityResult.createApplicable();
      }
      return ApplicabilityResult.createNotApplicable();
    }
    if (display instanceof SmelteryDisplay smeltery && menu instanceof TinkerStationContainerMenu
        && stationCategories.contains(smeltery.getCategoryIdentifier())) {
      return ApplicabilityResult.createApplicable();
    }
    return ApplicabilityResult.createNotApplicable();
  }

  @Override
  public Result handle(Context context) {
    AbstractContainerMenu menu = context.getMenu();
    Display display = context.getDisplay();
    LocalPlayer player = context.getMinecraft().player;
    if (menu == null || display == null || player == null) return Result.createNotApplicable();
    boolean max = context.isStackedCrafting();

    Request request;
    List<EntryIngredient> requested = new ArrayList<>();
    if (display instanceof CraftingDisplay crafting) {
      int width = menu instanceof ToolContainerMenu tool ? MenuTransferSlots.toolGridWidth(tool) : 3;
      List<List<ItemStack>> grid = new ArrayList<>();
      for (int i = 0; i < width * width; i++) grid.add(List.of());
      for (InputIngredient<EntryStack<?>> input : crafting.getInputIngredients(width, width)) {
        List<ItemStack> items = items(input.get());
        if (input.getIndex() >= 0 && input.getIndex() < grid.size()) grid.set(input.getIndex(), items);
        requested.add(EntryIngredients.ofItemStacks(items));
      }
      request = menu instanceof CraftingStationContainerMenu station
        ? MenuTransferSlots.craftingStation(station, player, grid, max)
        : MenuTransferSlots.toolInventory((ToolContainerMenu) menu, player, grid, max);
    } else if (display instanceof SmelteryDisplay smeltery && menu instanceof TinkerStationContainerMenu station) {
      List<List<ItemStack>> inputs = stationInputs(smeltery);
      inputs.forEach(items -> requested.add(EntryIngredients.ofItemStacks(items)));
      String problem = MenuTransferSlots.tinkerStationProblem(station, inputs);
      if ("too_large".equals(problem)) return Result.createFailed(Component.translatable("jei.tconstruct.tinker_station.too_large"));
      if (problem != null) return Result.createFailed(blocked(problem));
      request = MenuTransferSlots.tinkerStation(station, player, inputs, max);
      if (request == null) return Result.createFailed(Component.translatable("jei.tconstruct.tinker_station.too_large"));
    } else {
      return Result.createNotApplicable();
    }
    if (request == null) return Result.createNotApplicable();

    TransferPlanner.Plan plan = MenuTransferSlots.plan(menu, player, request);
    return switch (plan) {
      case TransferPlanner.Missing missing -> {
        List<EntryIngredient> missingEntries = new ArrayList<>();
        for (int index : missing.requirements()) {
          missingEntries.add(EntryIngredients.ofItemStacks(request.requirements().get(index).alternatives()));
        }
        yield Result.createFailed(Component.translatable("error.rei.not.enough.materials")).tooltipMissing(missingEntries);
      }
      case TransferPlanner.Blocked blocked -> Result.createFailed(blocked(blocked.reason()));
      case TransferPlanner.Ready ready -> {
        if (!context.isActuallyCrafting()) yield Result.createSuccessful();
        yield execute(context, menu, player, request, ready);
      }
    };
  }

  /** Returns to the container screen and sends the planned clicks through the game mode, like manual clicks. */
  private static Result execute(Context context, AbstractContainerMenu menu, LocalPlayer player, Request request, TransferPlanner.Ready ready) {
    Minecraft minecraft = context.getMinecraft();
    AbstractContainerScreen<?> screen = context.getContainerScreen();
    if (minecraft.gameMode == null || screen == null) return Result.createNotApplicable();
    minecraft.setScreen(screen);
    int containerId = menu.containerId;
    TransferExecutor.Outcome outcome = TransferExecutor.execute(menu, player, ready.clicks(), request.dump(),
      (slot, button) -> minecraft.gameMode.handleContainerInput(containerId, slot, button, ContainerInput.PICKUP, player));
    if (!outcome.complete()) {
      TConstruct.LOG.warn("REI transfer stopped after {} clicks: slot {} did not behave as planned", outcome.sent(), outcome.divergedAt());
      return Result.createFailed(blocked("diverged"));
    }
    return Result.createSuccessful();
  }

  /** Station input alternatives by station index for modifier and tool building displays. */
  private List<List<ItemStack>> stationInputs(SmelteryDisplay display) {
    List<List<ItemStack>> station = new ArrayList<>();
    if (display.getCategoryIdentifier().equals(modifiers)) {
      int[] slots = RecipeLayout.getIntArray(display.layout(), RecipeLayout.STATION_SLOTS);
      for (int i = 0; i < slots.length && i < display.inputs().size(); i++) {
        int index = slots[i];
        while (station.size() <= index) station.add(List.of());
        station.set(index, items(display.inputs().get(i)));
      }
    } else {
      // tool building: parts then extra requirements, in station order
      for (EntryIngredient input : display.inputs()) station.add(items(input));
    }
    return station;
  }

  /** Item alternatives from entries, ignoring non-item entries such as slot costs. */
  private static List<ItemStack> items(List<? extends EntryStack<?>> entries) {
    List<ItemStack> items = new ArrayList<>();
    for (EntryStack<?> entry : entries) {
      if (entry.getType().equals(VanillaEntryTypes.ITEM)) {
        ItemStack stack = entry.castValue();
        if (!stack.isEmpty()) items.add(stack.copy());
      }
    }
    return items;
  }

  private static Component blocked(String reason) {
    String fallback = switch (reason) {
      case "cursor" -> "Put down the item on your cursor first";
      case "no_room" -> "No room in your inventory for the items already in the grid";
      case "locked_input" -> "An item in the grid cannot be taken out";
      case "unsupported_item" -> "This recipe uses an item that cannot be moved automatically";
      case "too_many_clicks" -> "Too many items to move at once";
      case "diverged" -> "Transfer stopped because a slot did not accept the move";
      case "select_layout" -> "Select a station layout with enough slots for this recipe";
      default -> "Transfer is not possible here";
    };
    return Component.translatableWithFallback("rei.tconstruct.transfer." + reason, fallback);
  }
}
