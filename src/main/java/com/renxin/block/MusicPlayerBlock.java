package com.renxin.block;

import com.renxin.block.entity.MusicPlayerBlockEntity;
import com.renxin.cpmod.CpModConstants;
import com.renxin.item.CustomMusicDiscItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
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

// 确保使用 implements BlockEntityProvider，不要 extend BlockWithEntity
public class MusicPlayerBlock extends Block implements BlockEntityProvider {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public MusicPlayerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    // === 1. 强力调试：右键交互 ===
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        // 服务端逻辑
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof MusicPlayerBlockEntity playerBe) {
                // 打印当前库存，确认唱片是否存在
                ItemStack currentDisc = playerBe.getStack(0);
                String status = currentDisc.isEmpty() ? "§c[空]" : "§a[有唱片: " + currentDisc.getItem().getName().getString() + "]";
                player.sendMessage(Text.literal("§e[调试] 播放机状态: " + status), true);
            } else {
                player.sendMessage(Text.literal("§4[调试] 严重错误：BlockEntity 丢失！"), false);
            }
        }

        if (world.isClient) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof MusicPlayerBlockEntity playerBe)) return ActionResult.PASS;

        // 防抖
        long now = world.getTime();
        if (now - playerBe.lastInteractTime < 5) return ActionResult.CONSUME;
        playerBe.lastInteractTime = now;

        ItemStack held = player.getStackInHand(hand);
        ItemStack stored = playerBe.getStack(0);

        // 取出逻辑
        if (!stored.isEmpty()) {
            ItemStack toGive = stored.copy();
            playerBe.setStack(0, ItemStack.EMPTY);
            if (!player.getInventory().insertStack(toGive)) {
                player.dropItem(toGive, false);
            }
            player.getItemCooldownManager().set(toGive.getItem(), 10);
            return ActionResult.CONSUME;
        }

        // 放入逻辑
        if (!held.isEmpty() && held.getItem() instanceof CustomMusicDiscItem) {
            ItemStack one = held.split(1);
            playerBe.setStack(0, one);
            if (world instanceof ServerWorld serverWorld) {
                playerBe.startPlayback(serverWorld);
            }
            player.getItemCooldownManager().set(held.getItem(), 10);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    // === 2. 强力调试：玩家挖掘 (onBreak) ===
    // 这个方法比 onStateReplaced 更容易捕获玩家的操作
    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            player.sendMessage(Text.literal("§b[调试] 检测到破坏事件 (onBreak)"), false);
            // 尝试在这里执行掉落
            dropContent(world, pos, player);
        }
        super.onBreak(world, pos, state, player);
    }

    // === 3. 保底逻辑：状态改变 (onStateReplaced) ===
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (!world.isClient) {
                // 这里只能打印到控制台，或者广播给附近玩家
                dropContent(world, pos, null);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    // === 统一掉落逻辑 ===
    private void dropContent(World world, BlockPos pos, @Nullable PlayerEntity debugPlayer) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof MusicPlayerBlockEntity playerBe) {

            // 1. 停止播放
            if (world instanceof ServerWorld sw) {
                playerBe.forceStop(sw);
            }

            // 2. 掉落物品
            ItemStack stack = playerBe.getStack(0);
            if (!stack.isEmpty()) {
                // 复制一份
                ItemStack dropStack = stack.copy();
                // 清空库存，防止重复掉落
                playerBe.setStack(0, ItemStack.EMPTY);

                // 生成掉落物
                ItemEntity itemEntity = new ItemEntity(
                        world,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        dropStack
                );
                itemEntity.setToDefaultPickupDelay();
                world.spawnEntity(itemEntity);

                String msg = "§a[调试] 成功掉落唱片: " + dropStack.getItem().getName().getString();
                if (debugPlayer != null) {
                    debugPlayer.sendMessage(Text.literal(msg), false);
                } else {
                    // 广播给附近
                    for (PlayerEntity p : world.getPlayers()) {
                        if (p.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) < 256) {
                            p.sendMessage(Text.literal(msg), false);
                        }
                    }
                }
            } else {
                if (debugPlayer != null) {
                    debugPlayer.sendMessage(Text.literal("§c[调试] 破坏时库存为空，无掉落"), false);
                }
            }

            world.updateComparators(pos, this);
        }
    }

    // ... 其他标准方法 ...
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MusicPlayerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        // 这里的 EntityType 引用必须正确
        return world.isClient ? (world1, pos, state1, blockEntity) -> MusicPlayerBlockEntity.clientTick(world1, pos, state1, (MusicPlayerBlockEntity) blockEntity) : null;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) return;
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof MusicPlayerBlockEntity playerBe && world instanceof ServerWorld serverWorld) {
            boolean powered = world.isReceivingRedstonePower(pos);
            playerBe.onRedstoneUpdate(serverWorld, powered);
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