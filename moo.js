/* Tap the cow -> a loud, reasonably natural moo.

   The moo is synthesised from scratch on every tap, so there is no audio
   file to load. It uses source-filter synthesis -- the model speech
   synthesisers use -- rather than plain oscillators: a glottal pulse train
   (the voice source) is shaped by a cascade of resonators (the vocal
   tract). Oscillators through a filter sound buzzy however you envelope
   them, because a sawtooth is not the shape a throat actually makes. */

(function () {
  "use strict";

  var cow   = document.getElementById("cow");
  var moo   = document.getElementById("moo");
  var hint  = document.getElementById("hint");
  var mouth = document.getElementById("mouth");

  var MOUTH_CLOSED = "M26 120 Q42 128 58 123";
  var MOUTH_OPEN   = "M20 110 Q42 142 62 117 Q42 124 20 110 Z";

  var ctx = null;
  var busyUntil = 0;

  /* ---------- the voice ---------- */

  // Rosenberg glottal flow: the shape of the air pulse as the vocal folds
  // swing open and slam shut. Its gentle spectral roll-off is exactly what
  // a sawtooth gets wrong.
  function glottalFlow(p, tp, tn) {
    if (p < tp) return 0.5 * (1 - Math.cos(Math.PI * p / tp));
    if (p < tp + tn) return Math.cos(Math.PI * (p - tp) / (2 * tn));
    return 0;
  }

  // How the vocal tract moves across the call: mouth shut for the "mmm",
  // wide open through the "ooo", closing again for the tail.
  //
  // These formants are far lower than the textbook vowel values, because
  // those are human. Formants scale inversely with the length of the
  // tract, and a cow's is roughly 40cm against our 17cm -- so its
  // resonances land about half as high. Human formants on a low pitch
  // just sound like a person imitating a cow.
  // [position, F1, F2, F3, F4, openness]
  var TRACT = [
    [0.00, 210, 560, 1000, 1650, 0.00],
    [0.14, 250, 620, 1060, 1700, 0.18],
    [0.30, 430, 780, 1300, 1950, 1.00],
    [0.60, 390, 740, 1250, 1900, 1.00],
    [0.80, 320, 660, 1150, 1800, 0.70],
    [1.00, 230, 570, 1020, 1650, 0.12]
  ];

  function tractAt(u, out) {
    var i = 1;
    while (i < TRACT.length - 1 && TRACT[i][0] < u) i++;
    var a = TRACT[i - 1], b = TRACT[i];
    var k = (u - a[0]) / (b[0] - a[0]);
    if (k < 0) k = 0; else if (k > 1) k = 1;
    for (var j = 1; j < 6; j++) out[j - 1] = a[j] + (b[j] - a[j]) * k;
  }

  // Pitch: a scoop up into the call, a long steady middle, and the falling
  // "wwww" as the breath runs out.
  function pitchAt(u, f0) {
    var p;
    if (u < 0.10)      p = 0.78 + (u / 0.10) * 0.24;
    else if (u < 0.50) p = 1.02 + (u - 0.10) / 0.40 * 0.05;
    else if (u < 0.74) p = 1.07 - (u - 0.50) / 0.24 * 0.07;
    else               p = 1.00 - Math.pow((u - 0.74) / 0.26, 1.5) * 0.40;
    return f0 * p;
  }

  function ampAt(u) {
    if (u < 0.05) return (u / 0.05) * 0.40;
    if (u < 0.20) return 0.40 + (u - 0.05) / 0.15 * 0.30;
    if (u < 0.32) return 0.70 + (u - 0.20) / 0.12 * 0.30;
    if (u < 0.70) return 1.00;
    if (u < 0.88) return 1.00 - (u - 0.70) / 0.18 * 0.30;
    return 0.70 * Math.pow(1 - (u - 0.88) / 0.12, 1.5);
  }

  function renderMoo(sr) {
    var dur = 2.15 + Math.random() * 0.35;
    var n = Math.floor(sr * dur);
    var buf = new Float32Array(n);

    var f0 = 132 * (0.92 + Math.random() * 0.17);   // a cow sits low
    var tp = 0.40, tn = 0.16;                       // glottal pulse shape

    var y1 = [0, 0, 0, 0], y2 = [0, 0, 0, 0];       // resonator state
    var ca = [0, 0, 0, 0], cb = [0, 0, 0, 0], cc = [0, 0, 0, 0];
    var bw = [0, 0, 0, 0];
    var tract = [0, 0, 0, 0, 0];

    var phase = 1, jitter = 0, shimmer = 1, alt = 1, prev = 0, lp = 0;
    var i, f;

    for (i = 0; i < n; i++) {
      var u = i / n;

      if ((i & 31) === 0) {            // retune the tract ~1500x a second
        tractAt(u, tract);
        bw[0] = 90 + (1 - tract[4]) * 90;
        bw[1] = 130; bw[2] = 200; bw[3] = 280;
        for (f = 0; f < 4; f++) {
          var r = Math.exp(-Math.PI * bw[f] / sr);
          var th = 2 * Math.PI * tract[f] / sr;
          cb[f] = 2 * r * Math.cos(th);
          cc[f] = -r * r;
          ca[f] = 1 - cb[f] - cc[f];
        }
      }

      // --- source ---
      phase += pitchAt(u, f0) * (1 + jitter) / sr;
      if (phase >= 1) {
        phase -= 1;
        // Jitter and shimmer as a small random walk, not a tidy LFO.
        // Metronomic vibrato is the single biggest giveaway of a
        // synthetic voice; real ones wander.
        jitter = jitter * 0.82 + (Math.random() * 2 - 1) * 0.010;
        shimmer = 1 + (Math.random() * 2 - 1) * 0.07;
        alt = -alt;
      }

      var g = glottalFlow(phase, tp, tn) * shimmer;

      // The call goes rough as the breath runs out: every other pulse
      // weakens, which is the creak real animals trail off on.
      var rough = u > 0.72 ? (u - 0.72) / 0.28 : 0;
      if (alt < 0) g *= 1 - 0.22 * rough;

      // Breath, escaping mostly while the folds are open.
      var breath = (Math.random() * 2 - 1) *
                   (0.010 + 0.020 * tract[4]) *
                   (phase < tp + tn ? 1 : 0.35);

      // --- vocal tract: four resonators in series ---
      var x = g + breath;
      for (f = 0; f < 4; f++) {
        var y = ca[f] * x + cb[f] * y1[f] + cc[f] * y2[f];
        y2[f] = y1[f]; y1[f] = y; x = y;
      }

      // Lips radiate the pressure derivative; a shut mouth muffles it.
      var rad = x - prev;
      prev = x;
      var k2 = Math.exp(-2 * Math.PI * (350 + 2600 * tract[4]) / sr);
      lp = rad * (1 - k2) + lp * k2;

      buf[i] = lp * ampAt(u);
    }

    var peak = 0;
    for (i = 0; i < n; i++) { var a2 = buf[i] < 0 ? -buf[i] : buf[i]; if (a2 > peak) peak = a2; }
    if (peak > 0) { var norm = 0.99 / peak; for (i = 0; i < n; i++) buf[i] *= norm; }

    return buf;
  }

  /* ---------- playback ---------- */

  function audio() {
    if (ctx) return ctx;
    var AC = window.AudioContext || window.webkitAudioContext;
    ctx = new AC();

    // Gentle: the moo is already normalised, so this is here for level,
    // not for crushing it. Heavy compression was what made the old one
    // buzz.
    var comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -12;
    comp.knee.value = 20;
    comp.ratio.value = 3;
    comp.attack.value = 0.010;
    comp.release.value = 0.30;

    var makeup = ctx.createGain();
    makeup.gain.value = 1.25;

    // Rounds off anything that still pokes over, instead of letting it
    // hard-clip.
    var shaper = ctx.createWaveShaper();
    var m = 2048, curve = new Float32Array(m);
    for (var i = 0; i < m; i++) {
      var x = (i / (m - 1)) * 2 - 1;
      curve[i] = Math.tanh(x * 1.05) / Math.tanh(1.05);
    }
    shaper.curve = curve;
    shaper.oversample = "4x";

    var out = ctx.createGain();
    out.gain.value = 0.96;

    comp.connect(makeup);
    makeup.connect(shaper);
    shaper.connect(out);
    out.connect(ctx.destination);

    ctx._in = comp;
    return ctx;
  }

  function playMoo() {
    var ac = audio();
    if (ac.state === "suspended") ac.resume();

    var data = renderMoo(ac.sampleRate);
    var ab = ac.createBuffer(1, data.length, ac.sampleRate);
    ab.getChannelData(0).set(data);

    var src = ac.createBufferSource();
    src.buffer = ab;
    src.connect(ac._in);
    src.start();

    return data.length / ac.sampleRate;
  }

  /* ---------- interaction ---------- */

  function moooo() {
    var now = Date.now();
    if (now < busyUntil) return;      // let one moo finish before the next

    var dur;
    try {
      dur = playMoo();
    } catch (e) {
      dur = 2.3;                      // no audio? still do the animation
    }
    busyUntil = now + 350;

    if (hint) hint.style.display = "none";

    var m = "MO" + "O".repeat(5 + Math.floor(Math.random() * 5)) +
            "W".repeat(3 + Math.floor(Math.random() * 4)) + "!";
    moo.textContent = m;
    moo.classList.remove("show");
    void moo.offsetWidth;             // restart the animation
    moo.classList.add("show");

    cow.classList.add("mooing");
    mouth.setAttribute("d", MOUTH_OPEN);
    mouth.classList.add("open");

    setTimeout(function () {
      cow.classList.remove("mooing");
      mouth.setAttribute("d", MOUTH_CLOSED);
      mouth.classList.remove("open");
    }, dur * 1000);
  }

  cow.addEventListener("click", moooo);
  cow.addEventListener("touchstart", function (e) {
    e.preventDefault();               // fire instantly, skip the 300ms delay
    moooo();
  }, { passive: false });

  cow.addEventListener("keydown", function (e) {
    if (e.key === " " || e.key === "Spacebar") e.preventDefault();
  });
})();
