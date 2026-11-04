package slatefinder;

import java.util.*;

/**
 * Reads a sentence like "plains with a village next to a river, snowy mountains a bit further out"
 * into claims the search can run, and says out loud everything it could not read.
 *
 * It works with no internet and no key. Every word is accounted for: it names a biome or structure,
 * it is a connecting word, or it is reported back as something that was ignored or misspelt.
 */
public final class Reader {
    private Reader() {}

    public enum Role { TARGET, NEAR, ABSENT }
    public enum Sev { ERROR, WARN, INFO }

    /** One thing the sentence said, resolved against what the map can actually search. */
    public static final class Claim {
        public final Lexicon.Ref ref;
        /** The words as typed. */
        public final String said;
        public Role role;
        public int within;
        /** True if a number or a phrase like "next to" gave the distance, rather than the default. */
        public boolean explicit;
        /** Ranges ("400 to 800") and "at least 500": the thing must be farther than this, and within {@link #within}. */
        public int minWithin;
        /** Biome ids to match (including habitat biomes standing in for a structure). */
        public final List<String> biomes = new ArrayList<>();
        /** Structure kinds that have a position list built. */
        public final List<String> structures = new ArrayList<>();
        /** True if a structure is being stood in for by the biomes it generates in. */
        public boolean approximate;
        /** Why this claim cannot be searched, or null if it can. */
        public String unsearchable;
        int start, end;

        Claim(Lexicon.Ref ref, String said, int start, int end) { this.ref = ref; this.said = said; this.start = start; this.end = end; }

        public boolean searchable() { return unsearchable == null && (!biomes.isEmpty() || !structures.isEmpty()); }
        public String label() { return ref.label; }
    }

    public static final class Issue {
        public final Sev sev;
        public final String text;
        /** Optional one-click fix: replace this word in the description with that one. */
        public final String from, to;

        Issue(Sev sev, String text, String from, String to) { this.sev = sev; this.text = text; this.from = from; this.to = to; }
    }

    public static final class Reading {
        public final List<Claim> claims = new ArrayList<>();
        public final List<Issue> issues = new ArrayList<>();

        public Claim target() { for (Claim c : claims) if (c.role == Role.TARGET && c.searchable()) return c; return null; }
        public boolean canSearch() { return target() != null; }
        public boolean hasError() { for (Issue i : issues) if (i.sev == Sev.ERROR) return true; return false; }
        public List<Claim> searchable() { List<Claim> l = new ArrayList<>(); for (Claim c : claims) if (c.searchable()) l.add(c); return l; }
    }

    // ------------------------------------------------------------------ vocabulary of connecting words

    private static final Set<String> STOP = set(
        "a an the and or but so then also plus too as well like with without of in on at by to from for into onto over under above below beside besides between among " +
        "is are was were be been being am i im me my we you your it its this that these those there theres here where what which who whom some any all both each every " +
        "have has had having can could would should will want wanted need needed looking look looks see seeing saw see find finding locate location place places spot spots " +
        "area areas region regions biome biomes structure structures thing things stuff kind sort type very really quite pretty much many lot lots bit little " +
        "big small large huge tiny tall short wide narrow long near nearby close closeby next beside adjacent touching right left front back behind beyond " +
        "far farther further away distance distant out around about around roughly approximately maybe probably just only still even again " +
        "standing stand stood located inside outside middle center centre edge side top bottom end start " +
        "not no nor never none without isnt arent dont doesnt cant wont neither lacking absent missing minus except excluding " +
        "think know say said tell told get got give go going come coming take took make made use using used " +
        "one two three four five six seven eight nine ten first second third another other others same different " +
        "blocks block meters meter metres metre chunks chunk m km k " +
        "if when while although though because since until during after before once already yet now today within less more than most least maximum minimum max min under below radius range between closer nearer exactly kilometers kilometres km " +
        "up down off yes yeah ok okay please thanks thank hi hello hey " +
        "world map minecraft game server screenshot picture image photo taken live lives living base house home " +
        "can t s d ll ve re");

    private static final Set<String> NEGATORS = set("no", "not", "without", "isnt", "arent", "dont", "doesnt", "cant", "wont", "never", "none", "nor", "neither",
        "lacking", "absent", "missing", "minus", "except", "excluding", "avoid", "lack");

    /** Words that say the thing right after them is the place being asked for. */
    private static final String[][] MARKERS = {
        {"i", "am"}, {"im"}, {"i", "m"}, {"standing"}, {"stand"}, {"stood"}, {"located"}, {"inside"}, {"i", "want"}, {"i", "need"},
        {"looking", "for"}, {"find"}, {"locate"}, {"spot"}, {"target"}, {"i", "live"}, {"my", "base"}, {"screenshot"}, {"taken"}, {"i", "was"}, {"where", "i"}
    };

