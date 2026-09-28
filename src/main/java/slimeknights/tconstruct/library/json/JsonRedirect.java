package slimeknights.tconstruct.library.json;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.tconstruct.library.utils.ResourceId;

import javax.annotation.Nullable;

public record JsonRedirect(Identifier id, @Nullable ICondition condition) {
  public JsonRedirect(ResourceId id, @Nullable ICondition condition) {
    this(id.getId(), condition);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("id", id.toString());
    if (condition != null) {
      json.add("condition", ICondition.CODEC.encodeStart(JsonOps.INSTANCE, condition)
        .getOrThrow(IllegalStateException::new));
    }
    return json;
  }
}
