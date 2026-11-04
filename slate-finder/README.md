# Describing finder - new tab

Only the **Describing finder** tab changed. The Underground and Surface tabs are byte-for-byte the same code
(screenshots of both tabs are pixel-identical to your previous build).

## Install (nothing of yours is overwritten)

1. Copy `SlateFinder-20261104-130947.jar` into `C:\Users\Namai\Sites\slate-finder\builds\`.
2. Start Slate Finder as usual. `SlateFinder.vbs` runs whichever jar in `builds\` is newest, which is this one.

To go back, delete that one jar. Your older builds are untouched.

## Latest update

- **The number of places is next to the text box**, with the FIND IT button beside it, so nothing needs scrolling.
- **Exactly what you say.** `ocean` is the ocean only; `warm ocean`, `lukewarm ocean`, `cold ocean`, `frozen ocean`, `deep ocean`
  are each their own. Same for `plains` / `sunflower plains`, `forest` / `birch forest`, `river` / `frozen river`, and so on.
  The old map code treated `ocean` as every ocean; that is fixed. Say `any ocean`, `all forests` or `every taiga` for the whole family.
- **Specific structures**: `plains village`, `desert village`, `savanna village`, `snowy village`, `taiga village`,
  `warm ocean ruins`, `cold ocean ruins`, `beached shipwreck`, `ocean shipwreck`. With a village list they are cut to the biome
  the village really sits in, so they are exact.
- **Comma lists and typos**: `plains vilage, plais biome` reads as a plains village and the plains biome.
- **The book lists only what can be searched**: overworld biomes your map holds, and structures that are exact or can be found by
  biome. Nether, End and anywhere-underground structures are gone from the list (they are still explained if you type them).
- The "Try" examples are removed.

## What the tab does

- **Understands every overworld biome and structure** and the words people use for them: `planes`, `grassland`, `snowy mountains`, `mesa`, `sakura`, `witch hut`, `coral reef`...
  A word can mean a whole family (`mountains` = every peak, slope and windswept hill; `forest`, `taiga`, `ocean`, `river`).
- **Reads a whole sentence**: which place is "the spot", what is near it and how far (`right next to`, `a bit further`,
  `800 blocks`, `1.5k`), and what is absent (`no desert, ocean or jungle`).
- **Says every error out loud**, next to the text: misspellings (with a one-click fix), words it ignored, things the map does not
  record (mobs, blocks, weather...), Nether/End places on an overworld map, structures it cannot place.
- **The book**: every biome and structure with a picture. Click a picture to see it full size, `Esc` (or a click) closes it,
  arrow keys browse, `Enter` adds it to your description. Search forgives typos (`mountians` finds the mountains).
- **Cleaner results**: matching cells are grouped into places, nearest to spawn first, each with its size and extent.
  The funnel on the left shows how many places survive each thing you described. Copy and Use work as before.
- The search is also faster: each "near" claim is one sweep over the map, however far it is said to be.

## Honest limits

- **The pictures are painted illustrations, not game screenshots.** There are no game assets in the app, so each is drawn in
  code in a block style.
- **Structures need a position list to be exact.** Only `villages.txt` exists on your machine today. For other structures the
  tab searches the biomes they generate in and labels the result "by biome". Structures that appear everywhere underground
  (mineshaft, stronghold, trial chambers, dungeons, geodes) cannot be placed from a biome map, and it says so.
  If you generate `<structure>s.txt` files in the same format as `villages.txt` they are used automatically.
- Nether and End biomes are recognised if typed, but your map covers the overworld only, so they are reported rather than searched.
- Built and tested here on a made-up biome map, not your seed. The real `biome-grid-64.bin` is read by the same code as before.

## For the source

`src/slatefinder/*.java` are the new and replaced files. `DescribePanel.java` replaces your old one; `Describe.java`,
`BiomeBook.java`, `BiomeQuery.java` and the rest are untouched and still used. To rebuild a jar from an existing one:

```
./build-patch.sh path/to/SlateFinder-<old>.jar
```

`test/` holds `ReaderTest` (688 checks), `FullScale` (a 225,000-block map: about 1.5 s per search, under 300 MB),
`PreviewSheet`, and `PanelShot`/`TabCompare` (render the real window headlessly). They refuse to write outside the temp folder.
