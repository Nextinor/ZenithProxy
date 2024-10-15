package com.zenith.command.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Shared.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static java.util.Arrays.asList;

public class ServerCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.args(
            "server",
            CommandCategory.MANAGE,
            "Change the MC server ZenithProxy connects to.",
            asList(
                "<IP>",
                "<IP> <port>"
            )
        );
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("server").requires(Command::validateAccountOwner)
            .then(argument("ip", wordWithChars())
                      .then(argument("port", integer(1, 65535)).executes(c -> {
                          final String ip = StringArgumentType.getString(c, "ip");
                          final int port = IntegerArgumentType.getInteger(c, "port");
                          CONFIG.client.server.address = ip;
                          CONFIG.client.server.port = port;
                          c.getSource().getEmbed()
                              .title("Server Updated!");
                          return OK;
                      }))
                      .executes(c -> {
                          final String ip = StringArgumentType.getString(c, "ip");
                          CONFIG.client.server.address = (ip.equalsIgnoreCase("2b2t") ? "palanarchy.org" : ip);
                          CONFIG.client.server.port = 25565;
                          c.getSource().getEmbed()
                              .title("Server Updated!");
                          return OK;
                      }));
    }

    @Override
    public void postPopulate(final Embed builder) {
        builder
            .addField("IP", CONFIG.client.server.address, false)
            .addField("Port", CONFIG.client.server.port, true)
            .primaryColor();
    }
}
