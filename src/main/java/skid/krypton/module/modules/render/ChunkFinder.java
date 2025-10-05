package skid.krypton.module.modules.render;

import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import skid.krypton.event.EventListener;
import skid.krypton.event.events.Render3DEvent;
import skid.krypton.module.Category;
import skid.krypton.module.Module;
import skid.krypton.module.setting.BooleanSetting;
import skid.krypton.module.setting.NumberSetting;
import skid.krypton.utils.BlockUtil;
import skid.krypton.utils.EncryptedString;
import skid.krypton.utils.RenderUtils;

import java.awt.*;
import java.util.Iterator;

public final class ChunkFinder extends Module {
    private final NumberSetting alpha = new NumberSetting(EncryptedString.of("Alpha"), 1.0, 255.0, 125.0, 1.0);
    private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 1.0, 10.0, 5.0, 1.0);
    private final BooleanSetting tracers = new BooleanSetting(EncryptedString.of("Tracers"), false).setDescription(EncryptedString.of("Draws a line from your player to the block"));
    
    // Block detection settings
    private final BooleanSetting deepslate = new BooleanSetting(EncryptedString.of("Rotated Deepslate"), true).setDescription(EncryptedString.of("Detects rotated deepslate blocks"));
    private final BooleanSetting chests = new BooleanSetting(EncryptedString.of("Chests"), true).setDescription(EncryptedString.of("Detects misaligned chests"));
    private final BooleanSetting enderChests = new BooleanSetting(EncryptedString.of("Ender Chests"), true).setDescription(EncryptedString.of("Detects misaligned ender chests"));
    private final BooleanSetting spawners = new BooleanSetting(EncryptedString.of("Spawners"), true).setDescription(EncryptedString.of("Detects mob spawners"));
    private final BooleanSetting barrels = new BooleanSetting(EncryptedString.of("Barrels"), true).setDescription(EncryptedString.of("Detects misaligned barrels"));
    private final BooleanSetting pistons = new BooleanSetting(EncryptedString.of("Pistons"), true).setDescription(EncryptedString.of("Detects misaligned pistons"));
    private final BooleanSetting redstone = new BooleanSetting(EncryptedString.of("Redstone"), true).setDescription(EncryptedString.of("Detects misaligned redstone components"));

    public ChunkFinder() {
        super(EncryptedString.of("Chunk Finder"), EncryptedString.of("Finds chunk boundaries via rotated blocks"), -1, Category.RENDER);
        this.addSettings(this.alpha, this.range, this.tracers, this.deepslate, this.chests, this.enderChests, this.spawners, this.barrels, this.pistons, this.redstone);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onRender3D(final Render3DEvent render3DEvent) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }

        Camera camera = this.mc.gameRenderer.getCamera();
        if (camera == null) {
            return;
        }

        MatrixStack matrixStack = render3DEvent.matrixStack;
        matrixStack.push();
        
        Vec3d cameraPos = camera.getPos();
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        int playerChunkX = this.mc.player.getChunkPos().x;
        int playerChunkZ = this.mc.player.getChunkPos().z;
        int searchRange = this.range.getIntValue();

        // Search through chunks within range
        for (int chunkX = playerChunkX - searchRange; chunkX <= playerChunkX + searchRange; chunkX++) {
            for (int chunkZ = playerChunkZ - searchRange; chunkZ <= playerChunkZ + searchRange; chunkZ++) {
                WorldChunk chunk = (WorldChunk) this.mc.world.getChunk(chunkX, chunkZ);
                if (chunk != null) {
                    scanChunkForRotatedBlocks(chunk, matrixStack);
                }
            }
        }

        matrixStack.pop();
    }

    private void scanChunkForRotatedBlocks(WorldChunk chunk, MatrixStack matrixStack) {
        int chunkStartX = chunk.getPos().x << 4;
        int chunkStartZ = chunk.getPos().z << 4;
        
        // Scan the chunk for misaligned blocks
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = this.mc.world.getBottomY(); y < this.mc.world.getTopY(); y++) {
                    BlockPos pos = new BlockPos(chunkStartX + x, y, chunkStartZ + z);
                    BlockState state = this.mc.world.getBlockState(pos);
                    
                    if (isChunkIndicatorBlock(pos, state)) {
                        Color color = getColorForBlock(state.getBlock());
                        renderBlock(pos, matrixStack, color);
                        
                        if (this.tracers.getValue()) {
                            renderTracer(pos, matrixStack, color);
                        }
                    }
                }
            }
        }
    }

    private boolean isChunkIndicatorBlock(BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        
        // Check rotated deepslate (most common chunk finder)
        if (this.deepslate.getValue() && isRotatedDeepslate(pos, state)) {
            return true;
        }
        
        // Check chests
        if (this.chests.getValue() && block instanceof ChestBlock) {
            return isMisalignedChest(pos, state);
        }
        
        // Check ender chests
        if (this.enderChests.getValue() && block instanceof EnderChestBlock) {
            return isMisalignedDirectionalBlock(pos, state);
        }
        
        // Check spawners
        if (this.spawners.getValue() && block instanceof SpawnerBlock) {
            return true; // Spawners are always interesting for chunk finding
        }
        
        // Check barrels
        if (this.barrels.getValue() && block instanceof BarrelBlock) {
            return isMisalignedDirectionalBlock(pos, state);
        }
        
        // Check pistons
        if (this.pistons.getValue() && (block instanceof PistonBlock || block instanceof PistonHeadBlock)) {
            return isMisalignedDirectionalBlock(pos, state);
        }
        
        // Check redstone components
        if (this.redstone.getValue() && isRedstoneComponent(block)) {
            return isMisalignedRedstoneComponent(pos, state);
        }
        
        return false;
    }

    private boolean isRotatedDeepslate(BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        
        // Check for any deepslate variant
        if (block == Blocks.DEEPSLATE || 
            block == Blocks.COBBLED_DEEPSLATE ||
            block == Blocks.POLISHED_DEEPSLATE ||
            block == Blocks.DEEPSLATE_BRICKS ||
            block == Blocks.DEEPSLATE_TILES) {
            
            // Check if it has an axis property (indicating rotation)
            if (state.getProperties().contains(Properties.AXIS)) {
                Direction.Axis axis = state.get(Properties.AXIS);
                // Natural deepslate should have Y axis, rotated has X or Z
                return axis != Direction.Axis.Y;
            }
        }
        
        return false;
    }

    private boolean isMisalignedChest(BlockPos pos, BlockState state) {
        // Check if chest is facing an unusual direction for its position
        if (state.getProperties().contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            
            // Check if chest placement seems unnatural (not facing typical directions for the area)
            int x = pos.getX() & 15; // Position within chunk
            int z = pos.getZ() & 15;
            
            // Chests near chunk borders with specific facings might indicate chunk boundaries
            return (x <= 1 || x >= 14 || z <= 1 || z >= 14);
        }
        return false;
    }

    private boolean isMisalignedDirectionalBlock(BlockPos pos, BlockState state) {
        // Similar logic for other directional blocks
        if (state.getProperties().contains(Properties.HORIZONTAL_FACING)) {
            int x = pos.getX() & 15;
            int z = pos.getZ() & 15;
            return (x <= 1 || x >= 14 || z <= 1 || z >= 14);
        }
        return false;
    }

    private boolean isRedstoneComponent(Block block) {
        return block instanceof RedstoneWireBlock ||
               block instanceof RepeaterBlock ||
               block instanceof ComparatorBlock ||
               block instanceof RedstoneBlock ||
               block instanceof RedstoneTorchBlock;
    }

    private boolean isMisalignedRedstoneComponent(BlockPos pos, BlockState state) {
        // Redstone components at chunk borders are suspicious
        int x = pos.getX() & 15;
        int z = pos.getZ() & 15;
        return (x <= 2 || x >= 13 || z <= 2 || z >= 13);
    }

    private Color getColorForBlock(Block block) {
        // Different colors for different block types
        if (block == Blocks.DEEPSLATE || 
            block == Blocks.COBBLED_DEEPSLATE ||
            block == Blocks.POLISHED_DEEPSLATE ||
            block == Blocks.DEEPSLATE_BRICKS ||
            block == Blocks.DEEPSLATE_TILES) {
            return new Color(100, 100, 100, this.alpha.getIntValue()); // Gray for deepslate
        } else if (block instanceof ChestBlock) {
            return new Color(139, 69, 19, this.alpha.getIntValue()); // Brown for chests
        } else if (block instanceof EnderChestBlock) {
            return new Color(128, 0, 128, this.alpha.getIntValue()); // Purple for ender chests
        } else if (block instanceof SpawnerBlock) {
            return new Color(255, 0, 0, this.alpha.getIntValue()); // Red for spawners
        } else if (block instanceof BarrelBlock) {
            return new Color(160, 82, 45, this.alpha.getIntValue()); // Saddle brown for barrels
        } else if (block instanceof PistonBlock || block instanceof PistonHeadBlock) {
            return new Color(128, 128, 128, this.alpha.getIntValue()); // Gray for pistons
        } else if (isRedstoneComponent(block)) {
            return new Color(255, 0, 0, this.alpha.getIntValue()); // Red for redstone
        }
        
        return new Color(255, 255, 255, this.alpha.getIntValue()); // Default white
    }

    private void renderBlock(BlockPos pos, MatrixStack matrixStack, Color color) {
        RenderUtils.renderFilledBox(
            matrixStack,
            (float) pos.getX(),
            (float) pos.getY(), 
            (float) pos.getZ(),
            (float) (pos.getX() + 1),
            (float) (pos.getY() + 1),
            (float) (pos.getZ() + 1),
            color
        );
    }

    private void renderTracer(BlockPos pos, MatrixStack matrixStack, Color color) {
        if (this.mc.player == null) return;
        
        Vec3d playerPos = this.mc.player.getPos().add(0, this.mc.player.getEyeHeight(this.mc.player.getPose()), 0);
        Vec3d blockCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        
        RenderUtils.renderLine(matrixStack, color, playerPos, blockCenter);
    }
}