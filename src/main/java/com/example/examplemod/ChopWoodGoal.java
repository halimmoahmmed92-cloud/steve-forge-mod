package com.example.examplemod;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Steve looks for logs around the spot where he was given the order,
 * walks to the nearest one, chops it and keeps the drops in his inventory.
 */
public class ChopWoodGoal extends Goal {
    /** How far from the work spot he searches (blocks). */
    private static final int RADIUS = 16;
    /** How far up/down from the work spot he searches (blocks). */
    private static final int HEIGHT = 8;
    /** He can chop a log when it is within 4 blocks of his eyes. */
    private static final double REACH_SQR = 16.0;
    /** Time to chop one log (20 ticks = 1 second). */
    private static final int TICKS_PER_LOG = 25;

    private final SteveEntity steve;
    private final Set<BlockPos> unreachable = new HashSet<>();
    private BlockPos target;
    private int breakProgress;
    private int repathTimer;
    private int searchCooldown;
    private int stuckTimer;

    public ChopWoodGoal(SteveEntity steve) {
        this.steve = steve;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.steve.getTask() == SteveTask.CHOP_WOOD && this.steve.hasAxe();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.target = null;
        this.breakProgress = 0;
        this.searchCooldown = 0;
        this.unreachable.clear();
    }

    @Override
    public void stop() {
        this.target = null;
        this.steve.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(this.steve.level() instanceof ServerLevel level)) {
            return;
        }

        // The log disappeared (someone else chopped it)
        if (this.target != null && !isLog(level, this.target)) {
            this.resetTarget();
        }

        if (this.target == null) {
            if (--this.searchCooldown > 0) {
                return;
            }
            this.searchCooldown = 20;
            this.target = this.findNearestLog(level);
            this.breakProgress = 0;
            this.repathTimer = 0;
            this.stuckTimer = 0;
            if (this.target == null) {
                this.giveUp(level);
            }
            return;
        }

        double cx = this.target.getX() + 0.5;
        double cy = this.target.getY() + 0.5;
        double cz = this.target.getZ() + 0.5;
        this.steve.getLookControl().setLookAt(cx, cy, cz);

        if (this.steve.getEyePosition().distanceToSqr(cx, cy, cz) <= REACH_SQR) {
            // Close enough: chop
            this.steve.getNavigation().stop();
            if (this.breakProgress % 5 == 0) {
                this.steve.swing(InteractionHand.MAIN_HAND);
            }
            if (++this.breakProgress >= TICKS_PER_LOG) {
                this.breakLog(level, this.target);
                this.resetTarget();
                this.searchCooldown = 0;
            }
        } else {
            // Too far: walk toward it
            this.breakProgress = 0;
            if (--this.repathTimer <= 0) {
                this.repathTimer = 10;
                boolean pathFound = this.steve.getNavigation().moveTo(cx, this.target.getY(), cz, 1.0);
                if (!pathFound) {
                    this.markUnreachable();
                    return;
                }
            }
            if (++this.stuckTimer > 200) { // 10 seconds without getting there
                this.markUnreachable();
            }
        }
    }

    private void resetTarget() {
        this.target = null;
        this.breakProgress = 0;
    }

    private void markUnreachable() {
        if (this.target != null) {
            if (this.unreachable.size() > 64) {
                this.unreachable.clear();
            }
            this.unreachable.add(this.target);
        }
        this.resetTarget();
        this.steve.getNavigation().stop();
    }

    private static boolean isLog(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.LOGS);
    }

    private BlockPos findNearestLog(ServerLevel level) {
        BlockPos center = this.steve.getWorkCenter();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-RADIUS, -HEIGHT, -RADIUS),
                center.offset(RADIUS, HEIGHT, RADIUS))) {
            if (!level.hasChunkAt(pos) || this.unreachable.contains(pos) || !isLog(level, pos)) {
                continue;
            }
            double dist = this.steve.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        return best;
    }

    private void breakLog(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.LOGS)) {
            return;
        }
        ItemStack axe = this.steve.getAxe();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, this.steve, axe);
        level.destroyBlock(pos, false);
        for (ItemStack drop : drops) {
            ItemStack leftover = this.steve.getInventory().addItem(drop);
            if (!leftover.isEmpty()) {
                // Inventory full: drop it on the ground
                Block.popResource(level, pos, leftover);
            }
        }
    }

    private void giveUp(ServerLevel level) {
        this.steve.setTask(SteveTask.FOLLOW, this.steve.blockPosition());
        Player player = level.getNearestPlayer(this.steve, 32.0);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("gui.examplemod.tablet.no_trees"));
        }
    }
}
