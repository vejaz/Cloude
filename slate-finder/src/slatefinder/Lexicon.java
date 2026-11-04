package slatefinder;

import java.awt.Color;
import java.util.*;

/**
 * Every biome and structure the Describing finder knows by name, with the words people use for them.
 *
 * Biomes are the 1.21 list (overworld, caves, Nether, End). Structures are the overworld, Nether
 * and End ones. A word can mean one thing ("river") or a whole family ("mountains" is every peak,
 * slope and windswept hill), which is what {@link Ref} describes.
 */
public final class Lexicon {
    private Lexicon() {}

    public enum Kind { BIOME, STRUCTURE }

    /** One biome or structure: what the book lists and what a picture is drawn for. */
    public static final class Item {
        public final String id, name, note;
        public final Kind kind;
        /** O overworld, C cave, N nether, E end. */
        public final char dim;
        public final Color colour;
        public final String[] aliases;
        /** Structures only: the biomes it generates in, used when there is no list of its exact positions. */
        public final String[] habitat;
        public final String habitatNote;
        /** For a variant ("desert village"): the structure whose position list it is cut from. Null otherwise. */
        public final String pointsFrom;

        Item(String id, String name, Kind kind, char dim, int colour, String note, String[] aliases, String[] habitat, String habitatNote, String pointsFrom) {
            this.id = id; this.name = name; this.kind = kind; this.dim = dim; this.colour = new Color(colour);
            this.note = note; this.aliases = aliases; this.habitat = habitat; this.habitatNote = habitatNote;
            this.pointsFrom = pointsFrom == null || pointsFrom.isEmpty() ? null : pointsFrom;
        }

        /** The position-list name this is searched under. */
        public String listKind() { return pointsFrom != null ? pointsFrom : id; }

        public boolean structure() { return kind == Kind.STRUCTURE; }
        public String dimName() {
            switch (dim) { case 'N': return "Nether"; case 'E': return "The End"; case 'C': return "Underground"; default: return "Overworld"; }
        }
    }

    /** What a word in a sentence points at: some biomes, some structures, or both. */
    public static final class Ref {
        public final String label;
        public final List<String> biomes = new ArrayList<>();
        public final List<String> structures = new ArrayList<>();
        /** True when the word names a whole family rather than one exact thing. */
        public boolean family;

        Ref(String label) { this.label = label; }

        public boolean isStructure() { return biomes.isEmpty() && !structures.isEmpty(); }
        public boolean isBiome() { return !biomes.isEmpty(); }
    }

    private static final List<Item> ITEMS = new ArrayList<>();
    private static final Map<String, Item> BY_ID = new LinkedHashMap<>();
    /** Normalised alias -> what it means. */
    private static final Map<String, Ref> ALIASES = new LinkedHashMap<>();
    private static int longestAlias = 1;

    // ------------------------------------------------------------------ the data
    // id | name | colour | dim | note | aliases (comma separated)

