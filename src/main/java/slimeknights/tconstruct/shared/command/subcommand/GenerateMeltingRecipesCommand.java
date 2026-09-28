package slimeknights.tconstruct.shared.command.subcommand;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.tags.TagKey;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import slimeknights.mantle.command.GeneratePackHelper;
import slimeknights.mantle.command.MantleCommand;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.item.ItemPredicate;
import slimeknights.mantle.fluid.transfer.FluidContainerTransferManager;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer.TransferDirection;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipeBuilder;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipeLookup;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

/** Generates conservative melting recipes from recipes with known static consumption semantics. */
public class GenerateMeltingRecipesCommand {
  public static final Identifier MELTING_CONFIGURATION = TConstruct.getResource("command/generate_melting_recipes.json");
  private static final String KEY_SUCCESS = TConstruct.makeTranslationKey("command", "generate.melting_recipes");
  private static final SimpleCommandExceptionType CONFIG_INVALID = new SimpleCommandExceptionType(
    TConstruct.makeTranslation("command", "generate.melting_recipes.invalid_config"));
  // These exact classes consume one item per placement slot and have a fixed output. Subclasses may not.
  private static final Set<Class<?>> SUPPORTED_RECIPES = Set.of(ShapedRecipe.class, ShapelessRecipe.class,
    SmeltingRecipe.class, BlastingRecipe.class, SmokingRecipe.class, CampfireCookingRecipe.class, StonecutterRecipe.class);

  public static void register(LiteralArgumentBuilder<CommandSourceStack> subCommand, CommandBuildContext context) {
    subCommand.requires(sender -> MantleCommand.hasPermission(sender, MantleCommand.PERMISSION_GAME_COMMANDS))
      .then(Commands.argument("recipe_type", ResourceArgument.resource(context, Registries.RECIPE_TYPE))
        .executes(GenerateMeltingRecipesCommand::run));
  }

