package io.icker.factions.util;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.*;
import net.minecraft.util.Formatting;

public class Migrator {
    public static Connection con;
    public static void migrate() {
        File file = new File("./factions/factions.mv.db");
        if (file.isFile()) {
            moveData();
            file.renameTo(new File("./factions/factions.mv.db.OLD"));
            FactionsMod.LOGGER.info("Migration complete");
        }
    }

    private static void moveData() {
        try {
            con = DriverManager.getConnection("jdbc:h2:./factions/factions");
            Query emperorLocalizationQuery = new Query("SELECT * FROM EmperorLocalization;").executeQuery();
            while (emperorLocalizationQuery.next()){
                EmperorLocalization localization = new EmperorLocalization(emperorLocalizationQuery.getUUID("ID"), emperorLocalizationQuery.getString("localeForEmperorMainTitle"), emperorLocalizationQuery.getString("localeForEmperorTitleHandlers"));
                EmperorLocalization.add(localization);
            }

            Query warGoalsQuery = new Query("SELECT * FROM WarGoal;").executeQuery();
            while (warGoalsQuery.next()){
                WarGoal warGoal = new WarGoal(warGoalsQuery.getWGStateWithType("Agressor"), warGoalsQuery.getWGStateWithType("Victim"), warGoalsQuery.getWGType("GoalType"), warGoalsQuery.getUUID("ID"), warGoalsQuery.getLong("timeOfWarEnd"), warGoalsQuery.getLong("timeOfWarStarted"));
                WarGoal.add(warGoal);
            }

            Query empireQuery = new Query("SELECT * FROM Empire;").executeQuery();
            while (empireQuery.next()) {
                Empire empire = new Empire(empireQuery.getUUID("ID"), empireQuery.getUUID("metropolyID"), empireQuery.getListUUID("metropolyID"), empireQuery.getString("Name"), empireQuery.getString("metropolyBannerLocation"), empireQuery.getString("vassalBannerLocation"), Formatting.byName(empireQuery.getString("color")));
                Empire.add(empire);
            }

            Query query = new Query("SELECT * FROM Faction;").executeQuery();
            while (query.next()) {
                Faction faction = new Faction(query.getString("name"), query.getString("description"), "No faction MOTD set", Formatting.byName(query.getString("color")), query.getBool("open"), query.getInt("power"), query.getBool("admin"), query.getLong("relationsLastUpdate"), query.getJail("Jail"), query.getString("empireBannerLocation"), query.getString("regionBannerLocation"), query.getInt("capitulationPoints"), query.getBool("isCapitulated"));
                Faction.add(faction);

                Query homeQuery = new Query("SELECT * FROM Home WHERE faction = ?;").set(faction.getName()).executeQuery();
                if (homeQuery.success) {
                    Home home = new Home(faction.getID(), homeQuery.getDouble("x"), homeQuery.getDouble("y"), homeQuery.getDouble("z"), homeQuery.getFloat("yaw"), homeQuery.getFloat("pitch"), homeQuery.getString("level"));
                    faction.setHome(home);
                }

                Query claimQuery = new Query("SELECT * FROM Claim WHERE faction = ?;").set(faction.getName()).executeQuery();
                while (claimQuery.next()) {
                    Claim claim = new Claim(claimQuery.getInt("x"), claimQuery.getInt("z"), claimQuery.getString("level"), faction.getID(), claimQuery.getBool("create"), claimQuery.getOutpost("outpost"));
                    Claim.add(claim);
                }

                Query inviteQuery = new Query("SELECT * FROM Invite WHERE faction = ?;").set(faction.getName()).executeQuery();
                while (inviteQuery.next()) {
                    faction.invites.add(inviteQuery.getString("player"));
                }
            }

            query = new Query("SELECT * FROM Member;").executeQuery();
            while (query.next()) {
                OldRank rank;
                try {
                    rank = Enum.valueOf(OldRank.class, query.getString("rank"));
                } catch (IllegalArgumentException e) {
                    rank = OldRank.CIVILIAN;
                }

                User user = new User(query.getString("Name"));
                user.joinFaction(Faction.getByName(query.getString("faction")).getID(), migrateRank(rank));
                User.add(user);
            }

            query = new Query("SELECT * FROM PlayerConfig;").executeQuery();
            while (query.next()) {
                User.ChatMode opt;
                try {
                    opt = Enum.valueOf(User.ChatMode.class, query.getString("chat"));
                } catch (IllegalArgumentException e) {
                    opt = User.ChatMode.GLOBAL;
                }

                User user = User.get(query.getString("Name"));
                user.chat = opt;
                user.radar = query.getBool("zone");
            }

            query = new Query("SELECT * FROM Allies;").executeQuery();
            while (query.next()) {
                Faction source = Faction.getByName(query.getString("source"));
                Faction target = Faction.getByName(query.getString("target"));

                Relationship rel = new Relationship(target.getID(), FactionsMod.CONFIG.DAYS_TO_FABRICATE + 1);
                source.setRelationship(rel);

                if (query.getBool("accept")) {
                    Relationship rev = new Relationship(source.getID(), FactionsMod.CONFIG.DAYS_TO_FABRICATE + 1);
                    source.setRelationship(rev);
                }
            }
        } catch (SQLException err) {
            FactionsMod.LOGGER.error("An error occurred during data migration", err);
        }

        try {
            con.close();
        } catch (SQLException err) {
            FactionsMod.LOGGER.warn("An error occurred while closing the database", err);
        }
    }

