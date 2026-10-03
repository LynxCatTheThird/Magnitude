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
        String[] roots={"/magnitude config show","/magnitude scale","/magnitude action","/magnitude config server"};
        for(int i=0;i<CommandReference.GROUPS.size();i++){
            if(i==3&&!dev.magnitude.config.ConfigService.administrator(context.getSource()))continue;
            var line=CommandReference.suggestion("gui.magnitude.commandGroup."+CommandReference.GROUPS.get(i),roots[i]);
            context.getSource().sendSuccess(()->line,false);
        }
        return 1;
    }
    public static int denied(CommandContext<CommandSourceStack> context){
        context.getSource().sendFailure(Component.translatable("message.magnitude.denied"));return 0;
    }
}
