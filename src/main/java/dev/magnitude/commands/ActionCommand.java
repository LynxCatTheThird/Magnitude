package dev.magnitude.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.magnitude.interaction.Interactions;
import net.minecraft.commands.CommandSourceStack;
import static net.minecraft.commands.Commands.literal;

public final class ActionCommand {
    private ActionCommand(){}
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root){
        var actions=literal("action").executes(c->CommandReply.usage(c,"action"));
        String[] names={"blow","stomp","release","throw","ability","ride"};
        for(int i=0;i<names.length;i++){final int action=i;actions.then(literal(names[i]).executes(c->Interactions.action(c.getSource().getPlayerOrException(),action)?1:CommandReply.denied(c)));}
        actions.then(literal("pickup").executes(c->Interactions.pickup(c.getSource().getPlayerOrException())?1:CommandReply.denied(c)));
        root.then(actions);
    }
}