    private static final String BIOMES =
        "plains|Plains|8DB360|O|Flat grassland, the ordinary one.|plains,plain,planes,plane,grassland,grasslands,grass land,flatland,flatlands,flat land,flat grass,prairie,meadow land\n" +
        "sunflower_plains|Sunflower Plains|B5C93A|O|Plains dotted with sunflowers.|sunflower plains,sunflower plain,sunflower field,sunflower fields,sunflowers,sunflower\n" +
        "snowy_plains|Snowy Plains|F0F4F8|O|Flat snow, frozen ponds.|snowy plains,snowy plain,snow plains,snow plain,snowy flat,snow field,snowy field,tundra,snowy tundra,frozen plains,snow flatland\n" +
        "ice_spikes|Ice Spikes|A5C8E8|O|Tall spikes of packed ice. Rare.|ice spikes,ice spike,spikes of ice,ice spikey,icy spikes\n" +
        "desert|Desert|F2D98A|O|Sand, cactus, dunes. No grass.|desert,deserts,sahara,dunes,sand dunes,sandy desert,sand desert,dune biome\n" +
        "swamp|Swamp|4C6A3A|O|Shallow water, lily pads, dull green.|swamp,swamps,marsh,marshes,bog,wetland,wetlands,swampland,swamp land,swamp biome\n" +
        "mangrove_swamp|Mangrove Swamp|4F7A3A|O|Mangrove roots standing in water.|mangrove swamp,mangrove swamps,mangrove,mangroves,mangrove forest,mangrove biome\n" +
        "forest|Forest|4E8A3A|O|Oak and birch.|forest,forests,oak forest,woods,wood,woodland,woodlands,wooded,oak woods\n" +
        "flower_forest|Flower Forest|6AA84A|O|Forest carpeted in flowers.|flower forest,flower forests,flowery forest,flower woods,flower wood,flower garden forest\n" +
        "birch_forest|Birch Forest|6C9E4B|O|Pale trunks.|birch forest,birch forests,birch,birches,birch woods,birch wood,birch trees\n" +
        "old_growth_birch_forest|Old Growth Birch Forest|5E9A48|O|Very tall birch.|old growth birch forest,old growth birch,tall birch forest,tall birch,giant birch forest,giant birch\n" +
        "dark_forest|Dark Forest|2F4A22|O|Dark oak, gloomy roof of leaves.|dark forest,dark forests,dark oak forest,roofed forest,spooky forest,dark woods,dark wood,dark oak woods\n" +
        "pale_garden|Pale Garden|9AA6A0|O|Pale oak, hanging moss, creaking trees.|pale garden,pale gardens,pale forest,pale oak forest,pale woods,pale oak,creaking forest\n" +
        "taiga|Taiga|3A6B4A|O|Spruce, cool.|taiga,taigas,spruce forest,boreal forest,pine forest,regular taiga,normal taiga\n" +
        "snowy_taiga|Snowy Taiga|7E9A9A|O|Spruce under snow.|snowy taiga,snow taiga,cold taiga,snowy spruce forest,snowy pine forest,snowy forest,snow forest,winter forest\n" +
        "old_growth_pine_taiga|Old Growth Pine Taiga|3A5A3E|O|Huge pines, mossy ground.|old growth pine taiga,giant tree taiga,mega taiga,pine taiga,giant pine taiga,mega pine taiga\n" +
        "old_growth_spruce_taiga|Old Growth Spruce Taiga|3D5640|O|Huge spruce.|old growth spruce taiga,giant spruce taiga,mega spruce taiga,spruce taiga,old growth taiga\n" +
        "grove|Grove|8FB89A|O|Snowy trees below the peaks.|grove,groves,snowy grove,snow grove,snowy trees\n" +
        "jungle|Jungle|2F8A2A|O|Dense, vines, cocoa, melons.|jungle,jungles,rainforest,rain forest,tropical forest,jungle forest,normal jungle,regular jungle\n" +
        "sparse_jungle|Sparse Jungle|4A9A3A|O|A thinner jungle.|sparse jungle,light jungle,jungle edge,thin jungle,open jungle\n" +
        "bamboo_jungle|Bamboo Jungle|5FA032|O|Bamboo everywhere.|bamboo jungle,bamboo forest,bamboo,bamboo grove,bamboo biome\n" +
        "savanna|Savanna|BDB25A|O|Dry yellow grass, acacia.|savanna,savannas,savannah,savannahs,acacia forest,acacia,african plains,dry grassland\n" +
        "savanna_plateau|Savanna Plateau|C4B85E|O|Savanna up on a plateau.|savanna plateau,savannah plateau,high savanna,raised savanna\n" +
        "windswept_savanna|Windswept Savanna|B3A257|O|Savanna with jagged hills.|windswept savanna,shattered savanna,windswept savannah,jagged savanna\n" +
        "badlands|Badlands|C95F2E|O|Orange terracotta, the mesa.|badlands,badland,mesa,mesas,terracotta biome,red mesa,red badlands,clay hills\n" +
        "wooded_badlands|Wooded Badlands|B0733E|O|Badlands with trees on top.|wooded badlands,wooded mesa,badlands with trees,mesa with trees,forested badlands\n" +
        "eroded_badlands|Eroded Badlands|D26B35|O|Tall thin badlands spires.|eroded badlands,eroded mesa,spiky badlands,badlands spires,mesa spires,spire badlands\n" +
        "windswept_hills|Windswept Hills|7E8A82|O|Rocky green hills, steep.|windswept hills,windswept hill,windy hills,extreme hills,mountain hills,rocky hills,stone hills\n" +
        "windswept_gravelly_hills|Windswept Gravelly Hills|9AA39B|O|Hills with gravel patches.|windswept gravelly hills,gravelly hills,gravel hills,gravelly mountains,gravel mountains\n" +
        "windswept_forest|Windswept Forest|6E8A6A|O|Spruce on rocky hills.|windswept forest,windy forest,forest hills,forested hills,wooded hills\n" +
        "snowy_slopes|Snowy Slopes|E6EEF5|O|Snowy mountainside.|snowy slopes,snowy slope,snow slopes,snow slope,snowy mountain,snowy mountains,snow mountain,snow mountains,snowy mountainside\n" +
        "jagged_peaks|Jagged Peaks|DCE4EE|O|Sharp snowy summits.|jagged peaks,jagged peak,jagged mountains,sharp peaks,sharp mountains,spiky mountains,pointy mountains\n" +
        "frozen_peaks|Frozen Peaks|BFD8F0|O|Icy summits, packed ice.|frozen peaks,frozen peak,frozen mountains,frozen mountain,icy peaks,icy mountains,ice mountains,ice peaks\n" +
        "stony_peaks|Stony Peaks|A0A29E|O|Bare stone summits, calcite.|stony peaks,stony peak,stone peaks,stone mountains,stony mountains,rocky peaks,rocky mountains,bare mountains\n" +
        "meadow|Meadow|88C46E|O|High grassland with flowers and bees.|meadow,meadows,flower meadow,alpine meadow,highland meadow,mountain meadow\n" +
        "cherry_grove|Cherry Grove|F2A8C8|O|Pink cherry blossom.|cherry grove,cherry groves,cherry blossom,cherry blossoms,cherry forest,cherry trees,cherry woods,sakura,pink forest,pink trees\n" +
        "river|River|3F76E4|O|A narrow strip of water.|river,rivers,stream,streams,creek,creeks,brook,flowing water\n" +
        "frozen_river|Frozen River|A5C8F0|O|A river with ice on top.|frozen river,frozen rivers,icy river,ice river,frozen stream\n" +
        "beach|Beach|F4E6A8|O|Narrow sand at the water.|beach,beaches,sandy beach,sand beach,sandy shore\n" +
        "snowy_beach|Snowy Beach|F2EFD8|O|Icy shoreline.|snowy beach,snow beach,snowy beaches,icy beach,frozen beach,cold beach,snowy shore\n" +
        "stony_shore|Stony Shore|8A8D88|O|Stone and gravel at the water.|stony shore,stone shore,rocky shore,rocky beach,stone beach,stony beach,cliff shore,rocky coast\n" +
        "ocean|Ocean|2F5FB8|O|The ordinary sea.|ocean,oceans,sea,seas,ordinary ocean,regular ocean,normal ocean,open sea,open water\n" +
        "deep_ocean|Deep Ocean|1C3A80|O|Deeper, darker sea.|deep ocean,deep oceans,deep sea,deep water,deep seas,ocean depths,dark ocean\n" +
        "warm_ocean|Warm Ocean|2FB8D0|O|Bright blue, coral, tropical fish.|warm ocean,warm oceans,warm sea,coral reef,coral ocean,tropical ocean,tropical sea,coral sea,reef\n" +
        "lukewarm_ocean|Lukewarm Ocean|3A9AC8|O|Warm-ish blue, sand floor.|lukewarm ocean,lukewarm oceans,lukewarm sea,mild ocean,tepid ocean\n" +
        "deep_lukewarm_ocean|Deep Lukewarm Ocean|2A78A8|O|Deeper warm-ish sea.|deep lukewarm ocean,deep lukewarm sea,deep warm ocean\n" +
        "cold_ocean|Cold Ocean|3A6A98|O|Grey-blue, gravel floor.|cold ocean,cold oceans,cold sea,chilly ocean,cool ocean\n" +
        "deep_cold_ocean|Deep Cold Ocean|284E78|O|Deep grey-blue.|deep cold ocean,deep cold sea,deep chilly ocean\n" +
        "frozen_ocean|Frozen Ocean|8AA8D8|O|Sea with ice floes on top.|frozen ocean,frozen oceans,frozen sea,icy ocean,ice ocean,ice sea,icy sea,arctic ocean,iceberg ocean,icebergs\n" +
        "deep_frozen_ocean|Deep Frozen Ocean|6A84B0|O|Deep sea under ice and icebergs.|deep frozen ocean,deep frozen sea,deep icy ocean,deep ice ocean\n" +
        "mushroom_fields|Mushroom Fields|9B7CA8|O|Mycelium and giant mushrooms. Rare.|mushroom fields,mushroom field,mushroom island,mushroom islands,mushroom biome,mooshroom island,mycelium island,mushroom\n" +
        "dripstone_caves|Dripstone Caves|8F7A62|C|Underground, stone spikes.|dripstone caves,dripstone cave,dripstone,stalactite cave,stalactites,stalagmites,pointed dripstone\n" +
        "lush_caves|Lush Caves|5CA84A|C|Underground, moss, azalea, glow berries.|lush caves,lush cave,lush cavern,moss cave,azalea cave,glow berry cave\n" +
        "deep_dark|Deep Dark|103A4A|C|Underground, sculk, ancient cities.|deep dark,the deep dark,sculk caves,sculk cave,sculk biome,warden biome,warden caves\n" +
        "nether_wastes|Nether Wastes|8A2F2F|N|Red netherrack, lava, ghasts.|nether wastes,nether waste,netherrack plains,nether biome,the nether,nether\n" +
        "soul_sand_valley|Soul Sand Valley|5A5A6A|N|Soul sand, blue fire, skeletons.|soul sand valley,soul valley,soul sand biome,soul sand desert,blue fire valley\n" +
        "crimson_forest|Crimson Forest|A02A3A|N|Red huge fungi, weeping vines, piglins.|crimson forest,crimson woods,red nether forest,crimson fungus forest,crimson\n" +
        "warped_forest|Warped Forest|2A8A8A|N|Teal huge fungi, endermen.|warped forest,warped woods,teal nether forest,warped fungus forest,warped\n" +
        "basalt_deltas|Basalt Deltas|5A5A5E|N|Basalt columns and lava lakes.|basalt deltas,basalt delta,basalt columns,basalt biome,delta,deltas\n" +
        "the_end|The End|D8D8A8|E|The main island: end stone, obsidian pillars.|the end,end main island,main end island,end island,end dimension,end biome\n" +
        "small_end_islands|Small End Islands|C8C898|E|Little floating islands in the void.|small end islands,small end island,end islets,floating end islands,outer end islands\n" +
        "end_midlands|End Midlands|C8C080|E|Flat end stone with chorus plants.|end midlands,end midland,midlands\n" +
        "end_highlands|End Highlands|D0C888|E|High end stone, chorus forest, end cities.|end highlands,end highland,highlands\n" +
        "end_barrens|End Barrens|B8B090|E|Bare edge of the End islands.|end barrens,end barren,barrens";

