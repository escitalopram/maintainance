/* Shared URL helpers (background + tests). */

const SHORTS_PAGE_PREFIX = "https://www.youtube.com/shorts/";

/**
 * @param {string | undefined | null} url
 * @returns {boolean}
 */
function isYouTubeShortsUrl(url) {
  if (!url) {
    return false;
  }
  try {
    const parsed = new URL(url);
    return (
      parsed.protocol === "https:" &&
      parsed.hostname === "www.youtube.com" &&
      parsed.pathname.startsWith("/shorts/")
    );
  } catch {
    return false;
  }
}

/**
 * Extract the video id from /shorts/VIDEO_ID (optional trailing path segments ignored).
 *
 * @param {string} url
 * @returns {string | null}
 */
function extractShortsVideoId(url) {
  try {
    const parsed = new URL(url);
    const match = parsed.pathname.match(/^\/shorts\/([^/]+)/);
    return match?.[1] ?? null;
  } catch {
    return null;
  }
}

/**
 * Preserve start-time query params if present (Shorts rarely use these, but harmless).
 *
 * @param {URL} shortsUrl
 * @param {URL} watchUrl
 */
function copyStartTimeParam(shortsUrl, watchUrl) {
  const t = shortsUrl.searchParams.get("t");
  if (t) {
    watchUrl.searchParams.set("t", t);
    return;
  }
  const start = shortsUrl.searchParams.get("start");
  if (start) {
    watchUrl.searchParams.set("t", start);
  }
}

/**
 * @param {string} url
 * @returns {string | null}
 */
function shortsUrlToWatchUrl(url) {
  if (!isYouTubeShortsUrl(url)) {
    return null;
  }
  const videoId = extractShortsVideoId(url);
  if (!videoId) {
    return null;
  }

  const watchUrl = new URL("https://www.youtube.com/watch");
  watchUrl.searchParams.set("v", videoId);
  copyStartTimeParam(new URL(url), watchUrl);
  return watchUrl.href;
}

// Node test harness
if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    SHORTS_PAGE_PREFIX,
    isYouTubeShortsUrl,
    extractShortsVideoId,
    shortsUrlToWatchUrl,
  };
}
