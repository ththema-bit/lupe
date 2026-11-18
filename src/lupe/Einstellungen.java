package lupe;

import java.awt.Rectangle;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Merkt sich Bereich, Fensterposition und Optionen in ~/.lupe.properties. */
final class Einstellungen {
    Rectangle bereich;          // Bildschirmbereich, der vergrößert wird (null = noch keiner)
    Rectangle anzeige;          // Position/Größe des Anzeigefensters (null = automatisch)
    int fps = 30;
    boolean glatt = true;
    boolean vordergrund = true;
    boolean ohneRahmen = false;
    boolean seitenverhaeltnis = true;
    boolean vollbild = false;

    private final Path datei;

    Einstellungen(Path datei) { this.datei = datei; }

    static Einstellungen standard() {
        return new Einstellungen(Paths.get(System.getProperty("user.home"), ".lupe.properties"));
    }

    void laden() {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(datei)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        bereich = rect(p.getProperty("bereich"));
        anzeige = rect(p.getProperty("anzeige"));
        fps = Math.max(5, Math.min(120, zahl(p.getProperty("fps"), fps)));
        glatt = bool(p, "glatt", glatt);
        vordergrund = bool(p, "vordergrund", vordergrund);
        ohneRahmen = bool(p, "ohneRahmen", ohneRahmen);
        seitenverhaeltnis = bool(p, "seitenverhaeltnis", seitenverhaeltnis);
        vollbild = bool(p, "vollbild", vollbild);
    }

    void speichern() {
        Properties p = new Properties();
        if (bereich != null) p.setProperty("bereich", text(bereich));
        if (anzeige != null) p.setProperty("anzeige", text(anzeige));
        p.setProperty("fps", String.valueOf(fps));
        p.setProperty("glatt", String.valueOf(glatt));
        p.setProperty("vordergrund", String.valueOf(vordergrund));
        p.setProperty("ohneRahmen", String.valueOf(ohneRahmen));
        p.setProperty("seitenverhaeltnis", String.valueOf(seitenverhaeltnis));
        p.setProperty("vollbild", String.valueOf(vollbild));
        try (OutputStream out = Files.newOutputStream(datei)) {
            p.store(out, "Lupe");
        } catch (IOException ignored) {
        }
    }

    static String text(Rectangle r) { return r.x + "," + r.y + "," + r.width + "," + r.height; }

    static Rectangle rect(String s) {
        if (s == null) return null;
        String[] t = s.split(",");
        if (t.length != 4) return null;
        try {
            Rectangle r = new Rectangle(Integer.parseInt(t[0].trim()), Integer.parseInt(t[1].trim()),
                    Integer.parseInt(t[2].trim()), Integer.parseInt(t[3].trim()));
            return r.width > 0 && r.height > 0 ? r : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int zahl(String s, int fallback) {
        try { return s == null ? fallback : Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static boolean bool(Properties p, String k, boolean fallback) {
        String s = p.getProperty(k);
        return s == null ? fallback : Boolean.parseBoolean(s.trim());
    }
}
