package slimeknights.tconstruct.library.client.book.corefix;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * File: CoreBookFixMixinPlugin.java (Myrlin's Tinkers' Construct, 2026-09-30).
 *
 * Mixin config plugin for {@code tconstruct.client.mixins.json}, the book fixes folded in from the private Continuum
 * Core fork. Before a mixin is applied, this reads the target Core class and checks that it still has the exact stock
 * Continuum Core 1.12.1 code the mixin replaces (the calls, constants and locals its injectors match).
 *
 * Why: the same fixes also ship in the Core fork 1.12.x-arthur.1, and a later Core release may take them too. There
 * the stock code is gone, so a mixin would either fail its injection check (a crash while loading the class) or apply
 * a fix twice. Skipping instead leaves the Core's own fix in charge, which draws the same thing. Each decision is
 * logged once as "Myrlin's Tinkers' Construct book fix".
 *
 * Only ASM, Mixin and Log4j types are used here: this class is loaded while mixins are being prepared, before
 * Minecraft classes may load. It must not reference the other classes of this package.
 */
public class CoreBookFixMixinPlugin implements IMixinConfigPlugin {
  private static final Logger LOG = LogManager.getLogger("tconstruct");

  // internal names and descriptors of the stock Core code the mixins match
  private static final String BOOK_SCREEN = "slimeknights/mantle/client/screen/book/BookScreen";
  private static final String BOOK_DATA = "slimeknights/mantle/client/book/data/BookData";
  private static final String MINECRAFT = "net/minecraft/client/Minecraft";
  private static final String FONT_DESC = "Lnet/minecraft/client/gui/Font;";
  private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphicsExtractor";
  private static final String TEXT_COLLECTOR = "net/minecraft/client/gui/ActiveTextCollector";
  private static final String ACCEPT_DESC = "(IILnet/minecraft/network/chat/Component;)V";
  private static final String TEXT_5_DESC = "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V";
  private static final String DRAW_STRING_DESC = "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/lang/String;FFF)V";
  private static final String RECIPE_HOLDER_DESC = "Lnet/minecraft/world/item/crafting/RecipeHolder;";
  private static final String LEVEL_DESC = "Lnet/minecraft/world/level/Level;";
  private static final String RESOURCE_KEY_DESC = "Lnet/minecraft/resources/ResourceKey;";
  private static final String GET_SINGLEPLAYER_SERVER_DESC = "()Lnet/minecraft/client/server/IntegratedServer;";
  /** The Core fork's synced recipe class; any reference to it means the recipe fix is already there */
  private static final String CORE_BOOK_RECIPES_SUFFIX = "/BookRecipes";
  /** Official Mantle container label color, without an alpha byte in stock Core */
  private static final int STOCK_LABEL_COLOR = 0x404040;

  /** Target class nodes read so far, by dotted name; null value when the class could not be read */
  private final Map<String, ClassNode> nodes = new HashMap<>();

  @Override
  public void onLoad(String mixinPackage) {}

