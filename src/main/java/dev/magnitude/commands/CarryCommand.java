package dev.magnitude.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.magnitude.core.EntityState;
import dev.magnitude.network.Messages;
import net.minecraft.commands.CommandSourceStack;
import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;

public final class CarryCommand {
    private CarryCommand(){}
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root){
        var carry=literal("carry").executes(c->CommandReply.message(c,"usage.carry"));var position=literal("position");
        String[] modes={"shoulder","hand","custom"};
        for(int i=0;i<modes.length;i++){final int index=i;position.then(literal(modes[i]).executes(c->{
            var player=c.getSource().getPlayerOrException();EntityState.of(player).carryPosition=index;Messages.syncCarry(player);return CommandReply.message(c,"carry_mode");
        }));}
        carry.then(position);
        carry.then(literal("offset").then(argument("forward",doubleArg(-2,2)).then(argument("side",doubleArg(-2,2)).then(argument("up",doubleArg(0,2)).executes(c->{
            double forward=getDouble(c,"forward"),side=getDouble(c,"side"),up=getDouble(c,"up");
            if(!Double.isFinite(forward)||!Double.isFinite(side)||!Double.isFinite(up))return CommandReply.denied(c);
            var player=c.getSource().getPlayerOrException();var state=EntityState.of(player);
            state.offsetForward=forward;state.offsetSide=side;state.offsetUp=up;state.carryPosition=2;Messages.syncCarry(player);return CommandReply.message(c,"carry_mode");
        })))));
        root.then(carry);
    }
}
