const {
  isYouTubeShortsUrl,
  extractShortsVideoId,
  shortsUrlToWatchUrl,
} = require("../urls.js");

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

const videoId = "dQw4w9WgXcQ";

assert(
  isYouTubeShortsUrl(`https://www.youtube.com/shorts/${videoId}`),
  "basic shorts url"
);
assert(
  !isYouTubeShortsUrl(`https://www.youtube.com/watch?v=${videoId}`),
  "watch url rejected"
);
assert(
  !isYouTubeShortsUrl(`http://www.youtube.com/shorts/${videoId}`),
  "http rejected"
);
assert(
  !isYouTubeShortsUrl(`https://youtube.com/shorts/${videoId}`),
  "non-www rejected"
);

assert(
  extractShortsVideoId(`https://www.youtube.com/shorts/${videoId}`) === videoId,
  "extract id"
);
assert(
  extractShortsVideoId(`https://www.youtube.com/shorts/${videoId}/extra`) === videoId,
  "extract id with trailing segment"
);

assert(
  shortsUrlToWatchUrl(`https://www.youtube.com/shorts/${videoId}?feature=share`) ===
    `https://www.youtube.com/watch?v=${videoId}`,
  "drops unrelated query params"
);
assert(
  shortsUrlToWatchUrl(`https://www.youtube.com/shorts/${videoId}?t=42s`) ===
    `https://www.youtube.com/watch?v=${videoId}&t=42s`,
  "preserves t param"
);
assert(
  shortsUrlToWatchUrl(`https://www.youtube.com/shorts/${videoId}?start=90`) ===
    `https://www.youtube.com/watch?v=${videoId}&t=90`,
  "maps start to t"
);

console.log("urls.test.js: all tests passed");