    /** Words that are about blocks, mobs and so on, which the map does not record, with a hint. */
    private static final String HINTS =
        "gravel|a block - the map records biomes only. Try windswept gravelly hills or an ocean floor.\n" +
        "sand|a block. Try desert or beach.\n" +
        "dirt|a block - the map records biomes only.\n" +
        "stone|a block. Try stony peaks, stony shore or windswept hills.\n" +
        "clay|a block - the map records biomes only.\n" +
        "terracotta|a block. Try badlands.\n" +
        "cactus|a plant. Cactus grows in desert and badlands.\n" +
        "kelp|a plant - the map records biomes only. Try ocean.\n" +
        "seagrass|a plant - the map records biomes only. Try ocean.\n" +
        "coral|coral is not a biome. Try warm ocean.\n" +
        "lava|lava is not a biome.\n" +
        "tree|trees are not a biome. Name the biome they grow in: forest, taiga, jungle, birch forest.\n" +
        "oak|a tree. Try forest.\n" +
        "spruce|a tree. Try taiga.\n" +
        "acacia|a tree. Try savanna.\n" +
        "palm|palms do not exist in vanilla. Try jungle or beach.\n" +
        "flower|flowers are not a biome. Try flower forest, meadow or sunflower plains.\n" +
        "grass|grass is everywhere. Name a biome instead.\n" +
        "lake|lakes are not a biome. Try river or ocean.\n" +
        "pond|ponds are not a biome. Try river, swamp or snowy plains.\n" +
        "waterfall|waterfalls are not recorded. Try river or mountains.\n" +
        "island|islands are not a biome. Try mushroom fields, or ocean next to the land.\n" +
        "cliff|cliffs are not a biome. Try mountains or stony shore.\n" +
        "valley|valleys are not a biome. Try river or plains.\n" +
        "canyon|canyons are not a biome. Try badlands or ravine-style hills: windswept hills.\n" +
        "volcano|volcanoes do not exist in vanilla.\n" +
        "cow|a mob - the map records biomes and structures only.\n" +
        "sheep|a mob - the map records biomes and structures only.\n" +
        "pig|a mob - the map records biomes and structures only.\n" +
        "horse|a mob - the map records biomes and structures only.\n" +
        "mob|mobs are not recorded.\n" +
        "villager|villagers are not recorded. Try village.\n" +
        "zombie|mobs are not recorded.\n" +
        "creeper|mobs are not recorded.\n" +
        "animal|animals are not recorded.\n" +
        "ore|ores are not recorded.\n" +
        "diamond|ores are not recorded.\n" +
        "farm|player builds are not recorded.\n" +
        "road|player builds are not recorded.\n" +
        "path|paths are not recorded. Try village.\n" +
        "bridge|player builds are not recorded.\n" +
        "tower|towers are not recorded. Try pillager outpost.\n" +
        "castle|castles are not recorded. Try woodland mansion, or stronghold.\n" +
        "night|time of day is not recorded.\n" +
        "day|time of day is not recorded.\n" +
        "rain|weather is not recorded.\n" +
        "snowing|weather is not recorded. Try snowy plains or snowy taiga.\n" +
        "raining|weather is not recorded.\n" +
        "fog|weather is not recorded.\n" +
        "sunset|time of day is not recorded.\n" +
        "mountainside|try snowy slopes or mountains.\n" +
        "hot|temperature is not recorded. Try desert, badlands or savanna.\n" +
        "warm|try warm ocean, or desert, savanna, jungle.\n" +
        "dry|try desert, savanna or badlands.\n" +
        "wet|try swamp, jungle or river.\n" +
        "red|colours are not recorded. Try badlands, crimson forest or mushroom fields.\n" +
        "green|colours are not recorded. Try plains, forest or jungle.\n" +
        "white|colours are not recorded. Try snowy plains or snowy slopes.\n" +
        "blue|colours are not recorded. Try ocean, river or ice spikes.\n" +
        "dark|try dark forest, deep ocean or deep dark.\n" +
        "portal|try ruined portal (the naturally generated kind). Player-built portals are not recorded.\n" +
        "spawn|spawn is the centre of the map; use the Area box for distance from it.\n" +
        "nether|the Nether is not covered by this overworld map.\n" +
        "ravine|ravines are not recorded.\n" +
        "dungeon|try monster room.\n" +
        "boat|boats are not recorded. Try shipwreck.\n" +
        "water|water on its own is too broad. Try ocean, river or swamp.\n" +
        "trader|traders are not recorded.\n" +
        "pillager|pillagers are not recorded. Try pillager outpost.\n" +
        "witch|try swamp hut.\n" +
        "guardian|try ocean monument.\n" +
        "warden|try ancient city or deep dark.\n" +
        "ocelot|mobs are not recorded.";

    private static final Map<String, String> HINT_MAP = new HashMap<>();
    static {
        for (String l : HINTS.split("\n")) {
            int i = l.indexOf('|');
            HINT_MAP.put(Lexicon.stem(l.substring(0, i)), l.substring(i + 1));
        }
    }

