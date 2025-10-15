package skid.krypton.module.modules.combat;

import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import skid.krypton.event.EventListener;
import skid.krypton.event.events.TickEvent;
import skid.krypton.module.Category;
import skid.krypton.module.Module;
import skid.krypton.module.setting.BooleanSetting;
import skid.krypton.module.setting.NumberSetting;
import skid.krypton.utils.BlockUtil;
import skid.krypton.utils.EncryptedString;
import skid.krypton.utils.InventoryUtil;
import skid.krypton.utils.KeyUtils;

public final class AnchorMacro extends Module {
    private final NumberSetting switchDelay = new NumberSetting(EncryptedString.of("Switch Delay"), 0.0, 20.0, 0.0, 1.0);
    private final NumberSetting glowstoneDelay = new NumberSetting(EncryptedString.of("Glowstone Delay"), 0.0, 20.0, 0.0, 1.0);
    private final NumberSetting explodeDelay = new NumberSetting(EncryptedString.of("Explode Delay"), 0.0, 20.0, 0.0, 1.0);
    private final NumberSetting totemSlot = new NumberSetting(EncryptedString.of("Totem Slot"), 1.0, 9.0, 1.0, 1.0);
    private final BooleanSetting autoSafe = new BooleanSetting(EncryptedString.of("Auto Safe"), true).setDescription(EncryptedString.of("Automatically place obsidian to protect from explosion"));
    private final NumberSetting safeDelay = new NumberSetting(EncryptedString.of("Safe Delay"), 0.0, 20.0, 2.0, 1.0);

    private int keybind;
    private int glowstoneDelayCounter;
    private int explodeDelayCounter;
    private int safeDelayCounter;
    private boolean safeBlockPlaced;

    public AnchorMacro() {
        super(EncryptedString.of("Anchor Macro"), EncryptedString.of("Automatically blows up respawn anchors for you"), -1, Category.COMBAT);
        this.keybind = 0;
        this.glowstoneDelayCounter = 0;
        this.explodeDelayCounter = 0;
        this.safeDelayCounter = 0;
        this.safeBlockPlaced = false;
        this.addSettings(this.switchDelay, this.glowstoneDelay, this.explodeDelay, this.totemSlot, this.autoSafe, this.safeDelay);
    }

