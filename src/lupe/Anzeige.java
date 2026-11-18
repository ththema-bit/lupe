package lupe;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.MultiResolutionImage;
import java.util.List;

/** Fenster, das den gewählten Bereich laufend abfotografiert und vergrößert zeigt. */
final class Anzeige {
    private final Einstellungen e;
    private final Robot robot;
    private final Runnable geaendert;     // Optionen über das Kontextmenü geändert
    private final Runnable bereichWaehlen;
    private final JFrame frame = new JFrame("Lupe: Anzeige");
    private final Bild bild = new Bild();
    private volatile Image letztes;
    private volatile double gemesseneFps;
    private Rectangle normalBounds;       // Fenstergröße vor dem Vollbild
    private Thread aufnahme;

    Anzeige(Einstellungen e, Robot robot, Runnable geaendert, Runnable bereichWaehlen) {
        this.e = e;
        this.robot = robot;
        this.geaendert = geaendert;
        this.bereichWaehlen = bereichWaehlen;
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(bild);
        frame.getContentPane().setBackground(Theme.BG);
        frame.setIconImages(Lupe.icons());
        frame.addComponentListener(new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent ev) { merkeBounds(); }
            @Override public void componentResized(ComponentEvent ev) { merkeBounds(); }
            @Override public void componentHidden(ComponentEvent ev) { geaendert.run(); }
        });
        Rectangle b = e.anzeige != null && sichtbarAufEinemSchirm(e.anzeige) ? e.anzeige : startBounds();
        normalBounds = new Rectangle(b);
        frame.setBounds(b);
    }

    boolean sichtbar() { return frame.isVisible(); }
    double fps() { return gemesseneFps; }

    void zeigen() {
        if (e.vollbild) setzeVollbild(true);
        boolean undeko = e.ohneRahmen || e.vollbild;
        if (frame.isUndecorated() != undeko) {
            frame.dispose();
            frame.setUndecorated(undeko);
        }
        frame.setAlwaysOnTop(e.vordergrund);
        frame.setVisible(true);
        starteAufnahme();
    }

    void verstecken() { frame.setVisible(false); }

    /** Nach Änderungen an Rahmen/Vordergrund/Vollbild das Fenster neu aufbauen. */
    void optionenAnwenden() {
        boolean undeko = e.ohneRahmen || e.vollbild;
        boolean war = frame.isVisible();
        if (frame.isUndecorated() != undeko) {
            frame.dispose();
            frame.setUndecorated(undeko);
        }
        frame.setAlwaysOnTop(e.vordergrund);
        if (war) frame.setVisible(true);
        bild.repaint();
    }

    void vollbildUmschalten() {
        e.vollbild = !e.vollbild;
        setzeVollbild(e.vollbild);
        optionenAnwenden();
        geaendert.run();
    }

    private void setzeVollbild(boolean an) {
        if (an) {
            if (!istVollbildBounds(frame.getBounds())) normalBounds = frame.getBounds();
            frame.setBounds(schirmVon(frame.getBounds()).getDefaultConfiguration().getBounds());
        } else if (normalBounds != null) {
            frame.setBounds(normalBounds);
        }
    }

    private boolean istVollbildBounds(Rectangle r) {
        return schirmVon(r).getDefaultConfiguration().getBounds().equals(r);
    }

    private void merkeBounds() {
        if (!frame.isShowing()) return;
        if (!e.vollbild) {
            normalBounds = frame.getBounds();
            e.anzeige = new Rectangle(normalBounds);
        }
    }

    // ---- Aufnahme --------------------------------------------------------------------------

    private synchronized void starteAufnahme() {
        if (aufnahme != null && aufnahme.isAlive()) return;
        aufnahme = new Thread(this::schleife, "Lupe-Aufnahme");
        aufnahme.setDaemon(true);
        aufnahme.start();
    }

    private void schleife() {
        long zaehlStart = System.nanoTime();
        int bilder = 0;
        while (true) {
            long t0 = System.nanoTime();
            Rectangle r = e.bereich;
            if (frame.isVisible() && r != null) {
                try {
                    letztes = groessteVariante(robot.createMultiResolutionScreenCapture(r));
                    bilder++;
                    bild.repaint();
                } catch (RuntimeException ignored) {
                    // z. B. Bereich außerhalb aller Bildschirme
                }
            } else {
                bilder = 0;
                zaehlStart = t0;
                gemesseneFps = 0;
            }
            long jetzt = System.nanoTime();
            if (jetzt - zaehlStart >= 1_000_000_000L) {
                gemesseneFps = bilder * 1e9 / (jetzt - zaehlStart);
                bilder = 0;
                zaehlStart = jetzt;
            }
            long warte = 1_000_000_000L / Math.max(1, e.fps) - (jetzt - t0);
            try {
                Thread.sleep(frame.isVisible() ? Math.max(1, warte / 1_000_000L) : 200);
            } catch (InterruptedException ex) {
                return;
            }
        }
    }

    /** Bei Windows-Skalierung (125 %, 150 % …) das Bild in voller Pixel-Auflösung nehmen. */
    private static Image groessteVariante(MultiResolutionImage m) {
        List<Image> v = m.getResolutionVariants();
        Image best = v.get(0);
        for (Image i : v) if (i.getWidth(null) > best.getWidth(null)) best = i;
        return best;
    }

    // ---- Darstellung -----------------------------------------------------------------------

    /** Zielrechteck für ein Bild der Größe srcW×srcH in einer Fläche dstW×dstH. */
    static Rectangle einpassen(int srcW, int srcH, int dstW, int dstH, boolean seitenverhaeltnis) {
        if (!seitenverhaeltnis || srcW <= 0 || srcH <= 0) return new Rectangle(0, 0, dstW, dstH);
        double s = Math.min(dstW / (double) srcW, dstH / (double) srcH);
        int w = (int) Math.round(srcW * s), h = (int) Math.round(srcH * s);
        return new Rectangle((dstW - w) / 2, (dstH - h) / 2, w, h);
    }

    private final class Bild extends JPanel {
        private Point griff;          // Mausposition beim Ziehen (ohne Rahmen)
        private Rectangle griffBounds;
        private boolean groesse;      // true = Größe ändern statt verschieben

        Bild() {
            setBackground(Theme.BG);
            setOpaque(true);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent ev) {
                    if (ev.isPopupTrigger()) { menue(ev); return; }
                    if (!SwingUtilities.isLeftMouseButton(ev) || !frame.isUndecorated() || e.vollbild) return;
                    griff = ev.getLocationOnScreen();
                    griffBounds = frame.getBounds();
                    groesse = imGriff(ev.getPoint());
                }
                @Override public void mouseReleased(MouseEvent ev) {
                    if (ev.isPopupTrigger()) menue(ev);
                    griff = null;
                }
                @Override public void mouseDragged(MouseEvent ev) {
                    if (griff == null) return;
                    Point p = ev.getLocationOnScreen();
                    int dx = p.x - griff.x, dy = p.y - griff.y;
                    if (groesse) {
                        frame.setSize(Math.max(80, griffBounds.width + dx), Math.max(80, griffBounds.height + dy));
                    } else {
                        frame.setLocation(griffBounds.x + dx, griffBounds.y + dy);
                    }
                }
                @Override public void mouseMoved(MouseEvent ev) {
                    boolean g = frame.isUndecorated() && !e.vollbild && imGriff(ev.getPoint());
                    setCursor(Cursor.getPredefinedCursor(g ? Cursor.SE_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
                }
                @Override public void mouseClicked(MouseEvent ev) {
                    if (SwingUtilities.isLeftMouseButton(ev) && ev.getClickCount() == 2) vollbildUmschalten();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            // Esc beendet das Vollbild
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "esc");
            getActionMap().put("esc", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent ev) { if (e.vollbild) vollbildUmschalten(); }
            });
        }

        private boolean imGriff(Point p) { return p.x > getWidth() - 20 && p.y > getHeight() - 20; }

        private void menue(MouseEvent ev) {
            JPopupMenu m = new JPopupMenu();
            m.add(eintrag("Bereich neu wählen", bereichWaehlen));
            m.add(eintrag(e.vollbild ? "Vollbild beenden" : "Vollbild auf diesem Bildschirm", Anzeige.this::vollbildUmschalten));
            m.add(eintrag((e.ohneRahmen ? "Rahmen zeigen" : "Ohne Rahmen"), () -> { e.ohneRahmen = !e.ohneRahmen; optionenAnwenden(); geaendert.run(); }));
            m.add(eintrag((e.glatt ? "Scharfe Pixel" : "Glätten"), () -> { e.glatt = !e.glatt; repaint(); geaendert.run(); }));
            m.add(eintrag((e.vordergrund ? "Nicht mehr im Vordergrund" : "Immer im Vordergrund"), () -> { e.vordergrund = !e.vordergrund; optionenAnwenden(); geaendert.run(); }));
            m.addSeparator();
            m.add(eintrag("Anzeige schließen", Anzeige.this::verstecken));
            Theme.kontextMenueStil(m);
            m.show(this, ev.getX(), ev.getY());
        }

        private JMenuItem eintrag(String text, Runnable r) {
            JMenuItem i = new JMenuItem(text);
            i.addActionListener(a -> r.run());
            return i;
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            Image img = letztes;
            if (img == null || e.bereich == null) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(Theme.head.deriveFont(22f));
                g.setColor(Theme.ACC);
                g.drawString("KEIN BEREICH GEWÄHLT", 20, 40);
                g.setFont(Theme.body);
                g.setColor(Theme.DIM);
                g.drawString("Rechtsklick → Bereich neu wählen", 20, 64);
                return;
            }
            Rectangle z = einpassen(e.bereich.width, e.bereich.height, getWidth(), getHeight(), e.seitenverhaeltnis);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, e.glatt
                    ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                    : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g.drawImage(img, z.x, z.y, z.width, z.height, null);
            if (frame.isUndecorated() && !e.vollbild) { // Griff zum Größe ändern
                g.setColor(Theme.LINE);
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g.setColor(Theme.ACC2);
                int w = getWidth(), h = getHeight();
                for (int i = 4; i <= 12; i += 4) g.drawLine(w - i, h - 2, w - 2, h - i);
            }
        }
    }

    // ---- Bildschirm-Hilfen -----------------------------------------------------------------

    private static GraphicsDevice schirmVon(Rectangle r) {
        GraphicsDevice best = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        long flaeche = -1;
        for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle s = gd.getDefaultConfiguration().getBounds().intersection(r);
            long f = s.isEmpty() ? 0 : (long) s.width * s.height;
            if (f > flaeche) { flaeche = f; best = gd; }
        }
        return best;
    }

    private static boolean sichtbarAufEinemSchirm(Rectangle r) {
        for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle s = gd.getDefaultConfiguration().getBounds().intersection(r);
            if (s.width >= 80 && s.height >= 80) return true;
        }
        return false;
    }

    /** Erster Start: mittig auf dem zweiten Bildschirm, wenn es einen gibt. */
    private static Rectangle startBounds() {
        GraphicsDevice[] alle = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        GraphicsDevice haupt = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        GraphicsDevice ziel = haupt;
        for (GraphicsDevice gd : alle) if (gd != haupt) { ziel = gd; break; }
        Rectangle s = ziel.getDefaultConfiguration().getBounds();
        int g = Math.min(600, Math.min(s.width, s.height) - 100);
        return new Rectangle(s.x + (s.width - g) / 2, s.y + (s.height - g) / 2, g, g);
    }
}
