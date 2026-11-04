package slatefinder;

import java.awt.*;
import java.io.File;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * How much disk the maps use, the limit the person chose, and the buttons to empty it. Used in the settings window; it
 * keeps itself up to date while it is on screen.
 */
final class StoragePanel extends JPanel {
    private final Ui.Meter meter = new Ui.Meter();
    private final JLabel headline = new JLabel(" ");
    private final JLabel breakdown = new JLabel(" ");
    private final JLabel warn = new JLabel(" ");
    private Theme.Btn wipe, dropServer;
    private javax.swing.Timer timer;
    private volatile long places, server, pictures;
    private volatile boolean busy;

    StoragePanel(Component owner) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        headline.setFont(Theme.UI_BOLD);
        headline.setForeground(Theme.TEXT);
        headline.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(headline);
        add(Box.createVerticalStrut(6));
        meter.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(meter);
        add(Box.createVerticalStrut(6));
        breakdown.setFont(Theme.SMALL);
        breakdown.setForeground(Theme.MUTED);
        breakdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(breakdown);
        warn.setFont(Theme.SMALL);
        warn.setForeground(Theme.WARN);
        warn.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(warn);
        add(Box.createVerticalStrut(12));
        add(new Ui.Slider("Most the app may use", 1, 200, ExactStore.capGb(), v -> (int) Math.round(v) + " GB", v -> { ExactStore.setCapGb((int) Math.round(v)); showSizes(); }));
        JLabel hint = new JLabel("<html><body style='width:300px'>When it is full, the places you looked at longest ago are deleted first to make room for new ones.</body></html>");
        hint.setFont(Theme.SMALL);
        hint.setForeground(Theme.DIM);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(hint);
        add(Box.createVerticalStrut(12));

        wipe = new Theme.Btn("Delete the memory of the maps", false).on(() -> {
            long freeable = places + pictures;
            int ok = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(owner),
                "<html><body style='width:300px'>Delete every saved exact place and the 3D pictures of the book?<br><br>That gives back " + ExactStore.human(freeable)
                    + ". Nothing else is touched. A place is made again the next time you open it, and the pictures are drawn again when the tab opens.</body></html>",
                "Delete the memory of the maps", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            wipe.setEnabled(false);
            new Thread(() -> {
                long freed = ExactStore.deleteMaps();
                SwingUtilities.invokeLater(() -> { Toast.show(owner, "Freed " + ExactStore.human(freed), Skin.accent()); wipe.setEnabled(true); load(); });
            }, "delete-maps").start();
        });
        wipe.setPreferredSize(new Dimension(100, 34));
        wipe.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        wipe.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(wipe);
        add(Box.createVerticalStrut(8));
        dropServer = new Theme.Btn("Remove the downloaded Minecraft server", false).on(() -> {
            long freed = ExactStore.deleteServer();
            Toast.show(owner, "Freed " + ExactStore.human(freed) + " (it downloads again if you ask for an exact place)", Skin.accent());
            load();
        });
        dropServer.setPreferredSize(new Dimension(100, 34));
        dropServer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        dropServer.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(dropServer);
        add(Box.createVerticalStrut(8));
        Theme.Btn open = new Theme.Btn("Show the folder", false).on(() -> {
            try { File d = ExactStore.appDir(); d.mkdirs(); Desktop.getDesktop().open(d); } catch (Exception e) { Toast.show(owner, "Could not open the folder: " + e.getMessage(), Theme.WARN); }
        });
        open.setPreferredSize(new Dimension(100, 30));
        open.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        open.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(open);
        timer = new javax.swing.Timer(2500, e -> load());
        load();
    }

    @Override public void addNotify() { super.addNotify(); timer.start(); load(); }
    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

    /** Measures the folders (in the background, it can take a moment), then shows them. */
    void load() {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            try { places = ExactStore.placesBytes(); server = ExactStore.serverBytes(); pictures = ExactStore.picturesBytes(); }
            finally { busy = false; }
            SwingUtilities.invokeLater(this::showSizes);
        }, "storage-size").start();
    }

    private void showSizes() {
        long used = places + server + pictures, cap = ExactStore.capBytes();
        double f = used / (double) cap;
        headline.setText(ExactStore.human(used) + " of " + ExactStore.capGb() + " GB used");
        meter.set(Math.min(1, f), f > 1 ? Theme.BAD : f > 0.85 ? Theme.WARN : Skin.accent());
        breakdown.setText("<html>Exact places " + ExactStore.human(places) + "   ·   3D pictures " + ExactStore.human(pictures) + "   ·   Minecraft server " + ExactStore.human(server) + "</html>");
        if (f > 1) warn.setText("Over the limit: the oldest places go the next time one is made.");
        else {
            long free = new File(ExactStore.appDir().getParentFile() == null ? "." : ExactStore.appDir().getParent()).getUsableSpace();
            warn.setText(free > 0 && free < cap - used ? "The disk itself has only " + ExactStore.human(free) + " free." : " ");
        }
        dropServer.setVisible(server > 0);
        wipe.setEnabled(places + pictures > 0);
    }
}