    // id | name | colour | dim | note | aliases | habitat biomes (comma) or - | habitat note
    private static final String STRUCTURES =
        "village|Village|C8A064|O|Houses, farms and paths, with villagers.|village,villages,town,towns,settlement,villager village,villager town,npc village|plains,sunflower_plains,snowy_plains,desert,savanna,taiga,snowy_taiga,meadow|Villages generate in plains, desert, savanna, taiga and snowy biomes.\n" +
        "plains_village|Plains Village|C8A064|O|A village of oak houses on grass.|plains village,plains villages,plain village,grass village,grassland village,oak village,meadow village|plains,sunflower_plains,meadow|Plains villages generate in plains, sunflower plains and meadows.|village\n" +
        "desert_village|Desert Village|D8B878|O|Sandstone houses among cacti.|desert village,desert villages,sand village,sandstone village,cactus village|desert|Desert villages only generate in deserts.|village\n" +
        "savanna_village|Savanna Village|B89A5A|O|Dry acacia houses on yellow grass.|savanna village,savanna villages,savannah village,acacia village,dry village|savanna|Savanna villages only generate in savannas.|village\n" +
        "snowy_village|Snowy Village|DCE6EE|O|Snow-covered spruce houses.|snowy village,snow village,snowy villages,ice village,winter village,cold village|snowy_plains|Snowy villages generate in snowy plains.|village\n" +
        "taiga_village|Taiga Village|8A6A44|O|Spruce houses in the pines.|taiga village,taiga villages,spruce village,pine village,forest village|taiga,snowy_taiga|Taiga villages generate in taiga and snowy taiga.|village\n" +
        "pillager_outpost|Pillager Outpost|5A4630|O|A dark oak watchtower with pillagers.|pillager outpost,pillager outposts,outpost,outposts,pillager tower,illager outpost,illager tower,pillager watchtower|plains,sunflower_plains,desert,savanna,taiga,snowy_plains,snowy_taiga,grove,meadow,snowy_slopes,jagged_peaks,frozen_peaks,stony_peaks,cherry_grove|Outposts generate in flat and mountain biomes, never in forests or oceans.\n" +
        "woodland_mansion|Woodland Mansion|4A3A2A|O|A huge mansion with evokers, vindicators and allays.|woodland mansion,woodland mansions,mansion,mansions,illager mansion,evoker mansion,big mansion|dark_forest,pale_garden|Mansions only generate in dark forests (and pale gardens).\n" +
        "ocean_monument|Ocean Monument|4FA8A0|O|A prismarine temple with guardians and gold.|ocean monument,ocean monuments,monument,monuments,guardian temple,guardian monument,sea temple,underwater temple,prismarine temple|deep_ocean,deep_lukewarm_ocean,deep_cold_ocean,deep_frozen_ocean|Monuments sit in deep ocean.\n" +
        "ocean_ruin|Ocean Ruins|7A8A7A|O|Sunken stone-brick ruins, warm or cold.|ocean ruin,ocean ruins,underwater ruins,sunken ruins,sea ruins,underwater ruin,sunken ruin|ocean,deep_ocean,warm_ocean,lukewarm_ocean,deep_lukewarm_ocean,cold_ocean,deep_cold_ocean,frozen_ocean,deep_frozen_ocean|Ocean ruins generate in any ocean.\n" +
        "warm_ocean_ruin|Warm Ocean Ruins|8AA89A|O|Sandstone ruins in warm water.|warm ocean ruin,warm ocean ruins,warm ruins,warm underwater ruins,sandstone ruins,tropical ruins|warm_ocean,lukewarm_ocean,deep_lukewarm_ocean|Warm ruins are in warm and lukewarm oceans.|ocean_ruin\n" +
        "cold_ocean_ruin|Cold Ocean Ruins|6A7A82|O|Stone-brick ruins in cold water.|cold ocean ruin,cold ocean ruins,cold ruins,cold underwater ruins,stone brick ruins,frozen ruins|ocean,deep_ocean,cold_ocean,deep_cold_ocean,frozen_ocean,deep_frozen_ocean|Cold ruins are in ocean, cold ocean and frozen ocean.|ocean_ruin\n" +
        "shipwreck|Shipwreck|7A5A3A|O|A wrecked wooden ship with loot.|shipwreck,shipwrecks,ship wreck,wrecked ship,sunken ship,sunken boat,wrecked boat,shipwrecked,ship|ocean,deep_ocean,warm_ocean,lukewarm_ocean,deep_lukewarm_ocean,cold_ocean,deep_cold_ocean,frozen_ocean,deep_frozen_ocean,beach,snowy_beach|Shipwrecks sit on the sea floor and on beaches.\n" +
        "ocean_shipwreck|Ocean Shipwreck|6A4A32|O|A wreck on the sea floor.|ocean shipwreck,underwater shipwreck,sea shipwreck,sunken shipwreck,ocean floor shipwreck|ocean,deep_ocean,warm_ocean,lukewarm_ocean,deep_lukewarm_ocean,cold_ocean,deep_cold_ocean,frozen_ocean,deep_frozen_ocean|Ocean shipwrecks sit on the sea floor.|shipwreck\n" +
        "beached_shipwreck|Beached Shipwreck|8A6A44|O|A wreck washed up on a beach.|beached shipwreck,beach shipwreck,shore shipwreck,washed up ship,stranded ship,beached ship|beach,snowy_beach|Beached shipwrecks are on beaches.|shipwreck\n" +
        "buried_treasure|Buried Treasure|E8D08A|O|A chest buried under a beach.|buried treasure,buried treasures,treasure chest,buried chest,pirate treasure,beach treasure|beach,snowy_beach|Buried treasure is only under beaches.\n" +
        "desert_pyramid|Desert Pyramid|E0C070|O|A sandstone pyramid with a hidden trap room.|desert pyramid,desert pyramids,desert temple,desert temples,sand pyramid,sandstone pyramid,pyramid,pyramids|desert|Desert pyramids only generate in deserts.\n" +
        "jungle_temple|Jungle Temple|6A7A5A|O|A mossy stepped temple with arrow traps.|jungle temple,jungle temples,jungle pyramid,jungle ruins,mossy temple,jungle shrine|jungle,bamboo_jungle,sparse_jungle|Jungle temples only generate in jungles.\n" +
        "igloo|Igloo|EAF2F8|O|A snow dome, sometimes with a hidden basement.|igloo,igloos,ice house,snow house,snow dome,eskimo house|snowy_plains,snowy_taiga,snowy_slopes|Igloos generate in snowy plains, snowy taiga and snowy slopes.\n" +
        "swamp_hut|Swamp Hut|5A4A32|O|A witch hut on stilts.|swamp hut,swamp huts,hut,huts,witch hut,witch huts,witch house,witch s hut,witches hut,witch cottage,witch s house,swamp house|swamp|Witch huts only generate in swamps.\n" +
        "mineshaft|Mineshaft|7A5A3A|O|Abandoned mine tunnels, rails and cobwebs.|mineshaft,mineshafts,mine shaft,mine shafts,abandoned mine,abandoned mineshaft,abandoned mines,old mine|-|Mineshafts generate almost anywhere underground, so a biome map cannot place them.\n" +
        "stronghold|Stronghold|6A6A6A|O|Stone-brick complex with the end portal.|stronghold,strongholds,end portal,end portals,portal room,end portal room|-|There are only 128 strongholds, in rings around the origin. They need their own position list.\n" +
        "ancient_city|Ancient City|1A4A5A|O|A huge sunken city in the deep dark. Warden.|ancient city,ancient cities,sculk city,warden city,deep dark city|deep_dark|Ancient cities only generate in the deep dark.\n" +
        "trail_ruins|Trail Ruins|9A7A5A|O|Buried terracotta ruins with suspicious gravel.|trail ruins,trail ruin,archaeology ruins,archaeology site,buried ruins,suspicious gravel ruins,dig site|taiga,snowy_taiga,old_growth_pine_taiga,old_growth_spruce_taiga,old_growth_birch_forest,jungle|Trail ruins generate in taiga, old growth forests and jungle.\n" +
        "trial_chambers|Trial Chambers|B8704A|O|Underground copper chambers with trial spawners.|trial chambers,trial chamber,trial spawner,trial rooms,copper chambers,trials|-|Trial chambers are underground; they are placed from your seed, but what is above them is not recorded.\n" +
        "ruined_portal|Ruined Portal|3A2A5A|O|A broken obsidian portal with crying obsidian.|ruined portal,ruined portals,broken portal,broken nether portal,portal ruins,old portal,crying obsidian portal|-|Ruined portals are placed exactly from your seed, in the biomes the game allows.\n" +
        "monster_room|Monster Room|6A6A60|O|A small cobblestone room with a spawner and two chests.|monster room,monster rooms,dungeon,dungeons,spawner room,mob spawner,spawner,zombie dungeon|-|Dungeons are small and underground everywhere, so a biome map cannot place them.\n" +
        "desert_well|Desert Well|D8C27A|O|A small sandstone well in the sand.|desert well,desert wells,sand well,sandstone well,well|desert|Desert wells generate in deserts.\n" +
        "fossil|Fossil|C8C0A8|O|Giant bones buried in sand or swamp mud.|fossil,fossils,bones,giant bones,skeleton bones,dinosaur bones|desert,swamp,mangrove_swamp|Fossils generate in deserts and swamps.\n" +
        "amethyst_geode|Amethyst Geode|9A6AD0|O|A hollow purple crystal ball inside stone.|amethyst geode,amethyst geodes,geode,geodes,amethyst,amethysts,crystal cave|-|Geodes are buried everywhere, so a biome map cannot place them.\n" +
        "nether_fortress|Nether Fortress|4A2024|N|Dark nether-brick halls, blazes, wither skeletons.|nether fortress,nether fortresses,fortress,fortresses,nether brick fortress,blaze fortress,wither skeleton fortress|-|Fortresses are in the Nether, which this overworld map does not cover.\n" +
        "bastion_remnant|Bastion Remnant|3A3238|N|A blackstone fortress full of piglins and gold.|bastion remnant,bastion remnants,bastion,bastions,piglin bastion,piglin fortress,blackstone fortress|-|Bastions are in the Nether, which this overworld map does not cover.\n" +
        "nether_fossil|Nether Fossil|C8B898|N|Giant bones in the soul sand valley.|nether fossil,nether fossils,soul sand fossil,nether bones|soul_sand_valley|Nether fossils are in the Nether.\n" +
        "end_city|End City|B898B8|E|Purpur towers with shulkers, elytra and a ship.|end city,end cities,purpur city,purpur tower,end ship,elytra city,shulker city,elytra ship,end tower|-|End cities are in the End, which this overworld map does not cover.";

