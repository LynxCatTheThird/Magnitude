package dev.magnitude.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.magnitude.config.ConfigService;
import dev.magnitude.core.Dimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;

public final class ScaleCommand {
    private ScaleCommand(){}
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root){
        var scale=literal("scale").executes(c->CommandReply.usage(c,"scale"));
        scale.then(literal("get").executes(c->{
            var entity=c.getSource().getPlayerOrException();
            c.getSource().sendSuccess(()->Component.translatable("message.magnitude.size",Dimensions.size(entity),Dimensions.target(entity),entity.getBbHeight()),false);return 1;
        }));
        scale.then(literal("reset").executes(c->{Dimensions.reset(c.getSource().getPlayerOrException());return CommandReply.message(c,"reset");}));
        for(String operation:new String[]{"set","multiply","add","height"}) {
            scale.then(literal(operation).then(argument("value",doubleArg()).executes(c->resize(c,operation,20))
                .then(argument("ticks",integer(0,1200)).executes(c->resize(c,operation,getInteger(c,"ticks"))))));
        }
        var targets=literal("targets").requires(ConfigService::administrator);
        targets.then(literal("get").then(argument("target",EntityArgument.entity()).executes(c->{
            var entity=EntityArgument.getEntity(c,"target");
            c.getSource().sendSuccess(()->Component.translatable("message.magnitude.size",Dimensions.size(entity),Dimensions.target(entity),entity.getBbHeight()),false);return 1;
        })));
        targets.then(literal("set").then(argument("targets",EntityArgument.entities()).then(argument("value",doubleArg())
            .executes(c->setTargets(c,20)).then(argument("ticks",integer(0,1200)).executes(c->setTargets(c,getInteger(c,"ticks")))))));
        targets.then(literal("reset").then(argument("targets",EntityArgument.entities()).executes(c->{
            int count=0;for(var entity:EntityArgument.getEntities(c,"targets")){Dimensions.reset(entity);count++;}return count;
        })));
        scale.then(targets);root.then(scale);
    }
    private static int setTargets(CommandContext<CommandSourceStack> c,int ticks)throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        double value=getDouble(c,"value");if(!Double.isFinite(value))return CommandReply.denied(c);
        int count=0;for(var entity:EntityArgument.getEntities(c,"targets"))if(Dimensions.set(entity,value,ticks))count++;return count;
    }
    private static int resize(CommandContext<CommandSourceStack> c,String operation,int ticks)throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=c.getSource().getPlayerOrException();double value=getDouble(c,"value"),current=Dimensions.target(player);
        double result=switch(operation){case "multiply"->current*value;case "add"->current+value;case "height"->value/1.8;default->value;};
        return Dimensions.change(player,player,result,ticks)?CommandReply.message(c,"changed"):CommandReply.denied(c);
    }
}
