package slatefinder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Writes a contact sheet of the previews, for eyeballing. Args: out.png [biomes|structures|all] [from] [count] */
public final class PreviewSheet {
    public static void main(String[] a) throws Exception {
        System.setProperty("java.awt.headless", "true");
        String mode = a.length > 1 ? a[1] : "all";
        int from = a.length > 2 ? Integer.parseInt(a[2]) : 0, count = a.length > 3 ? Integer.parseInt(a[3]) : 999;
        List<Lexicon.Item> items = new ArrayList<>();
        for (Lexicon.Item it : Lexicon.items()) if (mode.equals("all") || (mode.equals("structures") == it.structure())) items.add(it);
        items = items.subList(Math.min(from, items.size()), Math.min(items.size(), from + count));
        int cols = 4, cw = 320, ch = 180 + 18;
        int rows = (items.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(Color.BLACK); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        for (int i = 0; i < items.size(); i++) {
            int x = (i % cols) * cw, y = (i / cols) * ch;
            g.drawImage(Previews.get(items.get(i)), x, y, cw, 180, null);
            g.setColor(Color.WHITE);
            g.drawString(items.get(i).name, x + 4, y + 180 + 13);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(a[0]));
    }
}
