# Describing finder - new tab

Only the **Describing finder** tab changed. The Underground and Surface tabs are byte-for-byte the same code
(screenshots of both tabs are pixel-identical to your previous build).

## Install (nothing of yours is overwritten)

1. Copy `SlateFinder-20261104-171036.jar` into `C:\Users\Namai\Sites\slate-finder\builds\`.
2. Start Slate Finder as usual. `SlateFinder.vbs` runs whichever jar in `builds\` is newest, which is this one.

To go back, delete that one jar. Your older builds are untouched.

## Latest update (maps, 3D pictures, settings)

- **See map.** Every place that is found has a "See map" button next to its coordinates. It opens a window with the place drawn
  around the coordinates in 3D, with a pin on the spot and a pin for every biome and structure you described, found near it, joined
  by lines where one was described next to another. A check list says whether everything you described is really there and how far
  each thing is. Views: 3D (turn, zoom, move), Stand there (walk around as a player, W A S D, F to fly), Top-down map (pan, zoom,
  coordinate grid, scale bar). Options on the right: time of day, how far you can see, field of view, detail, and what to show.
  Double-click anywhere to stand there. "Save picture" saves a PNG. "Copy coordinates" / "Copy /tp" show a message.
- **What is real in the map, and what is not.** The biome of every place and the positions of villages are real, from your biome map
  (one sample every 64 blocks). Everything finer - the exact hills, where each tree stands, the shape of a house - is generated to look
  right. It is a faithful picture of the place, not a copy of the blocks in your game; the window says so.
- **3D pictures in the book.** Every biome and structure is a small 3D scene with its own animal or buildings. Click one to open it as a
  live 3D scene: drag to turn, wheel to zoom, W A S D to walk, Esc to close. They are drawn the first time the tab opens (a minute in the
  background) and kept in %APPDATA%\SlateFinder\previews3d-v3, so they appear instantly after that.
- **Settings** (the gear in the book): accent colour, picture size, the player name used in /tp, and the map's defaults.
- Distances are now circles, not squares: "within 300 blocks" no longer reaches 450 blocks diagonally.

## Earlier update (chains, editable rows, clearer pictures)


- **Chains.** Things can be described relative to each other, not just to one spot, for example:
  "there's a desert village and next to the village is a desert and the savanna is touching a plains biome and the plains biome is
  touching a jungle biome and in the jungle there's a jungle temple and at the desert village there's a cherry blossom biome and
  touching the cherry blossom biome is a plains biome and touching the plains biome is a warm ocean".
  "a plains biome" starts a new plains, "the plains biome" is the latest one named, and "the village" after "desert village" is the
  same village. "touching" means the next map cell, "next to" about 100 blocks, "close to" about 300, "within 50 of" exactly that.
  Something that is not tied to anything in a story-style sentence is assumed to touch the thing named just before it, and the tab
  says so. The search follows the chain exactly (checked against a slow brute-force version).
- **The spot is the first thing you name** (the village in your examples), or whatever follows "searching for", "find",
  "looking for"... Results are that thing's coordinates; with a village list they are the village's exact position.
- **Rows under the text box are editable.** Click a distance, delete the number, type a new one (500, 1.5k or 400-800), press Enter.
  Press "make this the spot" on any row to get the coordinates of that biome or structure instead. "undo my changes" puts it back.
- **Pictures you can tell apart.** Every biome has its own animal or prop (panda, camel, goat, creaking, warden, axolotl, dolphin...)
  and every picture has a corner badge showing the block it is known by. The badge colour is the colour dot in the rows.
- **API key fields keep the key.** The saved key shows as dots and stays when you click the field; "Show" reveals it.

## Earlier update

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
- **Every way of saying how far**: `within 500`, `up to 500`, `at most 500`, `no more than 500`, `less than 500`, `under 500`,
  `inside 500`, `a radius of 500`, `500 block radius`, `about/roughly 500`, `1.5k`, `2 km`, `32 chunks`; ranges `400 to 800 blocks`,
  `between 400 and 800`, `400-800`; and minimums `at least 500 away`, `more than 500`. A range is a ring on the map.

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

`test/` holds `ReaderTest` (1,000 checks), `FullScale` (a 225,000-block map: about 1.5 s per search, under 300 MB),
`PreviewSheet`, and `PanelShot`/`TabCompare` (render the real window headlessly). They refuse to write outside the temp folder.
