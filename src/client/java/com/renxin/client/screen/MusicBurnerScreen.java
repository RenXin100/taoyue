package com.renxin.client.screen;

import com.renxin.cpmod.CpModConstants;
import com.renxin.cpmod.network.CpModNetworking;
import com.renxin.network.UploadChunkC2SPacket;
import com.renxin.screen.MusicBurnerScreenHandler;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;

public class MusicBurnerScreen extends HandledScreen<MusicBurnerScreenHandler> {

    private static final Identifier FURNACE_TEXTURE =
            new Identifier("minecraft", "textures/gui/container/furnace.png");
    private ButtonWidget burnButton;

    private Text overlayText;
    private int overlayTimer;
    private static final int OVERLAY_DURATION = 60;

    public MusicBurnerScreen(MusicBurnerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        int buttonWidth = 52;
        int buttonHeight = 20;
        int buttonX = x + (this.backgroundWidth - buttonWidth) / 2;
        int buttonY = y + 60;

        this.burnButton = ButtonWidget.builder(
                        Text.translatable("cp-mod.screen.burn"),
                        button -> onBurnButtonPressed()
                )
                .dimensions(buttonX, buttonY, buttonWidth, buttonHeight)
                .build();

        this.addDrawableChild(this.burnButton);
    }

    private void showOverlay(Text text) {
        this.overlayText = text;
        this.overlayTimer = OVERLAY_DURATION;
    }

    private void onBurnButtonPressed() {
        if (!this.handler.hasBlankDisc()) {
            showOverlay(Text.translatable("cp-mod.upload.no_blank_disc"));
            return;
        }

        File chosen = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] patterns = new String[]{"*.mp3"};
            PointerBuffer filterPatterns = stack.mallocPointer(patterns.length);
            for (String p : patterns) {
                filterPatterns.put(stack.UTF8(p));
            }
            filterPatterns.flip();

            String path = TinyFileDialogs.tinyfd_openFileDialog(
                    "Select MP3 File",
                    null,
                    filterPatterns,
                    "MP3 Audio (*.mp3)",
                    false
            );
            if (path != null && !path.isEmpty()) {
                chosen = new File(path);
            }
        } catch (Throwable t) {
            CpModConstants.LOGGER.error("Failed to open tinyfd file dialog", t);
        }

        if (chosen == null && !GraphicsEnvironment.isHeadless()) {
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select MP3 File");
                chooser.setFileFilter(new FileNameExtensionFilter("MP3 Audio (*.mp3)", "mp3"));
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    chosen = chooser.getSelectedFile();
                }
            } catch (Throwable t) {
                showOverlay(Text.translatable("cp-mod.upload.dialog_failed"));
                return;
            }
        }

        if (chosen == null) {
            return;
        }

        startUpload(chosen);
    }

    private void startUpload(File file) {
        if (!this.handler.hasBlankDisc()) {
            showOverlay(Text.translatable("cp-mod.upload.no_blank_disc"));
            return;
        }

        if (file == null || !file.isFile()) {
            showOverlay(Text.translatable("cp-mod.upload.read_failed"));
            return;
        }

        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".mp3")) {
            showOverlay(Text.literal("Invalid extension!"));
            return;
        }

        try {
            new MpegAudioFileReader().getAudioFileFormat(file);
        } catch (Exception e) {
            CpModConstants.LOGGER.warn("Invalid MP3 file detected: " + file.getName());
            showOverlay(Text.literal("Invalid MP3 format!"));
            return;
        }

        // === 修改点：限制改为 10MB ===
        long maxSize = 10L * 1024L * 1024L;
        if (file.length() > maxSize) {
            showOverlay(Text.translatable("cp-mod.upload.too_large"));
            return;
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            showOverlay(Text.translatable("cp-mod.upload.read_failed"));
            return;
        }

        BlockPos pos = this.handler.getBlockPos();
        UUID uploadId = UUID.randomUUID();
        int totalSize = data.length;

        PacketByteBuf startBuf = PacketByteBufs.create();
        startBuf.writeBlockPos(pos);
        startBuf.writeUuid(uploadId);
        startBuf.writeString(file.getName());
        startBuf.writeInt(totalSize);
        ClientPlayNetworking.send(CpModNetworking.UPLOAD_START, startBuf);

        final int chunkSize = UploadChunkC2SPacket.MAX_CHUNK_SIZE;
        int index = 0;
        for (int offset = 0; offset < totalSize; offset += chunkSize) {
            int len = Math.min(chunkSize, totalSize - offset);
            PacketByteBuf chunkBuf = PacketByteBufs.create();
            chunkBuf.writeUuid(uploadId);
            chunkBuf.writeVarInt(index);
            chunkBuf.writeVarInt(len);
            chunkBuf.writeBytes(data, offset, len);
            ClientPlayNetworking.send(CpModNetworking.UPLOAD_CHUNK, chunkBuf);
            index++;
        }

        PacketByteBuf doneBuf = PacketByteBufs.create();
        doneBuf.writeBlockPos(pos);
        doneBuf.writeUuid(uploadId);
        ClientPlayNetworking.send(CpModNetworking.UPLOAD_COMPLETE, doneBuf);

        showOverlay(Text.translatable("cp-mod.upload.sending"));
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        context.drawTexture(FURNACE_TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);

        int topAreaTop = y + 16;
        int topAreaBottom = y + 72;
        int left = x + 7;
        int right = x + this.backgroundWidth - 7;
        context.fill(left, topAreaTop, right, topAreaBottom, 0xFFC6C6C6);

        int slotSize = 18;
        int slotBg = 0xFFE0E0E0;
        int slotEdge = 0xFF555555;

        drawSlot(context, x + MusicBurnerScreenHandler.INPUT_SLOT_X - 1, y + MusicBurnerScreenHandler.INPUT_SLOT_Y - 1, slotSize, slotBg, slotEdge);
        drawSlot(context, x + MusicBurnerScreenHandler.OUTPUT_SLOT_X - 1, y + MusicBurnerScreenHandler.OUTPUT_SLOT_Y - 1, slotSize, slotBg, slotEdge);

        String arrow = ">>";
        int arrowWidth = this.textRenderer.getWidth(arrow);
        int arrowX = x + (this.backgroundWidth - arrowWidth) / 2;
        int arrowY = y + 32;
        context.drawText(this.textRenderer, arrow, arrowX, arrowY, 0xFF3F3F3F, false);
    }

    private void drawSlot(DrawContext context, int x, int y, int size, int bg, int edge) {
        context.fill(x, y, x + size, y + size, bg);
        context.fill(x, y, x + size, y + 1, edge);
        context.fill(x, y + size - 1, x + size, y + size, edge);
        context.fill(x, y, x + 1, y + size, edge);
        context.fill(x + size - 1, y, x + size, y + size, edge);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);

        if (this.overlayText != null && this.overlayTimer > 0) {
            float alpha = this.overlayTimer / (float) OVERLAY_DURATION;
            int a = (int) (alpha * 255.0f) & 0xFF;
            int color = (a << 24) | 0x00FFFFFF;
            int x = this.width / 2 - this.textRenderer.getWidth(this.overlayText) / 2;
            int y = this.height - 40;
            context.drawTextWithShadow(this.textRenderer, this.overlayText, x, y, color);
            this.overlayTimer--;
        }
    }
}