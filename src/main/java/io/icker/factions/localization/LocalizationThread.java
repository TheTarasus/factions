package io.icker.factions.localization;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.EmperorLocalization;
import io.icker.factions.api.persistents.Empire;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

public class LocalizationThread extends Thread {

    public static String GOOGLE_API_URL_WITH_TOKEN = "https://script.google.com/macros/s/AKfycbxfY9g4EgKnrymMfhSJKkuqQw-OjCci_T-GyzDnMthe_I3tVjwxK_fWOSDAfy3pQ5lFIA/exec";
    public Deque<EmperorLocalization> queuedLocalization = new ArrayDeque<>();

    private long previousTimer = System.currentTimeMillis() - delayInMilliseconds;

    public static long delayInMilliseconds = 1L * 30L * 1000L;

    @Override
    public void run(){
        FactionsMod.LOGGER.info("LocalizationThread has begun (google translator)");
        while(true){
            if(System.currentTimeMillis() <= delayInMilliseconds + previousTimer) {
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException ignored) {

                }
                continue;
            }
            previousTimer = System.currentTimeMillis() + delayInMilliseconds;
            while (!queuedLocalization.isEmpty()){
                try {
                    refineLocalization(queuedLocalization.remove());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void refineLocalization(EmperorLocalization localization) throws InterruptedException {
        System.out.println("Translating locales for Empire ID: " + localization.uuid);
        Thread.sleep(1250L);
        localization.setMainTitleWithoutTranslateQueue(translate(localization.getLocaleForEmperorMainTitle()));
        Thread.sleep(1250L);
        localization.setTitleHandlersWithoutTranslateQueue(translate(localization.getLocaleForEmperorTitleHandlers()));
        System.out.println("Translated text for Empire ID's " + localization.uuid + " main title: " + localization.getLocaleForEmperorMainTitle());
        System.out.println("Translated text for Empire ID's " + localization.uuid + " title handlers: " + localization.getLocaleForEmperorTitleHandlers());
    }

    private static String translate(String text) {
        if(text == null) return "";
        if(text.isEmpty() || text.isBlank()) return "";
        // INSERT YOU URL HERE
        String urlStr = null;
        try {
            urlStr = GOOGLE_API_URL_WITH_TOKEN +
                    "?input=" + URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            return text;
        }
        URL url = null;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException ignored) {
            return text;
        }
        StringBuilder response = new StringBuilder();
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) url.openConnection();
        } catch (IOException ignored) {
            return text;
        }
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        } catch (IOException ignored) {
            return text;
        }
        String inputLine;
        while (true) {
            try {
                if (!((inputLine = in.readLine()) != null)) break;
            } catch (IOException ignored) {
                return text;
            }
            response.append(inputLine);
        }
        try {
            in.close();
        } catch (IOException ignored) {
            return text;
        }
        String translation = response.toString();
        translation = translation.replaceAll("\"", "");
        return translation;
    }

    public void queueLocalization(EmperorLocalization localization){
        if(Empire.getEmpire(localization.uuid) == null) {localization.remove(); return;}
        if(queuedLocalization.contains(localization)) return;
        System.out.println("Localization added to Queue");
        queuedLocalization.add(localization);
    }

}
