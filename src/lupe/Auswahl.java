package lupe;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Legt über jeden Bildschirm ein Standbild (abgedunkelt). Mit der Maus ein Rechteck ziehen,
 * loslassen übernimmt den Bereich. Esc oder Rechtsklick bricht ab.
 */
final class Auswahl {
    private final List<JWindow> fenster = new ArrayList<>();
    private final Consumer<Rectangle> fertig;
    private final Rectangle alt;
    private boolean beendet;

    private Auswahl(Rectangle alt, Consumer<Rectangle> fertig) {
        this.alt = alt;
        this.fertig = fertig;
    }

    /** fertig bekommt den neuen Bereich in globalen Bildschirmkoordinaten oder null bei Abbruch. */
    static void starten(Robot robot, Rectangle alt, Consumer<Rectangle> fertig) {
        Auswahl a = new Auswahl(alt, fertig);
        for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle b = gd.getDefaultConfiguration().getBounds();
            BufferedImage bild = robot.createScreenCapture(b);
            a.oeffne(gd, b, bild);
        }
        if (!a.fenster.isEmpty()) a.fenster.get(0).toFront();
    }

    private void oeffne(GraphicsDevice gd, Rectangle b, BufferedImage bild) {
        JWindow w = new JWindow(gd.getDefaultConfiguration());
        Flaeche f = new Flaeche(b, bild);
        w.setContentPane(f);
        w.setBounds(b);
        w.setAlwaysOnTop(true);
        w.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        fenster.add(w);
        w.setVisible(true);
        w.requestFocus();
        f.requestFocusInWindow();
    }

    private void ende(Rectangle r) {
        if (beendet) return;
        beendet = true;
        for (JWindow w : fenster) w.dispose();
        fertig.accept(r);
    }

    /** Rechteck aus zwei Eckpunkten, immer positive Breite/Höhe. */
    static Rectangle aufspannen(Point a, Point b) {
        int x = Math.min(a.x, b.x), y = Math.min(a.y, b.y);
        return new Rectangle(x, y, Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    private final class Flaeche extends JComponent {
        private final Rectangle schirm;
        private final BufferedImage bild;
        private Point start, jetzt;

        Flaeche(Rectangle schirm, BufferedImage bild) {
            this.schirm = schirm;
            this.bild = bild;
            setFocusable(true);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) { ende(null); return; }
                    start = e.getPoint();
                    jetzt = start;
                    repaint();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (start == null) return;
                    jetzt = e.getPoint();
                    if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) { // Shift = Quadrat
                        int s = Math.max(Math.abs(jetzt.x - start.x), Math.abs(jetzt.y - start.y));
                        jetzt = new Point(start.x + Integer.signum(jetzt.x - start.x) * s,
                                start.y + Integer.signum(jetzt.y - start.y) * s);
                    }
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (start == null) return;
                    Rectangle r = aufspannen(start, jetzt);
                    start = null;
                    if (r.width < 8 || r.height < 8) { repaint(); return; } // versehentlicher Klick
                    r.translate(schirm.x, schirm.y);
                    ende(r);
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) ende(null);
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            int w = getWidth(), h = getHeight();
            g.drawImage(bild, 0, 0, w, h, null);
            g.setColor(new Color(20, 18, 25, 150));
            Rectangle r = start != null ? aufspannen(start, jetzt) : null;
            if (r == null) {
                g.fillRect(0, 0, w, h);
            } else { // alles außer der Auswahl abdunkeln
                g.fillRect(0, 0, w, r.y);
                g.fillRect(0, r.y + r.height, w, h - r.y - r.height);
                g.fillRect(0, r.y, r.x, r.height);
                g.fillRect(r.x + r.width, r.y, w - r.x - r.width, r.height);
            }
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (alt != null && r == null) {
                Rectangle a = new Rectangle(alt);
                a.translate(-schirm.x, -schirm.y);
                g.setColor(Theme.DIM);
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
                g.drawRect(a.x, a.y, a.width - 1, a.height - 1);
                g.setStroke(new BasicStroke(1f));
            }
            if (r != null) {
                g.setColor(Theme.ACC2);
                g.setStroke(new BasicStroke(2f));
                g.drawRect(r.x, r.y, r.width, r.height);
                String mass = r.width + " × " + r.height;
                g.setFont(Theme.bodyBold);
                FontMetrics fm = g.getFontMetrics();
                int tx = r.x, ty = r.y - 8;
                if (ty - fm.getAscent() < 0) ty = r.y + r.height + fm.getAscent() + 6;
                g.setColor(Theme.BG);
                g.fillRect(tx, ty - fm.getAscent() - 3, fm.stringWidth(mass) + 12, fm.getHeight() + 4);
                g.setColor(Theme.ACC2);
                g.drawString(mass, tx + 6, ty);
            }
            // Hinweis oben mittig
            String t1 = "BEREICH AUFZIEHEN";
            String t2 = "Maus ziehen · Shift = Quadrat · Esc / Rechtsklick = Abbrechen";
            Font hf = Theme.head.deriveFont(26f);
            g.setFont(hf);
            int bw = Math.max(g.getFontMetrics().stringWidth(t1), g.getFontMetrics(Theme.body).stringWidth(t2)) + 40;
            int bx = (w - bw) / 2, by = 30;
            g.setColor(Theme.PANEL);
            g.fillRect(bx, by, bw, 70);
            g.setColor(Theme.LINE);
            g.drawRect(bx, by, bw - 1, 69);
            g.setColor(Theme.ACC);
            g.drawString(t1, bx + 20, by + 32);
            g.setFont(Theme.body);
            g.setColor(Theme.DIM);
            g.drawString(t2, bx + 20, by + 54);
        }
    }
}