    /** Words that name a whole family of biomes or structures, not one exact thing. */
    private static final String FAMILIES =
        "Mountains|mountain,mountains,mountainous,mountain range,mountain ranges,high mountains,big mountains,tall mountains,big mountain,tall mountain,high mountain,peak,peaks,summit,summits,mountain top,mountain tops|jagged_peaks,frozen_peaks,stony_peaks,snowy_slopes,windswept_hills,windswept_gravelly_hills,windswept_forest|\n" +
        "Snowy mountains|snowy mountains,snowy mountain,snow mountains,snow mountain,snowy peaks,snowy peak,snow peaks,white mountains,icy mountain,icy mountains,ice mountain,ice mountains,snow capped mountains,snow capped mountain,snowcapped mountains|snowy_slopes,jagged_peaks,frozen_peaks,grove|\n" +
        "Hills|hill,hills,hilly,rolling hills,windy hills|windswept_hills,windswept_gravelly_hills,windswept_forest|\n" +
        "Snowy land|snow,snowy,snowy land,snowy biome,snowy biomes,snow biome,snow biomes,winter,winter biome,frozen land,icy,ice,cold biome,cold biomes,cold,frozen|snowy_plains,snowy_taiga,snowy_slopes,snowy_beach,ice_spikes,grove,jagged_peaks,frozen_peaks|\n" +
        "Shoreline|shore,shores,shoreline,shorelines,coast,coastline,coasts,coastal,seaside,water edge|beach,snowy_beach,stony_shore|\n" +
        "Caves|cave,caves,cavern,caverns,cave biome,underground,caving|dripstone_caves,lush_caves,deep_dark|\n" +
        "Temples|temple,temples,pyramid structure||desert_pyramid,jungle_temple\n" +
        "Ruins|ruin,ruins||ocean_ruin,trail_ruins\n" +
        "Underwater structures|underwater structure,underwater structures||ocean_ruin,shipwreck\n";

