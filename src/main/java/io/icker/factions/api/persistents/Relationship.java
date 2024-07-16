package io.icker.factions.api.persistents;

import java.util.UUID;

import io.icker.factions.FactionsMod;
import io.icker.factions.database.Field;
import io.icker.factions.database.Name;

@Name("Relationship")
public class Relationship {

    public enum Status {
        ALLY,
        IMPROVED,
        NEUTRAL,
        INSULTED,
        ENEMY;
    }

    @Field("Target")
    public UUID target;


    @Field("Source")
    public UUID source;

    @Field("Status")
    private Status status;

    @Field("points")
    public int points;

    public Relationship(UUID source, UUID target, int points) {
        this.source = source;
        this.target = target;
        this.points = points;
        this.status = points == 0 ? Status.NEUTRAL : points <= -FactionsMod.CONFIG.DAYS_TO_FABRICATE ? Status.ENEMY : points >= FactionsMod.CONFIG.DAYS_TO_FABRICATE ? Status.ALLY : points > 0 ? Status.IMPROVED : Status.INSULTED;
    }

    public Relationship() { ; }


    public Status getStatus() {
        return status;
    }

    public void setStatus(int points) {
        this.status = points == 0 ? Status.NEUTRAL : points <= -FactionsMod.CONFIG.DAYS_TO_FABRICATE ? Status.ENEMY : points >= FactionsMod.CONFIG.DAYS_TO_FABRICATE ? Status.ALLY : points > 0 ? Status.IMPROVED : Status.INSULTED;
    }
}