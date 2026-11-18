package lupe;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.InputStream;

/** Farben und Schriften für Swing. */
final class Theme {
    static final Color BG = new Color(0x141219);
    static final Color PANEL = new Color(0x1c1924);
    static final Color PANEL2 = new Color(0x241f2e);
    static final Color LINE = new Color(0x332c40);
    static final Color TXT = new Color(0xe9e4f0);
    static final Color DIM = new Color(0x8f8a9c);
    static final Color ACC = new Color(0xb496ea);
    static final Color ACC_DEEP = new Color(0x7d5bc4);
    static final Color ACC2 = new Color(0x3ccbb5);
    static final Color DOWN = new Color(0xe2563f);

    static Font head = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    static Font body = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    static Font bodyBold = new Font(Font.MONOSPACED, Font.BOLD, 13);

    private Theme() {}

    static void ladeSchriften() {
        Font h = lade("BarlowCondensed-700-latin.ttf");
        Font b = lade("IBMPlexMono-400-latin.ttf");
        Font bb = lade("IBMPlexMono-600-latin.ttf");
        if (h != null) head = h.deriveFont(Font.PLAIN, 20f);
        if (b != null) body = b.deriveFont(Font.PLAIN, 13f);
        if (bb != null) bodyBold = bb.deriveFont(Font.PLAIN, 13f);
    }

    private static Font lade(String name) {
        try (InputStream in = Theme.class.getResourceAsStream("/" + name)) {
            if (in == null) return null;
            Font f = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    /** Überschrift: Barlow Condensed, Großbuchstaben, Flieder. */
    static JLabel titel(String text, float size) {
        return label(text.toUpperCase(), head.deriveFont(size), ACC);
    }

    /** Kantiger Knopf; primary = Flieder gefüllt. */
    static JButton knopf(String text, boolean primary) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                boolean hover = getModel().isRollover() || getModel().isPressed();
                Color bg = primary ? (hover ? ACC_DEEP : ACC) : PANEL;
                Color rand = primary ? bg : (hover ? ACC : LINE);
                g.setColor(bg);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(rand);
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                setForeground(primary && !hover ? BG : TXT);
                super.paintComponent(g);
            }
        };
        b.setFont(bodyBold);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setBorder(new EmptyBorder(7, 14, 7, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Kantige Checkbox mit Türkis-Haken. */
    static JCheckBox haken(String text, boolean an) {
        JCheckBox c = new JCheckBox(text, an);
        c.setFont(body);
        c.setForeground(TXT);
        c.setOpaque(false);
        c.setFocusPainted(false);
        c.setIconTextGap(10);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.setIcon(new KastenIcon(false));
        c.setSelectedIcon(new KastenIcon(true));
        c.setRolloverIcon(new KastenIcon(false));
        return c;
    }

    private record KastenIcon(boolean an) implements Icon {
        public int getIconWidth() { return 14; }
        public int getIconHeight() { return 14; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(BG);
            g.fillRect(x, y, 14, 14);
            g.setColor(an ? ACC2 : LINE);
            g.drawRect(x, y, 13, 13);
            if (an) g.fillRect(x + 4, y + 4, 6, 6);
        }
    }

    /** Gruppe von Auswahl-Chips (z. B. Bilder pro Sekunde). */
    static JPanel chips(int[] werte, int aktuell, String suffix, java.util.function.IntConsumer onWahl) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        ButtonGroup grp = new ButtonGroup();
        for (int w : werte) {
            JToggleButton t = new JToggleButton(w + suffix, w == aktuell) {
                @Override protected void paintComponent(Graphics g) {
                    boolean sel = isSelected();
                    g.setColor(sel ? PANEL2 : PANEL);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(sel ? ACC2 : (getModel().isRollover() ? ACC : LINE));
                    g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    setForeground(sel ? ACC2 : TXT);
                    super.paintComponent(g);
                }
            };
            t.setFont(bodyBold);
            t.setContentAreaFilled(false);
            t.setFocusPainted(false);
            t.setBorderPainted(false);
            t.setRolloverEnabled(true);
            t.setBorder(new EmptyBorder(4, 10, 4, 10));
            t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            t.addActionListener(e -> onWahl.accept(w));
            grp.add(t);
            p.add(t);
        }
        return p;
    }

    /** Abschnittsüberschrift mit Linie danach. */
    static JComponent abschnitt(String text) {
        JPanel p = new JPanel(new BorderLayout(12, 0)) {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.add(label(text.toUpperCase(), head.deriveFont(15f), ACC), BorderLayout.WEST);
        JComponent linie = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(LINE);
                g.fillRect(0, getHeight() / 2, getWidth(), 1);
            }
        };
        p.add(linie, BorderLayout.CENTER);
        p.setAlignmentX(0);
        return p;
    }

    static void kontextMenueStil(JPopupMenu m) {
        m.setBackground(PANEL);
        m.setBorder(new LineBorder(LINE));
        for (Component c : m.getComponents()) {
            if (c instanceof JMenuItem i) {
                i.setFont(body);
                i.setBackground(PANEL);
                i.setForeground(TXT);
                i.setOpaque(true);
                i.setBorder(new EmptyBorder(5, 10, 5, 14));
                i.addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { i.setBackground(PANEL2); }
                    @Override public void mouseExited(MouseEvent e) { i.setBackground(PANEL); }
                });
            }
        }
    }
}