    /**
     * "any forest", "all oceans", "every taiga": the whole family, when you really do mean all of them.
     * Plain "forest" or "ocean" means that one biome only, so "ocean" and "warm ocean" are separate.
     */
    private static final String[][] ANY = {
        {"forest", "forest,flower_forest,birch_forest,old_growth_birch_forest,dark_forest"},
        {"plains", "plains,sunflower_plains"},
        {"taiga", "taiga,snowy_taiga,old_growth_pine_taiga,old_growth_spruce_taiga"},
        {"jungle", "jungle,sparse_jungle,bamboo_jungle"},
        {"badlands", "badlands,wooded_badlands,eroded_badlands"},
        {"savanna", "savanna,savanna_plateau,windswept_savanna"},
        {"ocean", "ocean,deep_ocean,warm_ocean,lukewarm_ocean,deep_lukewarm_ocean,cold_ocean,deep_cold_ocean,frozen_ocean,deep_frozen_ocean"},
        {"deep ocean", "deep_ocean,deep_lukewarm_ocean,deep_cold_ocean,deep_frozen_ocean"},
        {"warm ocean", "warm_ocean,lukewarm_ocean,deep_lukewarm_ocean"},
        {"cold ocean", "cold_ocean,deep_cold_ocean"},
        {"frozen ocean", "frozen_ocean,deep_frozen_ocean"},
        {"river", "river,frozen_river"},
        {"beach", "beach,snowy_beach"},
        {"swamp", "swamp,mangrove_swamp"},
        {"windswept", "windswept_hills,windswept_gravelly_hills,windswept_forest,windswept_savanna"},
    };
    private static final String[] ANY_WORDS = {"any", "all", "every", "any kind of", "all kinds of", "every kind of"};

