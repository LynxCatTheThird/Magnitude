package dev.magnitude.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import net.minecraft.commands.CommandSourceStack;
import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;

public final class RandomCommand {
    private RandomCommand(){}
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root){
        var random=literal("random");
        random.then(literal("stop").executes(c->{EntityState.of(c.getSource().getPlayerOrException()).randomPeriod=0;return CommandReply.message(c,"random");}));
        random.then(literal("start").then(argument("low",doubleArg(ScaleSafety.MINIMUM,ScaleSafety.MAXIMUM)).then(argument("high",doubleArg(ScaleSafety.MINIMUM,ScaleSafety.MAXIMUM)).then(argument("period",integer(20,72000)).executes(c->{
            var player=c.getSource().getPlayerOrException();if(!Magnitude.settings.allowSelfChange&&!Dimensions.operator(player))return CommandReply.denied(c);
            double low=getDouble(c,"low"),high=getDouble(c,"high");
            if(!Double.isFinite(low)||!Double.isFinite(high)||high<low||low<Magnitude.settings.minimum||high>Magnitude.settings.maximum)return CommandReply.denied(c);
            var state=EntityState.of(player);state.randomLow=low;state.randomHigh=high;state.randomPeriod=getInteger(c,"period");state.nextRandom=state.randomPeriod;return CommandReply.message(c,"random");
        })))));root.then(random);
    }
}
