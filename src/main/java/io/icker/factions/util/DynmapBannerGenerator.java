package io.icker.factions.util;

import io.icker.factions.FactionsMod;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DynmapBannerGenerator {

    public enum MinecraftColor{
        WHITE(16383998, "white"),ORANGE(16351261, "orange"),MAGENTA(13061821, "magenta"),LIGHT_BLUE(3847130, "light_blue"),
        YELLOW(16701501, "yellow"),LIME(8439583, "lime"),PINK(15961002, "pink"),GRAY(4673362,"gray"),
        LIGHT_GRAY(10329495, "light_gray"),CYAN(1481884,"cyan"),PURPLE(8991416, "purple"),BLUE(3949738, "blue"),
        BROWN(8606770, "brown"),GREEN(6192150, "green"),RED(11546150, "red"),BLACK(1908001, "black");

        public final int rgb;
        public final String color;
        MinecraftColor(int rgb, String color){
            this.rgb = rgb; this.color = color;
        }

        public static int getByName(String name){
            return Arrays.stream(values()).filter(minecraftColor -> minecraftColor.color.equalsIgnoreCase(name)).findFirst().orElse(BLACK).rgb;
        }

    }

    public enum FrameType {

        MINOR(17, "minor"),
        VASSAL(17, "vassal"),
        METROPOLY(13, "metropoly"),
        ADMIN(17, "admin");


        public final int yOffset;
        public final String name;
        FrameType(int yOffset, String name) {
            this.yOffset = yOffset;
            this.name = name;
        }
    }

    public static String BANNER_PATTERNS_PATH;
    public static String BANNER_FRAMINGS_PATH;
    public static String DYNMAP_ICONS_PATH;
    public static List<String> BANNER_PATTERNS_LIST = new ArrayList<>();
    public static List<String> BANNER_FRAMINGS_LIST = new ArrayList<>();

    public static HashMap<String, BufferedImage> IMAGES_CACHE = new HashMap<>();
    public static HashMap<String, BufferedImage> FRAMINGS_CACHE = new HashMap<>();

    public static void initialize() {
        initPatternsList();
        initFramingList();
        BANNER_PATTERNS_PATH = BANNER_PATTERNS_PATH == null ? FactionsMod.jarfile.getPath() + File.separator + "banner_patterns" : BANNER_PATTERNS_PATH;
        BANNER_FRAMINGS_PATH = BANNER_FRAMINGS_PATH == null ? FactionsMod.jarfile.getPath() + File.separator + "banner_framings" : BANNER_FRAMINGS_PATH;
        DYNMAP_ICONS_PATH = DYNMAP_ICONS_PATH == null ? FactionsMod.jarfile.getAbsolutePath() + File.separator + "rendered_banners" : DYNMAP_ICONS_PATH;
        System.out.println("Factions' banner patterns path: " + BANNER_PATTERNS_PATH);
        System.out.println("Dynmap Generated Icons Path: " + DYNMAP_ICONS_PATH);
        File dynmapIconsPath = new File(DYNMAP_ICONS_PATH);
        if(!dynmapIconsPath.exists()) dynmapIconsPath.mkdirs();
        try {
            loadCache();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void initFramingList() {
        BANNER_FRAMINGS_LIST.add("admin");
        BANNER_FRAMINGS_LIST.add("metropoly");
        BANNER_FRAMINGS_LIST.add("minor");
        BANNER_FRAMINGS_LIST.add("vassal");
    }

    private static void initPatternsList() {
        BANNER_PATTERNS_LIST.add("base");
        BANNER_PATTERNS_LIST.add("border");
        BANNER_PATTERNS_LIST.add("bricks");
        BANNER_PATTERNS_LIST.add("circle");
        BANNER_PATTERNS_LIST.add("creeper");
        BANNER_PATTERNS_LIST.add("cross");
        BANNER_PATTERNS_LIST.add("curly_border");
        BANNER_PATTERNS_LIST.add("diagonal_left");
        BANNER_PATTERNS_LIST.add("diagonal_right");
        BANNER_PATTERNS_LIST.add("diagonal_up_left");
        BANNER_PATTERNS_LIST.add("diagonal_up_right");
        BANNER_PATTERNS_LIST.add("flower");
        BANNER_PATTERNS_LIST.add("globe");
        BANNER_PATTERNS_LIST.add("gradient");
        BANNER_PATTERNS_LIST.add("gradient_up");
        BANNER_PATTERNS_LIST.add("half_horizontal");
        BANNER_PATTERNS_LIST.add("half_horizontal_bottom");
        BANNER_PATTERNS_LIST.add("half_vertical");
        BANNER_PATTERNS_LIST.add("half_vertical_right");
        BANNER_PATTERNS_LIST.add("mojang");
        BANNER_PATTERNS_LIST.add("piglin");
        BANNER_PATTERNS_LIST.add("rhombus");
        BANNER_PATTERNS_LIST.add("skull");
        BANNER_PATTERNS_LIST.add("small_stripes");
        BANNER_PATTERNS_LIST.add("square_bottom_left");
        BANNER_PATTERNS_LIST.add("square_bottom_right");
        BANNER_PATTERNS_LIST.add("square_top_left");
        BANNER_PATTERNS_LIST.add("square_top_right");
        BANNER_PATTERNS_LIST.add("straight_cross");
        BANNER_PATTERNS_LIST.add("stripe_bottom");
        BANNER_PATTERNS_LIST.add("stripe_center");
        BANNER_PATTERNS_LIST.add("stripe_downleft");
        BANNER_PATTERNS_LIST.add("stripe_downright");
        BANNER_PATTERNS_LIST.add("stripe_left");
        BANNER_PATTERNS_LIST.add("stripe_center");
        BANNER_PATTERNS_LIST.add("stripe_right");
        BANNER_PATTERNS_LIST.add("stripe_top");
        BANNER_PATTERNS_LIST.add("stripe_bottom");
        BANNER_PATTERNS_LIST.add("triangle_top");
        BANNER_PATTERNS_LIST.add("triangles_bottom");
        BANNER_PATTERNS_LIST.add("triangles_top");
    }


    public static boolean loadCache() throws IOException {
        for(String name : BANNER_PATTERNS_LIST) {
            Path temp = Files.createTempFile(name + "-", ".png");
            Files.copy(FactionsMod.class.getProtectionDomain().getClassLoader().getResourceAsStream("dynmap_data/banner_patterns/" + name + ".png"), temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                IMAGES_CACHE.put(name + ".png", ImageIO.read(new FileInputStream(temp.toFile())));
            } catch (IOException ignored) {

            }
        }
        for(String name : BANNER_FRAMINGS_LIST) {
            Path temp = Files.createTempFile(name + "-", ".png");
            Files.copy(FactionsMod.class.getProtectionDomain().getClassLoader().getResourceAsStream("dynmap_data/banner_framings/" + name + ".png"), temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                FRAMINGS_CACHE.put(name + ".png", ImageIO.read(new FileInputStream(temp.toFile())));
            } catch (IOException ignored) {

            }
        }
        return true;
    }


    public static File generate(List<String> patterns, UUID id, FrameType frameType){
        int size = patterns.size();
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TRANSLUCENT);
        for(int i = 0; i < size/2; i++){
            int rgb = Integer.parseInt(patterns.get(i*2));
            String pattern = patterns.get((i*2)+1);

            BufferedImage layer = IMAGES_CACHE.get(pattern + ".png");

            Color xorColor = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            BufferedImage layerTinted = tint((float) xorColor.getRed() / 255, (float) xorColor.getGreen() / 255, (float) xorColor.getBlue() / 255, (float) xorColor.getAlpha() / 255, layer);

            image.getGraphics().drawImage(layerTinted, 0, 0, null);
        }

        BufferedImage ready = image.getSubimage(1, 1, 19, 39);

        BufferedImage filler = new BufferedImage(55, 55, BufferedImage.TRANSLUCENT);

        int yOffset = frameType.yOffset;
        filler.getGraphics().drawImage(ready, yOffset, 8, null);
        ready = filler;
        ready = rotateCounterClockwise90(ready);

        BufferedImage framing = FRAMINGS_CACHE.get(frameType.name + ".png");
        ready.getGraphics().drawImage(framing, 0, 0, null);

        File export = new File(DYNMAP_ICONS_PATH + File.separator + id.toString() + "_" + frameType.name + ".png");

        try {
            ImageIO.write(ready, "png", export);
        } catch (IOException ignored) {

        }
        return export;
    }

    protected static BufferedImage tint(float r, float g, float b, float a,
                                 BufferedImage sprite)
    {
        BufferedImage tintedSprite = new BufferedImage(sprite.getWidth(), sprite.
                getHeight(), BufferedImage.TRANSLUCENT);
        Graphics2D graphics = tintedSprite.createGraphics();
        graphics.drawImage(sprite, 0, 0, null);
        graphics.dispose();

        for (int i = 0; i < tintedSprite.getWidth(); i++)
        {
            for (int j = 0; j < tintedSprite.getHeight(); j++)
            {
                int ax = tintedSprite.getColorModel().getAlpha(tintedSprite.getRaster().
                        getDataElements(i, j, null));
                int rx = tintedSprite.getColorModel().getRed(tintedSprite.getRaster().
                        getDataElements(i, j, null));
                int gx = tintedSprite.getColorModel().getGreen(tintedSprite.getRaster().
                        getDataElements(i, j, null));
                int bx = tintedSprite.getColorModel().getBlue(tintedSprite.getRaster().
                        getDataElements(i, j, null));
                rx *= r;
                gx *= g;
                bx *= b;
                ax += (int) (a*256);
                tintedSprite.setRGB(i, j, (ax << 24) | (rx << 16) | (gx << 8) | (bx));
            }
        }
        return tintedSprite;
    }

    public static BufferedImage rotateCounterClockwise90(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();

        BufferedImage dest = new BufferedImage(height, width, src.getType());

        Graphics2D graphics2D = dest.createGraphics();
        graphics2D.translate((height - width) / 2, (height - width) / 2);
        graphics2D.rotate((Math.PI * 3) / 2, height / 2, width / 2);
        graphics2D.drawRenderedImage(src, null);

        return dest;
    }
}