    private static Set<String> set(String words) { return new HashSet<>(Arrays.asList(words.split("\\s+"))); }
    private static Set<String> set(String... words) { return new HashSet<>(Arrays.asList(words)); }

    // ------------------------------------------------------------------ the reading

    private static final class Tok {
        final String raw, stem;
        final boolean punct;
        Tok(String raw, boolean punct) { this.raw = raw; this.punct = punct; this.stem = punct ? raw : Lexicon.stem(raw); }
    }

    public static Reading read(String text, BiomeGrid grid) {
        Reading out = new Reading();
        if (text == null || text.isBlank()) return out;
        int step = grid != null ? Math.max(1, grid.step) : 64;

        List<Tok> toks = tokenise(text);
        int m = toks.size();
        // "not far" means close, not absent.
        for (int i = 0; i + 1 < m; i++) {
            if (toks.get(i).raw.equals("not") && (toks.get(i + 1).raw.equals("far") || toks.get(i + 1).raw.equals("too"))) {
                int j = toks.get(i + 1).raw.equals("too") && i + 2 < m && toks.get(i + 2).raw.equals("far") ? i + 2 : i + 1;
                if (toks.get(j).raw.equals("far")) { toks.set(i, new Tok("close", false)); for (int k = i + 1; k <= j; k++) toks.set(k, new Tok("and", false)); }
            }
        }

        // 1. find every biome / structure name, longest phrase first, tolerating typos.
        List<Claim> found = new ArrayList<>();
        boolean[] used = new boolean[m];
        List<Issue> fuzzyNotes = new ArrayList<>();
        int i = 0;
        while (i < m) {
            Tok t = toks.get(i);
            if (t.punct || isNumber(t.raw)) { i++; continue; }
            int maxL = Math.min(Lexicon.longestAliasWords(), m - i);
            Claim c = null;
            int len = 0;
            // exact, longest first, for multi-word phrases
            for (int L = maxL; L >= 2 && c == null; L--) {
                String key = key(toks, i, L);
                if (key == null) continue;
                Lexicon.Ref r = Lexicon.find(key);
                if (r != null) { c = new Claim(r, said(toks, i, L), i, i + L); len = L; }
            }
            // typo-tolerant two and three word phrases ("snowy mountians")
            if (c == null) {
                for (int L = 3; L >= 2 && c == null; L--) {
                    if (i + L > m) continue;
                    String key = key(toks, i, L);
                    if (key == null || containsStop(toks, i, L)) continue;
                    boolean allExact = true;
                    for (int k = 0; k < L; k++) if (Lexicon.find(Lexicon.stem(toks.get(i + k).raw)) == null) allExact = false;
                    if (allExact) continue;
                    String[] best = closest(key, L, 1, key.length());
                    if (best != null) {
                        Lexicon.Ref r = Lexicon.find(best[0]);
                        c = new Claim(r, said(toks, i, L), i, i + L); len = L;
                        fuzzyNotes.add(new Issue(Sev.INFO, "Read \"" + c.said + "\" as " + r.label + ".", c.said, r.label.toLowerCase(Locale.ROOT)));
                    }
                }
            }
            if (c == null) {
                Lexicon.Ref r = Lexicon.find(t.stem);
                if (r != null) { c = new Claim(r, t.raw, i, i + 1); len = 1; }
            }
            if (c == null && !isStop(t) && t.raw.length() >= 4) {
                String[] best = closest(t.stem, 1, 1, t.raw.length());
                if (best != null) {
                    Lexicon.Ref r = Lexicon.find(best[0]);
                    c = new Claim(r, t.raw, i, i + 1); len = 1;
                    fuzzyNotes.add(new Issue(Sev.INFO, "Read \"" + t.raw + "\" as " + r.label + ".", t.raw, r.label.toLowerCase(Locale.ROOT)));
                }
            }
            if (c != null) {
                found.add(c);
                for (int k = i; k < i + len; k++) used[k] = true;
                i += len;
            } else i++;
        }
        out.issues.addAll(fuzzyNotes);

        // 2. who is negated, how far, and which one is "the spot"
        Claim prev = null;
        int[] numOwner = numberOwners(toks, found);
        boolean[] numUsed = new boolean[m];
        List<Claim> kept = new ArrayList<>();
        for (int k = 0; k < found.size(); k++) {
            Claim c = found.get(k);
            int from = prev == null ? 0 : prev.end;
            int hard = lastHardBreak(toks, from, c.start);
            if (hard >= 0) from = hard + 1;
            int wFrom = Math.max(from, c.start - 8);
            boolean negated = false;
            boolean carry = false;
            for (int q = wFrom; q < c.start; q++) {
                Tok t = toks.get(q);
                if (!t.punct && NEGATORS.contains(t.raw)) negated = true;
            }
            // a list: "no desert, ocean or jungle" keeps the "no" over every name
            if (!negated && prev != null && prev.role == Role.ABSENT && hard < 0) {
                carry = true;
                boolean article = false;
                for (int q = prev.end; q < c.start; q++) {
                    Tok t = toks.get(q);
                    String w = t.raw;
                    if (!(t.punct && w.equals(",")) && !w.equals("or") && !w.equals("nor") && !w.equals("and") && !w.equals("any")) {
                        if (w.equals("a") || w.equals("an") || w.equals("the")) article = true;
                        carry = false;
                    }
                }
                if (article) carry = false;
                if (carry) negated = true;
            }
            boolean farFrom = false;
            for (int q = wFrom; q + 1 < c.start; q++) {
                String a = toks.get(q).raw, b = toks.get(q + 1).raw;
                if ((a.equals("far") || a.equals("away")) && b.equals("from")) farFrom = true;
            }
            c.role = (negated || farFrom) ? Role.ABSENT : Role.NEAR;
            c.within = distance(toks, c, found, k, from, farFrom, numOwner, c);
            // "next to the village is a dark forest": the second name is as close as the first was said to be
            if (!c.explicit && prev != null && prev.explicit && prev.role == Role.NEAR && c.role == Role.NEAR && copulaBetween(toks, prev.end, c.start)) {
                c.within = prev.within;
                c.explicit = true;
            }
            if (marked(toks, wFrom, c.start) && c.role != Role.ABSENT) c.role = Role.TARGET;
            kept.add(c);
            prev = c;
        }

        // drop repeats of the same thing with the same role
        Map<String, Claim> seen = new LinkedHashMap<>();
        for (Claim c : kept) {
            String key = c.ref.label + "/" + (c.role == Role.TARGET ? "TARGET" : c.role == Role.ABSENT ? "ABSENT" : "NEAR");
            Claim old = seen.get(key);
            if (old == null) { seen.put(key, c); continue; }
            if (c.role == Role.TARGET) old.role = Role.TARGET;
            if (c.explicit && (!old.explicit || c.within < old.within)) { old.within = c.within; old.explicit = true; old.minWithin = c.minWithin; }
        }
        out.claims.addAll(seen.values());

        // 3. choose the target: the one marked, else the first biome mentioned, else the first thing
        int marked = 0;
        Claim first = null;
        for (Claim c : out.claims) if (c.role == Role.TARGET) { marked++; if (first == null) first = c; }
        if (marked > 1) {
            boolean firstDone = false;
            for (Claim c : out.claims) if (c.role == Role.TARGET) { if (!firstDone) firstDone = true; else c.role = Role.NEAR; }
            out.issues.add(new Issue(Sev.WARN, "You marked more than one place as the spot. I used the first (" + first.label() + ") and treated the others as things nearby.", null, null));
        }
        if (first == null) {
            Claim pick = null;
            for (Claim c : out.claims) if (c.role == Role.NEAR && c.ref.isBiome()) { pick = c; break; }
            if (pick == null) for (Claim c : out.claims) if (c.role == Role.NEAR) { pick = c; break; }
            if (pick != null) pick.role = Role.TARGET;
        }
        for (Claim c : out.claims) if (c.role == Role.TARGET) c.within = 0;

        // 4. resolve every claim against what the map actually holds
        for (Claim c : out.claims) resolve(c, grid, out.issues);

        // the target must be searchable; if the first pick is not, hand the role to the next that is
        Claim tgt = null;
        for (Claim c : out.claims) if (c.role == Role.TARGET) tgt = c;
        if (tgt != null && !tgt.searchable()) {
            Claim alt = null;
            for (Claim c : out.claims) if (c != tgt && c.role == Role.NEAR && c.searchable() && c.ref.isBiome()) { alt = c; break; }
            if (alt == null) for (Claim c : out.claims) if (c != tgt && c.role == Role.NEAR && c.searchable()) { alt = c; break; }
            if (alt != null) {
                out.issues.add(new Issue(Sev.WARN, tgt.label() + " cannot be searched, so " + alt.label() + " is now the place being found.", null, null));
                tgt.role = Role.NEAR;
                alt.role = Role.TARGET;
                alt.within = 0;
            }
        }

        // thin biomes are mostly invisible at the grid spacing
        for (Claim c : out.claims) {
            if (!c.searchable()) continue;
            boolean thin = !c.biomes.isEmpty();
            for (String b : c.biomes) if (!BiomeQuery.THIN.contains(b)) thin = false;
            if (!thin) continue;
            if (c.role == Role.TARGET) {
                out.issues.add(new Issue(Sev.WARN, c.label() + " is narrower than the " + step + "-block map spacing, so most of it is invisible here. Describe what it sits next to instead - for a beach, the ocean and the land behind it.", null, null));
            } else if (c.within < step * 2) {
                c.within = step * 2;
            }
        }

        // contradictions
        for (Claim a : out.claims) for (Claim b : out.claims) {
            if (a != b && a.ref == b.ref && a.role == Role.ABSENT && b.role != Role.ABSENT)
                out.issues.add(new Issue(Sev.WARN, "You said both that " + a.label() + " is there and that it is not. Nothing can match that.", null, null));
        }

        for (Claim c : out.claims) {
            if (c.minWithin > 0 && c.role == Role.NEAR) {
                boolean range = false;
                for (Tok t : toks) if (isRange(t.raw)) range = true;
                if (!range) out.issues.add(new Issue(Sev.INFO, c.label() + ": \"at least " + String.format("%,d", c.minWithin) + "\" has no upper limit, so I searched " + String.format("%,d", c.minWithin) + " to " + String.format("%,d", c.within) + " blocks. Say a range such as \"" + c.minWithin + " to " + (c.minWithin * 2) + "\" to choose it.", null, null));
            }
        }

        // 5. everything left over
        List<String> hintSeen = new ArrayList<>();
        for (int q = 0; q < m; q++) {
            Tok t = toks.get(q);
            if (used[q] || t.punct || isDistanceToken(t.raw) || isStop(t) || NEGATORS.contains(t.raw)) continue;
            if (isDistanceWord(t.stem)) continue;
            if (t.raw.length() < 3) continue;
            String hint = HINT_MAP.get(t.stem);
            if (hint != null) {
                if (!hintSeen.contains(t.stem)) { hintSeen.add(t.stem); out.issues.add(new Issue(Sev.WARN, "\"" + t.raw + "\" - " + hint + " Ignored.", null, null)); }
                continue;
            }
            String[] close = closest(t.stem, 1, 2, t.raw.length());
            if (close != null) {
                Lexicon.Ref r = Lexicon.find(close[0]);
                out.issues.add(new Issue(Sev.WARN, "I don't know \"" + t.raw + "\". Did you mean " + r.label.toLowerCase(Locale.ROOT) + "? Ignored for now.", t.raw, r.label.toLowerCase(Locale.ROOT)));
            } else {
                out.issues.add(new Issue(Sev.WARN, "I don't know \"" + t.raw + "\" - it is not a biome or a structure, so I ignored it.", null, null));
            }
        }

        // 6. nothing usable
        if (out.claims.isEmpty()) {
            out.issues.add(0, new Issue(Sev.ERROR, "I found no biome or structure name in that. Try something like \"plains with a village next to a river\", or pick from the book on the right.", null, null));
        } else if (out.target() == null) {
            boolean allAbsent = true;
            for (Claim c : out.claims) if (c.role != Role.ABSENT) allAbsent = false;
            if (allAbsent) out.issues.add(0, new Issue(Sev.ERROR, "Everything you described is something that is NOT there. Say what the place does have, too.", null, null));
            else out.issues.add(0, new Issue(Sev.ERROR, "None of the places you named can be searched in this map (see the notes below).", null, null));
        }
        return out;
    }

