package aebm.continuumtests;

import com.mojang.authlib.GameProfile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack.LoadVerifyResult;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.logic.ToolLoadVerification;

/**
 * Saved tool re-verification after load (release/arthur.8, follow-up 6). Command {@code aebmtoolverifytest}; require
 * {@code AEBM_TOOL_VERIFY_SUMMARY passed=N failed=0}.
 * <p>
 * Official 1.20.1 rebuilt a tool's derived data whenever it was read from NBT. 26.1 has no such hook, so arthur.8
 * verifies each loaded stack once through {@link ToolLoadVerification}. These cases build a real iron javelin with
 * upgrades, damage and persistent data, write deliberately stale derived data into it (wrong stats, trait list missing,
 * volatile data missing), save and reload it through the real ItemStack codec and a binary NBT round trip, then run
 * the production paths: the item's own inventoryTick, the login helper over a player inventory and ender chest, and a
 * real EntityJoinLevelEvent for an armor stand loaded from disk. The negative controls check that an unmodified tool is
 * left byte for byte unchanged, that a broken tool stays broken with the same damage, and that a stack instance is only
 * verified once.
 * <p>
 * Entities are detached (never added to the world).
 */
public final class ToolLoadVerifyServerFixture {
  private static final Identifier PROBE = Identifier.fromNamespaceAndPath("aebmcontinuumtests", "load_verify_probe");
  private static final int STALE_DURABILITY = 12345;

  private ToolLoadVerifyServerFixture() {}

