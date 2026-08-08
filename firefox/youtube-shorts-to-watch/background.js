/* global shortsUrlToWatchUrl, isYouTubeShortsUrl */

const ENABLED_TITLE = "Open as regular YouTube video";
const DISABLED_TITLE = "YouTube Shorts to Watch (not a Shorts page)";

async function updateActionForTab(tabId) {
  if (tabId < 0) {
    return;
  }

  try {
    const tab = await browser.tabs.get(tabId);
    const enabled = isYouTubeShortsUrl(tab.url);
    await browser.action.setEnabled(tabId, enabled);
    await browser.action.setTitle({
      tabId,
      title: enabled ? ENABLED_TITLE : DISABLED_TITLE,
    });
  } catch {
    // Tab closed or unavailable.
  }
}

async function updateAllTabs() {
  const tabs = await browser.tabs.query({});
  await Promise.all(tabs.map((tab) => updateActionForTab(tab.id)));
}

browser.tabs.onActivated.addListener(({ tabId }) => {
  updateActionForTab(tabId);
});

browser.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (changeInfo.url || changeInfo.status === "complete") {
    updateActionForTab(tabId);
  }
});

browser.action.onClicked.addListener(async (tab) => {
  if (!tab.url || !isYouTubeShortsUrl(tab.url)) {
    return;
  }

  const watchUrl = shortsUrlToWatchUrl(tab.url);
  if (!watchUrl) {
    return;
  }

  await browser.tabs.update(tab.id, { url: watchUrl });
});

browser.runtime.onInstalled.addListener(() => {
  updateAllTabs();
});

browser.runtime.onStartup.addListener(() => {
  updateAllTabs();
});

updateAllTabs();