    private static User.Rank migrateRank(OldRank rank) {
        switch (rank) {
            case OWNER -> {
                return User.Rank.OWNER;
            }
            case CO_OWNER -> {
                return User.Rank.LEADER;
            }
            case OFFICER -> {
                return User.Rank.COMMANDER;
            }
            case CIVILIAN -> {
                return User.Rank.MEMBER;
            }
        }

        return User.Rank.MEMBER;
    }

    public enum OldRank {
        OWNER,
        CO_OWNER,
        OFFICER,
        CIVILIAN
    }

    private static class Query {
        private PreparedStatement statement;
        private ResultSet result;

        private final String query;
        private int paramIndex = 1;
        private boolean skippedNext = false;

        boolean success;

        public Query(String query) {
            this.query = query;
            try {
                statement = con.prepareStatement(query);
            } catch (SQLException e) {
                error(e);
            }
        }

        public Query set(Object... items) {
            try {
                for (Object item : items) {
                    statement.setObject(paramIndex, item);
                    paramIndex++;
                }
            } catch (SQLException e) {
                error(e);
            }
            return this;
        }

        public ArrayList<UUID> getListUUID(String columnName){
            try {
                return result.getObject(columnName, new ArrayList<UUID>().getClass());
            } catch (SQLException e) {
                error(e);
            }
            return null;
        }
        public String getString(String columnName) {
            try {
                return result.getString(columnName);
            } catch (SQLException e) {
                error(e);
            }
            return null;
        }

        public UUID getUUID(String columnName) {
            try {
                return result.getObject(columnName, UUID.class);
            } catch (SQLException e) {
                error(e);
            }
            return null;
        }

        public Claim.Outpost getOutpost(String columnName) {
            try {
                return result.getObject(columnName, Claim.Outpost.class);
            } catch (SQLException e) {
                error(e);
            }
            return null;
        }

        public WarGoal.StateWithType getWGStateWithType(String columnName) {
            try {
                return result.getObject(columnName, WarGoal.StateWithType.class);
            } catch (SQLException e) {
                error(e);
            }
            return null;
        }

        public WarGoal.Type getWGType(String columnName) {
            try {
                return result.getObject(columnName, WarGoal.Type.class);
            } catch (SQLException e) {
                error(e);
            }
            return null;
        }

        public Jail getJail(String columnName){
            try {
            return result.getObject(columnName, Jail.class);
        } catch (SQLException e) {
            error(e);
        }
            return null;
    }

        public int getInt(String columnName) {
            try {
                return result.getInt(columnName);
            } catch (SQLException e) {
                error(e);
            }
            return 0;
        }
        public long getLong(String columnName){
            try{
                return result.getLong(columnName);
            } catch (SQLException e){
                error(e);
            }
            return 0;
        }

        public double getDouble(String columnName) {
            try {
                return result.getDouble(columnName);
            } catch (SQLException e) {
                error(e);
            }
            return 0;
        }

        public float getFloat(String columnName) {
            try {
                return result.getFloat(columnName);
            } catch (SQLException e) {
                error(e);
            }
            return 0;
        }

        public boolean getBool(String columnName) {
            try {
                return result.getBoolean(columnName);
            } catch (SQLException e) {
                error(e);
            }
            return false;
        }

        public Query executeQuery() {
            try {
                result = statement.executeQuery();
                success = result.next();
            } catch (SQLException e) {
                error(e);
            }
            return this;
        }

        public boolean next() {
            try {
                if (skippedNext) return result.next();
            } catch (SQLException e) {
                error(e);
            }
            skippedNext = true;
            return success;
        }

        private void error(SQLException e) {
            FactionsMod.LOGGER.error("Error executing database transaction {}", query, e);
        }
    }
}
