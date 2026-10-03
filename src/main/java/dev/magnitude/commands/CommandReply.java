package dev.magnitude.commands;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class CommandReply {
    private CommandReply(){}
    public static int message(CommandContext<CommandSourceStack> context,String key){
        context.getSource().sendSuccess(()->Component.translatable("message.magnitude."+key),false);return 1;
    }
    public static int denied(CommandContext<CommandSourceStack> context){
        context.getSource().sendFailure(Component.translatable("message.magnitude.denied"));return 0;
    }
}
