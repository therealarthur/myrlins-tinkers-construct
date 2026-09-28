package slimeknights.tconstruct.library.materials.definition;

import slimeknights.tconstruct.library.materials.MaterialRegistry;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** This class handles lazy loading of a material, as the times recipes load is too soon to fetch material objects */
public class LazyMaterial implements Supplier<IMaterial> {
  /** ID to fetch */
  private final MaterialId id;
  /** Explicit material for data generation; ID-based instances always follow registry reloads. */
  @Nullable
  private final IMaterial material;

  protected LazyMaterial(MaterialId id) {
    this.id = id;
    this.material = null;
  }

  protected LazyMaterial(IMaterial material) {
    this.id = material.getIdentifier();
    this.material = material;
  }

  public MaterialId getId() {
    return id;
  }

  /** Creates a new lazy material instance */
  public static LazyMaterial of(MaterialId id) {
    return new LazyMaterial(id);
  }

  /** Creates a new lazy material instance from an existing material */
  public static LazyMaterial of(IMaterial material) {
    return new LazyMaterial(material);
  }

  @Override
  public IMaterial get() {
    if (material != null) {
      return material;
    }
    // Recipes survive material synchronization and datapack reloads. Neither an old
    // material object nor a previously missing ID may be cached across those changes.
    return MaterialRegistry.isFullyLoaded() ? MaterialRegistry.getMaterial(id) : IMaterial.UNKNOWN;
  }

  /** If true, this material is intentionally the unknown ID. Unlike {@link #isUnknown()} this will not match if the material is a valid ID but is not found */
  public boolean isEmpty() {
    return id.equals(IMaterial.UNKNOWN_ID);
  }

  /** If true, this material was not found in the registry. Can use to immediately resolve a material */
  public boolean isUnknown() {
    return get() == IMaterial.UNKNOWN;
  }

  /* Predicate */

  /** Returns true if the passed material matches this lazy material, matches based on ID comparison */
  public boolean matches(MaterialId material) {
    return id.equals(material);
  }

  @Override
  public String toString() {
    return "LazyMaterial{" + id + '}';
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || this.getClass() != other.getClass()) {
      return false;
    }
    return this.id.equals(((LazyMaterial)other).id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
