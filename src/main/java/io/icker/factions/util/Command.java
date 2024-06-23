package io.icker.factions.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Empire;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.api.persistents.WarGoal;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Arrays;
import java.util.function.Predicate;


public interface Command {
    public LiteralCommandNode<ServerCommandSource> getNode();
    public static final boolean permissions = FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0");

    public interface Requires {
        boolean run(User user);

        @SafeVarargs
        public static Predicate<ServerCommandSource> multiple(Predicate<ServerCommandSource>... args) {
            return source -> {
                for (Predicate<ServerCommandSource> predicate : args) {
                    if (!predicate.test(source)) return false;
                }

                return true;
            };
        }

        public static Predicate<ServerCommandSource> isFactionless() {
            return require(user -> !user.isInFaction());
        }
        public static Predicate<ServerCommandSource> isEmpireless() {
            return require(user -> {
                Faction faction = user.getFaction();
                if(faction == null) return false;
                return Empire.getEmpireByFaction(faction.getID()) == null;
            });
        }

        public static Predicate<ServerCommandSource> isMember() {
            return require(user -> user.isInFaction());
        }

        public static Predicate<ServerCommandSource> isCommander() {
            return require(user -> user.rank == User.Rank.COMMANDER || user.rank == User.Rank.LEADER || user.rank == User.Rank.OWNER);
        }

        public static Predicate<ServerCommandSource> isLeader() {
            return require(user -> user.rank == User.Rank.LEADER || user.rank == User.Rank.OWNER);
        }

        public static Predicate<ServerCommandSource> isSheriff() {
            return require(user -> user.rank == User.Rank.SHERIFF || user.rank == User.Rank.COMMANDER || user.rank == User.Rank.LEADER || user.rank == User.Rank.OWNER);
        }

        public static Predicate<ServerCommandSource> isEmperor(){
            return require(user -> {
                if (user.getFaction() == null) return false;
                return (user.rank == User.Rank.OWNER || user.rank == User.Rank.LEADER) && Empire.isAnyMetropoly(user.getFaction().getID());
            });
        }

        public static Predicate<ServerCommandSource> isOwner() {
            return require(user -> user.rank == User.Rank.OWNER);
        }
        
        public static Predicate<ServerCommandSource> isAdmin() {
            return source -> source.hasPermissionLevel(FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL);
        }

        public static Predicate<ServerCommandSource> hasPerms(String permission, int defaultValue) {
            return source -> !permissions || Permissions.check(source, permission, defaultValue);
        }

        public static Predicate<ServerCommandSource> require(Requires req) {
            return source -> {
                ServerPlayerEntity entity = null;
                try {
                    entity = source.getPlayer();
                } catch (CommandSyntaxException e) {
                    FactionsMod.LOGGER.info("Error in command requirements", e);
                }
                User user = User.get(entity.getName().getString());
                return req.run(user);
            };
        }
    }

    public interface Suggests {
        String[] run(User user);

        public static SuggestionProvider<ServerCommandSource> allFactions() {
            return allFactions(true);
        }
        public static SuggestionProvider<ServerCommandSource> allFactions(boolean includeYou) {
            return suggest(user -> 
                Faction.all()
                    .stream()
                    .filter(f -> includeYou || !user.isInFaction() || !user.getFaction().getID().equals(f.getID()))
                    .map(f -> f.getName())
                    .toArray(String[]::new)
            );
        }

        public static SuggestionProvider<ServerCommandSource> allWithNegativeRelations(){
            return suggest(user ->
                user.getFaction().getEnemiesWith().stream().map(r -> Faction.get(r.target).getCapitalState().getName())
                        .toArray(String[]::new)
            );
        }

        public static SuggestionProvider<ServerCommandSource> allWargoals(){
            return suggest(user -> {
                Faction source = user.getFaction();
                return Arrays.stream(WarGoal.Type.values()).map(type -> type.name).toArray(String[]::new);
            }
            );
        }

        public static SuggestionProvider<ServerCommandSource> openFactions() {
            return suggest(user ->
                Faction.all()
                    .stream()
                    .filter(f -> f.isOpen())
                    .map(f -> f.getName())
                    .toArray(String[]::new)
            );
        }



        public static SuggestionProvider<ServerCommandSource> openInvitedFactions() {
            return suggest(user ->
                Faction.all()
                    .stream()
                    .filter(f -> f.isOpen() || f.isInvited(user.getName()))
                    .map(f -> f.getName())
                    .toArray(String[]::new)
            );
        }

        public static SuggestionProvider<ServerCommandSource> suggest(Suggests sug) {
            return (context, builder) -> {
                ServerPlayerEntity entity = context.getSource().getPlayer();
                User user = User.get(entity.getName().getString());
                for (String suggestion : sug.run(user)) {
                    builder.suggest(suggestion);
                }
                return builder.buildFuture();
            };
        }
    }
}