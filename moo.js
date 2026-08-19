/* Tap the cow -> a very loud "moooowwwwww".
   Everything is synthesised with the Web Audio API, so there are no
   audio files to download and it works offline. */

(function () {
  "use strict";

  var cow  = document.getElementById("cow");
  var moo  = document.getElementById("moo");
  var hint = document.getElementById("hint");
  var mouth = document.getElementById("mouth");

  var MOUTH_CLOSED = "M26 120 Q42 128 58 123";
  var MOUTH_OPEN   = "M20 110 Q42 142 62 117 Q42 124 20 110 Z";

  var ctx = null;
  var master = null;
  var busyUntil = 0;

  /* ---------- audio graph ---------- */

  function audio() {
    if (ctx) return ctx;

    var AC = window.AudioContext || window.webkitAudioContext;
    ctx = new AC();

    // Squeeze the dynamics, then push hard: loud without nasty clipping.
    var comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -22;
    comp.knee.value = 12;
    comp.ratio.value = 12;
    comp.attack.value = 0.003;
    comp.release.value = 0.25;

    master = ctx.createGain();
    master.gain.value = 2.4;          // makeup gain -> LOUD

    // Soft clipper: pushes the level right up to the ceiling and rounds off
    // whatever pokes through, instead of letting it hard-clip into a buzzsaw.
    var shaper = ctx.createWaveShaper();
    var n = 2048, curve = new Float32Array(n);
    for (var i = 0; i < n; i++) {
      var x = (i / (n - 1)) * 2 - 1;
      curve[i] = Math.tanh(x * 1.6) / Math.tanh(1.6);
    }
    shaper.curve = curve;
    shaper.oversample = "4x";

    var out = ctx.createGain();
    out.gain.value = 0.95;            // leave a sliver of headroom

    comp.connect(master);
    master.connect(shaper);
    shaper.connect(out);
    out.connect(ctx.destination);

    ctx._in = comp;
    return ctx;
  }

  // Short burst of noise for the breathy air in the moo.
  function noiseBuffer(ac, seconds) {
    var len = Math.floor(ac.sampleRate * seconds);
    var buf = ac.createBuffer(1, len, ac.sampleRate);
    var d = buf.getChannelData(0);
    for (var i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
    return buf;
  }

  function formant(ac, freq, q, gain, dest) {
    var f = ac.createBiquadFilter();
    f.type = "bandpass";
    f.frequency.value = freq;
    f.Q.value = q;
    var g = ac.createGain();
    g.gain.value = gain;
    f.connect(g);
    g.connect(dest);
    return { filter: f, gain: g };
  }

  /* ---------- the moo ---------- */

  function playMoo() {
    var ac = audio();
    if (ac.state === "suspended") ac.resume();

    var t = ac.currentTime + 0.02;
    var dur = 2.1;                              // moooooooowwwwww
    var f0 = 118 + (Math.random() * 16 - 8);    // a little variation each tap

    var voice = ac.createGain();                // overall envelope
    voice.gain.setValueAtTime(0.0001, t);
    voice.gain.exponentialRampToValueAtTime(0.55, t + 0.10);  // "mmm" swells in
    voice.gain.setValueAtTime(0.55, t + 0.28);
    voice.gain.linearRampToValueAtTime(1.0, t + 0.55);        // mouth opens: "OOO"
    voice.gain.setValueAtTime(1.0, t + dur * 0.62);
    voice.gain.linearRampToValueAtTime(0.72, t + dur * 0.85); // "wwww" tail
    voice.gain.exponentialRampToValueAtTime(0.0001, t + dur);

    // Vocal tract: opens up as the cow opens its mouth.
    var tract = ac.createBiquadFilter();
    tract.type = "lowpass";
    tract.Q.value = 1.1;
    tract.frequency.setValueAtTime(420, t);            // nasal, closed lips
    tract.frequency.exponentialRampToValueAtTime(2400, t + 0.55);
    tract.frequency.exponentialRampToValueAtTime(700, t + dur);

    voice.connect(tract);

    // Formants give it a throaty animal "ooo" instead of a flat buzz.
    var fA = formant(ac, 520, 6, 1.0, ac._in);
    var fB = formant(ac, 1050, 8, 0.55, ac._in);
    var fC = formant(ac, 2400, 9, 0.22, ac._in);
    tract.connect(fA.filter);
    tract.connect(fB.filter);
    tract.connect(fC.filter);
    tract.connect(ac._in);                              // keep some raw body

    fA.filter.frequency.setValueAtTime(430, t);
    fA.filter.frequency.linearRampToValueAtTime(640, t + 0.6);
    fA.filter.frequency.linearRampToValueAtTime(400, t + dur);

    // Wobbly pitch contour: up into the moo, sagging down at the end.
    function contour(param, mult) {
      param.setValueAtTime(f0 * 0.80 * mult, t);
      param.linearRampToValueAtTime(f0 * 1.14 * mult, t + 0.35);
      param.linearRampToValueAtTime(f0 * 1.05 * mult, t + dur * 0.65);
      param.linearRampToValueAtTime(f0 * 0.62 * mult, t + dur);      // wwwww
    }

    // Two detuned saws + a sub sine = big chesty cow.
    var oscs = [];
    [["sawtooth", 1, 0.5], ["sawtooth", 1.006, 0.35], ["square", 0.5, 0.4]]
      .forEach(function (spec) {
        var o = ac.createOscillator();
        o.type = spec[0];
        contour(o.frequency, spec[1]);
        var g = ac.createGain();
        g.gain.value = spec[2];
        o.connect(g);
        g.connect(voice);
        o.start(t);
        o.stop(t + dur + 0.1);
        oscs.push(o);
      });

    // Vibrato — the warble that makes it read as an animal.
    var lfo = ac.createOscillator();
    lfo.frequency.setValueAtTime(4.5, t);
    lfo.frequency.linearRampToValueAtTime(7.5, t + dur);
    var lfoGain = ac.createGain();
    lfoGain.gain.setValueAtTime(1.5, t);
    lfoGain.gain.linearRampToValueAtTime(7, t + dur);
    lfo.connect(lfoGain);
    oscs.forEach(function (o) { lfoGain.connect(o.frequency); });
    lfo.start(t);
    lfo.stop(t + dur + 0.1);

    // Breath.
    var noise = ac.createBufferSource();
    noise.buffer = noiseBuffer(ac, dur + 0.1);
    var nf = ac.createBiquadFilter();
    nf.type = "bandpass";
    nf.frequency.value = 1100;
    nf.Q.value = 0.8;
    var ng = ac.createGain();
    ng.gain.setValueAtTime(0.0001, t);
    ng.gain.linearRampToValueAtTime(0.09, t + 0.5);
    ng.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    noise.connect(nf); nf.connect(ng); ng.connect(voice);
    noise.start(t);
    noise.stop(t + dur + 0.1);

    return dur;
  }

  /* ---------- interaction ---------- */

  function moooo() {
    var now = Date.now();
    if (now < busyUntil) return;      // let one moo finish before the next

    var dur;
    try {
      dur = playMoo();
    } catch (e) {
      dur = 2.1;                      // no audio? still do the animation
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

  // Space / Enter for keyboard users (the button fires click already,
  // this just stops the page from scrolling on space).
  cow.addEventListener("keydown", function (e) {
    if (e.key === " " || e.key === "Spacebar") e.preventDefault();
  });
})();
