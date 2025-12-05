package com.renxin.block;

import com.renxin.block.entity.MusicBurnerBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class MusicBurnerBlock extends Block implements BlockEntityProvider {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public MusicBurnerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MusicBurnerBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof MusicBurnerBlockEntity burner) {
            player.openHandledScreen(burner);
        }
        return ActionResult.CONSUME;
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            dropContent(world, pos);
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            dropContent(world, pos);
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    private void dropContent(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof MusicBurnerBlockEntity burner) {
            boolean droppedAny = false;
            for (int i = 0; i < burner.size(); i++) {
                ItemStack stack = burner.getStack(i);
                if (!stack.isEmpty()) {
                    ItemEntity itemEntity = new ItemEntity(
                            world,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            stack.copy()
                    );
                    itemEntity.setToDefaultPickupDelay();
                    world.spawnEntity(itemEntity);

                    // 立即清空，防止被另一个方法重复掉落
                    burner.setStack(i, ItemStack.EMPTY);
                    droppedAny = true;
                }
            }
            if (droppedAny) {
                broadcastMsg(world, pos, "§b[调试] 刻录机物品已掉落！");
            }
            world.updateComparators(pos, this);
        }
    }

    private void broadcastMsg(World world, BlockPos pos, String msg) {
        for (PlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) < 256) {
                p.sendMessage(Text.literal(msg), false);
            }
        }
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }
    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }
    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }
}