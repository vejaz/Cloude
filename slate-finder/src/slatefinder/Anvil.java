package slatefinder;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** The game's region files (.mca): 32 x 32 chunks each, in 4 KiB sectors, every chunk compressed. */
final class Anvil {
    private Anvil() {}

    /** The folder with the overworld's region files for a world folder, or null. 1.21 keeps them in world/region. */
    static File regionDir(File world) {
        File a = new File(world, "region");
        if (a.isDirectory()) return a;
        File b = new File(world, "dimensions/minecraft/overworld/region");
        return b.isDirectory() ? b : null;
    }

    static final class Region {
        private final byte[] data;
        final int rx, rz;
        final File file;

        Region(File dir, int rx, int rz) throws IOException {
            this.rx = rx; this.rz = rz;
            this.file = new File(dir, "r." + rx + "." + rz + ".mca");
            this.data = file.isFile() ? Files.readAllBytes(file.toPath()) : new byte[0];
        }

        boolean exists() { return data.length >= 8192; }

        /** Whether this chunk (position inside the region, 0..31) has been written. */
        boolean has(int lx, int lz) {
            if (data.length < 8192) return false;
            int o = 4 * (lx + lz * 32);
            return ((data[o] & 255) | (data[o + 1] & 255) | (data[o + 2] & 255)) != 0 && (data[o + 3] & 255) != 0;
        }

        /** The chunk's data as nested maps, or null if it has not been written. */
        Map<String, Object> chunk(int lx, int lz) throws IOException {
            if (!has(lx, lz)) return null;
            int o = 4 * (lx + lz * 32);
            int sector = ((data[o] & 255) << 16) | ((data[o + 1] & 255) << 8) | (data[o + 2] & 255);
            int off = sector * 4096;
            if (off + 5 > data.length) throw new IOException("chunk " + lx + "," + lz + " of " + file.getName() + " points past the end of the file");
            int len = ((data[off] & 255) << 24) | ((data[off + 1] & 255) << 16) | ((data[off + 2] & 255) << 8) | (data[off + 3] & 255);
            int comp = data[off + 4] & 255;
            if (len < 1 || off + 4 + len > data.length) throw new IOException("chunk " + lx + "," + lz + " of " + file.getName() + " has a bad length");
            if ((comp & 128) != 0) return null;                       // stored in a separate .mcc file: huge chunks only, not used by generated terrain
            ByteArrayInputStream raw = new ByteArrayInputStream(data, off + 5, len - 1);
            DataInputStream in;
            switch (comp) {
                case 1: in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw), 1 << 16)); break;
                case 2: in = new DataInputStream(new java.io.BufferedInputStream(new InflaterInputStream(raw), 1 << 16)); break;
                case 3: in = new DataInputStream(raw); break;
                case 4: throw new IOException("this world uses LZ4 region compression; set region-file-compression=deflate in server.properties");
                default: throw new IOException("unknown chunk compression " + comp);
            }
            try (DataInputStream d = in) { return Nbt.readRoot(d); }
        }
    }
}
