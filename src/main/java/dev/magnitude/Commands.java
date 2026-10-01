package dev.magnitude;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.LivingEntity;
import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import dev.magnitude.content.Content;
import dev.magnitude.content.ToolItem;
import dev.magnitude.interaction.Interactions;
import dev.magnitude.network.Messages;
import static net.minecraft.commands.Commands.*;

public final class Commands {
    private Commands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root=literal("magnitude").executes(c -> message(c,"help"));
        root.then(literal("get").executes(c -> {
            var p=c.getSource().getPlayerOrException();
            c.getSource().sendSuccess(() -> Component.translatable("message.magnitude.size",Dimensions.size(p),Dimensions.target(p),p.getBbHeight()),false);return 1;
        }));
        root.then(literal("reset").executes(c -> {Dimensions.reset(c.getSource().getPlayerOrException());return message(c,"reset");}));
        for(String operation:new String[]{"set","multiply","add","height"}) root.then(literal(operation).then(argument("value",DoubleArgumentType.doubleArg()).executes(c -> resize(c,operation,20)).then(argument("ticks",IntegerArgumentType.integer(0,1200)).executes(c -> resize(c,operation,IntegerArgumentType.getInteger(c,"ticks"))))));
        var consent=literal("consent");
        for(String permission:new String[]{"resize","carry"}) consent.then(literal(permission).then(argument("enabled",BoolArgumentType.bool()).executes(c -> {
            var state=EntityState.of(c.getSource().getPlayerOrException());boolean enabled=BoolArgumentType.getBool(c,"enabled");
            if(permission.equals("resize"))state.acceptResize=enabled;else state.acceptCarry=enabled;
            return message(c,"consent");
        })));
        root.then(consent);
        root.then(literal("terrain").then(argument("enabled",BoolArgumentType.bool()).executes(c -> {EntityState.of(c.getSource().getPlayerOrException()).terrainEnabled=BoolArgumentType.getBool(c,"enabled");return message(c,"terrain");})));
        root.then(literal("release").executes(c -> action(c,2)));
        root.then(literal("throw").executes(c -> action(c,3)));
        root.then(literal("blow").executes(c -> action(c,0)));
        root.then(literal("stomp").executes(c -> action(c,1)));
        root.then(literal("ability").executes(c -> action(c,4)));
        root.then(literal("ride").executes(c -> action(c,5)));
        root.then(literal("pickup").executes(c -> {
            var p=c.getSource().getPlayerOrException();var target=Interactions.aim(p,16);
            return target!=null&&Interactions.carry(p,target)?1:denied(c);
        }));
        var carry=literal("carry");
        for(int mode=0;mode<3;mode++) {final int position=mode;carry.then(literal(new String[]{"shoulder","hand","custom"}[mode]).executes(c -> {
            var p=c.getSource().getPlayerOrException();EntityState.of(p).carryPosition=position;Messages.syncCarry(p);return message(c,"carry_mode");
        }));}
        carry.then(literal("offset").then(argument("forward",DoubleArgumentType.doubleArg(-2,2)).then(argument("side",DoubleArgumentType.doubleArg(-2,2)).then(argument("up",DoubleArgumentType.doubleArg(0,2)).executes(c -> {
            var p=c.getSource().getPlayerOrException();var state=EntityState.of(p);
            state.offsetForward=DoubleArgumentType.getDouble(c,"forward");state.offsetSide=DoubleArgumentType.getDouble(c,"side");state.offsetUp=DoubleArgumentType.getDouble(c,"up");state.carryPosition=2;
            Messages.syncCarry(p);return message(c,"carry_mode");
        })))));
        root.then(carry);
        var tool=literal("tool");
        tool.then(literal("value").then(argument("value",DoubleArgumentType.doubleArg(-32,32)).executes(c -> configureTool(c,"value",DoubleArgumentType.getDouble(c,"value")))));
        tool.then(literal("mode").then(argument("mode",IntegerArgumentType.integer(0,4)).executes(c -> configureTool(c,"operation",IntegerArgumentType.getInteger(c,"mode")))));
        tool.then(literal("duration").then(argument("ticks",IntegerArgumentType.integer(0,1200)).executes(c -> configureTool(c,"duration",IntegerArgumentType.getInteger(c,"ticks")))));
        tool.then(literal("unbind").executes(c -> {var p=c.getSource().getPlayerOrException();if(!p.getMainHandItem().is(Content.TUNER))return denied(c);var data=ToolItem.data(p.getMainHandItem());data.remove("binding");ToolItem.data(p.getMainHandItem(),data);return message(c,"tool");}));
        root.then(tool);
        root.then(literal("random").then(literal("stop").executes(c -> {EntityState.of(c.getSource().getPlayerOrException()).randomPeriod=0;return message(c,"random");})).then(argument("low",DoubleArgumentType.doubleArg(1.0/64,32)).then(argument("high",DoubleArgumentType.doubleArg(1.0/64,32)).then(argument("period",IntegerArgumentType.integer(20,72000)).executes(c -> {
            var p=c.getSource().getPlayerOrException();if(!Magnitude.settings.allowSelfChange&&!Dimensions.operator(p))return denied(c);
            double low=DoubleArgumentType.getDouble(c,"low"),high=DoubleArgumentType.getDouble(c,"high");if(high<low)return denied(c);
            var state=EntityState.of(p);state.randomLow=low;state.randomHigh=high;state.randomPeriod=IntegerArgumentType.getInteger(c,"period");state.nextRandom=state.randomPeriod;return message(c,"random");
        })))));
        var admin=literal("admin").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        admin.then(literal("set").then(argument("targets",EntityArgument.entities()).then(argument("value",DoubleArgumentType.doubleArg()).executes(c -> {
            int count=0;for(var entity:EntityArgument.getEntities(c,"targets"))if(Dimensions.set(entity,DoubleArgumentType.getDouble(c,"value"),20))count++;return count;
        }))));
        admin.then(literal("get").then(argument("target",EntityArgument.entity()).executes(c -> {
            var entity=EntityArgument.getEntity(c,"target");c.getSource().sendSuccess(() -> Component.translatable("message.magnitude.size",Dimensions.size(entity),Dimensions.target(entity),entity.getBbHeight()),false);return 1;
        })));
        admin.then(literal("reset").then(argument("targets",EntityArgument.entities()).executes(c -> {int count=0;for(var entity:EntityArgument.getEntities(c,"targets")){Dimensions.reset(entity);count++;}return count;})));
        admin.then(literal("reload").executes(c -> {try{Magnitude.settings=dev.magnitude.core.Settings.load();return message(c,"reloaded");}catch(RuntimeException error){c.getSource().sendFailure(Component.literal(error.getMessage()));return 0;}}));
        admin.then(literal("terrain").then(argument("enabled",BoolArgumentType.bool()).executes(c -> {Magnitude.settings.terrainDamage=BoolArgumentType.getBool(c,"enabled");Magnitude.settings.save();return message(c,"terrain");})));
        admin.then(literal("food").then(argument("item",StringArgumentType.word()).then(argument("factor",DoubleArgumentType.doubleArg(0.015625,4)).executes(c -> {
            var id=net.minecraft.resources.Identifier.tryParse(StringArgumentType.getString(c,"item"));if(id==null||!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))return denied(c);
            Magnitude.settings.foodFactors.put(id.toString(),DoubleArgumentType.getDouble(c,"factor"));Magnitude.settings.save();return message(c,"food");
        }))));
        root.then(admin);
        dispatcher.register(root);
    }
    private static int resize(CommandContext<CommandSourceStack> c,String operation,int ticks)throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=c.getSource().getPlayerOrException();double value=DoubleArgumentType.getDouble(c,"value");double current=Dimensions.target(player);
        double result=switch(operation){case "multiply"->current*value;case "add"->current+value;case "height"->value/1.8;default->value;};
        return Dimensions.change(player,player,result,ticks)?message(c,"changed"):denied(c);
    }
    private static int configureTool(CommandContext<CommandSourceStack> c,String field,double value)throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p=c.getSource().getPlayerOrException();var stack=p.getMainHandItem();if(!stack.is(Content.TUNER))return denied(c);
        var data=ToolItem.data(stack);if(field.equals("value"))data.putDouble(field,value);else data.putInt(field,(int)value);ToolItem.data(stack,data);return message(c,"tool");
    }
    private static int action(CommandContext<CommandSourceStack> c,int action)throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=c.getSource().getPlayerOrException();
        boolean result=switch(action){case 0->Interactions.blow(player);case 1->Interactions.shock(player,Math.min(6,Dimensions.size(player)*0.3),false);case 2,3->{if(!Interactions.cooldown(player,10))yield false;Interactions.release(player,action==3);yield true;}case 4->Interactions.ability(player);case 5->{var target=Interactions.aim(player,16);yield target!=null&&Interactions.ride(player,target);}default->false;};
        return result?1:denied(c);
    }
    private static int message(CommandContext<CommandSourceStack> c,String key){c.getSource().sendSuccess(() -> Component.translatable("message.magnitude."+key),false);return 1;}
    private static int denied(CommandContext<CommandSourceStack> c){c.getSource().sendFailure(Component.translatable("message.magnitude.denied"));return 0;}
}
