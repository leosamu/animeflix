const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../core/playback/src/main/kotlin/com/animeflix/playback/ProviderVideoResolver.kt'), 'utf8')
  .split('private val SOURCE_SCRIPT = """')[1].split('""".trimIndent()')[0];
const responses = new Map(), messages = [], intervals = [];
let videos = [], verification = false, clicks = 0;
function Xhr() {}
Xhr.prototype.open = function () {};
const context = {
  URL, JSON, XMLHttpRequest: Xhr,
  location: { hostname: 'voe.sx', href: 'https://voe.sx/e/episode' },
  AnimeFlixSource: { postMessage: raw => messages.push(JSON.parse(raw)) },
  setTimeout: () => {}, setInterval: fn => intervals.push(fn),
  document: {
    addEventListener() {},
    querySelectorAll: () => videos,
    querySelector: selector => selector === '.captcha-gate' ? (verification ? {} : null) : { click: () => clicks++ },
  },
  fetch: async url => ({ ok: true, url, headers: { get: () => 'application/vnd.apple.mpegurl' },
    clone: () => ({ text: async () => responses.get(url) }) }),
};
context.window = context;
vm.runInNewContext(source, context);
async function manifest(url, text) {
  responses.set(url, text);
  await context.fetch(url);
  await new Promise(setImmediate);
}
(async () => {
  await manifest('https://cdn.example/sample.m3u8', '#EXTM3U\n#EXTINF:10,\nsample.ts\n#EXT-X-ENDLIST');
  assert.equal(messages.length, 0, 'A short sample/ad must not become the episode');
  const master = 'https://cdn.example/master.m3u8';
  await manifest(master, '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\nvideo.m3u8\n#EXT-X-MEDIA:TYPE=AUDIO,URI="audio.m3u8"');
  assert.equal(messages.length, 0, 'A master alone does not establish episode duration');
  await manifest('https://cdn.example/audio.m3u8', '#EXTM3U\n#EXTINF:700,\n1.aac\n#EXTINF:720,\n2.aac\n#EXT-X-ENDLIST');
  assert.equal(messages[0].url, master, 'Keep the master so native playback retains video and audio');
  assert.equal(messages[0].duration, 1420);
  messages.length = 0;
  videos = [{ duration: 10, videoWidth: 1920, currentSrc: 'https://cdn.example/sample.mp4', paused: false }];
  intervals[0]();
  assert.equal(messages.length, 0, 'Reject short MP4 samples too');
  videos = [{ duration: 1420, videoWidth: 1920, currentSrc: 'blob:local-only', paused: false }];
  intervals[0]();
  assert.equal(messages.length, 0, 'A blob URL cannot be handed to the native HTTP player');
  verification = true; clicks = 0;
  intervals[0]();
  assert.equal(messages[0].kind, 'verification');
  assert.equal(clicks, 0, 'A human-verification gate must never be clicked automatically');
  console.log('Provider source checks passed: samples, master/audio, blob URLs and manual verification');
})().catch(error => { console.error(error); process.exitCode = 1; });