    /** Aliases where the family reading should win over the single biome that also owns the word. */
    private static final Set<String> GENERIC_OVERRIDES = new HashSet<>(Arrays.asList(
        "snowy mountain", "snow mountain", "icy mountain", "ice mountain", "snowy peak", "hill", "cave", "mountain", "peak",
        "underground", "windy hill", "temple", "pyramid structure"));

    static {
        for (String line : BIOMES.split("\n")) {
            String[] f = line.split("\\|", -1);
            add(new Item(f[0], f[1], Kind.BIOME, f[3].charAt(0), Integer.parseInt(f[2], 16), f[4], f[5].split(","), null, null, null));
        }
        for (String line : STRUCTURES.split("\n")) {
            String[] f = line.split("\\|", -1);
            String[] hab = f[6].equals("-") ? null : f[6].split(",");
            add(new Item(f[0], f[1], Kind.STRUCTURE, f[3].charAt(0), Integer.parseInt(f[2], 16), f[4], f[5].split(","), hab, f[7], f.length > 8 ? f[8] : ""));
        }
        // Families first would let them shadow exact names, so exact names go in first and families only
        // take an alias nothing exact has already claimed.
        for (Item it : ITEMS) {
            Ref r = new Ref(it.name);
            if (it.structure()) r.structures.add(it.id); else r.biomes.add(it.id);
            index(it.name, r, false);
            for (String a : it.aliases) index(a, r, false);
            index(it.id.replace('_', ' '), r, false);
        }
        for (String line : FAMILIES.split("\n")) {
            if (line.isBlank()) continue;
            String[] f = line.split("\\|", -1);
            Ref r = new Ref(f[0]);
            r.family = true;
            if (!f[2].isEmpty()) r.biomes.addAll(Arrays.asList(f[2].split(",")));
            if (f.length > 3 && !f[3].isEmpty()) r.structures.addAll(Arrays.asList(f[3].split(",")));
            if (r.biomes.isEmpty() && r.structures.isEmpty()) continue;
            for (String a : f[1].split(",")) index(a, r, true);
        }
        for (String[] a : ANY) {
            Ref r = new Ref("Any " + a[0]);
            r.family = true;
            r.biomes.addAll(Arrays.asList(a[1].split(",")));
            for (String w : ANY_WORDS) { index(w + " " + a[0], r, true); index(w + " " + a[0] + " biome", r, true); }
        }
    }