  @Nullable
  @Override
  public String getRefMapperConfig() {
    return null;
  }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    String mixin = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
    ClassNode target = readClass(targetClassName);
    if (target == null) {
      LOG.warn("Myrlin's Tinkers' Construct book fix {}: could not read {}, skipping it", mixin, targetClassName);
      return false;
    }
    String mismatch;
    try {
      mismatch = checkStock(mixin, target);
    } catch (RuntimeException e) {
      LOG.warn("Myrlin's Tinkers' Construct book fix {}: could not inspect {}, skipping it", mixin, targetClassName, e);
      return false;
    }
    if (mismatch == null) {
      LOG.info("Myrlin's Tinkers' Construct book fix {}: applying to stock Continuum Core {}", mixin, targetClassName);
      return true;
    }
    LOG.info("Myrlin's Tinkers' Construct book fix {}: skipped, {} is not the stock Continuum Core 1.12.1 code ({}); the installed Core is expected to carry this fix itself", mixin, targetClassName, mismatch);
    return false;
  }

  /** Reads a target class without loading it, caching the result */
  @Nullable
  private ClassNode readClass(String dottedName) {
    if (nodes.containsKey(dottedName)) {
      return nodes.get(dottedName);
    }
    ClassNode node = null;
    try {
      node = MixinService.getService().getBytecodeProvider().getClassNode(dottedName);
    } catch (Exception e) {
      LOG.warn("Myrlin's Tinkers' Construct book fix: could not read class {}", dottedName, e);
    }
    nodes.put(dottedName, node);
    return node;
  }

  /**
   * Checks that the target has the stock code a mixin expects.
   * @param mixin   Simple name of the mixin class
   * @param target  Target class
   * @return  null when the stock code is there, otherwise what differs
   */
  @Nullable
  static String checkStock(String mixin, ClassNode target) {
    return switch (mixin) {
      case "BookScreenFontMixin" -> firstMismatch(
        stockFontGetter(target, "getAltFont"),
        stockFontGetter(target, "getUniformFont"));
      case "BookDataFontMixin" -> {
        MethodNode load = method(target, "load", "()V");
        if (load == null) yield "no load()";
        yield firstMismatch(
          expectCount("fontRenderer writes in load()", countField(load, Opcodes.PUTFIELD, BOOK_DATA, "fontRenderer", FONT_DESC), 1),
          expectCount("getUniformFont calls in load()", countCall(load, BOOK_SCREEN, "getUniformFont", "()" + FONT_DESC), 1));
      }
      case "BookScreenTextMixin" -> {
        MethodNode render = method(target, "extractRenderState", "(L" + GRAPHICS + ";IIF)V");
        MethodNode cover = method(target, "renderCover", "(L" + GRAPHICS + ";" + FONT_DESC + ")V");
        if (render == null || cover == null) yield "no extractRenderState or renderCover";
        yield firstMismatch(
          expectCount("textRenderer().accept calls in extractRenderState", countCall(render, TEXT_COLLECTOR, "accept", ACCEPT_DESC), 3),
          expectCount("Font locals in extractRenderState", countLocals(render, FONT_DESC), 1),
          expectCount("drawString calls in renderCover", countCall(cover, BOOK_SCREEN, "drawString", DRAW_STRING_DESC), 2));
      }
      case "TextComponentDataRendererMixin" -> {
        MethodNode draw = method(target, "drawText", "(L" + GRAPHICS + ";IIII[Lslimeknights/mantle/client/book/data/element/TextComponentData;II" + FONT_DESC + "Ljava/util/List;)Ljava/lang/String;");
        if (draw == null) yield "no drawText";
        yield firstMismatch(
          expectCount("textRenderer().accept calls", countCall(draw, TEXT_COLLECTOR, "accept", ACCEPT_DESC), 1),
          expectCount("TextComponentData locals", countLocals(draw, "Lslimeknights/mantle/client/book/data/element/TextComponentData;"), 1),
          expectCount("Font locals", countLocals(draw, FONT_DESC), 1));
      }
      case "TextDataRendererMixin" -> {
        MethodNode draw = method(target, "drawText", "(L" + GRAPHICS + ";IIII[Lslimeknights/mantle/client/book/data/element/TextData;II" + FONT_DESC + "Ljava/util/List;)Ljava/lang/String;");
        if (draw == null) yield "no drawText";
        yield firstMismatch(
          expectCount("5 argument text() calls", countCall(draw, GRAPHICS, "text", TEXT_5_DESC), 1),
          expectCount("TextData locals", countLocals(draw, "Lslimeknights/mantle/client/book/data/element/TextData;"), 1));
      }
      case "SelectionElementMixin" -> {
        MethodNode draw = method(target, "draw", "(L" + GRAPHICS + ";IIF" + FONT_DESC + ")V");
        if (draw == null) yield "no draw";
        List<LocalVariableNode> booleans = locals(draw, "Z");
        yield firstMismatch(
          expectCount("textRenderer().accept calls", countCall(draw, TEXT_COLLECTOR, "accept", ACCEPT_DESC), 1),
          expectCount("Font locals", countLocals(draw, FONT_DESC), 1),
          booleans.size() == 2 && "hover".equals(booleans.get(1).name) ? null : "boolean locals are not [unlocked, hover]");
      }
      case "MultiModuleScreenMixin" -> {
        MethodNode title = method(target, "drawContainerName", "(L" + GRAPHICS + ";)V");
        MethodNode inventory = method(target, "drawPlayerInventoryName", "(L" + GRAPHICS + ";)V");
        if (title == null || inventory == null) yield "no drawContainerName or drawPlayerInventoryName";
        yield firstMismatch(
          expectCount("0x404040 in drawContainerName", countIntConstant(title, STOCK_LABEL_COLOR), 1),
          expectCount("0x404040 in drawPlayerInventoryName", countIntConstant(inventory, STOCK_LABEL_COLOR), 1));
      }
      case "ContentCraftingMixin" -> {
        MethodNode lookup = method(target, "getRecipeHolder", "(L" + MINECRAFT + ";" + LEVEL_DESC + "Lnet/minecraft/resources/Identifier;)" + RECIPE_HOLDER_DESC);
        if (lookup == null) yield "no getRecipeHolder";
        yield firstMismatch(
          countCall(lookup, MINECRAFT, "getSingleplayerServer", GET_SINGLEPLAYER_SERVER_DESC) > 0 ? null : "no integrated server fallback",
          countInstanceOf(lookup, "net/minecraft/world/item/crafting/RecipeManager") == 1 ? null : "no RecipeManager check",
          referencesBookRecipes(target) ? "already reads synced recipes" : null);
      }
      case "ContentSmeltingMixin" -> {
        MethodNode load = method(target, "load", "()V");
        if (load == null) yield "no load()";
        yield firstMismatch(
          countCall(load, MINECRAFT, "getSingleplayerServer", GET_SINGLEPLAYER_SERVER_DESC) > 0 ? null : "no integrated server fallback",
          referencesBookRecipes(target) ? "already reads synced recipes" : null,
          expectCount("RecipeHolder locals", countLocals(load, RECIPE_HOLDER_DESC), 1),
          expectCount("Level locals", countLocals(load, LEVEL_DESC), 1),
          expectCount("ResourceKey locals", countLocals(load, RESOURCE_KEY_DESC), 1));
      }
      default -> "no stock check for this mixin";
    };
  }

  /** A stock font getter only returns {@code Minecraft.getInstance().font} */
  @Nullable
  private static String stockFontGetter(ClassNode target, String name) {
    MethodNode getter = method(target, name, "()" + FONT_DESC);
    if (getter == null) {
      return "no " + name + "()";
    }
    int calls = 0;
    boolean readsGameFont = false;
    for (AbstractInsnNode insn : getter.instructions) {
      if (insn instanceof MethodInsnNode) {
        calls++;
      } else if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD && field.owner.equals(MINECRAFT) && field.name.equals("font")) {
        readsGameFont = true;
      }
    }
    return readsGameFont && calls == 1 ? null : name + "() does more than return the game font";
  }

  @Nullable
  private static String firstMismatch(@Nullable String... results) {
    for (String result : results) {
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  private static String expectCount(String what, int actual, int expected) {
    return actual == expected ? null : what + ": " + actual + ", stock has " + expected;
  }

  @Nullable
  private static MethodNode method(ClassNode node, String name, String desc) {
    for (MethodNode method : node.methods) {
      if (method.name.equals(name) && method.desc.equals(desc)) {
        return method;
      }
    }
    return null;
  }

  private static int countCall(MethodNode method, String owner, String name, String desc) {
    int count = 0;
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) {
        count++;
      }
    }
    return count;
  }

  private static int countField(MethodNode method, int opcode, String owner, String name, String desc) {
    int count = 0;
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof FieldInsnNode field && field.getOpcode() == opcode && field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(desc)) {
        count++;
      }
    }
    return count;
  }

  private static int countIntConstant(MethodNode method, int value) {
    int count = 0;
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer constant && constant == value) {
        count++;
      }
    }
    return count;
  }

  private static int countInstanceOf(MethodNode method, String type) {
    int count = 0;
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof TypeInsnNode typeInsn && typeInsn.getOpcode() == Opcodes.INSTANCEOF && typeInsn.desc.equals(type)) {
        count++;
      }
    }
    return count;
  }

  /** Locals of the given descriptor in the local variable table, sorted by slot; empty without a table */
  private static List<LocalVariableNode> locals(MethodNode method, String desc) {
    List<LocalVariableNode> found = new ArrayList<>();
    if (method.localVariables != null) {
      for (LocalVariableNode local : method.localVariables) {
        if (local.desc.equals(desc)) {
          found.add(local);
        }
      }
    }
    found.sort((a, b) -> Integer.compare(a.index, b.index));
    return found;
  }

  private static int countLocals(MethodNode method, String desc) {
    return locals(method, desc).size();
  }

  /** True if any method of the class calls into a class named BookRecipes (the Core fork's synced recipes) */
  private static boolean referencesBookRecipes(ClassNode node) {
    for (MethodNode method : node.methods) {
      for (AbstractInsnNode insn : method.instructions) {
        if (insn instanceof MethodInsnNode call && call.owner.endsWith(CORE_BOOK_RECIPES_SUFFIX)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

  @Nullable
  @Override
  public List<String> getMixins() {
    return null;
  }

  @Override
  public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

  @Override
  public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
