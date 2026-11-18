package lupe;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;

/** Steuerfenster: Bereich wählen, Anzeige ein/aus, Optionen. */
public final class Lupe {
    private final Einstellungen e = Einstellungen.standard();
    private final Robot robot;
    private final JFrame frame = new JFrame("Lupe");
    private final JLabel bereichInfo = Theme.label(" ", Theme.body, Theme.TXT);
    private final JLabel status = Theme.label(" ", Theme.body, Theme.DIM);
    private final JButton zeigenKnopf = Theme.knopf("Anzeige zeigen", false);
    private final JButton vollbildKnopf = Theme.knopf("Vollbild", false);
    private JCheckBox glatt, vordergrund, ohneRahmen, seiten;
    private final Anzeige anzeige;

    private Lupe() throws AWTException {
        e.laden();
        robot = new Robot();
        anzeige = new Anzeige(e, robot, () -> SwingUtilities.invokeLater(this::aktualisieren), this::bereichWaehlen);
        baueFenster();
        aktualisieren();
        if (e.bereich != null) anzeige.zeigen();
        new Timer(500, a -> aktualisieren()).start();
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--selftest")) {
            selbsttest();
            return;
        }
        System.setProperty("awt.useSystemAAFontSettings", "on");
        Theme.ladeSchriften();
        SwingUtilities.invokeLater(() -> {
            try {
                new Lupe();
            } catch (AWTException ex) {
                JOptionPane.showMessageDialog(null, "Bildschirmaufnahme nicht möglich:\n" + ex.getMessage(),
                        "Lupe", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void baueFenster() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.BG);
        p.setBorder(new EmptyBorder(18, 22, 18, 22));

        p.add(links(Theme.titel("Lupe", 34f)));
        p.add(links(Theme.label("Bildschirmbereich vergrößert in eigenem Fenster", Theme.body.deriveFont(12f), Theme.DIM)));
        p.add(Box.createVerticalStrut(18));

        p.add(Theme.abschnitt("Bereich"));
        p.add(Box.createVerticalStrut(8));
        p.add(links(bereichInfo));
        p.add(Box.createVerticalStrut(10));
        JButton waehlen = Theme.knopf("Bereich wählen", true);
        waehlen.addActionListener(a -> bereichWaehlen());
        p.add(reihe(waehlen));
        p.add(Box.createVerticalStrut(18));

        p.add(Theme.abschnitt("Anzeige"));
        p.add(Box.createVerticalStrut(10));
        zeigenKnopf.addActionListener(a -> {
            if (anzeige.sichtbar()) anzeige.verstecken(); else anzeige.zeigen();
            aktualisieren();
        });
        vollbildKnopf.addActionListener(a -> {
            if (!anzeige.sichtbar()) anzeige.zeigen();
            anzeige.vollbildUmschalten();
        });
        p.add(reihe(zeigenKnopf, vollbildKnopf));
        p.add(Box.createVerticalStrut(12));

        JPanel fpsReihe = reihe(Theme.label("Bilder/s", Theme.body, Theme.DIM),
                Theme.chips(new int[]{15, 30, 60}, e.fps, "", w -> e.fps = w));
        p.add(fpsReihe);
        p.add(Box.createVerticalStrut(10));

        glatt = Theme.haken("Glätten (aus = scharfe Pixel)", e.glatt);
        glatt.addActionListener(a -> { e.glatt = glatt.isSelected(); anzeige.optionenAnwenden(); });
        seiten = Theme.haken("Seitenverhältnis halten", e.seitenverhaeltnis);
        seiten.addActionListener(a -> { e.seitenverhaeltnis = seiten.isSelected(); anzeige.optionenAnwenden(); });
        vordergrund = Theme.haken("Immer im Vordergrund", e.vordergrund);
        vordergrund.addActionListener(a -> { e.vordergrund = vordergrund.isSelected(); anzeige.optionenAnwenden(); });
        ohneRahmen = Theme.haken("Ohne Fensterrahmen", e.ohneRahmen);
        ohneRahmen.addActionListener(a -> { e.ohneRahmen = ohneRahmen.isSelected(); anzeige.optionenAnwenden(); });
        for (JCheckBox c : new JCheckBox[]{glatt, seiten, vordergrund, ohneRahmen}) {
            p.add(links(c));
            p.add(Box.createVerticalStrut(4));
        }
        p.add(Box.createVerticalStrut(14));

        JComponent linie = new JComponent() {
            @Override protected void paintComponent(Graphics g) { g.setColor(Theme.LINE); g.fillRect(0, 0, getWidth(), 1); }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 1); }
            @Override public Dimension getPreferredSize() { return new Dimension(10, 1); }
        };
        linie.setAlignmentX(0);
        p.add(linie);
        p.add(Box.createVerticalStrut(8));
        p.add(links(status));
        p.add(links(Theme.label("<html>Anzeige: Doppelklick = Vollbild, Rechtsklick = Menü.<br>"
                + "Spiel im randlosen Fenstermodus laufen lassen.</html>", Theme.body.deriveFont(11.5f), Theme.DIM)));

        frame.setContentPane(p);
        frame.setIconImages(icons());
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent ev) {
                e.speichern();
                System.exit(0);
            }
        });
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private void bereichWaehlen() {
        boolean warSichtbar = anzeige.sichtbar();
        anzeige.verstecken();
        frame.setState(Frame.ICONIFIED);
        // kurz warten, bis beide Fenster wirklich weg sind, dann Standbild machen
        Timer t = new Timer(250, a -> Auswahl.starten(robot, e.bereich, r -> {
            if (r != null) {
                e.bereich = r;
                e.speichern();
            }
            frame.setState(Frame.NORMAL);
            if (r != null || warSichtbar) anzeige.zeigen();
            aktualisieren();
        }));
        t.setRepeats(false);
        t.start();
    }

    private void aktualisieren() {
        Rectangle r = e.bereich;
        bereichInfo.setText(r == null ? "Noch kein Bereich gewählt."
                : r.width + " × " + r.height + " px  bei  (" + r.x + ", " + r.y + ")");
        zeigenKnopf.setText(anzeige.sichtbar() ? "Anzeige verstecken" : "Anzeige zeigen");
        vollbildKnopf.setText(e.vollbild ? "Vollbild beenden" : "Vollbild");
        glatt.setSelected(e.glatt);
        seiten.setSelected(e.seitenverhaeltnis);
        vordergrund.setSelected(e.vordergrund);
        ohneRahmen.setSelected(e.ohneRahmen);
        status.setText(anzeige.sichtbar() ? String.format("Läuft · %.0f Bilder/s", anzeige.fps()) : "Anzeige aus");
    }

    private static JPanel reihe(JComponent... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setOpaque(false);
        p.setAlignmentX(0);
        for (int i = 0; i < cs.length; i++) {
            if (i > 0) p.add(Box.createHorizontalStrut(8));
            p.add(cs[i]);
        }
        return p;
    }

    private static JComponent links(JComponent c) {
        c.setAlignmentX(0);
        return c;
    }

    /** Programm-Icon: Lupe in den Theme-Farben. */
    static List<Image> icons() {
        return List.of(icon(16), icon(32), icon(64));
    }

    private static Image icon(int s) {
        BufferedImage b = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.BG);
        g.fillRect(0, 0, s, s);
        float k = s / 16f;
        g.setStroke(new BasicStroke(2.2f * k, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.setColor(Theme.ACC2);
        g.drawLine(Math.round(10 * k), Math.round(10 * k), Math.round(14 * k), Math.round(14 * k));
        g.setColor(Theme.ACC);
        g.drawRect(Math.round(2 * k), Math.round(2 * k), Math.round(8 * k), Math.round(8 * k));
        g.dispose();
        return b;
    }

    // ---- Selbsttest (ohne Bildschirm) ------------------------------------------------------

    private static void selbsttest() throws Exception {
        int fehler = 0;
        fehler += pruefe("aufspannen", Auswahl.aufspannen(new Point(50, 80), new Point(10, 20)).equals(new Rectangle(10, 20, 40, 60)));
        fehler += pruefe("einpassen quadratisch", Anzeige.einpassen(100, 100, 400, 300, true).equals(new Rectangle(50, 0, 300, 300)));
        fehler += pruefe("einpassen gestreckt", Anzeige.einpassen(100, 100, 400, 300, false).equals(new Rectangle(0, 0, 400, 300)));
        fehler += pruefe("rect parsen", new Rectangle(-1920, 5, 280, 280).equals(Einstellungen.rect("-1920,5,280,280")));
        fehler += pruefe("rect kaputt", Einstellungen.rect("1,2,x,4") == null && Einstellungen.rect("1,2,0,4") == null);

        Path tmp = Files.createTempFile("lupe", ".properties");
        Einstellungen a = new Einstellungen(tmp);
        a.bereich = new Rectangle(1600, 780, 300, 300);
        a.anzeige = new Rectangle(2000, 100, 800, 800);
        a.fps = 60; a.glatt = false; a.ohneRahmen = true; a.vollbild = true;
        a.speichern();
        Einstellungen b = new Einstellungen(tmp);
        b.laden();
        fehler += pruefe("einstellungen", a.bereich.equals(b.bereich) && a.anzeige.equals(b.anzeige)
                && b.fps == 60 && !b.glatt && b.ohneRahmen && b.vollbild && b.vordergrund);
        Files.deleteIfExists(tmp);

        Theme.ladeSchriften();
        fehler += pruefe("schriften", !Theme.head.getFamily().equals(Font.SANS_SERIF) && !Theme.body.getFamily().equals(Font.MONOSPACED));

        System.out.println(fehler == 0 ? "ALLES OK" : fehler + " FEHLER");
        if (fehler > 0) System.exit(1);
    }

    private static int pruefe(String name, boolean ok) {
        System.out.println((ok ? "ok    " : "FEHLER") + "  " + name);
        return ok ? 0 : 1;
    }
}
