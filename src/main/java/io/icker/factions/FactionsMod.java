package io.icker.factions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.command.*;
import io.icker.factions.config.Config;
import io.icker.factions.core.*;
import io.icker.factions.localization.LocalizationThread;
import io.icker.factions.util.Command;
import io.icker.factions.util.DynmapBannerGenerator;
import io.icker.factions.util.DynmapWrapper;
import io.icker.factions.util.Migrator;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;

public class FactionsMod implements DedicatedServerModInitializer{
    public static Logger LOGGER = LogManager.getLogger("Factions");

    public static Config CONFIG = Config.load();
    public static DynmapWrapper dynmap;
    public static LuckPerms LUCK_API;
    public static File ANCOM_BANNER;
    public static File ANCAP_METROPOLY_BANNER;
    public static File ANCAP_VASSAL_BANNER;
    public static LocalizationThread LOCALIZATION_THREAD;

    @Override
    public void onInitializeServer() {
        LOGGER.info("Initialized Factions Mod for Minecraft v1.18");

        Migrator.migrate();

        FactionsManager.register();
        InteractionManager.register();
        ServerManager.register();
        SoundManager.register();
        WorldManager.register();

        CommandRegistrationCallback.EVENT.register(FactionsMod::registerCommands);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                    try {
                        checkDynmapCSSOverride(server);
                    } catch (IOException e) {
                        System.out.println("Failed to instantiate Dynmap CSS overrides!");
                        e.printStackTrace();
                    }
                    LUCK_API = LuckPermsProvider.get();
            try {
                DynmapBannerGenerator.DYNMAP_ICONS_PATH = server.getRunDirectory().getCanonicalPath() + "/dynmap/import/country_banners/";
            } catch (IOException ignored) {}

            DynmapBannerGenerator.initialize();
            List<String> ancom = new ArrayList<>();
            ancom.add("1908001");
            ancom.add("base");
            ancom.add("11546150");
            ancom.add("diagonal_right");
            ANCOM_BANNER = DynmapBannerGenerator.generate(ancom, Util.NIL_UUID, DynmapBannerGenerator.FrameType.MINOR);

            List<String> ancap = new ArrayList<>();
            ancom.add("1908001");
            ancom.add("base");
            ancom.add("16701501");
            ancom.add("diagonal_right");
            ANCAP_VASSAL_BANNER = DynmapBannerGenerator.generate(ancap, Util.NIL_UUID, DynmapBannerGenerator.FrameType.VASSAL);
            ANCAP_METROPOLY_BANNER = DynmapBannerGenerator.generate(ancap, Util.NIL_UUID, DynmapBannerGenerator.FrameType.METROPOLY);
            dynmap = FabricLoader.getInstance().isModLoaded("dynmap") ? new DynmapWrapper() : null;

                    LOCALIZATION_THREAD = new LocalizationThread();
                    LOCALIZATION_THREAD.setName("Localization Thread");
                    LOCALIZATION_THREAD.setDaemon(true);
                    LOCALIZATION_THREAD.start();
        }
        );
    }

    private void checkDynmapCSSOverride(MinecraftServer server) throws IOException {
        File thisCSS = new File(server.getRunDirectory().getCanonicalPath() + "/dynmap/web/css/factions_override.css");
        File indexHTML = new File(server.getRunDirectory().getCanonicalPath() + "/dynmap/web/index.html");

        if(!checkIndexHTML(indexHTML)) return;
        checkCSS(thisCSS, server);

    }

    private void checkCSS(File file, MinecraftServer server) throws IOException {
        if(file.exists()) return;
        file.createNewFile();
        FileInputStream inputStream = new FileInputStream(FactionsMod.class.getClassLoader().getResource("dynmap_front_end/factions_override.css").getFile());
        Files.write(file.toPath(), inputStream.readAllBytes(), StandardOpenOption.WRITE);
    }

    private boolean checkIndexHTML(File file) throws IOException {
        if(!file.exists()) return false;
        System.out.println("Absolute Path for index.html is: " + file.getAbsolutePath());
        BufferedReader reader = new BufferedReader(new FileReader(file));
        List<String> stringStream = reader.lines().toList();
        long size = stringStream.size();
        System.out.println("BufferedReader's lines size is: " + size);
        long inject = 0;
        System.out.println("This file content is:");
        for (int i = 0; i<size;i++) {
            String line = stringStream.get(i);
            System.out.println(line);
            if(line == null) continue;
            if (line.contains("<!-- <link rel=\"stylesheet\" type=\"text/css\" href=\"css/override.css\" media=\"screen\" /> -->")) {
                i++;
                inject = i;
                while (i<size) {

                    String nextLine = stringStream.get(i);
                    if(nextLine.contains("<link rel=\"stylesheet\" type=\"text/css\" href=\"css/override.css\" media=\"screen\" />")) {
                        System.out.println("Injection found!" + file.getAbsolutePath());reader.close(); return true;}
                    i++;

                }
                break;
            }
        }
        System.out.println("So, the checker didn't find any injection.");
        reader.close();
        if(inject == 0) return false;
        System.out.println("However, the injection could be made");
        List<String> wholeFile = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        wholeFile.set((int) inject, wholeFile.get((int) inject) + "\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"css/factions_override.css\" media=\"screen\" />");

        StringBuilder unite = new StringBuilder();
        for(String part : wholeFile){
            unite.append(part);
            unite.append("\n");
        }



        Files.writeString(file.toPath(), unite.toString(), StandardOpenOption.WRITE);
        return true;
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated) {
        LiteralCommandNode<ServerCommandSource> factions = CommandManager
            .literal("factions")
            .build();

        LiteralCommandNode<ServerCommandSource> alias = CommandManager
            .literal("f")
            .build();

        dispatcher.getRoot().addChild(factions);
        dispatcher.getRoot().addChild(alias);

        Command[] commands = new Command[] {
            new AdminCommand(),
            new SettingsCommand(),
            new ClaimCommand(),
            new CreateCommand(),
            new DeclareCommand(),
            new DisbandCommand(),
            new HomeCommand(),
            new InfoCommand(),
            new InviteCommand(),
            new JoinCommand(),
            new KickCommand(),
            new LeaveCommand(),
            new ListCommand(),
            new MapCommand(),
            new ModifyCommand(),
            new RankCommand(),
            new SafeCommand(),
                new BurnResourcesCommand(),
                new JailCommand()
        };

        for (Command command : commands) {
            factions.addChild(command.getNode());
            alias.addChild(command.getNode());
        }
    }
}
