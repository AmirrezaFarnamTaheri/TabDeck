'use strict';

// bridge-runtime.js accepts and canonicalizes /api/v1/import, /api/v2/import, and /api/v3/import.
const REQUIRED_CONTROLS = [
  'endpoint', 'token', 'browser', 'deviceName', 'scope', 'excludePinned', 'stripTrackers',
  'rememberToken', 'send', 'cleanup', 'refresh', 'testConnection', 'status', 'duplicatePanel',
  'duplicateHeadline', 'tabCount', 'groupCount', 'duplicateCount'
];
const ui = Object.fromEntries(REQUIRED_CONTROLS.map(id => [id, document.getElementById(id)]));
const missingControls = REQUIRED_CONTROLS.filter(id => !ui[id]);
if (missingControls.length) throw new Error(`TabDeck popup is missing controls: ${missingControls.join(', ')}`);

const TRACKERS = new Set([
  'fbclid', 'gclid', 'dclid', 'msclkid', 'mc_cid', 'mc_eid', 'igshid', 'ref_src',
  'ref_url', 'srsltid', 'mkt_tok', 'vero_conv', 'vero_id', '_hsenc', '_hsmi',
  'oly_anon_id', 'oly_enc_id', 'rb_clickid', 'wickedid'
]);
let preview = { tabs: [], groups: new Map(), duplicates: [] };

async function fetchWithTimeout(url, options, timeoutMs = 20000) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error('TabDeck bridge timed out. Confirm the bridge is live and the ADB forward is active.');
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

function storageGet(defaults) {
  return new Promise(resolve => chrome.storage.local.get(defaults, resolve));
}

function storageSet(values) {
  return new Promise(resolve => chrome.storage.local.set(values, resolve));
}

const sourceSessionStorage = {
  reliable: Boolean(chrome.storage.session),
  get(defaults) {
    const area = chrome.storage.session || chrome.storage.local;
    return new Promise(resolve => area.get(defaults, resolve));
  },
  set(values) {
    const area = chrome.storage.session || chrome.storage.local;
    return new Promise(resolve => area.set(values, resolve));
  }
};

function tabsQuery(query) {
  return new Promise((resolve, reject) => chrome.tabs.query(query, tabs => {
    if (chrome.runtime.lastError) reject(chrome.runtime.lastError);
    else resolve(tabs);
  }));
}

function groupGet(id) {
  return new Promise(resolve => chrome.tabGroups.get(id, group => {
    if (chrome.runtime.lastError) resolve(null);
    else resolve(group);
  }));
}

function tabsRemove(ids) {
  return new Promise((resolve, reject) => chrome.tabs.remove(ids, () => {
    if (chrome.runtime.lastError) reject(chrome.runtime.lastError);
    else resolve();
  }));
}

function permissionsRequest(origins) {
  return new Promise((resolve, reject) => chrome.permissions.request({ origins }, granted => {
    if (chrome.runtime.lastError) reject(chrome.runtime.lastError);
    else resolve(granted);
  }));
}

async function removeTabsChunked(ids) {
  for (let index = 0; index < ids.length; index += 200) {
    await tabsRemove(ids.slice(index, index + 200));
  }
}

function normalized(raw, smart = true) {
  try {
    const url = new URL(raw);
    url.hostname = url.hostname.toLowerCase();
    url.hash = '';
    if ((url.protocol === 'https:' && url.port === '443') || (url.protocol === 'http:' && url.port === '80')) {
      url.port = '';
    }
    if (smart) {
      [...url.searchParams.keys()].forEach(key => {
        const lower = key.toLowerCase();
        if (lower.startsWith('utm_') || TRACKERS.has(lower)) url.searchParams.delete(key);
      });
      const sorted = [...url.searchParams.entries()]
        .sort(([leftKey, leftValue], [rightKey, rightValue]) => leftKey.localeCompare(rightKey) || leftValue.localeCompare(rightValue));
      url.search = '';
      sorted.forEach(([key, value]) => url.searchParams.append(key, value));
    }
    if (url.pathname !== '/') url.pathname = url.pathname.replace(/\/+$/, '') || '/';
    return url.toString();
  } catch {
    return raw;
  }
}

function transferable(tab) {
  return /^https?:\/\//i.test(tab.url || '') && !(ui.excludePinned.checked && tab.pinned);
}

