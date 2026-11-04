package slatefinder;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * "Test coordinates": type a spot and it looks around it on the map and writes what you could have described to get
 * exactly that spot. The text is read and searched for real before it is shown, so pasting it into the search finds it.
 */
final class ReverseDialog extends JDialog {
    private final BiomeGrid grid;
    private final IntSupplier radius;
    private final Consumer<String> use;
    private final JTextField fx = field(), fz = field();
    private final JTextArea out = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final JLabel verdict = new JLabel(" ");
    private final JPanel steps = new JPanel();
    private final Theme.Btn go;
    private final Theme.Btn copy, useBtn;
    private volatile boolean cancelled;
    private volatile boolean running;

    ReverseDialog(Component parent, BiomeGrid grid, IntSupplier radius, Consumer<String> use) {
        super(SwingUtilities.getWindowAncestor(parent), "Test coordinates", ModalityType.MODELESS);
        this.grid = grid;
        this.radius = radius;
        this.use = use;
        getContentPane().setBackground(Theme.BG);
        JPanel col = new DescribePanel.Column();
        col.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel intro = new JLabel("<html><body style='width:350px'>Type a spot. I look around it on your biome map and write down what you could have said to get exactly that place. "
            + "Paste the text into the search box and it finds the same spot.</body></html>");
        intro.setFont(Theme.UI);
        intro.setForeground(Theme.MUTED);
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(intro);
        col.add(Box.createVerticalStrut(12));

        Theme.Card c1 = card("Coordinates");
        JPanel in = stack();
        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.add(new DescribePanel.RoundBox(fx, 8));
        row.add(new DescribePanel.RoundBox(fz, 8));
        in.add(Theme.label("X                                              Z       (you can paste  1200 ~ -3400  into the first box)", Theme.SMALL, Theme.DIM));
        in.add(Box.createVerticalStrut(4));
        in.add(row);
        in.add(Box.createVerticalStrut(10));
        go = new Theme.Btn("What would describe this?", true).on(this::run);
        go.setAlignmentX(Component.LEFT_ALIGNMENT);
        go.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        go.setPreferredSize(new Dimension(100, 36));
        in.add(go);
        in.add(Box.createVerticalStrut(6));
        status.setFont(Theme.SMALL);
        status.setForeground(Theme.DIM);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        in.add(status);
        c1.add(in, BorderLayout.CENTER);
        col.add(c1);
        col.add(Box.createVerticalStrut(10));

        Theme.Card c2 = card("What you could say");
        JPanel in2 = stack();
        out.setLineWrap(true);
        out.setWrapStyleWord(true);
        out.setEditable(true);
        out.setOpaque(false);
        out.setForeground(Theme.TEXT);
        out.setCaretColor(Skin.accent());
        out.setFont(Theme.UI.deriveFont(14f));
        out.setBorder(new EmptyBorder(10, 12, 10, 12));
        out.setRows(5);
        DescribePanel.RoundBox ob = new DescribePanel.RoundBox(new JScrollPane(out, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {{ setBorder(null); setOpaque(false); getViewport().setOpaque(false); }}, 10);
        ob.setAlignmentX(Component.LEFT_ALIGNMENT);
        ob.setPreferredSize(new Dimension(100, 130));
        ob.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        in2.add(ob);
        in2.add(Box.createVerticalStrut(8));
        verdict.setFont(Theme.UI_BOLD);
        verdict.setForeground(Theme.MUTED);
        verdict.setAlignmentX(Component.LEFT_ALIGNMENT);
        in2.add(verdict);
        in2.add(Box.createVerticalStrut(8));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy = new Theme.Btn("Copy the text", false).on(() -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(out.getText()), null);
            Toast.show(this, "Copied  —  paste it into the search box", Skin.accent());
        });
        copy.setPreferredSize(new Dimension(130, 34));
        useBtn = new Theme.Btn("Use it in the search", true).on(() -> { use.accept(out.getText()); Toast.show(this, "Put in the search box", Skin.accent()); });
        useBtn.setPreferredSize(new Dimension(170, 34));
        btns.add(copy);
        btns.add(useBtn);
        in2.add(btns);
        c2.add(in2, BorderLayout.CENTER);
        col.add(c2);
        col.add(Box.createVerticalStrut(10));

        Theme.Card c3 = card("How it got there");
        steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
        steps.setOpaque(false);
        c3.add(steps, BorderLayout.CENTER);
        col.add(c3);

