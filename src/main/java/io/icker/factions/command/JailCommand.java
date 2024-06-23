package io.icker.factions.command;


import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.*;
import io.icker.factions.config.Config;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.damage.DamageTracker;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Date;

public class JailCommand implements Command {

    private int set(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        Faction faction = User.get(player.getName().getString()).getFaction();

        if (checkLimitToClaim(faction, player.getWorld(), player.getBlockPos())) {
            new Message("Cannot set jail to an unclaimed chunk").fail().send(player, false);
            return 0;
        }

        if(faction.getPower() <= FactionsMod.CONFIG.JAIL_CREATION_PAYMENT){
            new Message("Not enough power to create a jail, you need " + FactionsMod.CONFIG.JAIL_CREATION_PAYMENT + " power, but only have " + faction.getPower()).fail().send(player, false);
            return 0;
        }

        Jail jail = new Jail(
            faction.getID(),
            player.getX(), player.getY(), player.getZ(),
            player.getHeadYaw(), player.getPitch(),
            player.getWorld().getRegistryKey().getValue().toString(),
                new ArrayList<>()
        );

        faction.jail = jail;
        faction.adjustPower(-FactionsMod.CONFIG.JAIL_CREATION_PAYMENT);
        new Message("Jail set to %.2f, %.2f, %.2f by %s", jail.x, jail.y, jail.z, player.getName().getString()).send(faction);
        return 1;
    }

    private static boolean checkLimitToClaim(Faction faction, ServerWorld world, BlockPos pos) {
        ChunkPos chunkPos = world.getChunk(pos).getPos();
        String dimension = world.getRegistryKey().getValue().toString();

        Claim possibleClaim = Claim.get(chunkPos.x, chunkPos.z, dimension);
        return possibleClaim == null || possibleClaim.getFaction().getID() != faction.getID();
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
            .literal("jail")
            .then(
                CommandManager.literal("set")
                .requires(Requires.multiple(Requires.hasPerms("factions.jail.set", 0), Requires.isSheriff()))
                .executes(this::set)
            ).then(
                    CommandManager.literal("send").requires(Requires.multiple(Requires.hasPerms("factions.jail.send", 0), Requires.isSheriff()))
                            .then(CommandManager.argument("member", EntityArgumentType.player()).then(CommandManager.argument("hours", IntegerArgumentType.integer()).executes(this::send))
                            )
                )
                .then(
                        CommandManager.literal("release").requires(Requires.multiple(Requires.hasPerms("factions.jail.release", 0), Requires.isSheriff()))
                                .then(CommandManager.argument("imprisoned", EntityArgumentType.player()).executes(this::release)))
                .build();
    }

    private int release(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        Faction faction = User.get(player.getName().getString()).getFaction();

        if(faction.jail == null) {

            new Message("§cYour faction does not have any jail!").fail().send(player, false);
            return 0;
        }
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "imprisoned");
        User targetUser = User.get(target.getName().getString());
        boolean isImprisoned = targetUser.getPrisoner(source.getServer()) != null;
        if(!isImprisoned){
            new Message("§c" + target.getName().getString() + " is not in the jail!").fail().send(player, false);
            return 0;
        }
        Faction jailFaction = Faction.getByName(targetUser.getPrisoner(source.getServer()).jailFaction);
        boolean isInThatPrison = jailFaction.getName().equals(faction.getName());
        if(!isInThatPrison){
            new Message("§c" + target.getName().getString() + " is not in YOUR jail!").fail().send(player, false);
            return 0;
        }
        new Message("§e[JUDGEMENT]§a " + target.getName().getString() + "§e was released from prison by §9" + player.getName().getString() + "§e!").sendToGlobalChat();
        jailFaction.jail.prisoners.removeIf(p -> p.prisoner.equals(targetUser.getName()));
        targetUser.resetImprisoned(source.getServer().getOverworld());
        return 1;
    }

    private int send(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        Faction faction = User.get(player.getName().getString()).getFaction();

        if(faction.jail == null) {

            new Message("§cYour faction does not have any jail!").fail().send(player, false);
            return 0;
        }
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "member");
        int hours = IntegerArgumentType.getInteger(context, "hours");
        User targetUser = User.get(target.getName().getString());
        if(!targetUser.isInFaction()){
            new Message("§e" + targetUser.getName() + " §cis not in ANY faction (requires to be in your)!").fail().send(player, false);
            return 0;
        }
        Faction targetFaction = targetUser.getFaction();
        boolean willGo = faction.getName().equals(targetFaction.getName()) && targetUser.rank == User.Rank.MEMBER;
        if(!willGo) {
            new Message("§cThe target player is not in your faction or at least has the SHERIFF rank").fail().send(player, false);
            return 0;
        }
        if(hours > FactionsMod.CONFIG.MAX_HOURS_OF_DETENTION){

            new Message("§cMaximum time for detention is " + FactionsMod.CONFIG.MAX_HOURS_OF_DETENTION + " hours!").fail().send(player, false);
            return 0;
        }
        boolean isInPrison = targetUser.setImprisoned(source.getServer().getOverworld(), faction, hours);
        if(!isInPrison){
            new Message("§cYou can't just imprison this player. Maybe he is not online? Or maybe he is already imprisoned? Or maybe you just tried to imprison the privileged one?").fail().send(player, false);
            return 0;
        }
        new Message("§e[JUDGEMENT]§c " + target.getName().getString() + "§e was imprisoned by §9" + player.getName().getString() + "§e!").sendToFactionChat(faction);
        new Message("§eHe will break free legally at §b" + new Date(System.currentTimeMillis() + (hours*1000L*3600L))).sendToFactionChat(faction);
        return 1;
    }
}