function duplicateCopies(tabs) {
  const clusters = new Map();
  for (const tab of tabs) {
    const key = normalized(tab.url, ui.stripTrackers.checked);
    const cluster = clusters.get(key) || [];
    cluster.push(tab);
    clusters.set(key, cluster);
  }
  const copies = [];
  for (const cluster of clusters.values()) {
    if (cluster.length < 2) continue;
    cluster.sort((left, right) =>
      Number(right.pinned) - Number(left.pinned) ||
      Number(right.active) - Number(left.active) ||
      (right.lastAccessed || 0) - (left.lastAccessed || 0)
    );
    copies.push(...cluster.slice(1));
  }
  return copies;
}

async function readSession() {
  const query = ui.scope.value === 'current' ? { currentWindow: true } : {};
  const allTabs = await tabsQuery(query);
  const tabs = allTabs.filter(transferable).slice(0, 25000);
  const groupIds = [...new Set(tabs.map(tab => tab.groupId).filter(id => Number.isInteger(id) && id >= 0))];
  const groups = new Map();
  await Promise.all(groupIds.map(async id => {
    const group = await groupGet(id);
    if (group) groups.set(id, { title: group.title || `Group ${id}`, color: group.color || '' });
  }));

  preview = { tabs, groups, duplicates: duplicateCopies(tabs) };
  ui.tabCount.textContent = String(tabs.length);
  ui.groupCount.textContent = String(groups.size);
  ui.duplicateCount.textContent = String(preview.duplicates.length);
  ui.duplicatePanel.hidden = preview.duplicates.length === 0;
  ui.duplicateHeadline.textContent = `${preview.duplicates.length} removable duplicate ${preview.duplicates.length === 1 ? 'copy' : 'copies'}`;
  return preview;
}

function setStatus(message, type = '') {
  ui.status.textContent = message;
  ui.status.className = type;
}

async function refreshPreview() {
  ui.refresh.disabled = true;
  setStatus('Reading tabs and native groups…');
  try {
    await readSession();
    setStatus(`${preview.tabs.length} transferable tabs ready.`);
  } catch (error) {
    setStatus(error.message || String(error), 'error');
  } finally {
    ui.refresh.disabled = false;
  }
}

async function saveSettings() {
  const token = ui.token.value.trim();
  const settings = {
    endpoint: ui.endpoint.value.trim(),
    token: ui.rememberToken.checked ? token : '',
    browser: ui.browser.value,
    deviceName: ui.deviceName.value.trim(),
    scope: ui.scope.value,
    excludePinned: ui.excludePinned.checked,
    stripTrackers: ui.stripTrackers.checked,
    rememberToken: ui.rememberToken.checked
  };
  await storageSet(settings);
  return { ...settings, token };
}

async function restore() {
  const saved = await storageGet({
    endpoint: 'http://127.0.0.1:48721/api/v3/import',
    token: '',
    browser: 'Chrome',
    deviceName: '',
    scope: 'all',
    excludePinned: false,
    stripTrackers: true,
    rememberToken: false
  });
  for (const key of ['endpoint', 'browser', 'deviceName', 'scope']) ui[key].value = saved[key] ?? '';
  ui.excludePinned.checked = Boolean(saved.excludePinned);
  ui.stripTrackers.checked = saved.stripTrackers !== false;
  ui.rememberToken.checked = Boolean(saved.rememberToken);
  ui.token.value = saved.rememberToken ? (saved.token || '') : '';
  await refreshPreview();
}

async function requestBridgePermission(destination) {
  if (!(await permissionsRequest([destination.permission]))) {
    throw new Error('Bridge endpoint permission was not granted.');
  }
}

