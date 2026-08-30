# YouTube Shorts to Watch

Firefox extension that adds a toolbar button on YouTube Shorts pages. Click it to open the same video as a normal watch URL.

## Behavior

- Button is **enabled** only when the tab URL starts with `https://www.youtube.com/shorts/`.
- Click converts:
  - `https://www.youtube.com/shorts/VIDEO_ID` → `https://www.youtube.com/watch?v=VIDEO_ID`
- Other query parameters are dropped.
- If the Shorts URL has a start time (`t` or `start`), it is kept as `t` on the watch URL.

## Install (temporary)

1. Open `about:debugging` → **This Firefox**.
2. **Load Temporary Add-on…**
3. Choose `manifest.json` in this folder.

The toolbar button uses `default_area: "navbar"` so it should appear on the main toolbar on **first install**. If you loaded an older build, remove the add-on and load again (or pin manually via the puzzle menu → **Pin to Toolbar**).

## Install (persistent)

Zip the folder contents (not the parent directory) and install via `about:addons`, or use your own Firefox build with signing disabled.

## Development

```bash
node firefox/youtube-shorts-to-watch/test/urls.test.js
```
