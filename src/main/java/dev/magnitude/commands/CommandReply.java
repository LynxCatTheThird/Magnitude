package dev.magnitude.commands;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class CommandReply {
    private CommandReply(){}
    public static int message(CommandContext<CommandSourceStack> context,String key){
        context.getSource().sendSuccess(()->Component.translatable("message.magnitude."+key),false);return 1;
    }
    public static int help(CommandContext<CommandSourceStack> context){
        message(context,"help");
        for(String domain:CommandReference.DOMAINS){
            var line=CommandReference.suggestion("gui.magnitude.commandDomain."+domain,CommandReference.entry(domain));
            context.getSource().sendSuccess(()->line,false);
        }
        return 1;
    }
    public static int usage(CommandContext<CommandSourceStack> context,String domain){
        message(context,"usage."+domain.replace(' ','.'));
        boolean administrator=dev.magnitude.config.ConfigService.administrator(context.getSource());
        for(var example:CommandReference.examplesForDomain(domain,administrator)){
            var line=CommandReference.suggestion("gui.magnitude.command."+example[0],example[1]).copy()
                .append(Component.literal("  "+example[1]).withStyle(net.minecraft.ChatFormatting.GRAY));
            context.getSource().sendSuccess(()->line,false);
        }
        return 1;
    }
    public static int denied(CommandContext<CommandSourceStack> context){
        context.getSource().sendFailure(Component.translatable("message.magnitude.denied"));return 0;
    }
}
