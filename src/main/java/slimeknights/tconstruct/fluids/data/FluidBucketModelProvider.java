package slimeknights.tconstruct.fluids.data;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;
import slimeknights.mantle.data.GenericDataProvider;

import java.util.concurrent.CompletableFuture;

/** Quick and dirty data provider to generate fluid bucket models */
public class FluidBucketModelProvider extends GenericDataProvider {
  private final String modId;
  private final PackOutput.PathProvider itemDefinitions;

  public FluidBucketModelProvider(PackOutput packOutput, String modId) {
    super(packOutput, Target.RESOURCE_PACK, "models/item");
    this.modId = modId;
    this.itemDefinitions = packOutput.createPathProvider(Target.RESOURCE_PACK, "items");
  }

  /** Makes the JSON for a given bucket */
  @SuppressWarnings("deprecation")  // best way to get keys
  private static JsonObject makeJson(BucketItem bucket) {
    JsonObject json = new JsonObject();
    json.addProperty("parent", "neoforge:item/bucket_drip");
    // using our own model as the forge one expects us to use item colors to handle tints, when we could just bake it in
    json.addProperty("loader", "tconstruct:fluid_container");
    Fluid fluid = bucket.getContent();
    json.addProperty("flip_gas", fluid.getFluidType().isLighterThanAir());
    JsonObject textures = new JsonObject();
    textures.addProperty("base", "minecraft:item/bucket");
    textures.addProperty("fluid", "neoforge:item/mask/bucket_fluid_drip");
    textures.addProperty("particle", "minecraft:item/bucket");
    json.add("textures", textures);
    json.addProperty("fluid", BuiltInRegistries.FLUID.getKey(fluid).toString());
    return json;
  }

  private static JsonObject itemDefinition(Identifier item, JsonObject bucketModel) {
    JsonObject definition = new JsonObject();
    JsonObject model = new JsonObject();
    model.addProperty("type", "tconstruct:fluid_container");
    model.add("textures", bucketModel.getAsJsonObject("textures"));
    model.add("fluid", bucketModel.get("fluid"));
    model.add("flip_gas", bucketModel.get("flip_gas"));
    model.addProperty("cover_is_mask", true);
    model.addProperty("apply_fluid_luminosity", true);
    model.addProperty("force_opaque_fluid", true);
    definition.add("model", model);
    return definition;
  }

  @SuppressWarnings("deprecation")  // easiest item set lookup
  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    return allOf(
      BuiltInRegistries.ITEM.entrySet().stream()
        .filter(entry -> entry.getKey().identifier().getNamespace().equals(modId) && entry.getValue() instanceof BucketItem)
        .flatMap(entry -> {
          Identifier id = entry.getKey().identifier();
          JsonObject model = makeJson((BucketItem)entry.getValue());
          return java.util.stream.Stream.of(
            saveJson(cache, id, model),
            DataProvider.saveStable(cache, itemDefinition(id, model), itemDefinitions.json(id)));
        }));
  }

  @Override
  public String getName() {
    return modId + " Fluid Bucket Model Provider";
  }
}