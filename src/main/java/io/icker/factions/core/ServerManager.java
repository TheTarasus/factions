package io.icker.factions.core;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.MiscEvents;
import io.icker.factions.api.persistents.*;
import io.icker.factions.util.DynmapWrapper;
import io.icker.factions.util.Message;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import org.apache.commons.lang3.time.DateUtils;

import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ServerManager {
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register(ServerManager::playerJoin);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerManager::save);
        ServerLifecycleEvents.SERVER_STARTED.register(ServerManager::tax);
    }

    private static void tax(MinecraftServer server) {
        Date date = new Date();
        date.setHours(FactionsMod.CONFIG.TAXES_HOURS);
        date.setMinutes(FactionsMod.CONFIG.TAXES_MINUTES);

        Date newDate = new Date().after(date) ? DateUtils.addDays(date, 1) : date;
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Date date = new Date();
                server.getPlayerManager().broadcast(new LiteralText("§9Сейчас: " + date.toString() + ";"), MessageType.CHAT, Util.NIL_UUID);
                server.getPlayerManager().broadcast(new LiteralText("§9Время собирать налоги!"), MessageType.CHAT, Util.NIL_UUID);
                Faction.all().forEach(faction -> faction.adjustPower(-(1 + faction.getClaims().size()* FactionsMod.CONFIG.DAILY_TAX_PER_CHUNK)));
            }
        }, newDate, 24L*3600L*1000L);
        Timer warTimer = new Timer(true);
        warTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Date date = new Date();
                Date startDate = new Date();
                startDate.setHours(FactionsMod.CONFIG.OFFWAR_HOURS_START);
                Date endDate = new Date();
                endDate.setHours(FactionsMod.CONFIG.OFFWAR_HOURS_END);
                if(startDate.before(date) && endDate.after(date)) return;
                Faction.all().forEach(f -> {
                    f.payWarTaxes();
                    if(f.jail == null) return;
                    f.jail.updateWarPunishment();
                });
                new Message("Сейчас:"+date.toString()+"; Пришло время собирать военные сборы.").sendToGlobalChat();
            }
        }, 60000L, 60000L);

    }

    private static void save(MinecraftServer server) {
        Claim.save();
        Faction.save();
        User.save();
        Safe.save();
        Safe.saveBackup();
        Empire.save();
        WarGoal.save();
        EmperorLocalization.save();
    }

    private static void playerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        ServerPlayerEntity player = handler.getPlayer();
        User user = User.get(player.getName().getString());
        Prisoner prisoner = user.getPrisoner(server);
        if(prisoner != null){
            handler.player.setSpawnPoint(RegistryKey.of(Registry.WORLD_KEY, new Identifier(prisoner.world)), new BlockPos(prisoner.x, prisoner.y, prisoner.z), 0, true, false);
            new Message("You were sent in prison of " + prisoner.jailFaction + ". You will be released at " + new Date(prisoner.dateBeforeFree)).send(player, false);
            return;
        }

        if (user.isInFaction()) {
            Faction faction = user.getFaction();
            new Message("Welcome back " + player.getName().getString() + "!").send(player, false);
            new Message(faction.getMOTD()).prependFaction(faction).send(player, false);
        }
    }
}