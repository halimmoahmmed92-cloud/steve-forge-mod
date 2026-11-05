package com.example.examplemod;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Steve: a companion mob.
 * By default he follows the nearest player and fights hostile mobs.
 * Right-click him to open his tablet: give him tools, then give him a task.
 */
public class SteveEntity extends PathfinderMob {
    public static final int INVENTORY_SIZE = 9;

    /** Tools and items Steve carries (saved with him). */
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private SteveTask task = SteveTask.FOLLOW;
    /** The spot around which he works (where you stood when you gave the order). */
    private BlockPos workCenter = BlockPos.ZERO;

    public SteveEntity(EntityType<? extends SteveEntity> type, Level level) {
        super(type, level);
    }

    /** Health, speed, damage and how far he can notice targets. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    // ---- Inventory and tasks ----

    public SimpleContainer getInventory() {
        return this.inventory;
    }

    public SteveTask getTask() {
        return this.task;
    }

    public BlockPos getWorkCenter() {
        return this.workCenter;
    }

    public void setTask(SteveTask newTask, BlockPos center) {
        this.task = newTask;
        this.workCenter = center;
        this.getNavigation().stop();
    }

    /** The first axe found in his inventory, or an empty stack. */
    public ItemStack getAxe() {
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (stack.is(ItemTags.AXES)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean hasAxe() {
        return !this.getAxe().isEmpty();
    }

    // ---- Behaviour ----

    @Override
    protected void registerGoals() {
        // Lower number = higher priority
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(2, new ChopWoodGoal(this));
        this.goalSelector.addGoal(2, new FollowNearestPlayerGoal(this, 1.1, 4.0, 24.0));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return SteveEntity.this.getTask() == SteveTask.FOLLOW && super.canUse();
            }
        });
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        // If the axe is taken away from his inventory, he stops chopping
        if (!this.level().isClientSide() && this.tickCount % 20 == 0
            && this.task == SteveTask.CHOP_WOOD && !this.hasAxe()) {
            this.setTask(SteveTask.FOLLOW, this.blockPosition());
        }
    }

    /** Steve never despawns just because you walked away. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ---- Right-click: open the tablet ----

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                new SimpleMenuProvider(
                    (windowId, playerInventory, p) -> new SteveMenu(windowId, playerInventory, this),
                    Component.translatable("gui.examplemod.tablet.title")),
                buf -> buf.writeVarInt(this.getId()));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    // ---- Saving and loading ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        ValueOutput.TypedOutputList<ItemStackWithSlot> items = output.list("TabletItems", ItemStackWithSlot.CODEC);
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty()) {
                items.add(new ItemStackWithSlot(i, stack));
            }
        }
        output.putInt("Task", this.task.ordinal());
        output.putLong("WorkCenter", this.workCenter.asLong());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.listOrEmpty("TabletItems", ItemStackWithSlot.CODEC).forEach(slot -> {
            if (slot.isValidInContainer(this.inventory.getContainerSize())) {
                this.inventory.setItem(slot.slot(), slot.stack());
            }
        });
        this.task = SteveTask.byId(input.getIntOr("Task", 0));
        this.workCenter = BlockPos.of(input.getLongOr("WorkCenter", 0L));
    }

    /** When Steve dies, what he carries drops on the ground. */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide() && reason.shouldDestroy()) {
            for (int i = 0; i < this.inventory.getContainerSize(); i++) {
                ItemStack stack = this.inventory.removeItemNoUpdate(i);
                if (!stack.isEmpty()) {
                    Block.popResource(this.level(), this.blockPosition(), stack);
                }
            }
        }
        super.remove(reason);
    }

    /** Walks toward the nearest player until he is close enough (only when his task is FOLLOW). */
    private static class FollowNearestPlayerGoal extends Goal {
        private final SteveEntity mob;
        private final double speed;
        private final double startDistanceSqr;
        private final double searchRange;
        private final double stopDistanceSqr;
        private Player target;
        private int recalcTimer;

        FollowNearestPlayerGoal(SteveEntity mob, double speed, double stopDistance, double searchRange) {
            this.mob = mob;
            this.speed = speed;
            this.stopDistanceSqr = stopDistance * stopDistance;
            // Start walking once the player is a bit farther than the stop distance
            this.startDistanceSqr = (stopDistance + 2.0) * (stopDistance + 2.0);
            this.searchRange = searchRange;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.mob.getTask() != SteveTask.FOLLOW) {
                return false;
            }
            Player player = this.mob.level().getNearestPlayer(this.mob, this.searchRange);
            if (player == null || player.isSpectator()) {
                return false;
            }
            if (this.mob.distanceToSqr(player) < this.startDistanceSqr) {
                return false;
            }
            this.target = player;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.mob.getTask() == SteveTask.FOLLOW
                && this.target != null
                && this.target.isAlive()
                && !this.target.isSpectator()
                && this.mob.distanceToSqr(this.target) > this.stopDistanceSqr;
        }

        @Override
        public void start() {
            this.recalcTimer = 0;
        }

        @Override
        public void tick() {
            if (--this.recalcTimer <= 0) {
                this.recalcTimer = 10;
                this.mob.getNavigation().moveTo(this.target, this.speed);
            }
        }

        @Override
        public void stop() {
            this.target = null;
            this.mob.getNavigation().stop();
        }
    }
}
