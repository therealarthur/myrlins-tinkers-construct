/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  javax.annotation.Nullable
 *  lombok.Generated
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Holder
 *  net.minecraft.core.particles.ParticleOptions
 *  net.minecraft.core.particles.ParticleTypes
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.Identifier
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.tags.TagKey
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.WorldlyContainer
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.crafting.Recipe
 *  net.minecraft.world.item.crafting.RecipeHolder
 *  net.minecraft.world.item.crafting.RecipeInput
 *  net.minecraft.world.item.crafting.RecipeManager
 *  net.minecraft.world.item.crafting.RecipeType
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntityTicker
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.level.storage.ValueInput
 *  net.minecraft.world.level.storage.ValueOutput
 *  net.neoforged.neoforge.fluids.FluidStack
 *  net.neoforged.neoforge.fluids.capability.IFluidHandler
 *  net.neoforged.neoforge.fluids.capability.IFluidHandler$FluidAction
 *  net.neoforged.neoforge.items.IItemHandler
 *  net.neoforged.neoforge.items.wrapper.SidedInvWrapper
 *  slimeknights.mantle.fluid.FluidTransferHelper
 *  slimeknights.mantle.util.BlockEntityHelper
 */
package slimeknights.tconstruct.smeltery.block.entity;

import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Generated;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import slimeknights.mantle.fluid.FluidTransferHelper;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.Sounds;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.fluid.FluidActions;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.molding.MoldingRecipe;
import slimeknights.tconstruct.library.recipe.molding.IMoldingContainer;
import slimeknights.tconstruct.shared.block.entity.TableBlockEntity;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.AbstractCastingBlock;
import slimeknights.tconstruct.smeltery.block.entity.inventory.CastingContainerWrapper;
import slimeknights.tconstruct.smeltery.block.entity.inventory.MoldingContainerWrapper;
import slimeknights.tconstruct.smeltery.block.entity.tank.CastingFluidHandler;
import slimeknights.tconstruct.smeltery.network.FluidUpdatePacket;

