package aebm.continuumtests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import slimeknights.mantle.client.book.BookLoader;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.tconstruct.library.client.book.TinkerBook;
import slimeknights.tconstruct.library.client.book.content.AbstractMaterialContent;
import slimeknights.tconstruct.library.client.book.content.ContentMaterialSkull;
import slimeknights.tconstruct.library.client.book.content.material.LacesMaterialContent;
import slimeknights.tconstruct.library.client.book.content.material.RibcageMaterialContent;
import slimeknights.tconstruct.library.client.book.content.material.ShellMaterialContent;
import slimeknights.tconstruct.library.client.book.content.material.SingleMaterialStatContent;
import slimeknights.tconstruct.library.client.book.content.material.SlimeMaterialContent;
import slimeknights.tconstruct.library.client.book.sectiontransformer.materials.TierRangeMaterialSectionTransformer.MaterialTier;
import slimeknights.tconstruct.library.client.recipe.ClientRecipeCache;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.shared.item.TinkerBookItem.BookType;
import slimeknights.tconstruct.tools.stats.RepairStats;
import slimeknights.tconstruct.tools.stats.SkullStats;
import slimeknights.tconstruct.tools.stats.SlimeStats;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Real page registration, section parsing and snapshot invalidation; never initializes a screen or font. */
@EnabledIf(value = "clientDistribution", disabledReason = "Book APIs require a client-distribution test launch.")
final class MaterialBookContentTest {
  static boolean clientDistribution() {
    return FMLEnvironment.getDist() == Dist.CLIENT;
  }

  @BeforeAll
  static void registerPages() {
    if (BookLoader.getPageType(SlimeMaterialContent.ID) == null) {
      TinkerBook.initBook();
    }
  }

  @Test
  void bothShippedSectionsLoadEveryGroupAndKeepSkullBookmarks() throws Exception {
    Map<String,Class<?>> expected = Map.of(
      "slime", SlimeMaterialContent.class, "skull", ContentMaterialSkull.class,
      "ribcage", RibcageMaterialContent.class, "shell", ShellMaterialContent.class, "laces", LacesMaterialContent.class);
    MaterialId material = new MaterialId("tconstruct:bone");
    for (String book : List.of("tinkers_gadgetry", "encyclopedia")) {
      JsonArray index = resource("book/" + book + "/index.json").getAsJsonArray();
      String sectionName = book.equals("encyclopedia") ? "materials_skull" : "materials";
      JsonObject section = index.asList().stream().map(entry -> entry.getAsJsonObject())
        .filter(entry -> entry.get("name").getAsString().equals(sectionName)).findFirst().orElseThrow();
      JsonArray groups = section.getAsJsonObject("extraData").getAsJsonArray("tconstruct:material_tier");
      assertEquals(5, groups.size());
      HashSet<String> pageNames = new HashSet<>();
      for (var group : groups) {
        JsonObject json = group.getAsJsonObject();
        String type = json.get("type").getAsString().substring("tconstruct:".length());
        MaterialTier tier = MaterialTier.deserialize(json);
        AbstractMaterialContent page = tier.pageCreator().apply(material);
        assertEquals(expected.get(type), page.getClass());
        assertEquals(expected.get(type), BookLoader.getPageType(page.getId()), "direct JSON pages must also be registered");
        assertEquals(book.equals("encyclopedia"), page.detailed);
        assertTrue(pageNames.add((tier.name().isEmpty() ? "" : tier.name() + '.') + material), "one material in several groups needs distinct links");
        if (type.equals("skull")) {
          assertEquals("", tier.name(), "preserve the old unprefixed skull page name");
        }
        if (!type.equals("slime")) {
          assertNotNull(tier.titleSuffix(), "index entries must identify the part");
        }
      }
    }
  }

  @Test
  void singleStatPagesShowTheirOwnTraitsAndDoNotAdvertiseSlimeParts() throws Exception {
    MaterialId material = new MaterialId("tconstruct:bone");
    Map<SingleMaterialStatContent,Object> pages = Map.of(
      new SlimeMaterialContent(material, true), SlimeStats.ID,
      new ContentMaterialSkull(material, true), SkullStats.ID,
      new RibcageMaterialContent(material, true), RepairStats.RIBCAGE.getStatsId(),
      new ShellMaterialContent(material, true), RepairStats.SHELL.getStatsId(),
      new LacesMaterialContent(material, true), RepairStats.LACES.getStatsId());
    Method stat = method(SingleMaterialStatContent.class, "getStatType", int.class);
    Method rows = method(SingleMaterialStatContent.class, "getStatRows");
    Method part = method(SingleMaterialStatContent.class, "hasPart");
    for (var entry : pages.entrySet()) {
      assertEquals(entry.getValue(), stat.invoke(entry.getKey(), 0));
      assertNull(stat.invoke(entry.getKey(), 1));
      assertEquals(1, rows.invoke(entry.getKey()));
      assertEquals(!(entry.getKey() instanceof SlimeMaterialContent || entry.getKey() instanceof ContentMaterialSkull), part.invoke(entry.getKey()));
    }
    assertEquals(false, method(SlimeMaterialContent.class, "allowPartBuilder").invoke(new SlimeMaterialContent(material, false)));
    // Existing object-style sections retain their default name and need no suffix.
    MaterialTier old = MaterialTier.deserialize(JsonParser.parseString("{\"type\":\"tconstruct:skull\"}").getAsJsonObject());
    assertEquals("skull", old.name());
    assertNull(old.titleSuffix());
  }

  @Test
  void newSnapshotResetsBooksOnNextAccessButSameSnapshotKeepsTheirState() throws Exception {
    Field initialized = BookData.class.getDeclaredField("initialized");
    initialized.setAccessible(true);
    try {
      TinkerBook.getBook(BookType.MATERIALS_AND_YOU);
      List<BookData> books = java.util.Arrays.stream(BookType.values()).map(TinkerBook::getBook).toList();
      for (BookData book : books) initialized.setBoolean(book, true);
      TinkerBook.getBook(BookType.ENCYCLOPEDIA);
      for (BookData book : books) assertTrue(initialized.getBoolean(book), "unchanged snapshot should not reload every access");

      ClientRecipeCache.receive(RegistryAccess.EMPTY, RecipeMap.create(List.of()));
      TinkerBook.getBook(BookType.TINKERS_GADGETRY);
      for (BookData book : books) assertFalse(initialized.getBoolean(book), "received data must rebuild index icons and descriptions");

      for (BookData book : books) initialized.setBoolean(book, true);
      ClientRecipeCache.clear();
      TinkerBook.getBook(BookType.ENCYCLOPEDIA);
      for (BookData book : books) assertFalse(initialized.getBoolean(book), "disconnect must not retain the old server's pages");
    } finally {
      ClientRecipeCache.clear();
      TinkerBook.getBook(BookType.MATERIALS_AND_YOU);
    }
  }

  private static Method method(Class<?> owner, String name, Class<?>... parameters) throws Exception {
    Method method = owner.getDeclaredMethod(name, parameters);
    method.setAccessible(true);
    return method;
  }

  private static com.google.gson.JsonElement resource(String path) throws Exception {
    try (var stream = MaterialBookContentTest.class.getResourceAsStream("/assets/tconstruct/" + path)) {
      assertNotNull(stream, "Missing shipped book resource: " + path);
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
  }
}
