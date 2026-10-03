package dev.magnitude.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.magnitude.content.Content;
import dev.magnitude.content.ToolItem;
import dev.magnitude.core.ScaleSafety;
import net.minecraft.commands.CommandSourceStack;
import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;

public final class ToolCommand {
    private ToolCommand(){}
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root){
        var tool=literal("tool");var set=literal("set");
        set.then(literal("value").then(argument("value",doubleArg(-ScaleSafety.MAXIMUM,ScaleSafety.MAXIMUM)).executes(c->configure(c,"value",getDouble(c,"value")))));
        var mode=literal("mode");String[] modes={"multiply","add","set","swap","transfer"};
        for(int i=0;i<modes.length;i++){final int index=i;mode.then(literal(modes[i]).executes(c->configure(c,"operation",index)));}
        set.then(mode);
        set.then(literal("duration").then(argument("ticks",integer(0,1200)).executes(c->configure(c,"duration",getInteger(c,"ticks")))));
        tool.then(set);
        tool.then(literal("unbind").executes(c->{
            var stack=c.getSource().getPlayerOrException().getMainHandItem();if(!stack.is(Content.TUNER))return CommandReply.denied(c);
            var data=ToolItem.data(stack);data.remove("binding");ToolItem.data(stack,data);return CommandReply.message(c,"tool");
        }));root.then(tool);
    }
    private static int configure(CommandContext<CommandSourceStack> c,String field,double value)throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var stack=c.getSource().getPlayerOrException().getMainHandItem();if(!stack.is(Content.TUNER)||!Double.isFinite(value))return CommandReply.denied(c);
        var data=ToolItem.data(stack);if(field.equals("value"))data.putDouble(field,value);else data.putInt(field,(int)value);ToolItem.data(stack,data);return CommandReply.message(c,"tool");
    }
}
