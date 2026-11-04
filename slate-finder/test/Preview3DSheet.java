package slatefinder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Contact sheet of the 3D pictures. Args: out.png biomes|structures from count */
public final class Preview3DSheet {
    public static void main(String[] a) throws Exception {
        System.setProperty("java.awt.headless", "true");
        String mode = a[1];
        int from = Integer.parseInt(a[2]), count = Integer.parseInt(a[3]);
        List<Lexicon.Item> items = new ArrayList<>();
        for (Lexicon.Item it : Lexicon.items()) if (Previews3D.has3D(it) && (mode.equals("structures") == it.structure()) && (it.dim == 'O' || mode.equals("structures"))) items.add(it);
        items = items.subList(Math.min(from, items.size()), Math.min(items.size(), from + count));
        int cols = 3, cw = 448, ch = 252 + 18;
        int rows = (items.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(Color.BLACK); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < items.size(); i++) {
            Lexicon.Item it = items.get(i);
            BufferedImage im = Previews3D.render(Previews3D.scene(it));
            g.drawImage(im, (i % cols) * cw, (i / cols) * ch, null);
            g.setColor(Color.WHITE);
            g.drawString(it.name, (i % cols) * cw + 4, (i / cols) * ch + 252 + 14);
        }
        System.out.println(items.size() + " pictures in " + (System.currentTimeMillis() - t0) + " ms");
        g.dispose();
        ImageIO.write(sheet, "png", new File(a[0]));
    }
}
