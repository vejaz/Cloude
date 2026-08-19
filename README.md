# Tap the Cow 🐄

A one-page site with a whole cow standing side-on against black. Tap it
and it goes **MOOOOOOWWWW** — loudly.

## Run it

No build step, no dependencies. Open `index.html` in a browser, or serve
the folder:

```sh
python3 -m http.server 8000
```

then visit http://localhost:8000

## How the moo works

There is no audio file. `moo.js` synthesises the moo sample by sample on
every tap, using source-filter synthesis — the model speech synthesisers
use — rather than oscillators through a filter, which sound buzzy however
you envelope them:

- **Source**: a Rosenberg glottal pulse train, the shape of the air pulse as
  vocal folds swing open and slam shut. Its spectral roll-off is the part a
  sawtooth gets wrong.
- **Filter**: four resonators in series standing in for the vocal tract,
  retuned ~1500 times a second so the mouth shuts for the "mmm", opens
  through the "ooo" and closes again for the tail.
- **Formants sized for a cow, not a person.** Formants scale inversely with
  vocal tract length, and a cow's is roughly 40cm against a human's 17cm, so
  the resonances sit about half as high as textbook vowel values. Human
  formants on a low pitch just sound like someone imitating a cow.
- **Jitter and shimmer** as a small random walk rather than an LFO.
  Metronomic vibrato is the biggest giveaway of a synthetic voice; real ones
  wander.
- **Creak in the tail**: every other pulse weakens as the breath runs out.
- Lip radiation as a derivative, then normalisation and gentle limiting.

Each tap re-renders with a fresh pitch, length and jitter, so no two moos
are identical.

## Note on volume

The page plays as loud as the browser allows — the rest is the device
volume. On iPhone, Web Audio follows the physical silent switch, so flip the
switch off if you hear nothing.

## Files

| File | What it is |
| --- | --- |
|  x |
| `style.css` | layout, colours, the shake animation |
| `moo.js` | the moo synthesiser and the tap handling |
