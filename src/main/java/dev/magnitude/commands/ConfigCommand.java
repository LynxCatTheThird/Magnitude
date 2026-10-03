package dev.magnitude.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.magnitude.config.ConfigField;
import dev.magnitude.config.ConfigService;
import dev.magnitude.network.ConfigNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import java.util.Map;
import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static com.mojang.brigadier.arguments.BoolArgumentType.*;

/** Canonical configuration tree; no aliases with different scope semantics. */
public final class ConfigCommand {
    private ConfigCommand(){}
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root){
        var config=literal("config").executes(ConfigCommand::show);
        config.then(literal("show").executes(ConfigCommand::show));
        config.then(literal("reload").requires(ConfigService::administrator).executes(c->reply(c,ConfigService.INSTANCE.reload(c.getSource()))));
        var server=literal("server").requires(ConfigService::administrator).executes(ConfigCommand::serverValues);
        for(var field:ConfigField.values()) {
            var node=literal(field.id).executes(c->serverValue(c,field));
            if(field.bool)node.then(argument("value",bool()).executes(c->server(c,field.id,getBool(c,"value")?1:0)));
            else node.then(argument("value",doubleArg(field.minimum,field.maximum)).executes(c->server(c,field.id,getDouble(c,"value"))));
            server.then(node);
        }
        config.then(server);
        config.then(literal("food").requires(ConfigService::administrator).then(argument("item",net.minecraft.commands.arguments.IdentifierArgument.id())
            .then(argument("factor",doubleArg(.015625,4)).executes(c->reply(c,ConfigService.INSTANCE.food(c.getSource(),net.minecraft.commands.arguments.IdentifierArgument.getId(c,"item").toString(),getDouble(c,"factor")))))));

        var player=literal("player");
        for(String field:new String[]{"terrain","pressure","resize","carry"}) {
            var node=literal(field);
            for(boolean enabled:new boolean[]{true,false})node.then(literal(enabled?"on":"off").executes(c->personal(c,field,enabled)));
            player.then(node);
        }
        config.then(player);root.then(config);
        root.then(literal("menu").executes(c->{
            var playerEntity=c.getSource().getPlayerOrException();
            if(!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(playerEntity,dev.magnitude.network.ConfigSnapshot.TYPE)) {
                c.getSource().sendFailure(Component.translatable("config.magnitude.result.unsupported"));return 0;
            }
            ConfigNetworking.send(playerEntity,0,true,new ConfigService.Result("ready",""));return 1;
        }));
    }
    private static int serverValue(CommandContext<CommandSourceStack> c,ConfigField field){
        double value=field.read(dev.magnitude.Magnitude.settings);
        var label=Component.translatable("config.magnitude.field."+field.id);
        Component display=field.bool?Component.translatable(value==1?"options.on":"options.off"):Component.literal(Double.toString(value));
        c.getSource().sendSuccess(()->label.append(": ").append(display),false);return 1;
    }
    private static int serverValues(CommandContext<CommandSourceStack> c){for(var field:ConfigField.values())serverValue(c,field);return 1;}
    public static int server(CommandContext<CommandSourceStack> c,String field,double value){return reply(c,ConfigService.INSTANCE.server(c.getSource(),-1,Map.of(field,value)));}
    public static int personal(CommandContext<CommandSourceStack> c,String field,boolean value)throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return reply(c,ConfigService.INSTANCE.personal(c.getSource().getPlayerOrException(),-1,Map.of(field,value?1d:0d)));
    }
    public static int reply(CommandContext<CommandSourceStack> c,ConfigService.Result result){
        var message=Component.translatable("config.magnitude.result."+result.code());
        if(!result.detail().isEmpty())message.append(" ").append(Component.translatable("config.magnitude.field."+result.detail()));
        if(result.success())c.getSource().sendSuccess(()->message,false);else c.getSource().sendFailure(message);
        return result.success()?1:0;
    }
    public static int show(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!(c.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer))return ConfigService.administrator(c.getSource())?serverValues(c):0;
        var view=ConfigNetworking.view(c.getSource().getPlayerOrException(),new ConfigService.Result("ready",""));
        c.getSource().sendSuccess(()->Component.translatable("config.magnitude.summary",view.size(),Component.translatable("config.magnitude.reason."+view.terrainReason()),Component.translatable("config.magnitude.reason."+view.pressureReason())),false);
        c.getSource().sendSuccess(()->Component.translatable("config.magnitude.commandHint"),false);return 1;
    }
}
