package com.example.examplemod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SteveEntity extends PathfinderMob {
    private static final int STARE_LIMIT = 20;
    private static final int[][] DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private enum Mode { STALK, SABOTAGE, STRIKE, HUNT }

    private Mode mode = Mode.STALK;
    private Player victim;
    private Haunt.PendingChest chest;
    private int stareTicks;
    private int modeTicks;
    private int cooldown = 400;
    private int relocateDelay;

    public SteveEntity(EntityType<? extends SteveEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 6.0)
            .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3, true));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.tickCount % 2 != 0) {
            return;
        }
        if (this.victim == null || !this.victim.isAlive() || this.victim.level() != this.level() || this.tickCount % 40 == 0) {
            this.victim = level.getNearestPlayer(this, 160.0);
        }
        if (this.victim == null) {
            this.getNavigation().stop();
            return;
        }
        if (this.mode != Mode.HUNT && this.isWatchedBy(this.victim)) {
            this.stareTicks += 2;
            this.getNavigation().stop();
            this.getLookControl().setLookAt(this.victim, 30.0F, 30.0F);
            if (this.stareTicks > STARE_LIMIT || this.distanceToSqr(this.victim) < 16.0) {
                this.endAction();
                if (!this.relocate(this.victim)) {
                    this.discard();
                }
            }
            return;
        }
        this.stareTicks = Math.max(0, this.stareTicks - 1);
        switch (this.mode) {
            case STALK -> this.stalk(level);
            case SABOTAGE -> this.sabotage(level);
            case STRIKE -> this.strike(level);
            case HUNT -> this.hunt();
        }
    }

    private void stalk(ServerLevel level) {
        this.getLookControl().setLookAt(this.victim, 10.0F, 10.0F);
        if (this.cooldown > 0) {
            this.cooldown -= 2;
        } else if (this.chooseAction(level)) {
            return;
        }
        if (this.relocateDelay > 0) {
            this.relocateDelay -= 2;
            return;
        }
        boolean tooFar = this.distanceTo(this.victim) > 28.0F;
        boolean lostView = this.tickCount % 60 == 0 && this.isSheltered(level, this.victim)
            && !this.clearView(level, this.getEyePosition(), this.victim.getEyePosition());
        if ((tooFar || lostView) && !this.relocate(this.victim)) {
            this.relocateDelay = 40;
        }
    }

    private boolean chooseAction(ServerLevel level) {
        ThreatProfile profile = ThreatProfile.of(this.victim);
        if (this.victim.getHealth() <= 7.0F && !profile.totem() && this.distanceTo(this.victim) < 60.0F) {
            this.begin(Mode.HUNT);
            return true;
        }
        if (!profile.canSurviveFall() && this.hazardDirection(level) != null) {
            this.begin(Mode.STRIKE);
            return true;
        }
        Haunt.PendingChest pending = Haunt.nextReady(this.victim, level.getGameTime());
        if (pending != null) {
            this.chest = pending;
            this.begin(Mode.SABOTAGE);
            return true;
        }
        if (this.random.nextInt(4) == 0 && this.snuffTorches(level)) {
            this.cooldown = 600 + this.random.nextInt(1200);
        } else {
            this.cooldown = 100;
        }
        return false;
    }

    private void begin(Mode newMode) {
        this.mode = newMode;
        this.modeTicks = 0;
        if (newMode == Mode.HUNT) {
            this.setTarget(this.victim);
        }
    }

    private void endAction() {
        this.mode = Mode.STALK;
        this.modeTicks = 0;
        this.chest = null;
        this.setTarget(null);
        this.cooldown = 400 + this.random.nextInt(800);
    }

    private void sabotage(ServerLevel level) {
        this.modeTicks += 2;
        if (this.chest == null || this.modeTicks > 1200) {
            this.endAction();
            return;
        }
        BlockPos pos = this.chest.pos();
        if (!level.hasChunkAt(pos) || !isStorage(level.getBlockState(pos))) {
            Haunt.forget(this.victim.getUUID(), this.chest);
            this.endAction();
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        if (this.victim.distanceToSqr(center) < 196.0) {
            this.endAction();
            return;
        }
        if (this.distanceToSqr(center) > 9.0) {
            if (this.modeTicks == 2 && this.distanceToSqr(center) > 2304.0) {
                this.relocateNear(level, pos);
            }
            this.getNavigation().moveTo(center.x, center.y, center.z, 1.2);
            return;
        }
        this.burn(level, pos);
        Haunt.forget(this.victim.getUUID(), this.chest);
        this.endAction();
        this.relocate(this.victim);
    }

    private void strike(ServerLevel level) {
        this.modeTicks += 2;
        Vec3 direction = this.hazardDirection(level);
        if (direction == null || this.modeTicks > 400 || ThreatProfile.of(this.victim).canSurviveFall()) {
            this.endAction();
            return;
        }
        this.getLookControl().setLookAt(this.victim, 30.0F, 30.0F);
        if (this.distanceTo(this.victim) > 2.6F) {
            this.getNavigation().moveTo(this.victim, 1.5);
            return;
        }
        this.victim.setDeltaMovement(direction.x * 0.9, 0.4, direction.z * 0.9);
        if (this.victim instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        }
        this.endAction();
        this.relocate(this.victim);
    }

    private void hunt() {
        this.modeTicks += 2;
        if (this.modeTicks > 600 || this.victim.getHealth() > 12.0F) {
            this.endAction();
            this.relocate(this.victim);
            return;
        }
        this.setTarget(this.victim);
    }

    private Vec3 hazardDirection(ServerLevel level) {
        BlockPos base = this.victim.blockPosition();
        BlockPos floor = base.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return null;
        }
        int threshold = Math.min((int) this.victim.getHealth() + 3, 14);
        Vec3 best = null;
        int bestDrop = 0;
        for (int[] d : DIRECTIONS) {
            for (int step = 1; step <= 3; step++) {
                BlockPos column = base.offset(d[0] * step, 0, d[1] * step);
                if (!level.hasChunkAt(column) || !level.getBlockState(column).isAir()) {
                    break;
                }
                int drop = this.dropBelow(level, column);
                if (drop >= threshold && drop > bestDrop) {
                    bestDrop = drop;
                    best = new Vec3(d[0], 0.0, d[1]).normalize();
                }
            }
        }
        return best;
    }

    private int dropBelow(ServerLevel level, BlockPos column) {
        int drop = 0;
        for (int i = 1; i <= 24; i++) {
            BlockState state = level.getBlockState(column.below(i));
            if (state.getFluidState().is(FluidTags.LAVA)) {
                return 99;
            }
            if (!state.getFluidState().isEmpty()) {
                return 0;
            }
            if (!state.isAir()) {
                break;
            }
            drop++;
        }
        return drop;
    }

    private static boolean isStorage(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL);
    }

    private void burn(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        java.util.List<BlockPos> parts = new java.util.ArrayList<>();
        parts.add(pos);
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.relative(direction);
                if (level.getBlockState(neighbor).is(state.getBlock())) {
                    parts.add(neighbor);
                }
            }
        }
        for (BlockPos part : parts) {
            BlockEntity blockEntity = level.getBlockEntity(part);
            if (blockEntity instanceof Container container) {
                container.clearContent();
            }
            level.setBlock(part, Blocks.FIRE.defaultBlockState(), 3);
        }
    }

    private boolean snuffTorches(ServerLevel level) {
        Vec3 view = this.victim.getViewVector(1.0F);
        Vec3 eyes = this.victim.getEyePosition();
        BlockPos origin = this.victim.blockPosition();
        int removed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-12, -4, -12), origin.offset(12, 4, 12))) {
            BlockState state = level.getBlockState(pos);
            boolean torch = state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH);
            if (!torch) {
                continue;
            }
            Vec3 toTorch = Vec3.atCenterOf(pos).subtract(eyes).normalize();
            if (toTorch.dot(view) > -0.2) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            removed++;
            if (removed >= 2) {
                break;
            }
        }
        return removed > 0;
    }

    private boolean isWatchedBy(Player target) {
        Vec3 toMe = this.getEyePosition().subtract(target.getEyePosition());
        double distance = toMe.length();
        if (distance < 1.0E-4) {
            return true;
        }
        double alignment = target.getViewVector(1.0F).dot(toMe.scale(1.0 / distance));
        return alignment > 0.8 && this.level() instanceof ServerLevel level
            && this.clearView(level, target.getEyePosition(), this.getEyePosition());
    }

    private boolean clearView(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this)).getType()
            == HitResult.Type.MISS;
    }

    private boolean isSheltered(ServerLevel level, Player target) {
        return !level.canSeeSky(target.blockPosition().above());
    }

    public boolean relocate(Player target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        List<Vec3> candidates = this.isSheltered(level, target) ? this.windowSpots(level, target) : this.coverSpots(level, target);
        if (candidates.isEmpty()) {
            candidates = this.distantSpots(level, target);
        }
        Vec3 previous = this.position();
        for (Vec3 spot : candidates) {
            this.setPos(spot.x, spot.y, spot.z);
            if (!this.isWatchedBy(target)) {
                this.getNavigation().stop();
                this.stareTicks = 0;
                return true;
            }
        }
        this.setPos(previous.x, previous.y, previous.z);
        return false;
    }

    private static List<Vec3> nearest(List<Vec3> spots, Player target) {
        Vec3 origin = target.position();
        spots.sort(Comparator.comparingDouble(spot -> spot.distanceToSqr(origin)));
        return spots.size() > 8 ? new ArrayList<>(spots.subList(0, 8)) : spots;
    }

    private List<Vec3> windowSpots(ServerLevel level, Player target) {
        List<Vec3> spots = new ArrayList<>();
        Vec3 eyes = target.getEyePosition();
        for (int i = 0; i < 80; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = 5.0 + this.random.nextDouble() * 11.0;
            BlockPos around = BlockPos.containing(
                target.getX() + Math.cos(angle) * distance, target.getY(), target.getZ() + Math.sin(angle) * distance);
            BlockPos spot = findStandableBetween(level, around, target.getBlockY() + 3, target.getBlockY() - 4);
            if (spot == null || !level.canSeeSky(spot.above())) {
                continue;
            }
            Vec3 pos = new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            if (this.clearView(level, pos.add(0.0, 1.62, 0.0), eyes)) {
                spots.add(pos);
            }
        }
        return nearest(spots, target);
    }

    private List<Vec3> coverSpots(ServerLevel level, Player target) {
        List<Vec3> spots = new ArrayList<>();
        int px = Mth.floor(target.getX());
        int pz = Mth.floor(target.getZ());
        int py = target.getBlockY();
        for (int dx = -22; dx <= 22; dx++) {
            for (int dz = -22; dz <= 22; dz++) {
                int squared = dx * dx + dz * dz;
                if (squared < 36 || squared > 484) {
                    continue;
                }
                int ox = px + dx;
                int oz = pz + dz;
                if (!level.hasChunkAt(new BlockPos(ox, py, oz))) {
                    continue;
                }
                int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox, oz);
                double dirX = ox + 0.5 - target.getX();
                double dirZ = oz + 0.5 - target.getZ();
                double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
                dirX /= length;
                dirZ /= length;
                for (int side = -1; side <= 1; side += 2) {
                    double sx = ox + 0.5 + dirX * 1.2 - dirZ * 0.5 * side;
                    double sz = oz + 0.5 + dirZ * 1.2 + dirX * 0.5 * side;
                    int bx = Mth.floor(sx);
                    int bz = Mth.floor(sz);
                    if (!level.hasChunkAt(new BlockPos(bx, py, bz))) {
                        continue;
                    }
                    int standY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                    int rise = height - standY;
                    if (rise < 2 || rise > 8 || !this.canStandAt(level, sx, standY, sz)) {
                        continue;
                    }
                    spots.add(new Vec3(sx, standY, sz));
                }
            }
        }
        return nearest(spots, target);
    }

    private boolean canStandAt(ServerLevel level, double x, int y, double z) {
        BlockPos below = BlockPos.containing(x, y - 1, z);
        if (!level.getFluidState(below).isEmpty() || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        for (int cx = -1; cx <= 1; cx += 2) {
            for (int cz = -1; cz <= 1; cz += 2) {
                BlockPos feet = BlockPos.containing(x + cx * 0.3, y, z + cz * 0.3);
                if (!level.isEmptyBlock(feet) || !level.isEmptyBlock(feet.above())) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<Vec3> distantSpots(ServerLevel level, Player target) {
        List<Vec3> spots = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = 30.0 + this.random.nextDouble() * 14.0;
            BlockPos around = BlockPos.containing(
                target.getX() + Math.cos(angle) * distance, target.getY(), target.getZ() + Math.sin(angle) * distance);
            BlockPos spot = findStandable(level, around);
            if (spot != null) {
                spots.add(new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
            }
        }
        return spots;
    }

    private void relocateNear(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 16; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = 10.0 + this.random.nextDouble() * 6.0;
            BlockPos around = BlockPos.containing(
                pos.getX() + Math.cos(angle) * distance, pos.getY(), pos.getZ() + Math.sin(angle) * distance);
            BlockPos spot = findStandable(level, around);
            if (spot != null) {
                this.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                this.getNavigation().stop();
                return;
            }
        }
    }

    private static BlockPos findStandable(ServerLevel level, BlockPos around) {
        if (!level.hasChunkAt(around)) {
            return null;
        }
        for (int y = around.getY() + 10; y >= around.getY() - 24; y--) {
            BlockPos pos = new BlockPos(around.getX(), y, around.getZ());
            if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) {
                BlockPos below = pos.below();
                if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static BlockPos findStandableBetween(ServerLevel level, BlockPos around, int top, int bottom) {
        if (!level.hasChunkAt(around)) {
            return null;
        }
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(around.getX(), y, around.getZ());
            if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) {
                BlockPos below = pos.below();
                if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return pos;
                }
            }
        }
        return null;
    }
}
