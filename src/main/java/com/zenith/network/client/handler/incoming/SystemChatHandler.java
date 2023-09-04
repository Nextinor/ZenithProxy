package com.zenith.network.client.handler.incoming;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import com.zenith.event.proxy.DeathMessageEvent;
import com.zenith.event.proxy.SelfDeathMessageEvent;
import com.zenith.event.proxy.ServerChatReceivedEvent;
import com.zenith.feature.deathmessages.DeathMessageParseResult;
import com.zenith.feature.deathmessages.DeathMessagesParser;
import com.zenith.network.client.ClientSession;
import com.zenith.network.registry.AsyncIncomingHandler;
import com.zenith.util.ComponentSerializer;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;
import java.util.Optional;

import static com.zenith.Shared.*;
import static java.util.Objects.nonNull;

public class SystemChatHandler implements AsyncIncomingHandler<ClientboundSystemChatPacket, ClientSession> {
    private final DeathMessagesParser deathMessagesHelper = new DeathMessagesParser();

    @Override
    public boolean applyAsync(@NonNull ClientboundSystemChatPacket packet, @NonNull ClientSession session) {
        try {
            CHAT_LOG.info(ComponentSerializer.serialize(packet.getContent()));
            final Component component = packet.getContent();
            final String messageString = ComponentSerializer.toRawString(component);
            if (!messageString.startsWith("<")) { // normal chat msg
                // death message color on 2b
                if (component.children().stream().anyMatch(child -> nonNull(child.color())
                    && Objects.equals(child.color(), TextColor.color(170, 0, 0)))) {
                    final Optional<DeathMessageParseResult> deathMessageParseResult = deathMessagesHelper.parse(messageString);
                    if (deathMessageParseResult.isPresent()) {
                        EVENT_BUS.postAsync(new DeathMessageEvent(deathMessageParseResult.get(), messageString));
                        if (deathMessageParseResult.get().getVictim().equals(CACHE.getProfileCache().getProfile().getName())) {
                            EVENT_BUS.postAsync(new SelfDeathMessageEvent(messageString));
                        }
                    } else {
                        CLIENT_LOG.warn("Failed to parse death message: {}", messageString);
                    }
                }
            }

            boolean isWhisper = false;
            String playerName = null;
            if (messageString.startsWith("<")) {
                playerName = extractSenderNameNormalChat(messageString);
            } else {
                final String[] split = messageString.split(" ");
                if (split.length > 2 && split[1].startsWith("whispers")) {
                    isWhisper = true;
                    playerName = extractSenderNameWhisper(split);
                }
            }
            if (playerName == null) {
                EVENT_BUS.postAsync(new ServerChatReceivedEvent(Optional.empty(), messageString, isWhisper));
            } else {
                EVENT_BUS.postAsync(new ServerChatReceivedEvent(CACHE.getTabListCache().getTabList().getFromName(playerName), messageString, isWhisper));
            }
        } catch (final Exception e) {
            CLIENT_LOG.error("Caught exception in ChatHandler. Packet: " + packet, e);
        }
        return true;
    }

    @Override
    public Class<ClientboundSystemChatPacket> getPacketClass() {
        return ClientboundSystemChatPacket.class;
    }

    private String extractSenderNameNormalChat(final String message) {
        return message.substring(message.indexOf("<") + 1, message.indexOf(">"));
    }

    private String extractSenderNameWhisper(final String[] messageSplit) {
        return messageSplit[0].trim();
    }
}