async function testBridgeConnection() {
  ui.testConnection.disabled = true;
  setStatus('Testing the Android bridge…');
  try {
    const settings = await saveSettings();
    if (!settings.endpoint) throw new Error('Enter the forwarded loopback endpoint.');
    const destination = TabDeckBridgeRuntime.endpointInfo(settings.endpoint);
    await requestBridgePermission(destination);
    ui.endpoint.value = destination.url;
    await storageSet({ endpoint: destination.url });

    const health = new URL(destination.url);
    health.pathname = '/health';
    health.search = '';
    health.hash = '';
    const response = await fetchWithTimeout(health.toString(), { method: 'GET', headers: { Accept: 'application/json' } }, 7000);
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok !== true) throw new Error(payload.error || `Bridge returned HTTP ${response.status}.`);
    const expiry = Number(payload.expiresAtEpochMs);
    const remaining = Number.isFinite(expiry) ? Math.max(0, Math.ceil((expiry - Date.now()) / 60000)) : null;
    setStatus(`Bridge ready · API v${payload.version ?? 3}${remaining === null ? '' : ` · ${remaining} min remaining`}.`, 'success');
  } catch (error) {
    setStatus(error.message || String(error), 'error');
  } finally {
    ui.testConnection.disabled = false;
  }
}

async function sendSnapshot() {
  ui.send.disabled = true;
  setStatus('Preparing a fresh grouped snapshot…');
  try {
    const settings = await saveSettings();
    if (!settings.endpoint || !settings.token) throw new Error('Enter the forwarded endpoint and current bridge token.');
    const destination = TabDeckBridgeRuntime.endpointInfo(settings.endpoint);
    await requestBridgePermission(destination);
    ui.endpoint.value = destination.url;
    await storageSet({ endpoint: destination.url });
    await readSession();
    if (!preview.tabs.length) throw new Error('No transferable HTTP(S) tabs were found.');

    const capturedAt = Date.now();
    const device = settings.deviceName || 'Desktop browser';
    const sourceSession = await TabDeckBridgeRuntime.getSourceSession(sourceSessionStorage);
    const body = {
      browser: settings.browser,
      completeSnapshot: settings.scope === 'all' && !settings.excludePinned && sourceSession.reliable,
      sourceSessionId: sourceSession.id,
      identityVersion: 1,
      sourceLabel: `${settings.browser} desktop`,
      deviceName: device,
      capturedAt,
      tabs: preview.tabs.map(tab => {
        const group = preview.groups.get(tab.groupId);
        return {
          id: String(tab.id),
          url: tab.url,
          title: tab.title || '',
          group: group?.title || `Window ${tab.windowId}`,
          groupColor: group?.color || '',
          pinned: Boolean(tab.pinned),
          active: Boolean(tab.active),
          createdAt: tab.lastAccessed || capturedAt,
          lastSeenAt: capturedAt,
          deviceId: device
        };
      })
    };

    setStatus(`Sending ${body.tabs.length} tabs and ${preview.groups.size} groups…`);
    const response = await fetchWithTimeout(destination.url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-TabDeck-Token': settings.token,
        'X-TabDeck-Request-Id': crypto.randomUUID?.() || `${Date.now()}-${Math.random()}`
      },
      body: JSON.stringify(body)
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || `Bridge returned HTTP ${response.status}.`);
    setStatus(`Imported ${payload.imported ?? body.tabs.length} tabs into TabDeck.`, 'success');
  } catch (error) {
    setStatus(error.message || String(error), 'error');
  } finally {
    ui.send.disabled = false;
  }
}

async function cleanupDuplicates() {
  await readSession();
  const ids = preview.duplicates.map(tab => tab.id).filter(Number.isInteger);
  if (!ids.length) {
    setStatus('No duplicate copies remain.', 'success');
    return;
  }
  const confirmed = confirm(`Close ${ids.length} duplicate tab ${ids.length === 1 ? 'copy' : 'copies'}?\n\nPinned, active, and recently accessed tabs are preserved.`);
  if (!confirmed) return;
  ui.cleanup.disabled = true;
  try {
    await removeTabsChunked(ids);
    await refreshPreview();
    setStatus(`Closed ${ids.length} duplicate copies.`, 'success');
  } catch (error) {
    setStatus(error.message || String(error), 'error');
  } finally {
    ui.cleanup.disabled = false;
  }
}

ui.send.addEventListener('click', sendSnapshot);
ui.cleanup.addEventListener('click', cleanupDuplicates);
ui.refresh.addEventListener('click', refreshPreview);
ui.testConnection.addEventListener('click', testBridgeConnection);
for (const control of [ui.scope, ui.excludePinned, ui.stripTrackers]) {
  control.addEventListener('change', refreshPreview);
}
restore().catch(error => setStatus(error.message || String(error), 'error'));
