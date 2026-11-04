package slatefinder;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** The Describing tab's settings: colours, picture sizes, the map's defaults, and the name used in /tp. */
final class SettingsDialog extends JDialog {
    SettingsDialog(Component parent) {
        super(SwingUtilities.getWindowAncestor(parent), "Describing finder settings", ModalityType.MODELESS);
        getContentPane().setBackground(Theme.BG);
        JPanel col = new DescribePanel.Column();
        col.setBorder(new EmptyBorder(14, 14, 14, 6));

        // ------------------------------------------------------------ look
        Theme.Card look = card("Look");
        JPanel in = stack();
        in.add(Theme.label("Accent colour", Theme.UI, Theme.MUTED));
        in.add(Box.createVerticalStrut(8));
        in.add(swatches());
        in.add(Box.createVerticalStrut(14));
        in.add(Theme.label("Size of the pictures in the book", Theme.UI, Theme.MUTED));
        in.add(Box.createVerticalStrut(8));
        String[] sizes = {"small", "medium", "large"};
        int cur = Math.max(0, java.util.Arrays.asList(sizes).indexOf(Settings.get("cardSize", "medium")));
        in.add(new Ui.Seg(new String[]{"Small", "Medium", "Large"}, cur, i -> Settings.set("cardSize", sizes[i])));
        in.add(Box.createVerticalStrut(12));
        in.add(new Ui.Toggle("Smooth animations", Settings.getBool("animations", true), v -> Settings.set("animations", v)));
        in.add(new Ui.Toggle("Show tips under the text box", Settings.getBool("tips", true), v -> Settings.set("tips", v)));
        look.add(in, BorderLayout.CENTER);
        col.add(look);
        col.add(Box.createVerticalStrut(10));

        // ------------------------------------------------------------ player
        Theme.Card who = card("Teleport command");
        JPanel in2 = stack();
        in2.add(Theme.label("Your player name, used in the /tp that gets copied", Theme.UI, Theme.MUTED));
        in2.add(Box.createVerticalStrut(8));
        JTextField name = new JTextField(Settings.player());
        name.setOpaque(false);
        name.setForeground(Theme.TEXT);
        name.setCaretColor(Skin.accent());
        name.setFont(Theme.UI);
        name.setBorder(new EmptyBorder(6, 10, 6, 10));
        name.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void save() { Settings.set("player", name.getText().trim()); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { save(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { save(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { save(); }
        });
        DescribePanel.RoundBox nb = new DescribePanel.RoundBox(name, 10);
        nb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        nb.setPreferredSize(new Dimension(100, 38));
        nb.setAlignmentX(Component.LEFT_ALIGNMENT);
        in2.add(nb);
        who.add(in2, BorderLayout.CENTER);
        col.add(who);
        col.add(Box.createVerticalStrut(10));

        // ------------------------------------------------------------ the map
        Theme.Card map = card("The map");
        JPanel in3 = stack();
        in3.add(Theme.label("Opens in", Theme.UI, Theme.MUTED));
        in3.add(Box.createVerticalStrut(8));
        String[] modes = {"3d", "walk", "map"};
        int mi = Math.max(0, java.util.Arrays.asList(modes).indexOf(Settings.get("mapMode", "3d")));
        in3.add(new Ui.Seg(new String[]{"3D view", "Stand there", "Top-down"}, mi, i -> Settings.set("mapMode", modes[i])));
        in3.add(Box.createVerticalStrut(12));
        in3.add(new Ui.Slider("Time of day", 8, 80, Settings.getInt("sun", 52), v -> v < 20 ? "dawn" : v < 62 ? "day" : "noon", v -> Settings.set("sun", (int) (double) v)));
        in3.add(new Ui.Slider("How far you can see", 150, 1100, Settings.getInt("distance", 520), v -> String.format("%,.0f blocks", v), v -> Settings.set("distance", (int) (double) v)));
        in3.add(new Ui.Slider("Detail", 1, 3, Settings.getInt("detail", 2), v -> v < 1.5 ? "fast" : v < 2.5 ? "balanced" : "sharp", v -> Settings.set("detail", (int) Math.round(v))));
        in3.add(new Ui.Toggle("Shadows", Settings.getBool("shadows", true), v -> Settings.set("shadows", v)));
        in3.add(new Ui.Toggle("Soft corners", Settings.getBool("ao", true), v -> Settings.set("ao", v)));
        in3.add(new Ui.Toggle("Villages and structures", Settings.getBool("showSites", true), v -> Settings.set("showSites", v)));
        map.add(in3, BorderLayout.CENTER);
        col.add(map);
        col.add(Box.createVerticalStrut(10));

        // ------------------------------------------------------------ the pictures
        Theme.Card pics = card("Pictures");
        JPanel in4 = stack();
        in4.add(new Ui.Toggle("3D pictures in the book", Settings.getBool("pics3d", true), v -> Settings.set("pics3d", v)));
        in4.add(Box.createVerticalStrut(6));
        JLabel note = new JLabel("<html><body style='width:300px'>The 3D pictures are drawn the first time the tab opens (a few seconds) and kept, so they appear instantly after that. Turn this off to keep the flat paintings.</body></html>");
        note.setFont(Theme.SMALL);
        note.setForeground(Theme.DIM);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        in4.add(note);
        pics.add(in4, BorderLayout.CENTER);
        col.add(pics);
        col.add(Box.createVerticalStrut(14));

        // ------------------------------------------------------------ the exact world and the disk
        Theme.Card exact = card("Exact world");
        JPanel in5 = stack();
        in5.add(new Ui.Toggle("Open a saved exact place automatically", Settings.getBool("exactAuto", true), v -> Settings.set("exactAuto", v)));
        in5.add(Box.createVerticalStrut(6));
        in5.add(Theme.label("How much around the spot is made exact", Theme.UI, Theme.MUTED));
        in5.add(Box.createVerticalStrut(8));
        int[] radii = {160, 288, 448};
        int ri = 1;
        for (int i = 0; i < radii.length; i++) if (Math.abs(radii[i] - ExactJob.radius()) < Math.abs(radii[ri] - ExactJob.radius())) ri = i;
        in5.add(new Ui.Seg(new String[]{"Small", "Normal", "Large"}, ri, i -> Settings.set("exactRadius", radii[i])));
        JLabel rn = new JLabel("<html><body style='width:300px'>Small is about 320 blocks across and takes a minute or two; Large is about 900 blocks across and takes much longer and more disk.</body></html>");
        rn.setFont(Theme.SMALL);
        rn.setForeground(Theme.DIM);
        rn.setAlignmentX(Component.LEFT_ALIGNMENT);
        in5.add(Box.createVerticalStrut(6));
        in5.add(rn);
        exact.add(in5, BorderLayout.CENTER);
        col.add(exact);
        col.add(Box.createVerticalStrut(10));

        Theme.Card disk = card("Storage for the maps");
        disk.add(new StoragePanel(this), BorderLayout.CENTER);
        col.add(disk);
        col.add(Box.createVerticalStrut(14));

        Theme.Btn reset = new Theme.Btn("Put everything back to normal", false).on(() -> {
            for (String k : new String[]{"accent", "cardSize", "animations", "tips", "player", "mapMode", "sun", "distance", "detail", "shadows", "ao", "showSites", "pics3d", "exactAuto", "exactRadius"}) Settings.set(k, defaults(k));
            dispose();
        });
        reset.setPreferredSize(new Dimension(100, 34));
        reset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        reset.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(reset);

        JScrollPane sp = new JScrollPane(col, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(Theme.BG);
        DescribePanel.slim(sp.getVerticalScrollBar(), 8);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        setContentPane(sp);
        sp.setBackground(Theme.BG);
        setSize(460, 860);
        setLocationRelativeTo(parent);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { dispose(); } });
    }

    private static String defaults(String k) {
        switch (k) {
            case "accent": return "Cyan"; case "cardSize": return "medium"; case "player": return "vejasss"; case "mapMode": return "3d";
            case "sun": return "52"; case "distance": return "520"; case "detail": return "2"; case "exactRadius": return "288";
            default: return "true";
        }
    }

    private static Theme.Card card(String title) {
        Theme.Card c = new Theme.Card(title, new BorderLayout());
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JPanel stack() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    private static JComponent swatches() {
        return new JComponent() {
            { setPreferredSize(new Dimension(300, 40)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 40)); setAlignmentX(Component.LEFT_ALIGNMENT);
              setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { int i = e.getX() / 46; if (i >= 0 && i < Skin.NAMES.length) { Settings.set("accent", Skin.NAMES[i]); repaint(); } } }); }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.smooth(g0);
                String cur = Settings.get("accent", "Cyan");
                int[] c = {0x4FD1E8, 0x4ADE80, 0xA78BFA, 0xFB923C, 0xF472B6, 0x60A5FA, 0xFACC15};
                for (int i = 0; i < c.length; i++) {
                    int x = i * 46;
                    g.setColor(new Color(c[i]));
                    g.fillOval(x + 4, 6, 28, 28);
                    if (Skin.NAMES[i].equalsIgnoreCase(cur)) { g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2.4f)); g.drawOval(x + 1, 3, 34, 34); }
                }
            }
        };
    }
}
