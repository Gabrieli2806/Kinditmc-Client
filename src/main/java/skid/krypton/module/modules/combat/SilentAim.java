package skid.krypton.module.modules.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import skid.krypton.event.EventListener;
import skid.krypton.event.events.AttackEvent;
import skid.krypton.event.events.SendMovementPacketsEvent;
import skid.krypton.event.events.TickEvent;
import skid.krypton.module.Category;
import skid.krypton.module.Module;
import skid.krypton.module.setting.BooleanSetting;
import skid.krypton.module.setting.ModeSetting;
import skid.krypton.module.setting.NumberSetting;
import skid.krypton.utils.EncryptedString;

import java.util.Random;

public final class SilentAim extends Module {
    private final NumberSetting accuracy = new NumberSetting(EncryptedString.of("Accuracy"), 0.0, 100.0, 85.0, 1.0).getValue(EncryptedString.of("Hit accuracy percentage (100 = always hit)"));
    private final NumberSetting reach = new NumberSetting(EncryptedString.of("Reach"), 3.0, 6.0, 4.5, 0.1).getValue(EncryptedString.of("Maximum attack reach distance"));
    private final NumberSetting fov = new NumberSetting(EncryptedString.of("FOV"), 30.0, 180.0, 90.0, 5.0).getValue(EncryptedString.of("Field of view angle to detect targets"));
    private final BooleanSetting wTap = new BooleanSetting(EncryptedString.of("W-Tap"), false).setDescription(EncryptedString.of("Automatically W-tap when hitting"));
    private final ModeSetting<HitMode> hitMode = new ModeSetting<>(EncryptedString.of("Hit Mode"), HitMode.AUTO, HitMode.class);

    private final Random random = new Random();
    private Entity targetEntity;
    private boolean shouldWTap;
    private int wTapTicks;
    private float targetYaw;
    private float targetPitch;
    private boolean shouldRotate;

    public SilentAim() {
        super(EncryptedString.of("Silent Aim"), EncryptedString.of("Automatically aims at nearby entities"), -1, Category.COMBAT);
        this.addSettings(this.accuracy, this.reach, this.fov, this.wTap, this.hitMode);
    }

    @Override
    public void onEnable() {
        this.targetEntity = null;
        this.shouldWTap = false;
        this.wTapTicks = 0;
        this.shouldRotate = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.targetEntity = null;
        this.shouldWTap = false;
        this.shouldRotate = false;
        super.onDisable();
    }

    @EventListener
    public void onTick(final TickEvent event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }

        // Handle W-tap timing
        if (this.wTap.getValue() && this.shouldWTap) {
            if (this.wTapTicks > 0) {
                this.mc.player.input.movementForward = 0.0f;
                this.wTapTicks--;
            } else {
                this.shouldWTap = false;
            }
        }

        // Find nearest target within reach and FOV
        this.targetEntity = this.findTarget();

        // Auto-hit mode with cooldown check
        if (this.hitMode.isMode(HitMode.AUTO) && this.targetEntity != null) {
            if (this.canAttack() && this.shouldHit()) {
                this.aimAndAttackTarget();
            }
        }
    }

    @EventListener
    public void onAttack(final AttackEvent event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }

        // Manual hit mode - only aim when player attacks
        if (this.hitMode.isMode(HitMode.MANUAL)) {
            final Entity target = this.findTarget();
            if (target != null && this.shouldHit()) {
                this.targetEntity = target;
                // Attack will happen naturally from the event
                if (this.wTap.getValue()) {
                    this.triggerWTap();
                }
            }
        }
    }

    @EventListener
    public void onSendMovementPackets(final SendMovementPacketsEvent event) {
        if (this.mc.player == null || !this.shouldRotate) {
            return;
        }

        // Apply rotation silently through movement packets
        this.mc.player.setYaw(this.targetYaw);
        this.mc.player.setPitch(this.targetPitch);
    }

    private Entity findTarget() {
        if (this.mc.player == null || this.mc.world == null) {
            return null;
        }

        Entity closestEntity = null;
        double closestDistance = this.reach.getValue();

        for (final Entity entity : this.mc.world.getEntities()) {
            if (entity == this.mc.player || !(entity instanceof LivingEntity)) {
                continue;
            }

            if (entity instanceof PlayerEntity && ((PlayerEntity) entity).isCreative()) {
                continue;
            }

            final double distance = this.mc.player.distanceTo(entity);

            // Check if entity is within reach and FOV
            if (distance <= this.reach.getValue() && this.isInFOV(entity)) {
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }

        return closestEntity;
    }

    private boolean isInFOV(final Entity entity) {
        if (this.mc.player == null) {
            return false;
        }

        final Vec3d playerPos = this.mc.player.getEyePos();
        final Vec3d entityPos = entity.getBoundingBox().getCenter();
        final Vec3d toEntity = entityPos.subtract(playerPos).normalize();

        final float playerYaw = this.mc.player.getYaw();
        final float playerPitch = this.mc.player.getPitch();

        final double yawRad = Math.toRadians(playerYaw);
        final double pitchRad = Math.toRadians(playerPitch);

        final Vec3d lookVec = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();

        final double dot = lookVec.dotProduct(toEntity);
        final double angle = Math.toDegrees(Math.acos(dot));

        return angle <= this.fov.getValue() / 2.0;
    }

    private boolean shouldHit() {
        final double hitChance = this.accuracy.getValue() / 100.0;
        return this.random.nextDouble() <= hitChance;
    }

    private boolean canAttack() {
        if (this.mc.player == null) {
            return false;
        }
        // Check if attack cooldown is complete (>= 1.0 = full strength)
        return this.mc.player.getAttackCooldownProgress(0.5f) >= 1.0f;
    }

    private void aimAndAttackTarget() {
        if (this.targetEntity == null || this.mc.player == null || this.mc.interactionManager == null) {
            return;
        }

        // Calculate rotation to target
        final Vec3d playerPos = this.mc.player.getEyePos();
        final Vec3d targetPos = this.targetEntity.getBoundingBox().getCenter();
        final Vec3d diff = targetPos.subtract(playerPos);

        final double distance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        this.targetYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        this.targetPitch = (float) -Math.toDegrees(Math.atan2(diff.y, distance));

        // Enable rotation for next movement packet
        this.shouldRotate = true;

        // Attack the target
        this.mc.interactionManager.attackEntity(this.mc.player, this.targetEntity);
        this.mc.player.swingHand(this.mc.player.getActiveHand());

        // Reset rotation flag after attack
        this.shouldRotate = false;

        if (this.wTap.getValue()) {
            this.triggerWTap();
        }
    }

    private void triggerWTap() {
        this.shouldWTap = true;
        this.wTapTicks = 2; // Stop moving forward for 2 ticks
    }

    public Entity getTargetEntity() {
        return this.targetEntity;
    }

    public enum HitMode {
        AUTO("Auto", 0),
        MANUAL("Manual", 1);

        HitMode(final String name, final int ordinal) {
        }
    }
}
