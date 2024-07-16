package io.icker.factions.api.persistents;

import java.util.List;
import java.util.UUID;

public interface StateTypeable {
    public WarGoal.StateType getStateType();

    public Faction getCapitalState();

    public List<User> findAllUsersOfEmpire();

    public WarGoal getActiveWargoal();

    public UUID getID();
    public String getName();
}