  /** Registered from ReturningServerFixture with one addListener line. */
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmtoolverifytest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
    }

    private int run() {
      test("stale_stats_recomputed_after_reload", () -> {
        ToolStack good = javelin(false);
        ItemStack goodStack = good.createStack();
        CompoundTag goodTag = tag(goodStack);
        ItemStack loaded = roundtrip(stale(goodStack));
        // decoding alone must not verify (there is no load hook in 26.1); the stale data is really on the loaded stack
        require(ToolStack.from(loaded).getStats().getInt(ToolStats.DURABILITY) == STALE_DURABILITY, "reloaded stack must still carry the stale durability before verification");
        require(loaded.getOrDefault(DataComponents.MAX_DAMAGE, 0) == STALE_DURABILITY, "reloaded stack must still carry the stale max damage component");
        // production path: the tool's own inventory tick
        Zombie holder = zombie();
        loaded.getItem().inventoryTick(loaded, level, holder, null);
        CompoundTag verified = tag(loaded);
        checkPreserved(goodTag, verified);
        require(verified.get("tic_stats").equals(goodTag.get("tic_stats")), "stats must be recomputed to the current data: " + verified.get("tic_stats") + " vs " + goodTag.get("tic_stats"));
        require(verified.get("tic_modifiers").equals(goodTag.get("tic_modifiers")), "trait list must be rebuilt: " + verified.get("tic_modifiers") + " vs " + goodTag.get("tic_modifiers"));
        require(java.util.Objects.equals(verified.get("tic_volatile_data"), goodTag.get("tic_volatile_data")), "volatile data must be rebuilt");
        require(verified.equals(goodTag), "the whole verified tool tag must equal a freshly built tool: " + verified + " vs " + goodTag);
        int durability = good.getStats().getInt(ToolStats.DURABILITY);
        require(loaded.getOrDefault(DataComponents.MAX_DAMAGE, 0) == durability, "max damage component must follow the recomputed durability " + durability);
        require(loaded.getOrDefault(DataComponents.DAMAGE, -1) == 17, "damage component must keep the saved damage");
        require(ToolStack.from(loaded).getPersistentData().getInt(PROBE) == 91, "persistent modifier data must survive");
        // a second tick on the same instance does no further work
        require(ToolLoadVerification.verifyOnce(loaded) == LoadVerifyResult.SKIPPED, "an instance must only be verified once");
      });
      test("unmodified_tool_unchanged_after_reload", () -> {
        ItemStack goodStack = javelin(false).createStack();
        ItemStack loaded = roundtrip(goodStack);
        ItemStack before = loaded.copy();
        LoadVerifyResult result = ToolLoadVerification.verifyOnce(loaded);
        require(result == LoadVerifyResult.UNCHANGED, "a current tool must report UNCHANGED, got " + result);
        require(ItemStack.matches(before, loaded), "a current tool must not be touched: " + before.getComponentsPatch() + " vs " + loaded.getComponentsPatch());
        require(tag(loaded).equals(tag(goodStack)), "a current tool must keep its exact tag");
      });
      test("broken_tool_keeps_damage_broken_and_upgrades", () -> {
        ToolStack good = javelin(true);
        ItemStack goodStack = good.createStack();
        CompoundTag goodTag = tag(goodStack);
        require(good.isBroken(), "fixture tool must be broken");
        ItemStack loaded = roundtrip(stale(goodStack));
        LoadVerifyResult result = ToolLoadVerification.verifyOnce(loaded);
        require(result == LoadVerifyResult.UPDATED, "a stale broken tool must be updated, got " + result);
        CompoundTag verified = tag(loaded);
        checkPreserved(goodTag, verified);
        ToolStack after = ToolStack.from(loaded);
        require(after.isBroken(), "a broken tool must stay broken");
        require(after.getDamage() == good.getDamage(), "a broken tool must keep its damage " + good.getDamage() + ", has " + after.getDamage());
        require(verified.equals(goodTag), "a broken tool must verify to the freshly built data");
      });
      test("redirected_material_resolved_without_stat_loss", () -> {
        // official redirect: tconstruct:platinum became seared stone (MaterialDataProvider addRedirect)
        ToolStack iron = javelin(false);
        List<MaterialVariantId> expectedIds = new ArrayList<>();
        for (MaterialVariant variant : iron.getMaterials().getList()) {
          expectedIds.add(variant.getVariant());
        }
        expectedIds.set(0, MaterialVariantId.create(MaterialIds.searedStone, ""));
        ToolStack expected = build(expectedIds, false);
        CompoundTag expectedTag = tag(expected.createStack());
        CompoundTag saved = tag(stale(iron.createStack()));
        net.minecraft.nbt.ListTag materials = saved.getList("tic_materials").orElseThrow();
        materials.set(0, net.minecraft.nbt.StringTag.valueOf("tconstruct:platinum"));
        saved.put("tic_materials", materials);
        ItemStack stack = iron.createStack();
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(saved));
        ItemStack loaded = roundtrip(stack);
        LoadVerifyResult result = ToolLoadVerification.verifyOnce(loaded);
        require(result == LoadVerifyResult.UPDATED, "a tool with a redirected material must be updated, got " + result);
        CompoundTag verified = tag(loaded);
        require(verified.get("tic_materials").equals(expectedTag.get("tic_materials")), "platinum must resolve to seared stone and nothing else: " + verified.get("tic_materials"));
        require(verified.get("tic_stats").equals(expectedTag.get("tic_stats")), "stats must come from the redirect target, not the unknown material");
        require(verified.get("tic_upgrades").equals(expectedTag.get("tic_upgrades")), "upgrades must survive the redirect");
      });
      test("login_and_entity_paths_verify_saved_tools", () -> {
        ToolStack good = javelin(false);
        CompoundTag goodTag = tag(good.createStack());
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ToolVerifyFixture"));
        ItemStack inInventory = roundtrip(stale(good.createStack()));
        ItemStack inEnderChest = roundtrip(stale(good.createStack()));
        player.getInventory().setItem(3, inInventory);
        player.getEnderChestInventory().setItem(5, inEnderChest);
        int updated = ToolLoadVerification.verifyPlayer(player);
        require(updated == 2, "login verification must update the inventory and ender chest tools, updated " + updated);
        require(tag(player.getInventory().getItem(3)).equals(goodTag), "inventory tool must be verified in place");
        require(tag(player.getEnderChestInventory().getItem(5)).equals(goodTag), "ender chest tool must be verified in place");
        // entity loaded from disk: the real EntityJoinLevelEvent listener
        ArmorStand stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        require(stand != null, "armor stand fixture must be constructible");
        stand.setPos(0, level.getMaxY() + 64, 0);
        stand.setItemSlot(EquipmentSlot.MAINHAND, roundtrip(stale(good.createStack())));
        NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(stand, level, false));
        require(ToolStack.from(stand.getItemBySlot(EquipmentSlot.MAINHAND)).getStats().getInt(ToolStats.DURABILITY) == STALE_DURABILITY,
          "negative control: a newly spawned entity (not loaded from disk) is not verified by the join listener");
        NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(stand, level, true));
        require(tag(stand.getItemBySlot(EquipmentSlot.MAINHAND)).equals(goodTag), "equipment of an entity loaded from disk must be verified");
      });
      source.sendSuccess(() -> Component.literal("AEBM_TOOL_VERIFY_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    /** An iron javelin with two upgrades, persistent data and damage (or broken). */
    private ToolStack javelin(boolean broken) {
      ItemStack stack = ToolBuildHandler.createSingleMaterial(TinkerTools.javelin.get(), MaterialVariant.of(MaterialIds.iron, ""));
      require(!stack.isEmpty(), "loaded materials must build an iron javelin");
      List<MaterialVariantId> ids = new ArrayList<>();
      for (MaterialVariant variant : ToolStack.from(stack).getMaterials().getList()) {
        ids.add(variant.getVariant());
      }
      return build(ids, broken);
    }

    private ToolStack build(List<MaterialVariantId> ids, boolean broken) {
      var item = TinkerTools.javelin.get();
      ToolStack tool = ToolStack.createTool(item, IModifiable.getToolDefinition(item), new MaterialNBT(ids.stream().map(MaterialVariant::of).toList()));
      tool.addModifier(ModifierIds.returning, 2);
      tool.addModifier(ModifierIds.sharpness, 1);
      tool.getPersistentData().putInt(PROBE, 91);
      int durability = tool.getStats().getInt(ToolStats.DURABILITY);
      require(durability > 17, "fixture tool needs durability above 17");
      tool.setDamage(broken ? durability : 17);
      require(tool.getModifiers().getModifiers().size() > tool.getUpgrades().getModifiers().size(), "fixture tool needs material traits so a missing trait list is detectable");
      return tool;
    }

    /** Writes stale derived data: wrong stats, traits dropped from the modifier list, volatile data removed. */
    private static ItemStack stale(ItemStack good) {
      CompoundTag tag = tag(good);
      ToolStack tool = ToolStack.from(good);
      tag.put("tic_stats", StatsNBT.builder().set(ToolStats.DURABILITY, STALE_DURABILITY).set(ToolStats.ATTACK_DAMAGE, 0.5f).build().serializeToNBT());
      tag.put("tic_modifiers", tool.getUpgrades().serializeToNBT());
      tag.remove("tic_volatile_data");
      ItemStack stale = good.copy();
      stale.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      stale.set(DataComponents.MAX_DAMAGE, STALE_DURABILITY);
      return stale;
    }

    /** Materials, upgrades, persistent data, damage and broken flag must match the saved tool exactly. */
    private static void checkPreserved(CompoundTag expected, CompoundTag actual) {
      for (String key : List.of("tic_materials", "tic_upgrades", "tic_persistent", "Damage", "tic_broken")) {
        require(java.util.Objects.equals(expected.get(key), actual.get(key)), key + " must be preserved: " + expected.get(key) + " vs " + actual.get(key));
      }
    }

    private Zombie zombie() {
      Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
      require(zombie != null, "zombie fixture must be constructible");
      zombie.setPos(0, level.getMaxY() + 64, 0);
      zombie.setSilent(true);
      return zombie;
    }

    /** Saves and reloads a stack exactly like a world save: ItemStack codec with registry context plus binary NBT. */
    private ItemStack roundtrip(ItemStack original) {
      try {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, level.registryAccess());
        output.store("tool", ItemStack.CODEC, original);
        require(problems.isEmpty(), "item encoding: " + problems.getReport());
        CompoundTag saved = binaryRoundtrip(output.buildResult());
        ItemStack restored = TagValueInput.create(problems, level.registryAccess(), saved)
          .read("tool", ItemStack.CODEC).orElseThrow(() -> new AssertionError("item codec produced no tool"));
        require(problems.isEmpty(), "item decoding: " + problems.getReport());
        require(restored != original, "round trip must produce a new stack instance");
        return restored;
      } catch (IOException e) {
        throw new AssertionError("binary round trip failed", e);
      }
    }

    private void test(String name, Runnable check) {
      try {
        check.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_TOOL_VERIFY_PASS " + name), false);
      } catch (Throwable failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_TOOL_VERIFY_FAIL " + name + ": " + failure));
        slimeknights.tconstruct.TConstruct.LOG.error("AEBM_TOOL_VERIFY_FAIL " + name, failure);
      }
    }
  }

  private static CompoundTag tag(ItemStack stack) {
    return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
  }

  private static CompoundTag binaryRoundtrip(CompoundTag tag) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      NbtIo.write(tag, output);
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return NbtIo.read(input, NbtAccounter.unlimitedHeap());
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
