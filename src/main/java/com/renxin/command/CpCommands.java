package com.renxin.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.renxin.config.CpConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class CpCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("cp")
                // /cp distance <数值>
                .then(CommandManager.literal("distance")
                        .then(CommandManager.argument("range", IntegerArgumentType.integer(1, 1024))
                                .executes(CpCommands::setDistance)))
                // 也可以加一个 get 查看当前距离
                .then(CommandManager.literal("info")
                        .executes(CpCommands::getInfo))
        );
    }

    private static int setDistance(CommandContext<ServerCommandSource> context) {
        int range = IntegerArgumentType.getInteger(context, "range");

        // 修改配置
        CpConfig config = CpConfig.get();
        config.broadcastRange = range;
        CpConfig.save(); // 持久化保存

        context.getSource().sendFeedback(() -> Text.literal("§a[CP-Mod] 全局广播距离已设置为: " + range + " 格"), true);
        context.getSource().sendFeedback(() -> Text.literal("§e注意：所有播放中的设备需重新播放才能生效。"), false);

        return 1;
    }

    private static int getInfo(CommandContext<ServerCommandSource> context) {
        int range = CpConfig.get().broadcastRange;
        context.getSource().sendFeedback(() -> Text.literal("§b[CP-Mod] 当前全局广播距离: " + range), false);
        return 1;
    }
}