    private static void add(Item it) {
        ITEMS.add(it);
        BY_ID.put(it.id, it);
    }

    private static void index(String phrase, Ref r, boolean family) {
        String key = norm(phrase);
        if (key.isEmpty()) return;
        longestAlias = Math.max(longestAlias, key.split(" ").length);
        Ref old = ALIASES.get(key);
        // First writer wins. The one exception: a family written on purpose ("forest", "mountains",
        // "coast") replaces the single biome that happens to share the word, listed below.
        if (old == null || (family && !old.family && GENERIC_OVERRIDES.contains(key))) ALIASES.put(key, r);
    }


    // ------------------------------------------------------------------ public lookups

    public static List<Item> items() { return Collections.unmodifiableList(ITEMS); }

    public static Item byId(String id) { return BY_ID.get(id); }

    public static int longestAliasWords() { return longestAlias; }

    /** Looks up an already-normalised phrase. */
    public static Ref find(String normalised) { return ALIASES.get(normalised); }

    public static Set<String> aliasKeys() { return Collections.unmodifiableSet(ALIASES.keySet()); }

    /** Display name for an id (biome or structure). */
    public static String nameOf(String id) {
        Item it = BY_ID.get(id);
        return it != null ? it.name : id.replace('_', ' ');
    }

    // ------------------------------------------------------------------ normalising

    /**
     * Lower-cases, drops punctuation and reduces each word to a singular stem, so "Snowy Mountains",
     * "snowy mountain" and "snowy-mountains" all come out as "snowy mountain".
     */
    public static String norm(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.toLowerCase(Locale.ROOT).replace("'", "").replace('_', ' ').replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(stem(w));
        }
        return sb.toString();
    }

    public static String stem(String w) {
        if (w.length() <= 3) return w;
        if (w.endsWith("ies") && w.length() > 4) return w.substring(0, w.length() - 3) + "y";
        if (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("sses") || w.endsWith("xes")) return w.substring(0, w.length() - 2);
        if (w.endsWith("ss") || (w.endsWith("us") && !w.endsWith("aus")) || w.endsWith("is")) return w;
        if (w.endsWith("s")) return w.substring(0, w.length() - 1);
        return w;
    }
}