  private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    long start = System.nanoTime();
    ServerLevel level = context.getSource().getLevel();
    RecipeType<?> type = ResourceArgument.getResource(context, "recipe_type", Registries.RECIPE_TYPE).value();
    Configuration config = readConfiguration(level);
    Map<Item,List<MeltingResult>> generated = new HashMap<>();
    Map<String,Integer> rejected = new TreeMap<>();
    MeltingCache cache = new MeltingCache();
    for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getRecipes()) {
      Recipe<?> recipe = holder.value();
      if (recipe.getType() != type || config.skipRecipes.contains(holder.id().identifier())) {
        continue;
      }
      ItemStack result = ItemStack.EMPTY;
      try {
        result = fixedResult(recipe.display());
        Item item = result.getItem();
        if (!config.melt.matches(item) || MeltingRecipeLookup.canMelt(item)) {
          continue;
        }
        if (recipe.isSpecial() || !SUPPORTED_RECIPES.contains(recipe.getClass())) {
          throw unsupported("unsupported recipe class");
        }
        // Original NBT-bearing non-damageable results were excluded. Component patches are its successor.
        if (!result.getComponentsPatch().isEmpty() && !result.isDamageableItem()) {
          throw unsupported("component-dependent output");
        }
        List<MeltingResult> fluids = infer(recipe.placementInfo(), result.getCount(), config.inputs::matches, config.ignore::matches, cache::get);
        generated.merge(item, fluids, MeltingResult::intersection);
      } catch (UnsupportedRecipe exception) {
        // A cheaper or unknown recipe for the same static item must veto a previous candidate.
        if (!result.isEmpty()) {
          generated.put(result.getItem(), List.of());
        }
        rejected.merge(exception.getMessage(), 1, Integer::sum);
        TConstruct.LOG.debug("Skipping melting generation for {}: {}", holder.id().identifier(), exception.getMessage());
      } catch (RuntimeException exception) {
        if (!result.isEmpty()) {
          generated.put(result.getItem(), List.of());
        }
        rejected.merge("recipe inspection failed (see log)", 1, Integer::sum);
        TConstruct.LOG.warn("Failed to inspect recipe {} for melting generation", holder.id().identifier(), exception);
      }
    }

    // Encode everything before writing a pack. Constructing MeltingRecipe registers lookup entries,
    // so always release the freeze, including when a builder or codec throws.
    Map<Identifier,JsonElement> encoded = new TreeMap<>();
    RecipeOutput output = new RecipeOutput() {
      @Override
      public void accept(ResourceKey<Recipe<?>> key, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
        if (conditions.length != 0) {
          throw new IllegalArgumentException("Generated melting recipes must be unconditional");
        }
        encoded.put(key.identifier(), Recipe.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), recipe).getOrThrow());
      }

      @Override
      public Advancement.Builder advancement() {
        return Advancement.Builder.advancement();
      }

      @Override
      public void includeRootAdvancement() {}
    };
    withFrozenLookup(() -> {
      generated.forEach((item, fluids) -> {
        if (!fluids.isEmpty()) {
          buildRecipe(item, fluids, output);
        }
      });
    });

    int successes = 0;
    int failures = 0;
    Path pack = GeneratePackHelper.getDatapackPath(level.getServer());
    if (!encoded.isEmpty()) {
      if (!GeneratePackHelper.saveJson(packMetadata(), pack.resolve("pack.mcmeta"))) {
        throw GeneratePackHelper.FAILED_SAVE.create("pack.mcmeta");
      }
      for (var entry : encoded.entrySet()) {
        if (GeneratePackHelper.saveJson(entry.getValue(), recipePath(pack, entry.getKey()))) {
          successes++;
        } else {
          failures++;
        }
      }
    }
    int count = successes;
    float elapsed = (System.nanoTime() - start) / 1_000_000_000f;
    context.getSource().sendSuccess(() -> Component.translatable(KEY_SUCCESS, count, elapsed, GeneratePackHelper.getOutputComponent(pack)), true);
    if (!rejected.isEmpty()) {
      int total = rejected.values().stream().mapToInt(Integer::intValue).sum();
      context.getSource().sendSuccess(() -> Component.literal("Skipped " + total + " recipes that could not be inferred safely: " + rejected), false);
    }
    if (failures > 0) {
      context.getSource().sendFailure(Component.literal("Failed to save " + failures + " generated melting recipes; see the server log."));
    }
    return successes;
  }

  private static Configuration readConfiguration(ServerLevel level) throws CommandSyntaxException {
    var resource = level.getServer().getResourceManager().getResource(MELTING_CONFIGURATION);
    if (resource.isPresent()) {
      JsonObject json = JsonHelper.getJson(resource.get(), MELTING_CONFIGURATION);
      if (json != null) {
        try {
          return new Configuration(ItemPredicate.LOADER.getOrDefault(json, "melt"), ItemPredicate.LOADER.getOrDefault(json, "inputs"),
            ItemPredicate.LOADER.getOrDefault(json, "ignore"), Set.copyOf(Loadables.RESOURCE_LOCATION.list(0).getOrDefault(json, "skip_recipes", List.of())));
        } catch (RuntimeException exception) {
          TConstruct.LOG.error("Invalid melting generation configuration {} from {}", MELTING_CONFIGURATION, resource.get().sourcePackId(), exception);
        }
      }
    }
    throw CONFIG_INVALID.create();
  }

  private record Configuration(IJsonPredicate<Item> melt, IJsonPredicate<Item> inputs, IJsonPredicate<Item> ignore, Set<Identifier> skipRecipes) {}

  /** Accept only literal results; example/tag/transformation displays do not establish actual output. */
  static ItemStack fixedResult(List<RecipeDisplay> displays) {
    ItemStack result = ItemStack.EMPTY;
    for (RecipeDisplay display : displays) {
      ItemStack next;
      if (display.result() instanceof SlotDisplay.ItemStackSlotDisplay literal) {
        next = literal.stack().create();
      } else if (display.result() instanceof SlotDisplay.ItemSlotDisplay literal) {
        next = new ItemStack(literal.item());
      } else {
        throw unsupported("dynamic or nonliteral output display");
      }
      if (next.isEmpty()) {
        throw unsupported("empty output display");
      }
      if (!result.isEmpty() && (result.getCount() != next.getCount() || !ItemStack.isSameItemSameComponents(result, next))) {
        throw unsupported("ambiguous output displays");
      }
      result = next;
    }
    if (result.isEmpty()) {
      throw unsupported("no output display");
    }
    return result;
  }

  /** Uses the placement indices, so repeated slots remain repeated even when their ingredient is shared. */
  @SuppressWarnings("deprecation")
  static List<MeltingResult> infer(PlacementInfo placement, int outputCount, Predicate<Item> inputs, Predicate<Item> ignore, Function<Item,MeltingResult> lookup) {
    if (placement.isImpossibleToPlace() || outputCount <= 0) {
      throw unsupported("nonplaceable recipe");
    }
    List<MeltingResult> fluids = new ArrayList<>();
    for (int index : placement.slotsToIngredientIndex()) {
      if (index == PlacementInfo.EMPTY_SLOT) {
        continue;
      }
      if (index < 0 || index >= placement.ingredients().size()) {
        throw unsupported("invalid ingredient placement");
      }
      Ingredient ingredient = placement.ingredients().get(index);
      if (!ingredient.isSimple()) {
        throw unsupported("component-dependent ingredient");
      }
      var alternatives = ingredient.items().toList();
      if (alternatives.isEmpty()) {
        throw unsupported("empty ingredient alternatives");
      }
      MeltingResult chosen = null;
      boolean ignored = false;
      for (var alternative : alternatives) {
        Item item = alternative.value();
        if (!inputs.test(item)) {
          throw unsupported("input excluded by configuration");
        }
        MeltingResult fluid = lookup.apply(item);
        if (fluid.fluid.isEmpty()) {
          if (chosen != null || !ignore.test(item)) {
            throw unsupported("unmeltable or mixed ingredient alternatives");
          }
          ignored = true;
        } else {
          if (ignored || chosen != null && !MeltingResult.matches(chosen, fluid)) {
            throw unsupported("different-fluid ingredient alternatives");
          }
          chosen = chosen == null ? fluid : MeltingResult.min(chosen, fluid);
        }
      }
      if (chosen != null) {
        boolean merged = false;
        for (int i = 0; i < fluids.size(); i++) {
          if (MeltingResult.matches(fluids.get(i), chosen)) {
            fluids.set(i, MeltingResult.merge(fluids.get(i), chosen));
            merged = true;
            break;
          }
        }
        if (!merged) {
          fluids.add(chosen);
        }
      }
    }
    List<MeltingResult> scaled = new ArrayList<>();
    for (MeltingResult fluid : fluids) {
      int amount = fluid.fluid.getAmount() / outputCount;
      if (amount > 0) {
        scaled.add(fluid.withAmount(amount));
      }
    }
    return scaled;
  }

  static void buildRecipe(Item item, List<MeltingResult> fluids, RecipeOutput output) {
    List<MeltingResult> sorted = new ArrayList<>(fluids);
    sorted.sort(Comparator.comparingInt(MeltingResult::temperature).reversed()
      .thenComparing(Comparator.comparingInt((MeltingResult result) -> result.fluid.getAmount()).reversed())
      .thenComparing(result -> BuiltInRegistries.FLUID.getKey(result.fluid.getFluid())));
    MeltingResult first = sorted.getFirst();
    MeltingRecipeBuilder builder = MeltingRecipeBuilder.melting(Ingredient.of(item), first.toOutput(), first.temperature, 1.0f);
    for (int i = 1; i < sorted.size(); i++) {
      builder.addByproduct(sorted.get(i).toOutput());
    }
    if (new ItemStack(item).isDamageableItem()) {
      builder.setDamagable(10);
    }
    Identifier id = BuiltInRegistries.ITEM.getKey(item);
    builder.save(output, Identifier.fromNamespaceAndPath("tinkers_generated", "melting/" + id.getNamespace() + '/' + id.getPath()));
  }

  static void withFrozenLookup(Runnable action) {
    MeltingRecipeLookup.freeze();
    try {
      action.run();
    } finally {
      MeltingRecipeLookup.unfreeze();
    }
  }

  static JsonObject packMetadata() {
    JsonObject json = new JsonObject();
    json.add("pack", PackMetadataSection.SERVER_TYPE.codec().encodeStart(JsonOps.INSTANCE,
      new PackMetadataSection(Component.literal("Melting recipes generated by Tinkers' Construct."),
        new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA)))).getOrThrow());
    return json;
  }

  static Path recipePath(Path pack, Identifier id) {
    Path directory = pack.resolve("data").resolve(id.getNamespace()).resolve("recipe").normalize();
    Path file = directory.resolve(id.getPath() + ".json").normalize();
    if (!file.startsWith(directory)) {
      throw unsupported("recipe path escapes generated pack");
    }
    return file;
  }

  static final class UnsupportedRecipe extends IllegalArgumentException {
    UnsupportedRecipe(String reason) {
      super(reason);
    }
  }

  private static UnsupportedRecipe unsupported(String reason) {
    return new UnsupportedRecipe(reason);
  }

  /** Tagged outputs remain tagged; component identity is never discarded when comparing fluids. */
  record MeltingResult(FluidStack fluid, @Nullable TagKey<Fluid> tag, int temperature) {
    static final MeltingResult EMPTY = new MeltingResult(FluidStack.EMPTY, null, 0);

    static MeltingResult from(FluidStack fluid, @Nullable TagKey<Fluid> tag, int temperature) {
      if (fluid.isEmpty()) {
        return EMPTY;
      }
      // Current Core's FluidOutput JSON supports custom_data but no other component patches.
      if (fluid.getComponentsPatch().entrySet().stream().anyMatch(entry -> entry.getKey() != DataComponents.CUSTOM_DATA || entry.getValue().isEmpty())) {
        throw unsupported("fluid components unsupported by output codec");
      }
      return new MeltingResult(fluid.copy(), tag, temperature);
    }

    static MeltingResult from(FluidStack fluid) {
      return from(fluid, null, fluid.isEmpty() ? 0 : Math.max(100, fluid.getFluid().getFluidType().getTemperature(fluid) - 300));
    }

    MeltingResult withAmount(int amount) {
      return amount <= 0 ? EMPTY : new MeltingResult(fluid.copyWithAmount(amount), tag, temperature);
    }

    FluidOutput toOutput() {
      if (tag != null) {
        var data = fluid.get(DataComponents.CUSTOM_DATA);
        return FluidOutput.fromTag(tag, fluid.getAmount(), data == null ? null : data.copyTag());
      }
      return FluidOutput.fromStack(fluid.copy());
    }

    static boolean matches(MeltingResult first, MeltingResult second) {
      return (first.tag == null || second.tag == null || first.tag.equals(second.tag))
        && FluidStack.isSameFluidSameComponents(first.fluid, second.fluid);
    }

    static MeltingResult simpler(MeltingResult first, MeltingResult second) {
      return first.tag == null ? first : second;
    }

    static MeltingResult merge(MeltingResult first, MeltingResult second) {
      try {
        return simpler(first, second).withAmount(Math.addExact(first.fluid.getAmount(), second.fluid.getAmount()));
      } catch (ArithmeticException exception) {
        throw unsupported("fluid amount overflow");
      }
    }

    static MeltingResult min(MeltingResult first, MeltingResult second) {
      return simpler(first, second).withAmount(Math.min(first.fluid.getAmount(), second.fluid.getAmount()));
    }

    static List<MeltingResult> intersection(List<MeltingResult> first, List<MeltingResult> second) {
      List<MeltingResult> result = new ArrayList<>();
      // Small lists: direct matching handles multiple components/tags of the same fluid without
      // relying on a fluid-name comparator to establish component equality.
      List<MeltingResult> remaining = new ArrayList<>(second);
      for (MeltingResult candidate : first) {
        for (int i = 0; i < remaining.size(); i++) {
          if (matches(candidate, remaining.get(i))) {
            result.add(min(candidate, remaining.remove(i)));
            break;
          }
        }
      }
      return result;
    }
  }

  static MeltingResult readFluidHandler(ResourceHandler<FluidResource> handler) {
    FluidStack contained = FluidStack.EMPTY;
    for (int tank = 0; tank < handler.size(); tank++) {
      FluidResource resource = handler.getResource(tank);
      long amount = handler.getAmountAsLong(tank);
      if (!resource.isEmpty() && amount > 0) {
        if (!contained.isEmpty()) {
          throw unsupported("multiple fluid tanks in ingredient");
        }
        if (amount > Integer.MAX_VALUE) {
          throw unsupported("fluid amount overflow");
        }
        contained = resource.toStack((int) amount);
      }
    }
    return MeltingResult.from(contained);
  }

  static final class MeltingCache {
    private final Map<Item,MeltingResult> cache = new HashMap<>();

    MeltingResult get(Item item) {
      return cache.computeIfAbsent(item, this::find);
    }

    private MeltingResult find(Item item) {
      ItemStack stack = new ItemStack(item);
      IFluidContainerTransfer transfer = FluidContainerTransferManager.INSTANCE.getTransfer(stack, FluidStack.EMPTY);
      if (transfer != null) {
        var result = transfer.transfer(stack.copy(), FluidStack.EMPTY, new FluidTank(10000), TransferDirection.EMPTY_ITEM);
        if (result != null && !result.didFill()) {
          return MeltingResult.from(result.fluid());
        }
      }
      if (item instanceof BucketItem bucket && bucket.getContent() != Fluids.EMPTY) {
        return MeltingResult.from(new FluidStack(bucket.getContent(), FluidType.BUCKET_VOLUME));
      }
      var handler = ItemAccess.forStack(stack).oneByOne().getCapability(Capabilities.Fluid.ITEM);
      if (handler != null) {
        MeltingResult contained = readFluidHandler(handler);
        if (!contained.fluid.isEmpty()) {
          return contained;
        }
      }
      var melting = MeltingRecipeLookup.findFluid(item);
      return MeltingResult.from(melting.result().get(), melting.result().getTag(), melting.temperature());
    }
  }
}
