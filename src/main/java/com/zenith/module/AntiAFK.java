package com.zenith.module;

import com.collarmc.pounce.Subscribe;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerSwingArmPacket;
import com.zenith.Proxy;
import com.zenith.event.module.ClientTickEvent;
import com.zenith.pathing.BlockPos;
import com.zenith.util.TickTimer;

import java.util.concurrent.ThreadLocalRandom;

import static com.zenith.util.Constants.*;
import static java.util.Objects.isNull;

public class AntiAFK extends Module {
    private final TickTimer swingTickTimer = new TickTimer();
    private final TickTimer startWalkTickTimer = new TickTimer();
    private final TickTimer walkTickTimer = new TickTimer();
    private final TickTimer rotateTimer = new TickTimer();
    private boolean shouldWalk = false;
    // toggle this between 1 and -1
    private double xDirectionMultiplier = 1.0;

    public AntiAFK(Proxy proxy) {
        super(proxy);
    }

    @Subscribe
    public void handleClientTickEvent(final ClientTickEvent event) {
        if (CONFIG.client.extra.antiafk.enabled && isNull(this.proxy.getCurrentPlayer().get()) && !proxy.isInQueue()) {
            if (CONFIG.client.extra.antiafk.actions.swingHand) {
                swingTick();
            }
            if (CONFIG.client.extra.antiafk.actions.walk) {
                walkTick();
            }
            if (CONFIG.client.extra.antiafk.actions.rotate && (!CONFIG.client.extra.spook.enabled || !spookHasTarget())) {
                rotateTick();
            }
        }
    }

    private boolean spookHasTarget() {
        return this.proxy.getModules().stream()
                .filter(m -> m instanceof Spook)
                .map(m -> ((Spook) m).hasTarget.get())
                .findFirst()
                .orElse(false);
    }

    private void rotateTick() {
        if (rotateTimer.tick(1500L, true)) {
            this.proxy.getClient().send(new ClientPlayerRotationPacket(
                    true,
                    -90 + (90 + 90) * ThreadLocalRandom.current().nextFloat(),
                    -90 + (90 + 90) * ThreadLocalRandom.current().nextFloat()
            ));
        }
    }

    private void walkTick() {
        // temp lowering this 200 for testing
        if (startWalkTickTimer.tick(200L, true)) {
            shouldWalk = !shouldWalk;
            walkTickTimer.reset();
        }
        if (shouldWalk) {
            if (walkTickTimer.tick(100L, true)) {
                shouldWalk = false;
                xDirectionMultiplier *= -1.0;
            } else {
                // calculate a walk, to keep things simple let's just walk +x and -x
                double newX = CACHE.getPlayerCache().getX() + (0.2 * xDirectionMultiplier);
                if (nextWalkSafe((int) Math.floor(newX), (int) Math.floor(CACHE.getPlayerCache().getY()), (int) Math.floor(CACHE.getPlayerCache().getZ()))) {
                    this.proxy.getClient().send(
                            new ClientPlayerPositionPacket(
                                    true,
                                    newX,
                                    CACHE.getPlayerCache().getY(),
                                    CACHE.getPlayerCache().getZ()));
                    CACHE.getPlayerCache().setX(newX);
                } else {
                    CLIENT_LOG.info("next move not safe {}, {}, {}", newX,
                            CACHE.getPlayerCache().getY(),
                            CACHE.getPlayerCache().getZ());
                }
            }
        }
    }

    private boolean nextWalkSafe(final int x, final int y, final int z) {
        boolean groundSolid = this.proxy.world.isSolidBlock(new BlockPos(x, y - 1, z));
        boolean blocked = this.proxy.world.isSolidBlock(new BlockPos(x, y, z)) || this.proxy.world.isSolidBlock(new BlockPos(x, y + 1, z));
        return groundSolid && !blocked;
    }

    private void swingTick() {
        if (swingTickTimer.tick(3000L, true)) {
            this.proxy.getClient().send(new ClientPlayerSwingArmPacket(Hand.MAIN_HAND));
        }
    }
}