        JScrollPane sp = new JScrollPane(col, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(Theme.BG);
        DescribePanel.slim(sp.getVerticalScrollBar(), 8);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        setContentPane(sp);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(500, 700);
        setLocationRelativeTo(parent);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { cancelled = true; dispose(); } });
        addWindowListener(new java.awt.event.WindowAdapter() { public void windowClosing(java.awt.event.WindowEvent e) { cancelled = true; } });
        for (JTextField f : new JTextField[]{fx, fz}) f.addActionListener(e -> run());
        setEnabledResult(false);
    }

    private void setEnabledResult(boolean on) { copy.setEnabled(on); useBtn.setEnabled(on); }

    /** For tests: runs with these coordinates and waits for the answer. */
    Reverse.Report runNow(int x, int z) {
        return Reverse.describe(grid, x, z, radius.getAsInt(), null, null);
    }

    private static int[] parse(String a, String b) {
        // "1200 ~ -3400", "1200, -3400", "1200 -3400" typed or pasted in the first box
        String s = a.trim();
        String[] parts = s.split("[\\s,;~/]+");
        try {
            if (parts.length >= 2 && !b.trim().matches("-?[\\d,]+")) return new int[]{Integer.parseInt(parts[0].replace(",", "")), Integer.parseInt(parts[1].replace(",", ""))};
            if (parts.length >= 2 && b.trim().isEmpty()) return new int[]{Integer.parseInt(parts[0].replace(",", "")), Integer.parseInt(parts[1].replace(",", ""))};
            return new int[]{Integer.parseInt(s.replace(",", "")), Integer.parseInt(b.trim().replace(",", ""))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    String resultText() { return out.getText(); }
    boolean isRunning() { return running; }

    /** Starts the search for the coordinates in the boxes. */
    void start() { run(); }

    private void run() {
        if (running) { cancelled = true; return; }
        int[] xz = parse(fx.getText(), fz.getText());
        if (xz == null) { Toast.show(this, "Type two whole numbers: X and Z", Theme.WARN); return; }
        fx.setText(String.valueOf(xz[0]));
        fz.setText(String.valueOf(xz[1]));
        if (grid == null) { Toast.show(this, "The biome map is not loaded yet", Theme.WARN); return; }
        running = true;
        cancelled = false;
        go.text = "Stop"; go.repaint();
        setEnabledResult(false);
        out.setText("");
        verdict.setText(" ");
        verdict.setForeground(Theme.MUTED);
        steps.removeAll();
        status.setText("Looking around…");
        final int x = xz[0], z = xz[1];
        Thread t = new Thread(() -> {
            Reverse.Report rep;
            try {
                rep = Reverse.describe(grid, x, z, radius.getAsInt(), () -> cancelled, s -> SwingUtilities.invokeLater(() -> status.setText(s.length() > 90 ? s.substring(0, 88) + "…" : s)));
            } catch (RuntimeException | Error e) {
                rep = new Reverse.Report();
                rep.note = "Something went wrong: " + e;
            }
            final Reverse.Report r = rep;
            SwingUtilities.invokeLater(() -> show(r, x, z));
        }, "reverse-describe");
        t.setDaemon(true);
        t.start();
    }

    private void show(Reverse.Report r, int x, int z) {
        running = false;
        go.text = "What would describe this?"; go.repaint();
        status.setText(" ");
        if (cancelled && r.text.isEmpty()) { verdict.setText("Stopped."); return; }
        out.setText(r.text);
        out.setCaretPosition(0);
        setEnabledResult(!r.text.isEmpty());
        if (r.ok) {
            verdict.setText("<html><body style='width:350px'>\u2713  " + esc(r.note) + "</body></html>");
            verdict.setForeground(Theme.GOOD);
        } else {
            verdict.setText("<html><body style='width:350px'>" + esc(r.note.isEmpty() ? "Nothing found." : r.note) + "</body></html>");
            verdict.setForeground(Theme.WARN);
        }
        steps.removeAll();
        for (int i = 0; i < r.steps.size(); i++) {
            String s = r.steps.get(i);
            String added = i == 0 ? "start with the biome you are in" : i <= r.used.size() ? "then add the detail that narrows it down" : "then make every distance as small as it can be";
            JLabel l = new JLabel("<html><body style='width:350px'><b>" + String.format("%,d", r.stepPlaces.get(i)) + "</b> place" + (r.stepPlaces.get(i) == 1 ? "" : "s") + " &nbsp;<span style='color:#8a96a3'>" + added + "</span></body></html>");
            l.setFont(Theme.SMALL);
            l.setForeground(Theme.TEXT);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            steps.add(l);
            steps.add(Box.createVerticalStrut(4));
        }
        if (!r.used.isEmpty()) {
            steps.add(Box.createVerticalStrut(6));
            for (Reverse.Near n : r.used) {
                JLabel l = new JLabel("<html><body style='width:350px'>" + esc(n.name) + (n.structure ? " (listed position)" : "") + "  \u2014  within " + n.said + " blocks</body></html>");
                l.setFont(Theme.SMALL);
                l.setForeground(Theme.MUTED);
                l.setAlignmentX(Component.LEFT_ALIGNMENT);
                steps.add(l);
                steps.add(Box.createVerticalStrut(3));
            }
        }
        steps.revalidate();
        steps.repaint();
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    void prefill(int x, int z) { fx.setText(String.valueOf(x)); fz.setText(String.valueOf(z)); }

    private static JTextField field() {
        JTextField f = new JTextField();
        f.setOpaque(false);
        f.setBorder(new EmptyBorder(6, 10, 6, 10));
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Skin.accent());
        f.setFont(Theme.UI.deriveFont(14f));
        return f;
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
}