    @Override
    public void onEnable() {
        this.resetCounters();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onTick(final TickEvent tickEvent) {
        if (this.mc.currentScreen != null) {
            return;
        }
        if (this.isShieldOrFoodActive()) {
            return;
        }
        if (KeyUtils.isKeyPressed(1)) {
            this.handleAnchorInteraction();
        }
    }

    private boolean isShieldOrFoodActive() {
        final boolean isFood = this.mc.player.getMainHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD) || this.mc.player.getOffHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD);
        final boolean isShield = this.mc.player.getMainHandStack().getItem() instanceof ShieldItem || this.mc.player.getOffHandStack().getItem() instanceof ShieldItem;
        final boolean isRightClickPressed = GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), 1) == 1;
        return (isFood || isShield) && isRightClickPressed;
    }

    private void handleAnchorInteraction() {
        if (!(this.mc.crosshairTarget instanceof BlockHitResult blockHitResult)) {
            return;
        }
        if (!BlockUtil.isBlockAtPosition(blockHitResult.getBlockPos(), Blocks.RESPAWN_ANCHOR)) {
            return;
        }
        this.mc.options.useKey.setPressed(false);
        if (BlockUtil.isRespawnAnchorUncharged(blockHitResult.getBlockPos())) {
            this.placeGlowstone(blockHitResult);
        } else if (BlockUtil.isRespawnAnchorCharged(blockHitResult.getBlockPos())) {
            this.explodeAnchor(blockHitResult);
        }
    }

    private void placeGlowstone(final BlockHitResult blockHitResult) {
        if (!this.mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            if (this.keybind < this.switchDelay.getIntValue()) {
                ++this.keybind;
                return;
            }
            this.keybind = 0;
            InventoryUtil.swap(Items.GLOWSTONE);
        }
        if (this.mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            if (this.glowstoneDelayCounter < this.glowstoneDelay.getIntValue()) {
                ++this.glowstoneDelayCounter;
                return;
            }
            this.glowstoneDelayCounter = 0;
            BlockUtil.interactWithBlock(blockHitResult, true);
        }
    }

    private void explodeAnchor(final BlockHitResult blockHitResult) {
        // ========== PASO 1: COLOCAR OBSIDIANA PROTECTORA PRIMERO ==========
        if (this.autoSafe.getValue() && !this.safeBlockPlaced) {
            // Intenta colocar el bloque de obsidiana
            if (this.placeSafeBlock(blockHitResult.getBlockPos())) {
                this.safeBlockPlaced = true;
                // DETENER AQUÍ - No explotar todavía
            }
            // Siempre retornar mientras coloca obsidiana
            return;
        }

        // ========== PASO 2: ESPERAR DESPUÉS DE COLOCAR OBSIDIANA ==========
        if (this.autoSafe.getValue() && this.safeBlockPlaced && this.safeDelayCounter < this.safeDelay.getIntValue()) {
            ++this.safeDelayCounter;
            // DETENER AQUÍ - Esperando que el bloque se asiente
            return;
        }

        // ========== PASO 3: AHORA SÍ, EXPLOTAR EL ANCHOR ==========
        // Cambiar al slot del totem
        final int selectedSlot = this.totemSlot.getIntValue() - 1;
        if (this.mc.player.getInventory().selectedSlot != selectedSlot) {
            if (this.keybind < this.switchDelay.getIntValue()) {
                ++this.keybind;
                return;
            }
            this.keybind = 0;
            this.mc.player.getInventory().selectedSlot = selectedSlot;
            return;
        }

        // Explotar el anchor
        if (this.explodeDelayCounter < this.explodeDelay.getIntValue()) {
            ++this.explodeDelayCounter;
            return;
        }

        // ¡BOOM! Explotar con protección
        this.explodeDelayCounter = 0;
        BlockUtil.interactWithBlock(blockHitResult, true);

        // Reset para el próximo uso
        this.safeBlockPlaced = false;
        this.safeDelayCounter = 0;
    }

    private boolean placeSafeBlock(final BlockPos anchorPos) {
        if (this.mc.player == null || this.mc.world == null) {
            return false;
        }

        // Find the direction player is facing relative to anchor
        final Direction playerDirection = this.getPlayerFacingDirection(anchorPos);
        if (playerDirection == null) {
            return false;
        }

        // Calculate position for safe block (between player and anchor)
        final BlockPos safePos = anchorPos.offset(playerDirection);

        // Check if position is already occupied
        if (!this.mc.world.getBlockState(safePos).isReplaceable()) {
            return true; // Already has a block there
        }

        // Switch to obsidian
        if (!this.mc.player.getMainHandStack().isOf(Items.OBSIDIAN)) {
            InventoryUtil.swap(Items.OBSIDIAN);
            return false;
        }

        // Place the obsidian block
        if (this.mc.player.getMainHandStack().isOf(Items.OBSIDIAN)) {
            final BlockHitResult hitResult = new BlockHitResult(
                safePos.toCenterPos(),
                playerDirection.getOpposite(),
                safePos,
                false
            );

            this.mc.interactionManager.interactBlock(
                this.mc.player,
                Hand.MAIN_HAND,
                hitResult
            );

            return true;
        }

        return false;
    }

    private Direction getPlayerFacingDirection(final BlockPos anchorPos) {
        if (this.mc.player == null) {
            return null;
        }

        final BlockPos playerPos = this.mc.player.getBlockPos();
        final int dx = anchorPos.getX() - playerPos.getX();
        final int dz = anchorPos.getZ() - playerPos.getZ();

        // Determine which direction the player is relative to the anchor
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.WEST : Direction.EAST;
        } else {
            return dz > 0 ? Direction.NORTH : Direction.SOUTH;
        }
    }

    private void resetCounters() {
        this.keybind = 0;
        this.glowstoneDelayCounter = 0;
        this.explodeDelayCounter = 0;
        this.safeDelayCounter = 0;
        this.safeBlockPlaced = false;
    }
}