package dev.magnitude;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import dev.magnitude.commands.*;
import static net.minecraft.commands.Commands.literal;

/** One root and one canonical command path for each domain. */
public final class Commands {
    private Commands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=literal("magnitude").executes(CommandReply::help);
        ScaleCommand.attach(root);
        ConfigCommand.attach(root);
        ActionCommand.attach(root);
        CarryCommand.attach(root);
        ToolCommand.attach(root);
        RandomCommand.attach(root);
        DiagnosticCommand.attach(root);
        dispatcher.register(root);
    }
}
