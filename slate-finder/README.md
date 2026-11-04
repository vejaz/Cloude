# Describing finder - new tab

Only the **Describing finder** tab changed. The Underground and Surface tabs are byte-for-byte the same code
(screenshots of both tabs are pixel-identical to your previous build).

## Install (nothing of yours is overwritten)

1. Copy `SlateFinder-20261104-220542.jar` into `C:\Users\Namai\Sites\slate-finder\builds\`.
2. Start Slate Finder as usual. `SlateFinder.vbs` runs whichever jar in `builds\` is newest, which is this one.

To go back, delete that one jar. Your older builds are untouched.

## Latest update (exact world, anywhere-spot, test coordinates, storage)

- **Exact world.** In "See map" there is a new **Exact world** box. Press **Make it exact** and the map around the coordinates
  becomes the real blocks of your world: the app uses the seed from your biome map and has the game's own world generator
  (Minecraft 1.21.11) make that area, then reads every block, tree, house and plant and draws it with the game's own textures.
  You can turn it, walk through it (even into a village house), see it from above. "Show the exact blocks" switches back to the
  generated picture. How it works, step by step:
  1. It asks once (you agree to Minecraft's EULA) before downloading the game's server (about 56 MB, from Mojang) into
     `%APPDATA%\SlateFinder\exact\server`.
  2. It runs that server quietly on this computer, reachable only from this computer, with your seed, and asks it to load the area.
     Java 21 (the one this app already runs on) is used. Only the part around the marked spot is made: about 160 blocks across
     (Settings > Exact world: Small about 100, Normal about 160, Large about 320), roughly 120 chunks, so it is quick.
  3. It reads the finished chunks, keeps a small copy (a few MB) in `%APPDATA%\SlateFinder\exact\places`, and deletes the big
     world files. The next time you open the same spot it opens at once, with no server.
  - The game's pictures are read from your own Minecraft jar (`.minecraft\versions\1.21.11`); if it is not found the app's own
    painted textures are used. Nothing from the game is copied into this app.
  - It is the **natural world only**: what players built on a server is not in the seed. The seed and version must match the
    world you are looking at.
  - Past the edge of the exact area the picture is still the generated one, and the window says so.
- **Faster 3D.** A full-resolution picture of the map now takes about a quarter of the time (2.4 s to 0.5 s on a 4-core test machine,
  with byte-identical pixels): empty sky and empty space above the terrain are crossed a whole chunk at a time, and chunk lookups no
  longer allocate. A picture that is no longer wanted (you zoomed or turned again) is dropped at once instead of being finished first.
- **Test coordinates window fixed.** Closing it with the X used to only hide it, so pressing the button again did nothing. It now
  opens every time (checked by `ReverseReopenTest`, which fails without the fix).
- **3D view: zoom to the pointer, pins that stay put, click to stand there.** The wheel zooms toward whatever is under the pointer
  (3D, standing and top-down). Pins, names and village dots are placed with the same camera as the picture on screen, so they no
  longer slide around while you drag and the picture catches up. Click a pin or a village dot (the pointer turns into a hand) and
  you stand there; dragging from a pin only turns the view.
- **Quicker, simpler pictures while you move.** While the view is moving it draws flat block colours (easy to read, drawn in a
  fraction of the time); the detailed textured picture with shadows follows once you hold still for a moment. Settings > The map and
  the View box have **Simple look (flat colours, fastest)** to keep it flat all the time.
- **Storage you control.** Settings > Storage for the maps shows `used of limit` (default 10 GB) with a bar and a breakdown, lets
  you change the limit (1 to 200 GB), **Delete the memory of the maps** (saved places and the 3D pictures of the book), remove
  the downloaded server, and open the folder. When the limit is reached the places you looked at longest ago are deleted first.
  If a new place would not fit it tells you and generates nothing. Settings > Exact world: how big an area (small / normal /
  large) and whether saved places open by themselves.
- **Say where you want the coordinates.** "I want the coordinates next to the village and the river" (also: "tp me near...",
  "I am standing next to...", "a place between a village and a river", "I want to build near...") makes the spot *anywhere near all
  of them*, none of them is the spot. One closeness is shared by the list ("next to the village and the river"). There is also a
  button under the rows: "the spot can be anywhere near all of these", and "make this the spot" still works on any row.
- **Test coordinates.** The new "Test coordinates" button (next to Settings) takes X and Z, looks around them on your biome map and
  writes what you could have described to get exactly that place, for example `searching for the swamp, the warm ocean within 800
  blocks, the snowy slopes within 2000 blocks, ...`. Every description is read and searched for real before it is shown, so pasting
  it into the search finds the same spot. It says how many places fit it (often one) and has "Copy the text" and "Use it in the
  search".

## Earlier update (maps, 3D pictures, settings)

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
- **The exact world was tested against a made-up world written in the game's own file format and a stand-in for the server.** The step
  that starts Mojang's real server could not be run where this was built, so the first run on your computer is the real test. If it
  fails the box shows why (with the server's last lines); the generated map keeps working either way.

## For the source

`src/slatefinder/*.java` are the new and replaced files. `DescribePanel.java` replaces your old one; `Describe.java`,
`BiomeBook.java`, `BiomeQuery.java` and the rest are untouched and still used. To rebuild a jar from an existing one:

```
./build-patch.sh path/to/SlateFinder-<old>.jar
```

`test/` holds `ReaderTest` (1,056 checks), `World3DTest` (227), `ReverseReopenTest` (15), `ViewTest` (34), `ExactTest` (66: reads a made-up world written in the game's file format, and runs a stand-in for the game's server), `FullScale` (a 225,000-block map: about 1.5 s per search, under 300 MB),
`PreviewSheet`, and `PanelShot`/`TabCompare` (render the real window headlessly). They refuse to write outside the temp folder.
