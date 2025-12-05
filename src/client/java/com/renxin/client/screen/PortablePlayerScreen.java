package com.renxin.client.screen;

import com.renxin.cpmod.network.CpModNetworking;
import com.renxin.screen.PortablePlayerScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip; // 导入 Tooltip
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PortablePlayerScreen extends HandledScreen<PortablePlayerScreenHandler> {

    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/gui/container/dispenser.png");

    public PortablePlayerScreen(PortablePlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleY = 5;
        this.playerInventoryTitleY = 72;

        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        // 1. 上一首
        this.addDrawableChild(ButtonWidget.builder(Text.literal("⏮"), button -> sendControlPacket(0))
                .dimensions(x + 25, y + 35, 20, 20)
                .tooltip(Tooltip.of(Text.literal("上一首"))) // 添加悬浮提示
                .build());

        // 2. 播放/暂停
        this.addDrawableChild(ButtonWidget.builder(Text.literal("⏯"), button -> sendControlPacket(1))
                .dimensions(x + 130, y + 35, 20, 20)
                .tooltip(Tooltip.of(Text.literal("播放/暂停"))) // 添加悬浮提示
                .build());

        // 3. 下一首
        this.addDrawableChild(ButtonWidget.builder(Text.literal("⏭"), button -> sendControlPacket(2))
                .dimensions(x + 152, y + 35, 20, 20)
                .tooltip(Tooltip.of(Text.literal("下一首"))) // 添加悬浮提示
                .build());

        // 4. 模式切换
        this.addDrawableChild(ButtonWidget.builder(Text.literal("🔂"), button -> sendControlPacket(3))
                .dimensions(x + 4, y + 35, 20, 20)
                .tooltip(Tooltip.of(Text.literal("切换播放模式"))) // 添加悬浮提示
                .build());
    }

    private void sendControlPacket(int type) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(type);
        ClientPlayNetworking.send(CpModNetworking.PORTABLE_CONTROL, buf);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);

        // 绘制进度条
        int barX = x + 61;
        int barY = y + 72;
        int barWidth = 54;
        int barHeight = 4;

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);

        float progress = handler.getProgress();
        int progressWidth = (int) (barWidth * progress);
        if (progressWidth > 0) {
            context.fill(barX, barY, barX + progressWidth, barY + barHeight, 0xFF80FF20);
        }

        // 绘制当前模式文字
        int mode = handler.getPlayMode();
        String modeText = switch (mode) {
            case 0 -> "顺序";
            case 1 -> "单曲";
            case 2 -> "随机";
            default -> "列表";
        };
        context.drawText(textRenderer, Text.literal(modeText), x + 5, y + 25, 0x404040, false);
    }
}