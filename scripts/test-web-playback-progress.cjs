const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('core/playback/src/main/kotlin/com/animeflix/playback/WebPlaybackProgress.kt', 'utf8')
  .split('internal val SCRIPT = """')[1].split('""".trimIndent()')[0];
const messages = [], events = {};
class Video {
  constructor() { this.duration = 1200; this.currentTime = 0; this.seeking = false; this.ended = false; this.events = {}; this.starts = 0; }
  addEventListener(name, action) { this.events[name] = action; }
  play() { this.starts++; return Promise.resolve(); }
}
const bridge = { postMessage: raw => messages.push(JSON.parse(raw)) };
const context = { window: { AnimeFlixProgress: bridge, addEventListener() {} }, AnimeFlixProgress: bridge,
  document: { addEventListener: (name, callback) => { events[name] = callback; } },
  HTMLVideoElement: Video, WeakSet, Date, Math, JSON, isFinite };
vm.runInNewContext(source, context);
const video = new Video();
events.loadedmetadata({ target: video });
const ready = messages.at(-1);
bridge.onmessage({ data: JSON.stringify({ nonce: ready.nonce, position: 0, autoPlay: true }) });
assert.equal(video.starts, 1);
video.events.playing();
video.currentTime = 1140;
video.events.timeupdate(); video.events.pause();
assert.equal(messages.filter(m => m.kind === 'ended').length, 0);
video.currentTime = video.duration; video.ended = true;
video.events.ended();
assert.equal(messages.filter(m => m.kind === 'ended').length, 1);
assert.equal(messages.at(-1).position, 1200);
const idle = new Video();
events.loadedmetadata({ target: idle });
bridge.onmessage({ data: JSON.stringify({ nonce: messages.at(-1).nonce, position: 0, autoPlay: false }) });
idle.currentTime = idle.duration; idle.ended = true; idle.events.ended();
assert.equal(idle.starts, 0);
assert.equal(messages.filter(m => m.kind === 'ended').length, 1);
console.log('Web playback checks passed: autoplay, end telemetry, pause and unplayed-video guards');
