package io.icker.factions.api.persistents;

import io.icker.factions.FactionsMod;
import io.icker.factions.database.Database;
import io.icker.factions.database.Field;
import io.icker.factions.database.Name;
import io.icker.factions.localization.LocalizationThread;
import io.icker.factions.util.DynmapWrapper;

import java.util.HashMap;
import java.util.UUID;

@Name("EmperorLocalization")
public class EmperorLocalization {


    private static final HashMap<UUID, EmperorLocalization> STORE = Database.load(EmperorLocalization.class, EmperorLocalization::getID);
    public static void add(EmperorLocalization localization) {
        STORE.put(localization.uuid, localization);
    }

    public UUID getID(){
        return uuid;
    }
    public static void save() {
        Database.save(EmperorLocalization.class, STORE.values().stream().toList());
    }

    @Field("ID")
    public UUID uuid;

    @Field("localeForEmperorMainTitle")
    private String localeForEmperorMainTitle = "";
    @Field("localeForEmperorTitleHandlers")
    private String localeForEmperorTitleHandlers = "";

    public EmperorLocalization(UUID uuid, String localeForEmperorMainTitle, String localeForEmperorTitleHandlers){
        this.uuid = uuid;
        this.setLocaleForEmperorMainTitle(localeForEmperorMainTitle);
        this.setLocaleForEmperorTitleHandlers(localeForEmperorTitleHandlers);
    }
    public EmperorLocalization() {}

    public String getLocaleForEmperorTitleHandlers() {
        return localeForEmperorTitleHandlers;
    }

    public void setLocaleForEmperorTitleHandlers(String localeForEmperorTitleHandlers) {
        if(localeForEmperorTitleHandlers == null) {this.localeForEmperorTitleHandlers = DynmapWrapper.getDefaultLocaleForEmperorTitleHandlers(Empire.getEmpire(this.uuid)); return;}
        if(localeForEmperorTitleHandlers.isEmpty() || localeForEmperorTitleHandlers.isBlank()) {
            this.localeForEmperorTitleHandlers = DynmapWrapper.getDefaultLocaleForEmperorTitleHandlers(Empire.getEmpire(this.uuid));
            addInQueue();
            return;
        }
        this.localeForEmperorTitleHandlers = localeForEmperorTitleHandlers;
        addInQueue();
    }

    public static EmperorLocalization get(UUID id) {
        return STORE.get(id);
    }

    public String getLocaleForEmperorMainTitle() {
        return localeForEmperorMainTitle;
    }

    public void setLocaleForEmperorMainTitle(String localeForEmperorMainTitle) {
        if(localeForEmperorMainTitle == null) {
            this.localeForEmperorMainTitle = DynmapWrapper.getEmperorDefaultMainTitleSuffix(Empire.getEmpire(this.uuid));
            addInQueue();
            return;
        }
        if(localeForEmperorMainTitle.isBlank() || localeForEmperorMainTitle.isEmpty()) {
            this.localeForEmperorMainTitle = DynmapWrapper.getEmperorDefaultMainTitleSuffix(Empire.getEmpire(this.uuid));
            addInQueue();
            return;
        }
        this.localeForEmperorMainTitle = localeForEmperorMainTitle;
        addInQueue();
    }

    public void addInQueue(){
        if(this.localeForEmperorTitleHandlers == null) return;
        if(this.localeForEmperorMainTitle == null) return;
        FactionsMod.LOCALIZATION_THREAD.queueLocalization(this);
    }

    public void remove(){
        STORE.remove(this.uuid);
    }

    public void setMainTitleWithoutTranslateQueue(String localeForEmperorMainTitle){
        this.localeForEmperorMainTitle = localeForEmperorMainTitle;
    }
    public void setTitleHandlersWithoutTranslateQueue(String localeForEmperorTitleHandlers){
        this.localeForEmperorTitleHandlers = localeForEmperorTitleHandlers;
    }
}
