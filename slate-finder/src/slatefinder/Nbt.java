package slatefinder;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the game's NBT format into plain Java values: compounds are Maps, lists are Lists, numbers are their boxed
 * type, and the three array types are byte[], int[] and long[].
 */
final class Nbt {
    private Nbt() {}

    /** Reads one named root tag (as chunk files store them) and returns its value. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> readRoot(DataInput in) throws IOException {
        int type = in.readUnsignedByte();
        if (type != 10) throw new IOException("chunk data does not start with a compound (found tag " + type + ")");
        in.readUTF();
        return (Map<String, Object>) value(in, 10, 0);
    }

    private static Object value(DataInput in, int type, int depth) throws IOException {
        if (depth > 128) throw new IOException("NBT nested too deeply");
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: { int n = len(in); byte[] a = new byte[n]; in.readFully(a); return a; }
            case 8: return in.readUTF();
            case 9: {
                int et = in.readUnsignedByte();
                int n = len(in);
                List<Object> l = new ArrayList<>(Math.min(n, 4096));
                if (n > 0 && et == 0) throw new IOException("list of end tags");
                for (int i = 0; i < n; i++) l.add(value(in, et, depth + 1));
                return l;
            }
            case 10: {
                Map<String, Object> m = new HashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) break;
                    String name = in.readUTF();
                    m.put(name, value(in, t, depth + 1));
                }
                return m;
            }
            case 11: { int n = len(in); int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = in.readInt(); return a; }
            case 12: { int n = len(in); long[] a = new long[n]; for (int i = 0; i < n; i++) a[i] = in.readLong(); return a; }
            default: throw new IOException("unknown NBT tag type " + type);
        }
    }

    private static int len(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > (1 << 24)) throw new IOException("NBT array of " + n + " entries");
        return n;
    }

    // ------------------------------------------------------------------ small helpers for reading the result

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) { return o instanceof Map ? (Map<String, Object>) o : null; }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) { return o instanceof List ? (List<Object>) o : null; }

    static String str(Object o) { return o instanceof String ? (String) o : null; }

    static int num(Object o, int def) { return o instanceof Number ? ((Number) o).intValue() : def; }
}
