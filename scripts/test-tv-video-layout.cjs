// Checks reversible sizing of the original published iframe without a remote browser.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../core/playback/src/main/kotlin/com/animeflix/playback/TvPlayerLayout.kt'), 'utf8');
const match = source.match(/internal fun tvVideoLayoutScript\(fit: Boolean\): String = """([\s\S]*?)"""\.trimIndent\(\)/);
assert.ok(match, 'TV layout script not found');
function iframe() {
  const attrs = new Map([['style', 'height:315px'], ['src', 'https://player.example/published']]);
  return {style: {}, hasAttribute: k => attrs.has(k), getAttribute: k => attrs.get(k),
    setAttribute: (k,v) => attrs.set(k,v), removeAttribute: k => attrs.delete(k)};
}
let frame = iframe();
let observer;
const context = vm.createContext({window: {}, document: {documentElement: {}, querySelector: () => frame},
  MutationObserver: class {constructor(callback) {observer = callback;} observe() {} }});
function run(fit) { vm.runInContext(match[1].replace('$fit', String(fit)), context); }
run(true);
assert.equal(frame.style.width, '100vw');
assert.equal(frame.style.height, '100vh');
assert.equal(frame.style.position, 'fixed');
run(true);
run(false);
assert.equal(frame.getAttribute('style'), 'height:315px');
assert.equal(frame.hasAttribute('data-animeflix-style'), false);
assert.equal(frame.getAttribute('src'), 'https://player.example/published');
frame = null;
run(true); // Page may initially have no iframe.
frame = iframe();
observer(); // A dynamically inserted iframe is sized as well.
assert.equal(frame.style.height, '100vh');
run(false);
assert.equal(frame.getAttribute('style'), 'height:315px');
console.log('TV iframe layout checks passed');
