package io.icker.factions.util;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.ClaimEvents;
import io.icker.factions.api.events.FactionEvents;
import io.icker.factions.api.persistents.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.*;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.UUID;

public class DynmapWrapper {
    public DynmapCommonAPI api;
    private MarkerAPI markerApi;
    private MarkerSet empireMarkerSet;
    private MarkerSet regionMarkerSet;

    public DynmapWrapper() {
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI dCAPI) {
                api = dCAPI;
                markerApi = api.getMarkerAPI();
                empireMarkerSet = markerApi.getMarkerSet("dynmap-empires");
                regionMarkerSet = markerApi.getMarkerSet("dynmap-regions");
                if (empireMarkerSet == null) empireMarkerSet = markerApi.createMarkerSet("dynmap-empires", "Империи", null, true);
                if (regionMarkerSet == null) regionMarkerSet = markerApi.createMarkerSet("dynmap-regions", "Регионы", null, true);
                regionMarkerSet.setHideByDefault(true);
                FactionsMod.dynmap.reloadAll();
            }
        });

        ClaimEvents.ADD.register(this::addClaim);
        ClaimEvents.REMOVE.register((x, z, level, faction) -> removeClaim(x, z, level, faction, 0));

        FactionEvents.SET_HOME.register(this::setHome);
        FactionEvents.SET_HOME.register(this::setHomeRegion);
        FactionEvents.MODIFY.register(this::updateFaction);
        FactionEvents.REMOVE_ALL_CLAIMS.register(faction -> removeBanners(faction.getID(), regionMarkerSet));
        FactionEvents.REMOVE_ALL_CLAIMS.register(faction -> removeBanners(faction.getID(), empireMarkerSet));
        FactionEvents.BANNER_UPDATE.register(faction -> removeBanners(faction.getID(), regionMarkerSet));
        FactionEvents.BANNER_UPDATE.register(faction -> removeBanners(faction.getID(), empireMarkerSet));
        FactionEvents.EMPIRE_BANNER_UPDATE.register(empire -> {
            empire.getVassalsIDList().forEach(id -> removeBanners(id, empireMarkerSet)); removeBanners(empire.getMetropolyID(), empireMarkerSet); });
        FactionEvents.MEMBER_JOIN.register((faction, user) -> updateFaction(faction));
        FactionEvents.MEMBER_LEAVE.register((faction, user) -> updateFaction(faction));
        FactionEvents.POWER_CHANGE.register((faction, oldPower) -> updateFaction(faction));
        FactionEvents.SET_HOME.register((faction, home) -> removeBanners(faction.getID(), regionMarkerSet));
        FactionEvents.SET_HOME.register((faction, home) -> removeBanners(faction.getID(), empireMarkerSet));
        FactionEvents.UPDATE_ALL_EMPIRE.register(this::updateAllLocalizations);
    }

    private void updateAllLocalizations(Empire empire) {
        updateFaction(empire.getCapitalState());
    }


    private void generateMarkers() {
        for (Faction faction : Faction.all()) {
            Home home = faction.getHome();
            if (home != null) {
                setHome(faction, home);
                setHomeRegion(faction, home);
            }

            String info = getInfo(faction);
            for (Claim claim : faction.getClaims()) {
                addClaim(claim, info);
                addBorders(claim, faction.getClaims());
            }
        }
    }

    private void addClaim(Claim claim, String factionInfo) {
        Faction faction = claim.getFaction();
        ChunkPos pos = new ChunkPos(claim.x, claim.z);
        AreaMarker marker = empireMarkerSet.createAreaMarker(
            claim.getKey(), factionInfo,
            true, dimensionTagToID(claim.level),
            new double[]{pos.getStartX(), pos.getEndX() + 1},
            new double[]{pos.getStartZ(), pos.getEndZ() + 1},
            true
        );
        float opacity = claim.dotational ? 0.1f : 0.5f;

        Empire empire = Empire.getEmpireByFaction(faction.getID());
        Formatting color = faction.getColor();
        if(empire != null) color = empire.getCapitalState().getColor();

        if (marker != null) {
            marker.setFillStyle(opacity, color.getColorValue());
            marker.setLineStyle(0, 1.0, color.getColorValue());
        }
        AreaMarker regionMarker = regionMarkerSet.createAreaMarker(
                claim.getKey(), factionInfo,
                true, dimensionTagToID(claim.level),
                new double[]{pos.getStartX(), pos.getEndX() + 1},
                new double[]{pos.getStartZ(), pos.getEndZ() + 1},
                true
        );
        if (regionMarker != null) {
            regionMarker.setFillStyle(opacity, faction.getColor().getColorValue());
            regionMarker.setLineStyle(0, 1.0, faction.getColor().getColorValue());
        }
    }

    private void addBorders(Claim claim, List<Claim> claims) {
            Faction faction = claim.getFaction();
            ChunkPos pos = new ChunkPos(claim.x, claim.z);
            if(!claim.getFaction().getID().equals(faction.getID())) return;
        Empire empire = Empire.getEmpireByFaction(faction.getID());
            Formatting color = faction.getColor();
            if (empire != null) color = empire.getCapitalState().getColor();
            deletePolyMarkers(claim.x, claim.z, claim.level, empireMarkerSet);
            deletePolyMarkers(claim.x, claim.z, claim.level, regionMarkerSet);


        int thickness = claim.dotational ? 1 : 3;

            boolean haveEast = !claims.contains(Claim.get(claim.x + 1, claim.z, claim.level));
            boolean haveNorth = !claims.contains(Claim.get(claim.x, claim.z - 1, claim.level));
            boolean haveWest = !claims.contains(Claim.get(claim.x - 1, claim.z, claim.level));
            boolean haveSouth = !claims.contains(Claim.get(claim.x, claim.z + 1, claim.level));

            if (haveWest) {
                PolyLineMarker marker = empireMarkerSet.createPolyLineMarker(
                        claim.getKey() + "_west", "",
                        true, dimensionTagToID(claim.level),
                        new double[]{pos.getStartX(), pos.getStartX()},
                        new double[]{64, 64},
                        new double[]{pos.getStartZ(), pos.getEndZ() + 1},
                        true
                );
                if (marker != null)
                    marker.setLineStyle(thickness, 1.0, color.getColorValue());
            }
            if (haveEast) {
                PolyLineMarker marker = empireMarkerSet.createPolyLineMarker(
                        claim.getKey() + "_east", "",
                        true, dimensionTagToID(claim.level),
                        new double[]{pos.getEndX() + 1, pos.getEndX() + 1},
                        new double[]{64, 64},
                        new double[]{pos.getStartZ(), pos.getEndZ() + 1},
                        true
                );
                if (marker != null)
                    marker.setLineStyle(thickness, 1.0, color.getColorValue());
            }
            if (haveNorth) {
                PolyLineMarker marker = empireMarkerSet.createPolyLineMarker(
                        claim.getKey() + "_north", "",
                        true, dimensionTagToID(claim.level),
                        new double[]{pos.getStartX(), pos.getEndX() + 1},
                        new double[]{64, 64},
                        new double[]{pos.getStartZ(), pos.getStartZ()},
                        true
                );
                if (marker != null)
                    marker.setLineStyle(thickness, 1.0, color.getColorValue());
            }

            if (haveSouth) {
                PolyLineMarker marker = empireMarkerSet.createPolyLineMarker(
                        claim.getKey() + "_south", "",
                        true, dimensionTagToID(claim.level),
                        new double[]{pos.getStartX(), pos.getEndX() + 1},
                        new double[]{64, 64},
                        new double[]{pos.getEndZ() + 1, pos.getEndZ() + 1},
                        true
                );

                if (marker != null)
                    marker.setLineStyle(thickness, 1.0, color.getColorValue());
            }

            if (haveWest) {
                PolyLineMarker marker = regionMarkerSet.createPolyLineMarker(
                        claim.getKey() + "_west", "",
                        true, dimensionTagToID(claim.level),
                        new double[]{pos.getStartX(), pos.getStartX()},
                        new double[]{64, 64},
                        new double[]{pos.getStartZ(), pos.getEndZ() + 1},
                        true
                );
                if (marker != null)
                    marker.setLineStyle(thickness, 1.0, faction.getColor().getColorValue());
            }
            if (haveEast) {
                PolyLineMarker marker = regionMarkerSet.createPolyLineMarker(
                        claim.getKey()+ "_east", "",
                        true, dimensionTagToID(claim.level),
                        new double[]{pos.getEndX() + 1, pos.getEndX() + 1},
                        new double[]{64, 64},
                        new double[]{pos.getStartZ(), pos.getEndZ() + 1},
                        true
                );
                if (marker != null)
                    marker.setLineStyle(thickness, 1.0, faction.getColor().getColorValue());
            }
            if (haveNorth) {
                PolyLineMarker marker = regionMarkerSet.createPolyLineMarker(
                        claim.getKey() + "_north", "",
                        true, dimensionTagToID(claim.level),
                        new double[]{pos.getStartX(), pos.getEndX() + 1},
                        new double[]{64, 64},
                        new double[]{pos.getStartZ(), pos.getStartZ()},
                        true
                );
                if (marker != null)
                    marker.setLineStyle(thickness, 1.0, faction.getColor().getColorValue());
            }

            if (haveSouth) {
                PolyLineMarker marker = regionMarkerSet.createPolyLineMarker(
                        claim.getKey() + "_south", "",
                        true, dimensionTagToID(claim.level),
                        new double[]{pos.getStartX(), pos.getEndX() + 1},
                        new double[]{64, 64},
                        new double[]{pos.getEndZ() + 1, pos.getEndZ() + 1},
                        true
                );

                if (marker != null)
                    marker.setLineStyle(thickness, 1.0, faction.getColor().getColorValue());
            }
    }

    private void addClaim(Claim claim) {
        addClaim(claim, getInfo(claim.getFaction()));
        Faction faction = claim.getFaction();
        addBorders(claim, faction.getClaims());
        notifyNeighbours(claim, claim.getFaction());
    }

    public void notifyNeighbours(Claim claim, Faction faction){
        Claim eastClaim = Claim.get(claim.x - 1, claim.z, claim.level);
        Claim westClaim = Claim.get(claim.x + 1, claim.z, claim.level);
        Claim northClaim = Claim.get(claim.x, claim.z - 1, claim.level);
        Claim southClaim = Claim.get(claim.x, claim.z + 1, claim.level);
        if(eastClaim != null) addBorders(eastClaim, faction.getClaims());
        if(westClaim != null) addBorders(westClaim, faction.getClaims());
        if(northClaim != null) addBorders(northClaim, faction.getClaims());
        if(southClaim != null) addBorders(southClaim, faction.getClaims());
    }

    private void removeClaim(int x, int z, String level, Faction faction, int updateRadius) {
        if(updateRadius > 1) return;
        String areaMarkerId = String.format("%s-%d-%d", level, x, z);
        Claim claim = Claim.get(x, z, level);
        if(claim == null) return;
        AreaMarker empireMarker = empireMarkerSet.findAreaMarker(areaMarkerId);
        AreaMarker regionMarker = regionMarkerSet.findAreaMarker(areaMarkerId);
        if(empireMarker != null) empireMarker.deleteMarker();
        if(regionMarker != null) regionMarker.deleteMarker();
        deletePolyMarkers(claim.x, claim.z, claim.level, empireMarkerSet);
        deletePolyMarkers(claim.x, claim.z, claim.level, regionMarkerSet);
        Claim eastClaim = Claim.get(claim.x - 1, claim.z, claim.level);
        Claim westClaim = Claim.get(claim.x + 1, claim.z, claim.level);
        Claim northClaim = Claim.get(claim.x, claim.z - 1, claim.level);
        Claim southClaim = Claim.get(claim.x, claim.z + 1, claim.level);

        if (eastClaim != null) {

            String markerId = String.format("%s-%d-%d", eastClaim.level, eastClaim.x, eastClaim.z);
            empireMarker = empireMarkerSet.findAreaMarker(markerId);
            regionMarker = regionMarkerSet.findAreaMarker(markerId);
            if(empireMarker != null) empireMarker.deleteMarker();
            if(regionMarker != null) regionMarker.deleteMarker();
            removeClaim(eastClaim.x, eastClaim.z, eastClaim.level, faction, updateRadius +1 );
        }
        if (westClaim != null) {

            String markerId = String.format("%s-%d-%d", westClaim.level, westClaim.x, westClaim.z);
            empireMarker = empireMarkerSet.findAreaMarker(markerId);
            regionMarker = regionMarkerSet.findAreaMarker(markerId);
            if(empireMarker != null) empireMarker.deleteMarker();
            if(regionMarker != null) regionMarker.deleteMarker();
            removeClaim(westClaim.x, westClaim.z, westClaim.level, faction, updateRadius +1 );
        }
        if (northClaim != null){
            String markerId = String.format("%s-%d-%d", northClaim.level, northClaim.x, northClaim.z);
            empireMarker = empireMarkerSet.findAreaMarker(markerId);
            regionMarker = regionMarkerSet.findAreaMarker(markerId);
            if(empireMarker != null) empireMarker.deleteMarker();
            if(regionMarker != null) regionMarker.deleteMarker();
            removeClaim(northClaim.x, northClaim.z, northClaim.level, faction, updateRadius +1 );
        }
        if(southClaim != null) {
            String markerId = String.format("%s-%d-%d", southClaim.level, southClaim.x, southClaim.z);
            empireMarker = empireMarkerSet.findAreaMarker(markerId);
            regionMarker = regionMarkerSet.findAreaMarker(markerId);
            if(empireMarker != null) empireMarker.deleteMarker();
            if(regionMarker != null) regionMarker.deleteMarker();
            removeClaim(southClaim.x, southClaim.z, southClaim.level, faction, updateRadius +1 );
        }
    }

    public void deletePolyMarkers(int x, int z, String level, MarkerSet set) {
        String areaMarkerId = String.format("%s-%d-%d", level, x, z);
        PolyLineMarker north = set.findPolyLineMarker(areaMarkerId + "_north");
        PolyLineMarker south = set.findPolyLineMarker(areaMarkerId + "_south");
        PolyLineMarker east = set.findPolyLineMarker(areaMarkerId + "_east");
        PolyLineMarker west = set.findPolyLineMarker(areaMarkerId + "_west");

        if(north != null) north.deleteMarker();
        if(south != null) south.deleteMarker();
        if(east != null) east.deleteMarker();
        if(west != null) west.deleteMarker();
    }

    private void updateFaction(Faction faction) {
        if(faction == null) return;
        String info = getInfo(faction);
        Empire empire = Empire.getEmpireByFaction(faction.getID());
        Formatting color = faction.getColor();

        List<Claim> claims = faction.getClaims();

        if(empire != null) color = empire.getCapitalState().getColor();
        if(!faction.getClaims().isEmpty())
            for (Claim claim : claims)
                removeClaim(claim.x, claim.z, claim.level, faction, 1);

        for (Claim claim : claims) {
            addClaim(claim, info);
            addBorders(claim, claims);
        }
    }

    public void removeBanners(UUID id, MarkerSet set){
        MarkerIcon icon0 = markerApi.getMarkerIcon(id + "_empire");
        MarkerIcon icon1 = markerApi.getMarkerIcon(id + "_regional");
        if(icon0 != null) icon0.deleteIcon();
        if(icon1 != null) icon1.deleteIcon();
        Marker marker0 = set.findMarker(id + "_empire");
        Marker marker1 = set.findMarker(id + "_regional");
        if(marker0 != null) marker0.deleteMarker();
        if(marker1 != null) marker1.deleteMarker();
        Faction faction = Faction.get(id);
        if(faction != null) {
            setHome(faction, faction.getHome());
            setHomeRegion(faction, faction.getHome());
        }
    }

    private void setHome(Faction faction, Home home) {
        if(home == null) return;
        Empire empire = Empire.getEmpireByFaction(faction.getID());


        String markerName = empire == null ?
                faction.getID().toString() :
                empire.isVassal(faction.getID())
                        ? empire.getVassalFlagPath()
                        .replaceAll("^(.*[\\\\\\/])", "")
                        .replaceAll(".png", "")

                        : empire.getMetropolyFlagPath()
                        .replaceAll("^(.*[\\\\\\/])", "")
                        .replaceAll(".png", "");


        String markerPostfix = "";
        if(faction.getStateType().equals(WarGoal.StateType.FREE_STATE)) markerPostfix = "_minor";
        markerName = markerName + markerPostfix;
        if(faction.isAdmin()) markerName = faction.getID() + "_admin";

        Marker marker = empireMarkerSet.findMarker(markerName);

        if (marker != null)
            marker.deleteMarker();


        File file = new File(DynmapBannerGenerator.DYNMAP_ICONS_PATH + markerName + ".png");
        System.out.println("Empire banner File path: " +  file.toString() + "; Exists?: " + file.exists());
        if(!file.exists()){
            String newName = "";
            switch (faction.getStateType()){
                case VASSAL:
                    newName = FactionsMod.ANCAP_VASSAL_BANNER.toString()
                        .replaceAll("^(.*[\\\\\\/])", "").replaceAll(".png", "");
                    break;
                case EMPIRE:
                    newName = FactionsMod.ANCAP_METROPOLY_BANNER.toString()
                        .replaceAll("^(.*[\\\\\\/])", "").replaceAll(".png", "");
                    break;
                case FREE_STATE:
                    newName = FactionsMod.ANCOM_BANNER.toString().replaceAll("^(.*[\\\\\\/])", "").replaceAll(".png", "");
                    break;
            };

            MarkerIcon icon2 = markerApi.getMarkerIcon(newName);
            if(icon2==null) icon2 = generateIcon(faction.getID() + "_empire", markerName);
            empireMarkerSet.createMarker(markerName, faction.getName(), dimensionTagToID(home.level), home.x, home.y, home.z, icon2, true);
            System.out.println("Failed to find the regional marker, attempt to create: " + newName);
            return;
        }

        MarkerIcon icon = generateIcon(faction.getID() + "_empire", markerName);

        empireMarkerSet.createMarker(markerName, faction.getName(), dimensionTagToID(home.level), home.x, home.y, home.z, icon, true);
        System.out.println("Successfully created a marker icon!");

    }

    @Nullable
    public MarkerIcon generateIcon(String id, String filename){
        MarkerIcon markerIcon = null;
        String path = DynmapBannerGenerator.DYNMAP_ICONS_PATH + filename + ".png";
        if(!new File(path).exists()) {
            System.out.println("File not found: " + path);
            return null;
        }
        try {
            markerIcon = markerApi.createMarkerIcon(id, id, new FileInputStream(path));
        } catch (FileNotFoundException ignored){
            System.out.println("File not found: " + path);
            return null;
        }
        return markerIcon;

    }

    private void setHomeRegion(Faction faction, Home home) {
        if(home == null) return;
        String markerName = faction.getID() + "_minor";
        if(faction.isAdmin()){
            markerName = faction.getID() + "_admin";
        }

        MarkerIcon icon = markerApi.getMarkerIcon(markerName);
        Marker marker = regionMarkerSet.findMarker(markerName);


        if (marker != null)
            marker.deleteMarker();

        File file = new File(DynmapBannerGenerator.DYNMAP_ICONS_PATH + markerName + ".png");

        if(!file.exists()){
            String newName = FactionsMod.ANCOM_BANNER.toString()
                    .replaceAll("^(.*[\\\\\\/])", "").replaceAll(".png", "");
            MarkerIcon icon2 = markerApi.getMarkerIcon(newName);

            if(icon2==null) icon2 = generateIcon(faction.getID() + "_region", newName);
            regionMarkerSet.createMarker(markerName, faction.getName(), dimensionTagToID(home.level), home.x, home.y, home.z, icon2, true);
            System.out.println("Failed to find the regional marker, attempt to create: " + newName);
            return;
        }

        if(icon!=null) {icon.deleteIcon();
            icon = generateIcon(faction.getID() + "_region", markerName);}
        System.out.println("Successfully created Regional marker!");

        marker = regionMarkerSet.createMarker(markerName, faction.getName(), dimensionTagToID(home.level), home.x, home.y, home.z, icon, true);


        }

    private String dimensionTagToID(String level) { // TODO: allow custom dimensions
        if (level.equals("minecraft:overworld")) return "world";
        if (level.equals("minecraft:the_nether")) return "DIM-1";
        if (level.equals("minecraft:the_end")) return "DIM1";
        return level;
    }

    private String getInfo(Faction faction) {
        if(faction == null) return "";
        if(faction.getUsers().isEmpty()) return "";
        User owner = getOwner(faction);
        if(owner == null) return "";
        String ownerName = owner.getName();
        String neededLoop = faction.getName().length() >= 11 ? "<span>"+faction.getName()+"</span>" : faction.getName();
        return "<div class=\"town-title\">" + neededLoop + "</div><br>"
                + getEmpireStatus(faction) + "<br>"
                + "Описание: " + faction.getDescription() + "<br>"
                + "Казна: " + faction.getPower() + " ₽<br>"
                + "Глава: " + ownerName + "<br>"
                + "Количество игроков: " + faction.getUsers().size() + "<br>"
                + "<details><summary>Список игроков: </summary>" + getMembers(faction) + "</details>";// + "<br>"
            //+ "Allies: " + Ally.getAllies(faction.getName).stream().map(ally -> ally.target).collect(Collectors.joining(", "));
    }

    public User getOwner(Faction faction){
        return faction.getUsers().stream().filter(user -> user.getRank() == User.Rank.OWNER).findFirst().orElse(null);
    }

    private String getEmpireStatus(Faction faction){
        Empire empire = Empire.getEmpireByFaction(faction.getID());
        if(faction.isAdmin()) return "Админский город";
        if(empire == null) return "Вольный город";
        if(empire.isMetropoly(faction.getID())) {
            return "Столица империи: [" + empire.name + "]<br>" + getEmpireCapitalLore(faction) + "<br>";
        }
        return "Вассал империи [" + empire.name + "]<br>Метрополия: [" + Faction.get(empire.getMetropolyID()).getName() + "]<br>";
    }

    private String getEmpireCapitalLore(Faction faction) {
        User owner = faction.getUsers().stream().filter(user -> user.getRank() == User.Rank.OWNER).findFirst().orElse(null);
        String leaderName = owner == null ? "Всепоглощающая Пустота" : owner.getName();
        List<User> regents = faction.getUsers().stream().filter(user -> user.getRank() == User.Rank.LEADER).toList();
        int size = regents.size();
        String regentsString = "";
        for(int i = 0; i<size; i++){
            regentsString = regentsString + regents.get(i).getName();
            regentsString = i < size-1 ? regentsString + ", " : regentsString;
            regentsString = i % 4 == 3 ? regentsString + "<br>" : regentsString;
        }
        regentsString = regentsString.isEmpty() || regentsString.isBlank() ? "(Нет регентов)" : regentsString;

        return  "<div class=\"accordion\">\n" +
                "  <div class=\"accordion-item\">\n" +
                "    <input type=\"checkbox\" id=\"accordion1\" class=\"accordion-item__trigger\">\n" +
                "    <label for=\"accordion1\" class=\"accordion-item__title\">Его Величество - "+ leaderName +"</label>\n" +
                "    <div class=\"accordion-item__content\">\n" +
                "      <div class=\"accordion-item\">\n" +
                "    <input type=\"checkbox\" id=\"accordion2\" class=\"accordion-item__trigger\">\n" +
                "    <label for=\"accordion2\" class=\"accordion-item__title\">Полный список всех титулов:</label>\n" +
                "    <div class=\"accordion-item__content\">\n" +
                "      <p>"+ getEmperorsTitles(Empire.getEmpireByFaction(faction.getID()), leaderName) +"</p>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                " \n" +
                "\n" +
                "  <div class=\"accordion-item\">\n" +
                "    <input type=\"checkbox\" id=\"accordion3\" class=\"accordion-item__trigger\">\n" +
                "    <label for=\"accordion3\" class=\"accordion-item__title\">Регенты:</label>\n" +
                "    <div class=\"accordion-item__content\">\n" +
                "    <p>"+ regentsString +"</p>\n" +
                "    </div>" +
                "</div>";
    }
    public static String getEmperorDefaultMainTitleSuffix(Empire empire){
        String suffix = "Герцог of " + empire.name;
        int greatness = empire.getVassalsIDList().size();

        if(greatness > 3){
            suffix = "Царь of " + empire.name;
        }

        if(greatness > 6){
            suffix = "Царь of всея " + empire.name;
        }

        if(greatness > 9){
            suffix = "Император и Самодержец всея " + empire.name;
        }

        return suffix;
    }
    public String getEmperorsTitles(Empire empire, String emperorName) {

        int greatness = empire.getVassalsIDList().size();

        EmperorLocalization localization = EmperorLocalization.get(empire.getID());
        String prefix = emperorName + ", ";

        if(greatness > 9){
            prefix = "Его Величество - " + emperorName + ", ";
        }

        if(greatness > 12){
            prefix = "Божиею поспешествующею Милостию, Его Величество - " + emperorName + ", ";
        }
        prefix = prefix + localization.getLocaleForEmperorMainTitle();
        prefix = prefix + ":<br>";
        String titles = splitTitleHandlers(localization.getLocaleForEmperorTitleHandlers());
        prefix = prefix + titles;

        if(greatness > 12)
            prefix = prefix + ", и прочая, прочая, прочая...";

        return prefix;
    }

    public static String getDefaultLocaleForEmperorTitleHandlers(Empire empire) {
        List<UUID> factionsUnsorted = empire.getVassalsIDList();
        factionsUnsorted.add(empire.getMetropolyID());


        factionsUnsorted.sort((o1, o2) -> {
            Faction targetFaction = Faction.get(o1);
            Faction sourceFaction = Faction.get(o2);
            int s1 = sourceFaction.getUsers().size(), s2 = targetFaction.getUsers().size();
            return Integer.compare(s2, s1);
        });
        int size = factionsUnsorted.size();



        String allHandlers = "";
        for(int i = 0; i<size; i++){
            Faction vassal = Faction.get(factionsUnsorted.get(i));
            allHandlers = allHandlers + getGreatnessOfCityTowardsEmpire(vassal) + "of " + vassal.getName();
            allHandlers = i >= size-1 ? allHandlers : allHandlers + ", ";
        }



        return allHandlers;
    }

    public String splitTitleHandlers(String titleHandlers){
        String dividedTitles = "";
        String[] titleHandlersSplit = titleHandlers.split(",");
        int length = titleHandlersSplit.length;
        for(int i = 0; i<length; i++){
            String title = titleHandlersSplit[i];
            dividedTitles = dividedTitles + title;
            dividedTitles = i % 4 == 3 ? dividedTitles + "<br>" : dividedTitles;
        }

        return dividedTitles;
    }

    public static String getGreatnessOfCityTowardsEmpire(Faction faction){
        int counter = faction.getUsers().size();

        if(counter > 12){
            return "Царь ";
        }
        if(counter > 9){
            return "Князь ";
        }
        if(counter > 6){
            return "Герцог ";
        }
        if(counter > 3){
            return "Граф ";
        }
        return "Барон ";



    }

    private String getMembers(Faction faction){
        String count = "<p>[";
        List<User> users = faction.getUsers();
        int size = users.size();
        for(int i = 0; i < size; i++){
            count = count + users.get(i).getName();
            count = i % 5 == 0 && i > 5 ? count + "<br>" : count;
            count = i >= size-1 ? count : count + ", ";
        }
        count = count + "]</p>";
        return count;
    }

    public void reloadAll() {
        empireMarkerSet.deleteMarkerSet();
        empireMarkerSet = markerApi.createMarkerSet("dynmap-empires", "Империи", null, true);
        regionMarkerSet.deleteMarkerSet();
        regionMarkerSet = markerApi.createMarkerSet("dynmap-regions", "Регионы", null, true);
        regionMarkerSet.setHideByDefault(true);
        generateMarkers();
    }
}
