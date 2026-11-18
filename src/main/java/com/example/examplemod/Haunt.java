package com.example.examplemod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class Haunt {
    private static final int MAX_PENDING = 8;
    private static final long EXPIRE_TICKS = 24000L;
    private static final long MIN_AGE_TICKS = 200L;
    private static final double SAFE_DISTANCE_SQR = 18.0 * 18.0;
    private static final Map<UUID, Deque<PendingChest>> PENDING = new HashMap<>();

    public record PendingChest(ResourceKey<Level> dimension, BlockPos pos, long time) {}

    private Haunt() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity) {
            remember(event.getEntity().getUUID(),
                new PendingChest(level.dimension(), event.getPos().immutable(), level.getGameTime()));
        }
    }

    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (!(event.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 200 != 0 || player.isSpectator()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getMaxLocalRawBrightness(player.blockPosition()) > 7) {
            return;
        }
        if (level.random.nextInt(60) != 0) {
            return;
        }
        if (!level.getEntitiesOfClass(SteveEntity.class, player.getBoundingBox().inflate(160.0)).isEmpty()) {
            return;
        }
        SteveEntity steve = new SteveEntity(ExampleMod.STEVE.get(), level);
        if (steve.relocate(player)) {
            level.addFreshEntity(steve);
        }
    }

    public static PendingChest nextReady(Player player, long now) {
        Deque<PendingChest> queue = PENDING.get(player.getUUID());
        if (queue == null) {
            return null;
        }
        queue.removeIf(chest -> now - chest.time() > EXPIRE_TICKS);
        for (PendingChest chest : queue) {
            if (!chest.dimension().equals(player.level().dimension())) {
                continue;
            }
            if (now - chest.time() < MIN_AGE_TICKS) {
                continue;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(chest.pos())) < SAFE_DISTANCE_SQR) {
                continue;
            }
            return chest;
        }
        return null;
    }

    public static void forget(UUID playerId, PendingChest chest) {
        Deque<PendingChest> queue = PENDING.get(playerId);
        if (queue != null) {
            queue.remove(chest);
        }
    }

    private static void remember(UUID playerId, PendingChest chest) {
        Deque<PendingChest> queue = PENDING.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        queue.removeIf(existing -> existing.pos().equals(chest.pos()) && existing.dimension().equals(chest.dimension()));
        queue.addLast(chest);
        while (queue.size() > MAX_PENDING) {
            queue.removeFirst();
        }
    }
}