public abstract class CastingBlockEntity
extends TableBlockEntity
implements WorldlyContainer,
FluidUpdatePacket.IFluidPacketReceiver {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    private static final String TAG_TANK = "tank";
    private static final String TAG_TIMER = "timer";
    private static final String TAG_RECIPE = "recipe";
    private static final String TAG_REDSTONE = "redstone";
    private static final Component NAME = TConstruct.makeTranslation("gui", "casting");
    public static final BlockEntityTicker<CastingBlockEntity> SERVER_TICKER = (level, pos, state, self) -> self.serverTick(level, pos);
    public static final BlockEntityTicker<CastingBlockEntity> CLIENT_TICKER = (level, pos, state, self) -> self.clientTick(level, pos);
    private final CastingFluidHandler tank = new CastingFluidHandler(this);
    private final RecipeType<ICastingRecipe> castingType;
    private final CastingContainerWrapper castingInventory;
    private int timer;
    private int coolingTime = -1;
    private ICastingRecipe currentRecipe;
    private Identifier recipeName;
    private ICastingRecipe lastCastingRecipe;
    private ItemStack lastOutput = null;
    private final boolean requireCast;
    private final TagKey<Item> emptyCastTag;
    private final RecipeType<MoldingRecipe> moldingType;
    private final MoldingContainerWrapper moldingInventory;
    private MoldingRecipe lastMoldingRecipe;
    private boolean lastRedstone = false;
    private int lastAnalogSignal;

    protected CastingBlockEntity(BlockEntityType<?> beType, BlockPos pos, BlockState state, RecipeType<ICastingRecipe> castingType, RecipeType<MoldingRecipe> moldingType, TagKey<Item> emptyCastTag) {
        super(beType, pos, state, NAME, 2, 1);
        AbstractCastingBlock casting;
        Block block = state.getBlock();
        this.requireCast = block instanceof AbstractCastingBlock && (casting = (AbstractCastingBlock)block).isRequireCast();
        this.emptyCastTag = emptyCastTag;
        this.itemHandler = new SidedInvWrapper((WorldlyContainer)this, Direction.DOWN);
        this.castingType = castingType;
        this.moldingType = moldingType;
        this.castingInventory = new CastingContainerWrapper(this);
        this.moldingInventory = new MoldingContainerWrapper((IItemHandler)this.itemHandler, 0);
    }

    public IFluidHandler getFluidHandler(@Nullable Direction facing) {
        return this.tank;
    }

    public void interact(Player player, InteractionHand hand) {
        if (this.level == null || this.level.isClientSide() || this.coolingTime >= 0 && this.timer > 0) {
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (FluidTransferHelper.interactWithContainer((Level)this.level, (BlockPos)this.worldPosition, (IFluidHandler)this.tank, (Player)player, (InteractionHand)hand).didTransfer() || !this.tank.isEmpty()) {
            return;
        }
        ItemStack input = this.getItem(0);
        ItemStack output = this.getItem(1);
        if (!input.isEmpty() && output.isEmpty()) {
            this.moldingInventory.setPattern(held);
            MoldingRecipe recipe = this.findMoldingRecipe();
            if (recipe != null) {
                ItemStack result = recipe.assemble(this.moldingInventory);
                result.onCraftedBy(player, 1);
                if (held.isEmpty()) {
                    this.setItem(0, ItemStack.EMPTY);
                    player.setItemInHand(hand, result);
                } else {
                    this.setItem(0, result);
                    if (!recipe.isPatternConsumed()) {
                        this.setItem(1, CastingBlockEntity.copyStackWithSize(held, 1));
                        this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
                    }
                    held.shrink(1);
                    player.setItemInHand(hand, held.isEmpty() ? ItemStack.EMPTY : held);
                }
                this.moldingInventory.setPattern(ItemStack.EMPTY);
                return;
            }
            if (!held.isEmpty()) {
                this.moldingInventory.setPattern(ItemStack.EMPTY);
                recipe = this.findMoldingRecipe();
                if (recipe != null) {
                    this.setItem(0, ItemStack.EMPTY);
                    player.getInventory().placeItemBackInInventory(recipe.assemble(this.moldingInventory));
                    return;
                }
            }
            this.moldingInventory.setPattern(ItemStack.EMPTY);
        }
        if (input.isEmpty() && output.isEmpty()) {
            if (!held.isEmpty()) {
                ItemStack stack = held.split(this.stackSizeLimit);
                player.setItemInHand(hand, held.isEmpty() ? ItemStack.EMPTY : held);
                this.setItem(0, stack);
            }
        } else {
            int slot = output.isEmpty() ? 0 : 1;
            ItemStack stack = this.getItem(slot).copy();
            this.setItem(slot, ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(stack);
            if (slot == 1) {
                this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
            }
        }
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack original = this.getItem(slot);
        super.setItem(slot, stack);
        if (slot == 1 && stack.isEmpty()) {
            this.lastOutput = null;
            if (this.tank.isEmpty()) {
                this.timer = 0;
                this.coolingTime = -1;
                this.currentRecipe = null;
                this.recipeName = null;
                this.castingInventory.setFluid(FluidStack.EMPTY);
            }
        }
        if (original.isEmpty() != stack.isEmpty()) {
            this.updateAnalogSignal();
        }
        if (this.level != null && !this.level.isClientSide()) {
            boolean hasItem = !this.getItem(0).isEmpty() || !this.getItem(1).isEmpty();
            BlockState state = this.getBlockState();
            if ((Boolean)state.getValue((Property)AbstractCastingBlock.HAS_ITEM) != hasItem) {
                this.level.setBlockAndUpdate(this.worldPosition, (BlockState)state.setValue((Property)AbstractCastingBlock.HAS_ITEM, (Comparable)Boolean.valueOf(hasItem)));
            } else if (!ItemStack.matches((ItemStack)original, (ItemStack)stack)) {
                this.level.sendBlockUpdated(this.worldPosition, state, state, 2);
            }
        }
    }

    public void handleRedstone(boolean hasSignal) {
        if (this.lastRedstone != hasSignal) {
            if (hasSignal && this.level != null) {
                this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 2);
            }
            this.lastRedstone = hasSignal;
        }
    }

    public void swap() {
        if (this.currentRecipe == null) {
            ItemStack output = this.getItem(1);
            this.setItem(1, this.getItem(0));
            this.setItem(0, output);
            if (this.level != null) {
                this.level.playSound(null, this.getBlockPos(), Sounds.CASTING_CLICKS.getSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        }
    }

    @Nonnull
    public int[] getSlotsForFace(Direction side) {
        return new int[]{0, 1};
    }

    public boolean canPlaceItemThroughFace(int index, ItemStack itemStackIn, @Nullable Direction direction) {
        return this.tank.isEmpty() && index == 0 && !this.isStackInSlot(1);
    }

    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return this.tank.isEmpty() && index == 1;
    }

    private void serverTick(Level level, BlockPos pos) {
        if (this.currentRecipe == null) {
            return;
        }
        FluidStack currentFluid = this.tank.getFluid();
        if (this.coolingTime >= 0) {
            ++this.timer;
            if (this.timer >= this.coolingTime) {
                if (!this.currentRecipe.matches((ICastingContainer)this.castingInventory, level)) {
                    this.currentRecipe = this.findCastingRecipe();
                    this.recipeName = null;
                    if (this.currentRecipe == null || this.currentRecipe.getFluidAmount(this.castingInventory) > currentFluid.getAmount()) {
                        this.timer = 0;
                        this.updateAnalogSignal();
                        return;
                    }
                }
                boolean consumed = this.currentRecipe.isConsumed(this.castingInventory);
                ItemStack output = this.currentRecipe.assemble((ICastingContainer)this.castingInventory);
                if (this.currentRecipe.switchSlots() != this.lastRedstone) {
                    if (!consumed) {
                        this.setItem(1, this.getItem(0));
                    }
                    this.setItem(0, output);
                } else {
                    if (consumed) {
                        ItemStack input = this.getItem(0).copy();
                        input.shrink(1);
                        this.setItem(0, input);
                    }
                    this.setItem(1, output);
                }
                if (this.lastRedstone) {
                    level.playSound(null, this.getBlockPos(), Sounds.CASTING_CLICKS.getSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
                }
                level.playSound(null, pos, Sounds.CASTING_COOLS.getSound(), SoundSource.BLOCKS, 0.5f, 4.0f);
                this.reset();
            } else {
                this.updateAnalogSignal();
            }
        }
    }

    private void clientTick(Level level, BlockPos pos) {
        if (this.currentRecipe == null) {
            return;
        }
        FluidStack currentFluid = this.tank.getFluid();
        if (currentFluid.getAmount() >= this.tank.getCapacity() && !currentFluid.isEmpty()) {
            ++this.timer;
            if (level.getRandom().nextFloat() > 0.9f) {
                level.addParticle((ParticleOptions)ParticleTypes.SMOKE, (double)pos.getX() + level.getRandom().nextDouble(), (double)pos.getY() + 1.1, (double)pos.getZ() + level.getRandom().nextDouble(), 0.0, 0.0, 0.0);
            }
        }
    }

    @Nullable
    private RecipeManager getRecipeManager(Level level) {
        if (level.recipeAccess() instanceof RecipeManager manager) {
            return manager;
        }
        return level.getServer() == null ? null : level.getServer().getRecipeManager();
    }

    @Nullable
    private CastingRecipeMatch findCastingRecipeMatch() {
        if (this.level == null) {
            return null;
        }
        RecipeManager recipeManager = this.getRecipeManager(this.level);
        Collection<?> recipes;
        if (recipeManager != null) {
            recipes = recipeManager.recipeMap().byType(this.castingType);
        } else if (this.level.isClientSide()) {
            // official matched casting recipes on the client too; since 26.1 the client has the synced casting types
            recipes = slimeknights.tconstruct.library.recipe.SidedRecipeLookup.recipes(this.level).byType(this.castingType);
        } else {
            return null;
        }
        for (Object rawHolder : recipes) {
            RecipeHolder<?> holder = (RecipeHolder<?>)rawHolder;
            ICastingRecipe recipe = (ICastingRecipe)holder.value();
            boolean matches;
            try {
                matches = recipe.matches((ICastingContainer)this.castingInventory, this.level);
            } catch (RuntimeException e) {
                TConstruct.LOG.error("Casting recipe {} failed while matching, skipping", holder.id(), e);
                continue;
            }
            if (!matches) continue;
            this.lastCastingRecipe = recipe;
            return new CastingRecipeMatch((RecipeHolder<ICastingRecipe>)holder);
        }
        return null;
    }

    @Nullable
    private ICastingRecipe findCastingRecipe() {
        CastingRecipeMatch match = this.findCastingRecipeMatch();
        return match == null ? null : match.recipe();
    }

    @Nullable
    private MoldingRecipe findMoldingRecipe() {
        if (this.level == null) {
            return null;
        }
        if (this.lastMoldingRecipe != null && this.lastMoldingRecipe.matches(this.moldingInventory, this.level)) {
            return this.lastMoldingRecipe;
        }
        RecipeManager recipeManager = this.getRecipeManager(this.level);
        if (recipeManager == null) {
            return null;
        }
        return recipeManager.getRecipeFor(this.moldingType, (IMoldingContainer)this.moldingInventory, this.level).map(holder -> {
            this.lastMoldingRecipe = (MoldingRecipe)holder.value();
            return this.lastMoldingRecipe;
        }).orElse(null);
    }

    public int initNewCasting(FluidStack fluid, IFluidHandler.FluidAction action) {
        boolean hasOutput;
        if (this.currentRecipe != null || this.recipeName != null) {
            return 0;
        }
        boolean hasInput = !this.getItem(0).isEmpty();
        boolean bl = hasOutput = !this.getItem(1).isEmpty();
        if (hasInput && hasOutput) {
            return 0;
        }
        this.castingInventory.setFluid(fluid.copyWithAmount(Integer.MAX_VALUE));
        if (!hasOutput) {
            if (!hasInput && this.requireCast) {
                return 0;
            }
            this.castingInventory.useInput();
            CastingRecipeMatch match = this.findCastingRecipeMatch();
            if (match != null) {
                if (action == FluidActions.EXECUTE) {
                    this.currentRecipe = match.recipe();
                    this.recipeName = match.name();
                    this.lastOutput = null;
                }
                int amount = match.recipe().getFluidAmount(this.castingInventory);
                return amount;
            }
        } else {
            this.castingInventory.useOutput();
            CastingRecipeMatch match = this.findCastingRecipeMatch();
            if (match != null) {
                if (action == FluidActions.EXECUTE) {
                    this.currentRecipe = match.recipe();
                    this.recipeName = match.name();
                    this.lastOutput = null;
                    this.setItem(0, this.getItem(1));
                    this.setItem(1, ItemStack.EMPTY);
                    this.castingInventory.useInput();
                }
                int amount = match.recipe().getFluidAmount(this.castingInventory);
                return amount;
            }
        }
        return 0;
    }

    public void reset() {
        this.timer = 0;
        this.currentRecipe = null;
        this.recipeName = null;
        this.lastOutput = null;
        this.castingInventory.setFluid(FluidStack.EMPTY);
        this.tank.reset();
        this.onContentsChanged();
    }

    public CastingState createCastingStateSnapshot() {
        return new CastingState(this.timer, this.coolingTime, this.currentRecipe, this.recipeName, this.lastOutput == null ? null : this.lastOutput.copy());
    }

    public void restoreCastingStateSnapshot(CastingState state) {
        this.timer = state.timer;
        this.coolingTime = state.coolingTime;
        this.currentRecipe = state.currentRecipe;
        this.recipeName = state.recipeName;
        this.lastOutput = state.lastOutput == null ? null : state.lastOutput.copy();
        this.castingInventory.setFluid(this.tank.getFluid());
    }

    public void onContentsChanged() {
        FluidStack fluidStack = this.tank.getFluid();
        if (fluidStack.getAmount() >= this.tank.getCapacity() && this.currentRecipe != null) {
            this.castingInventory.setFluid(fluidStack);
            this.coolingTime = Math.max(0, this.currentRecipe.getCoolingTime(this.castingInventory));
        } else {
            this.coolingTime = -1;
        }
        this.setChangedFast();
        this.updateAnalogSignal();
        Level world = this.getLevel();
        if (world != null && !world.isClientSide()) {
            BlockPos pos = this.getBlockPos();
            TinkerNetwork.getInstance().sendToClientsAround((Object)new FluidUpdatePacket(pos, fluidStack, this.tank.getCapacity()), (LevelAccessor)world, pos);
        }
    }

    @Override
    public void updateFluidTo(FluidStack fluid) {
        this.updateFluidTo(fluid, 0);
    }

    @Override
    public void updateFluidTo(FluidStack fluid, int syncedCapacity) {
        if (fluid.isEmpty()) {
            this.reset();
        } else if (syncedCapacity > 0) {
            this.tank.setCapacity(syncedCapacity);
            this.resolveClientRecipe(fluid);
        } else {
            int capacity = this.initNewCasting(fluid, FluidActions.EXECUTE);
            if (capacity > 0) {
                this.tank.setCapacity(capacity);
            }
        }
        this.tank.setFluid(fluid);
        this.onContentsChanged();
    }

    /**
     * Client only. Official clients found the casting recipe themselves when the fluid packet arrived
     * (initNewCasting), which drives the cooling visuals: the client timer, the smoke particles and the
     * output item fading in over the fluid. The port's packet carries the server's capacity instead, so
     * this finds the same recipe from the synced casting recipes, using the same slot rules as
     * initNewCasting, without moving items or changing the synced capacity. The server stays authoritative.
     */
    private void resolveClientRecipe(FluidStack fluid) {
        if (this.level == null || !this.level.isClientSide() || this.currentRecipe != null || fluid.isEmpty()) {
            return;
        }
        boolean hasInput = !this.getItem(0).isEmpty();
        boolean hasOutput = !this.getItem(1).isEmpty();
        // Casting onto a finished output moves that output into the cast slot on the server first; the
        // client learns the new slots from the block update, so only the plain cast slot case is resolved here.
        if (hasOutput || !hasInput && this.requireCast) {
            return;
        }
        this.castingInventory.setFluid(fluid.copyWithAmount(Integer.MAX_VALUE));
        this.castingInventory.useInput();
        CastingRecipeMatch match = this.findCastingRecipeMatch();
        if (match != null) {
            this.currentRecipe = match.recipe();
            this.recipeName = match.name();
            this.lastOutput = null;
        }
        this.castingInventory.useInput();
        this.castingInventory.setFluid(fluid);
    }

    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return null;
    }

    public ItemStack getRecipeOutput() {
        if (this.lastOutput == null) {
            if (this.currentRecipe == null || this.level == null) {
                return ItemStack.EMPTY;
            }
            this.castingInventory.setFluid(this.tank.getFluid());
            if (this.level.isClientSide()) {
                // Only the renderer asks on the client, every frame; a recipe that cannot assemble on the
                // client must not take the render thread down, so show no output item instead.
                try {
                    this.lastOutput = this.currentRecipe.assemble((ICastingContainer)this.castingInventory);
                } catch (RuntimeException e) {
                    TConstruct.LOG.error("Casting recipe {} failed to assemble on the client, not rendering its output", this.recipeName, e);
                    this.lastOutput = ItemStack.EMPTY;
                }
            } else {
                this.lastOutput = this.currentRecipe.assemble((ICastingContainer)this.castingInventory);
            }
        }
        return this.lastOutput;
    }

    private void updateAnalogSignal() {
        int newStrength;
        if (!(this.level != null && this.level.isClientSide() || (newStrength = this.getAnalogSignal()) == this.lastAnalogSignal)) {
            this.lastAnalogSignal = newStrength;
            if (this.level != null) {
                this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
            }
        }
    }

    public int getAnalogSignal() {
        if (this.isStackInSlot(1)) {
            return 15;
        }
        if (this.coolingTime > 0) {
            return 11 + this.timer * 4 / this.coolingTime;
        }
        int capacity = this.tank.getCapacity();
        if (capacity > 0) {
            return 2 + this.tank.getFluid().getAmount() * 9 / capacity;
        }
        if (this.isStackInSlot(0)) {
            return 1;
        }
        return 0;
    }

    private void loadRecipe(Level level, Identifier name) {
        FluidStack fluid = this.tank.getFluid();
        if (!fluid.isEmpty()) {
            RecipeManager recipeManager = this.getRecipeManager(level);
            java.util.Optional<RecipeHolder<?>> found;
            if (recipeManager != null) {
                found = recipeManager.byKey(ResourceKey.create((ResourceKey)Registries.RECIPE, (Identifier)name));
            } else if (level.isClientSide()) {
                // The client reads the synced recipe name to cool the cast like official: timer, smoke and the
                // output fading in. Since 26.1 the client only has the casting recipes the server synced.
                found = java.util.Optional.ofNullable(slimeknights.tconstruct.library.recipe.SidedRecipeLookup.byId(level, name));
            } else {
                return;
            }
            found.ifPresent(rawHolder -> {
                RecipeHolder<?> holder = (RecipeHolder<?>)rawHolder;
                Recipe patt0$temp = holder.value();
                if (!(patt0$temp instanceof ICastingRecipe)) {
                    return;
                }
                ICastingRecipe recipe = (ICastingRecipe)patt0$temp;
                if (recipe != this.currentRecipe) {
                    // cached output belongs to the previous recipe (the client keeps this block entity between casts)
                    this.lastOutput = null;
                }
                this.recipeName = holder.id().identifier();
                this.currentRecipe = recipe;
                this.castingInventory.setFluid(fluid);
                this.tank.setCapacity(recipe.getFluidAmount(this.castingInventory));
                if (fluid.getAmount() >= this.tank.getCapacity()) {
                    this.coolingTime = recipe.getCoolingTime(this.castingInventory);
                }
            });
        }
    }

    public void setLevel(Level pLevel) {
        super.setLevel(pLevel);
        if (this.recipeName != null) {
            this.loadRecipe(pLevel, this.recipeName);
        }
    }

    public void saveAdditional(ValueOutput output) {
        Identifier name;
        super.saveAdditional(output);
        output.putBoolean(TAG_REDSTONE, this.lastRedstone);
        this.tank.writeToOutput(output.child(TAG_TANK));
        if (this.currentRecipe != null || this.recipeName != null) {
            output.putInt(TAG_TIMER, this.timer);
        }
        if ((name = this.recipeName) != null) {
            output.putString(TAG_RECIPE, name.toString());
        }
    }

    public void writeInventoryToNBT(CompoundTag tag) {
        this.rebindUnregisteredInventoryItems();
        super.writeInventoryToNBT(tag);
    }

    protected void writeInventoryToOutput(ValueOutput output) {
        this.rebindUnregisteredInventoryItems();
        super.writeInventoryToOutput(output);
    }

    public void saveSynced(CompoundTag tags) {
        super.saveSynced(tags);
        tags.put(TAG_TANK, (Tag)this.tank.writeToTag(new CompoundTag()));
        if (this.currentRecipe != null || this.recipeName != null) {
            tags.putInt(TAG_TIMER, this.timer);
        }
        if (this.recipeName != null) {
            tags.putString(TAG_RECIPE, this.recipeName.toString());
        }
    }

    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.rebindUnregisteredInventoryItems();
        input.child(TAG_TANK).ifPresent(this.tank::readFromInput);
        this.timer = input.getIntOr(TAG_TIMER, 0);
        if (this.level != null && this.level.isClientSide() && input.getString(TAG_RECIPE).isEmpty()) {
            // the server finished or cleared the cast; drop the recipe the client loaded for cooling visuals
            this.currentRecipe = null;
            this.recipeName = null;
            this.lastOutput = null;
            this.coolingTime = -1;
        }
        input.getString(TAG_RECIPE).ifPresent(recipe -> {
            Identifier name = Identifier.parse((String)recipe);
            if (this.level != null) {
                this.loadRecipe(this.level, name);
            } else {
                this.recipeName = name;
            }
        });
        this.lastRedstone = input.getBooleanOr(TAG_REDSTONE, false);
    }

    private void rebindUnregisteredInventoryItems() {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            ItemStack stack = this.getItem(i);
            ItemStack rebound = CastingBlockEntity.rebindUnregisteredItem(stack);
            if (rebound == stack) continue;
            this.setItem(i, rebound);
            TConstruct.LOG.warn("Rebound direct item holder in casting inventory: {}", rebound.typeHolder().unwrapKey().orElse(null));
        }
    }

    private static ItemStack rebindUnregisteredItem(ItemStack stack) {
        if (stack.isEmpty() || stack.typeHolder().unwrapKey().isPresent()) {
            return stack;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return BuiltInRegistries.ITEM.get(key).map(holder -> new ItemStack((Holder)holder, stack.getCount(), stack.getComponentsPatch())).orElse(stack);
    }

    @Nullable
    public static <CAST extends CastingBlockEntity, RET extends BlockEntity> BlockEntityTicker<RET> getTicker(Level level, BlockEntityType<RET> check, BlockEntityType<CAST> casting) {
        return BlockEntityHelper.castTicker(check, casting, level.isClientSide() ? CLIENT_TICKER : SERVER_TICKER);
    }

    private static ItemStack copyStackWithSize(ItemStack stack, int size) {
        return stack.copyWithCount(size);
    }

    @Generated
    public CastingFluidHandler getTank() {
        return this.tank;
    }

    @Generated
    public int getTimer() {
        return this.timer;
    }

    @Generated
    public int getCoolingTime() {
        return this.coolingTime;
    }

    @Generated
    public TagKey<Item> getEmptyCastTag() {
        return this.emptyCastTag;
    }

    private record CastingRecipeMatch(Identifier name, ICastingRecipe recipe) {
        private CastingRecipeMatch(RecipeHolder<ICastingRecipe> holder) {
            this(holder.id().identifier(), (ICastingRecipe)holder.value());
        }
    }

    public record CastingState(int timer, int coolingTime, @Nullable ICastingRecipe currentRecipe, @Nullable Identifier recipeName, @Nullable ItemStack lastOutput) {
    }

    public static class Table
    extends CastingBlockEntity {
        public Table(BlockPos pos, BlockState state) {
            super((BlockEntityType)TinkerSmeltery.table.get(), pos, state, (RecipeType<ICastingRecipe>)((RecipeType)TinkerRecipeTypes.CASTING_TABLE.get()), (RecipeType<MoldingRecipe>)((RecipeType)TinkerRecipeTypes.MOLDING_TABLE.get()), TinkerTags.Items.TABLE_EMPTY_CASTS);
        }
    }

    public static class Basin
    extends CastingBlockEntity {
        public Basin(BlockPos pos, BlockState state) {
            super((BlockEntityType)TinkerSmeltery.basin.get(), pos, state, (RecipeType<ICastingRecipe>)((RecipeType)TinkerRecipeTypes.CASTING_BASIN.get()), (RecipeType<MoldingRecipe>)((RecipeType)TinkerRecipeTypes.MOLDING_BASIN.get()), TinkerTags.Items.BASIN_EMPTY_CASTS);
        }
    }
}