    // ------------------------------------------------------------------ pieces

    /** Builds a claim directly (used when something other than the built-in reader produced it). */
    static Claim claimFor(Lexicon.Ref ref, String said, Role role, int within) {
        Claim c = new Claim(ref, said, 0, 0);
        c.role = role;
        c.within = role == Role.TARGET ? 0 : Math.max(0, within);
        return c;
    }

    static void resolveAll(Reading r, BiomeGrid grid) {
        for (Claim c : r.claims) resolve(c, grid, r.issues);
    }

    private static List<Tok> tokenise(String text) {
        String s = text.toLowerCase(Locale.ROOT).replace('’', '\'');
        // "1.5k", "2k", "1.5 km" -> numbers; "30 chunks" -> blocks
        s = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:k|km|kilometers?|kilometres?)\\b").matcher(s).replaceAll(r -> String.valueOf((long) Math.round(Double.parseDouble(r.group(1)) * 1000)) + " blocks");
        s = java.util.regex.Pattern.compile("(\\d+)\\s*chunks?").matcher(s).replaceAll(r -> String.valueOf(Integer.parseInt(r.group(1)) * 16) + " blocks");
        s = s.replaceAll("(\\d),(\\d{3})", "$1$2");
        // ranges: "400 to 800 blocks", "400-800", "between 400 and 800"
        s = java.util.regex.Pattern.compile("between\\s+(\\d+)\\s*(?:blocks?)?\\s*(?:and|-|to)\\s*(\\d+)").matcher(s).replaceAll(r -> " rng" + r.group(1) + "x" + r.group(2) + " ");
        s = java.util.regex.Pattern.compile("(\\d+)\\s*(?:blocks?)?\\s*(?:-|\u2013|\u2014|to|until)\\s*(\\d+)(\\s*block)?").matcher(s).replaceAll(r -> {
            boolean unit = r.group(3) != null;
            if (!unit && (Long.parseLong(r.group(1)) < 20 || Long.parseLong(r.group(2)) < 20)) return java.util.regex.Matcher.quoteReplacement(r.group(0));
            return " rng" + r.group(1) + "x" + r.group(2) + (unit ? " blocks" : "") + " ";
        });
        // "no more than 500" is a limit, not an absence
        s = s.replaceAll("\\b(?:no|not)\\s+(?:more|further|farther|greater|longer|over|beyond)\\s+(?:than\\s+)?(?=\\d)", "within ");
        // "at least 500 blocks away", "more than 500": the thing is farther than that
        s = java.util.regex.Pattern.compile("\\b(?:at\\s+least|more\\s+than|greater\\s+than|further\\s+than|farther\\s+than|further\\s+away\\s+than|over|beyond|minimum\\s+of|min\\s+of|(?:not|no)\\s+closer\\s+than)\\s+(\\d+)").matcher(s).replaceAll(r -> " min" + r.group(1) + " ");
        // limits: "up to 500", "at most 500", "less than 500", "under 500", "inside 500", "a radius of 500", "500 block radius"
        s = s.replaceAll("\\b(?:at\\s+most|maximum\\s+of|max\\s+of|up\\s+to|not\\s+over|less\\s+than|fewer\\s+than|closer\\s+than|nearer\\s+than|(?:within\\s+)?(?:a\\s+)?radius\\s+of|(?:under|below|inside)(?=\\s+\\d))", "within");
        s = s.replaceAll("(\\d+)[\\s-]*blocks?[\\s-]*radius", "within $1 blocks");
        s = s.replace("'", "").replaceAll("(\\d),(\\d{3})", "$1$2");
        List<Tok> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i <= s.length(); i++) {
            char ch = i < s.length() ? s.charAt(i) : ' ';
            if (Character.isLetterOrDigit(ch) && ch < 128) cur.append(ch);
            else {
                if (cur.length() > 0) { out.add(new Tok(cur.toString(), false)); cur.setLength(0); }
                if (ch == ',' || ch == '.' || ch == ';' || ch == '!' || ch == '?' || ch == '\n') out.add(new Tok(ch == '\n' ? "." : String.valueOf(ch), true));
            }
        }
        return out;
    }

    private static boolean isNumber(String w) { return !w.isEmpty() && w.chars().allMatch(Character::isDigit); }
    /** "rng400x800" (a range) or "min500" (at least 500): produced by the tokeniser from distance phrases. */
    private static boolean isRange(String w) { return w.matches("rng\\d+x\\d+"); }
    private static boolean isMin(String w) { return w.matches("min\\d+"); }
    private static boolean isDistanceToken(String w) { return isNumber(w) || isRange(w) || isMin(w); }
    private static boolean isStop(Tok t) { return STOP.contains(t.raw) || STOP.contains(t.stem); }
    private static boolean containsStop(List<Tok> toks, int i, int L) {
        for (int k = 0; k < L; k++) if (isStop(toks.get(i + k)) || toks.get(i + k).punct) return true;
        return false;
    }
    private static boolean isDistanceWord(String s) {
        return s.equals("next") || s.equals("beside") || s.equals("adjacent") || s.equals("near") || s.equals("nearby") || s.equals("close") || s.equals("far");
    }

    private static String key(List<Tok> toks, int i, int L) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < L; k++) {
            Tok t = toks.get(i + k);
            if (t.punct) return null;
            if (k > 0) sb.append(' ');
            sb.append(t.stem);
        }
        return sb.toString();
    }

    private static String said(List<Tok> toks, int i, int L) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < L; k++) { if (k > 0) sb.append(' '); sb.append(toks.get(i + k).raw); }
        return sb.toString();
    }

    private static int lastHardBreak(List<Tok> toks, int from, int to) {
        int last = -1;
        for (int q = from; q < to; q++) {
            Tok t = toks.get(q);
            if ((t.punct && !t.raw.equals(",")) || t.raw.equals("but") || t.raw.equals("however") || t.raw.equals("although") || t.raw.equals("while")) last = q;
        }
        return last;
    }

    private static boolean copulaBetween(List<Tok> toks, int from, int to) {
        boolean copula = false;
        for (int q = from; q < to; q++) {
            String w = toks.get(q).raw;
            if (w.equals("is") || w.equals("are") || w.equals("theres") || w.equals("sits") || w.equals("stands")) copula = true;
            else if (!(w.equals("there") || w.equals("a") || w.equals("an") || w.equals("the") || w.equals("also"))) return false;
        }
        return copula;
    }

    private static boolean marked(List<Tok> toks, int from, int to) {
        for (int q = from; q < to; q++) {
            for (String[] mk : MARKERS) {
                boolean ok = q + mk.length <= to;
                for (int k = 0; ok && k < mk.length; k++) if (!toks.get(q + k).raw.equals(mk[k])) ok = false;
                if (ok) return true;
            }
        }
        return false;
    }

    /** How far away the thing is said to be, from numbers and phrases around its name. */
    private static int distance(List<Tok> toks, Claim c, List<Claim> all, int idx, int from, boolean farFrom, int[] numOwner, Claim self) {
        int def = farFrom ? 800 : 300;
        int aEnd = c.end;
        int limit = Math.min(toks.size(), c.end + 5);
        for (int q = c.end; q < limit; q++) {
            Tok t = toks.get(q);
            if (t.punct || (!t.raw.equals("blocks") && false)) { limit = q; break; }
            if (idx + 1 < all.size() && q >= all.get(idx + 1).start) { limit = q; break; }
        }
        int lo = Math.max(from, c.start - 8);
        int best = -1;
        // an explicit number wins ("within 500 blocks", "800 blocks away"), whichever name it belongs to
        for (int q = 0; q < toks.size(); q++) {
            if (numOwner[q] != idx) continue;
            Tok t = toks.get(q);
            self.explicit = true;
            if (isRange(t.raw)) {
                String[] ab = t.raw.substring(3).split("x");
                long rlo = Long.parseLong(ab[0]), rhi = Long.parseLong(ab[1]);
                if (rlo > rhi) { long tmp = rlo; rlo = rhi; rhi = tmp; }
                self.minWithin = (int) Math.min(rlo, 400000);
                return (int) Math.min(Math.max(rhi, rlo + 1), 400000);
            }
            if (isMin(t.raw)) {
                long mlo = Long.parseLong(t.raw.substring(3));
                self.minWithin = (int) Math.min(mlo, 400000);
                return (int) Math.min(Math.max(mlo * 4, mlo + 1500), 400000);
            }
            long v = Long.parseLong(t.raw.length() > 9 ? "999999999" : t.raw);
            return (int) Math.min(v, 400000);
        }
        for (int q = lo; q < limit; q++) {
            if (q >= c.start && q < c.end) continue;
            String w = toks.get(q).raw;
            String n = q + 1 < toks.size() ? toks.get(q + 1).raw : "";
            int d = -1;
            if (w.equals("far") || w.equals("faraway") || w.equals("miles") || w.equals("mile")) d = n.equals("from") ? 800 : 2000;
            else if (w.equals("further") || w.equals("futher") || w.equals("farther") || w.equals("distance") || w.equals("distant") || w.equals("behind") || w.equals("beyond") || w.equals("horizon") || w.equals("away")) d = 800;
            else if (w.equals("next") || w.equals("beside") || w.equals("adjacent") || w.equals("touching") || w.equals("bordering") || (w.equals("right") && n.equals("by"))) d = 100;
            else if (w.equals("near") || w.equals("nearby") || w.equals("close") || w.equals("closeby") || w.equals("around")) d = 300;
            if (d > best) best = d;
        }
        if (best > 0) self.explicit = true;
        return best > 0 ? best : def;
    }

    /**
     * Which name each distance number belongs to. A number that opens a clause ("..., about 400 blocks there is a
     * grove") belongs to the name that follows it; one that trails a name ("ocean 800 blocks away") belongs to that name.
     */
    private static int[] numberOwners(List<Tok> toks, List<Claim> found) {
        int m = toks.size();
        int[] owner = new int[m];
        Arrays.fill(owner, -1);
        Set<String> quals = set("about", "around", "roughly", "approximately", "approx", "within", "like", "at", "maybe", "some", "over", "up", "to", "exactly", "nearly", "almost");
        for (int q = 0; q < m; q++) {
            Tok t = toks.get(q);
            if (!isDistanceToken(t.raw)) continue;
            if (isNumber(t.raw)) {
                long v = t.raw.length() > 9 ? 999999999L : Long.parseLong(t.raw);
                boolean unit = q + 1 < m && (toks.get(q + 1).raw.startsWith("block") || toks.get(q + 1).raw.equals("m") || toks.get(q + 1).raw.startsWith("meter") || toks.get(q + 1).raw.startsWith("metre"));
                if (!((unit && v > 0) || v >= 20)) continue;
            }
            int prevIdx = -1, nextIdx = -1;
            for (int k = 0; k < found.size(); k++) {
                if (found.get(k).end <= q) prevIdx = k;
                if (found.get(k).start > q && nextIdx < 0) nextIdx = k;
            }
            int b = q - 1;
            while (b >= 0 && quals.contains(toks.get(b).raw)) b--;
            boolean opensClause = b < 0 || toks.get(b).punct || toks.get(b).raw.equals("and") || toks.get(b).raw.equals("but") || toks.get(b).raw.equals("then") || toks.get(b).raw.equals("also");
            boolean nextClose = nextIdx >= 0 && found.get(nextIdx).start - q <= 7 && lastHardBreak(toks, q, found.get(nextIdx).start) < 0;
            boolean prevClose = prevIdx >= 0 && q - found.get(prevIdx).end <= 4;
            if (opensClause && nextClose) owner[q] = nextIdx;
            else if (prevClose) owner[q] = prevIdx;
            else if (nextClose) owner[q] = nextIdx;
            else if (prevIdx >= 0) owner[q] = prevIdx;
            else if (nextIdx >= 0) owner[q] = nextIdx;
        }
        return owner;
    }

    private static void resolve(Claim c, BiomeGrid grid, List<Issue> issues) {
        Set<String> biomes = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        List<String> otherDim = new ArrayList<>();
        for (String id : c.ref.biomes) {
            Lexicon.Item it = Lexicon.byId(id);
            if (it != null && (it.dim == 'N' || it.dim == 'E')) { otherDim.add(it.name + " (" + it.dimName() + ")"); continue; }
            if (grid != null && !PlaceSearch.known(grid, id)) { missing.add(Lexicon.nameOf(id)); continue; }
            biomes.add(id);
        }
        List<String> noData = new ArrayList<>();
        List<String> habitatUsed = new ArrayList<>();
        String cannotPlace = null;
        for (String sid : c.ref.structures) {
            Lexicon.Item it = Lexicon.byId(sid);
            if (it == null) continue;
            if (it.dim == 'N' || it.dim == 'E') { otherDim.add(it.name + " (" + it.dimName() + ")"); continue; }
            if (PlaceSearch.hasPoints(sid)) { c.structures.add(sid); continue; }
            if (it.habitat != null) {
                int added = 0;
                for (String h : it.habitat) if (grid == null || PlaceSearch.known(grid, h)) { biomes.add(h); added++; }
                if (added > 0) { c.approximate = true; habitatUsed.add(it.name); noData.add(it.name + ": " + it.habitatNote); }
                else cannotPlace = it.name + " generates in biomes that are not in this map.";
            } else cannotPlace = it.name + ": " + it.habitatNote;
        }
        c.biomes.addAll(biomes);
        if (!c.biomes.isEmpty() || !c.structures.isEmpty()) {
            if (!otherDim.isEmpty()) issues.add(new Issue(Sev.INFO, c.label() + ": left out " + String.join(", ", otherDim) + " - this map covers the overworld only.", null, null));
            if (!missing.isEmpty()) issues.add(new Issue(Sev.INFO, c.label() + ": " + String.join(", ", missing) + " is not in your biome map, so it was left out.", null, null));
            if (c.approximate)
                issues.add(new Issue(Sev.WARN, "No position list is built for " + String.join(", ", habitatUsed) + ", so I searched the biomes it generates in instead. Treat results as \"could be here\", not exact. "
                    + String.join(" ", noData), null, null));
            return;
        }
        if (!otherDim.isEmpty() && missing.isEmpty() && cannotPlace == null) c.unsearchable = String.join(", ", otherDim) + " is not in the overworld, and this map only covers the overworld.";
        else if (cannotPlace != null) c.unsearchable = cannotPlace;
        else if (!missing.isEmpty()) c.unsearchable = String.join(", ", missing) + " does not exist in your biome map (this world has none of it).";
        else c.unsearchable = "nothing to search for.";
        issues.add(new Issue(Sev.WARN, "Understood \"" + c.said + "\" as " + c.label() + ", but it cannot be searched: " + c.unsearchable, null, null));
    }

    // ------------------------------------------------------------------ typo matching

    /** What a short phrase typed into a search box means, forgiving of plurals and small typos. Null if nothing. */
    public static Lexicon.Ref lookup(String phrase) {
        String key = Lexicon.norm(phrase);
        if (key.isEmpty()) return null;
        Lexicon.Ref r = Lexicon.find(key);
        if (r != null) return r;
        int words = key.split(" ").length;
        String[] best = closest(key, words, 2, phrase.trim().length());
        return best == null ? null : Lexicon.find(best[0]);
    }

    /**
     * The alias (with the same word count) closest to {@code s}, within the edit distance allowed for its
     * length. {@code strictness} 1 is tight enough to auto-correct; 2 is loose enough to suggest.
     */
    private static String[] closest(String s, int words, int strictness, int rawLen) {
        if (s.length() < 4) return null;
        String best = null;
        int bestD = Integer.MAX_VALUE;
        boolean tie = false;
        for (String k : Lexicon.aliasKeys()) {
            if (Math.abs(k.length() - s.length()) > 3) continue;
            if (k.split(" ").length != words) continue;
            int limit = allowed(Math.min(k.length(), rawLen), strictness);
            if (limit == 0) continue;
            int d = osa(s, k, limit);
            if (d <= limit) {
                if (d < bestD) { bestD = d; best = k; tie = false; }
                else if (d == bestD && best != null && !sameMeaning(best, k)) tie = true;
            }
        }
        if (best == null || (strictness == 1 && tie)) return null;
        return new String[]{best};
    }

    private static boolean sameMeaning(String a, String b) { return Lexicon.find(a) == Lexicon.find(b); }

    private static int allowed(int len, int strictness) {
        if (strictness == 1) return len <= 4 ? 0 : len <= 9 ? 1 : 2;
        return len <= 4 ? 1 : len <= 7 ? 2 : 3;
    }

    /** Optimal-string-alignment distance (insert, delete, substitute, swap neighbours), cut off at limit. */
    static int osa(String a, String b, int limit) {
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > limit) return limit + 1;
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
            }
        }
        return d[n][m];
    }
}
