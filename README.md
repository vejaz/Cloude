# Tap the Cow 🐄

A one-page site with a whole cow standing side-on in the middle. Tap it
and it goes
**MOOOOOOWWWW** — loudly.

## Run it

No build step, no dependencies. Open  the page and the inline SVG cow in a browser, or serve
the folder:

```sh
python3 -m http.server 8000
```

then visit http://localhost:8000

## How the moo works

There is no audio file. `moo.js` synthesises the moo with the Web Audio API
each time you tap:

- two detuned sawtooth oscillators plus a sub square for the chesty body
- a pitch contour that rises into the "moo" and sags down for the "wwww"
- vibrato that speeds up towards the end, so it warbles like an animal
- a lowpass sweep that opens up like a mouth, plus bandpass formants for the
  vowel and a little filtered noise for breath
- a compressor into a soft clipper, which gets the level right up to the
  ceiling (~-6 dBFS RMS, peaking at 0.95) without hard-clipping

Each tap randomises the pitch and the number of O's, so no two moos are
identical.

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
