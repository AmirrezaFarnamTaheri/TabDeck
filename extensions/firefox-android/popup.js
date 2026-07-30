const $ = selector => document.querySelector(selector);
const ui = {
  endpoint: $('#endpoint'), token: $('#token'), browser: $('#browser'), deviceName: $('#deviceName'),
  scope: $('#scope'), excludePinned: $('#excludePinned'), stripTrackers: $('#stripTrackers'), rememberToken: $('#rememberToken'),
  send: $('#send'), cleanup: $('#cleanup'), refresh: $('#refresh'), testConnection: $('#testConnection'), status: $('#status'),
  duplicatePanel: $('#duplicatePanel'), duplicateHeadline: $('#duplicateHeadline'), duplicateDetail: $('#duplicateDetail'),
  transferableCount: $('#transferableCount'), duplicateCount: $('#duplicateCount'), windowCount: $('#windowCount')
};
const TRACKERS = new Set(['fbclid','gclid','dclid','msclkid','mc_cid','mc_eid','igshid','ref_src','ref_url','srsltid','mkt_tok','vero_conv','vero_id','_hsenc','_hsmi','oly_anon_id','oly_enc_id','rb_clickid','wickedid']);
let preview = { tabs: [], duplicates: [], windows: 0 };


async function fetchWithTimeout(url, options, timeoutMs = 20000) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try { return await fetch(url, { ...options, signal: controller.signal }); }
  catch (error) { if (error?.name === 'AbortError') throw new Error('TabDeck bridge timed out. Confirm the bridge is live and the endpoint is reachable.'); throw error; }
  finally { clearTimeout(timeout); }
}

function endpointInfo(value) {
  const url = new URL(value);
  if (url.protocol !== 'http:') throw new Error('Use TabDeck’s HTTP bridge endpoint.');
  const host = url.hostname.toLowerCase();
  const parts = host.split('.').map(Number);
  const privateV4 = parts.length === 4 && parts.every(Number.isInteger) && (
    parts[0] === 10 || parts[0] === 127 ||
    (parts[0] === 192 && parts[1] === 168) ||
    (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31)
  );
  const ipv6 = host.replace(/^\[|\]$/g, '');
  const privateV6 = ipv6 === '::1' || /^(fc|fd|fe[89ab])/i.test(ipv6);
  if (!(host === 'localhost' || privateV4 || privateV6)) {
    throw new Error('Use the localhost or private-LAN address shown by TabDeck.');
  }
  if (!['/api/v1/import', '/api/v2/import', '/api/v3/import'].includes(url.pathname)) throw new Error('Endpoint path must be /api/v3/import.');
  url.pathname = '/api/v3/import';
  url.search = '';
  url.hash = '';
  return { url: url.toString(), permission: `${url.origin}/*` };
}

function normalized(raw, smart = true) {
  try {
    const url = new URL(raw);
    url.hostname = url.hostname.toLowerCase();
    url.hash = '';
    if ((url.protocol === 'https:' && url.port === '443') || (url.protocol === 'http:' && url.port === '80')) url.port = '';
    if (smart) {
      [...url.searchParams.keys()].forEach(key => {
        if (key.toLowerCase().startsWith('utm_') || TRACKERS.has(key.toLowerCase())) url.searchParams.delete(key);
      });
      const sorted = [...url.searchParams.entries()].sort(([a,av],[b,bv]) => a.localeCompare(b) || av.localeCompare(bv));
      url.search = '';
      sorted.forEach(([key, value]) => url.searchParams.append(key, value));
    }
    if (url.pathname !== '/') url.pathname = url.pathname.replace(/\/+$/, '') || '/';
    return url.toString();
  } catch { return raw; }
}

function transferable(tab) {
  return /^https?:\/\//i.test(tab.url || '') && !(ui.excludePinned.checked && tab.pinned);
}

function duplicateCopies(tabs) {
  const clusters = new Map();
  tabs.forEach(tab => {
    const key = normalized(tab.url, ui.stripTrackers.checked);
    const list = clusters.get(key) || [];
    list.push(tab);
    clusters.set(key, list);
  });
  const copies = [];
  for (const cluster of clusters.values()) {
    if (cluster.length < 2) continue;
    cluster.sort((a, b) => Number(b.pinned) - Number(a.pinned) || Number(b.active) - Number(a.active) || (b.lastAccessed || 0) - (a.lastAccessed || 0));
    copies.push(...cluster.slice(1));
  }
  return copies;
}

async function readTabs() {
  const query = ui.scope.value === 'current' ? { currentWindow: true } : {};
  const all = await browser.tabs.query(query);
  const tabs = all.filter(transferable).slice(0, 25000);
  preview = { tabs, duplicates: duplicateCopies(tabs), windows: new Set(tabs.map(tab => tab.windowId)).size };
  ui.transferableCount.textContent = String(tabs.length);
  ui.duplicateCount.textContent = String(preview.duplicates.length);
  ui.windowCount.textContent = String(preview.windows);
  ui.duplicatePanel.hidden = preview.duplicates.length === 0;
  ui.duplicateHeadline.textContent = `${preview.duplicates.length} removable duplicate ${preview.duplicates.length === 1 ? 'copy' : 'copies'}`;
  ui.duplicateDetail.textContent = 'Pinned, active, and recently accessed tabs are preferred as survivors.';
  return preview;
}

async function saveSettings() {
  const token = ui.token.value.trim();
  const settings = {
    endpoint: ui.endpoint.value.trim(), token: ui.rememberToken.checked ? token : '', browser: ui.browser.value,
    deviceName: ui.deviceName.value.trim(), scope: ui.scope.value,
    excludePinned: ui.excludePinned.checked, stripTrackers: ui.stripTrackers.checked,
    rememberToken: ui.rememberToken.checked
  };
  await browser.storage.local.set(settings);
  return { ...settings, token };
}

async function restore() {
  const saved = await browser.storage.local.get({
    endpoint: 'http://127.0.0.1:48721/api/v3/import', token: '', browser: 'Firefox',
    deviceName: '', scope: 'all', excludePinned: false, stripTrackers: true, rememberToken: false
  });
  Object.entries({ endpoint:'endpoint', token:'token', browser:'browser', deviceName:'deviceName', scope:'scope' })
    .forEach(([key, id]) => ui[id].value = saved[key] ?? '');
  ui.excludePinned.checked = Boolean(saved.excludePinned);
  ui.stripTrackers.checked = saved.stripTrackers !== false;
  ui.rememberToken.checked = Boolean(saved.rememberToken);
  ui.token.value = saved.rememberToken ? (saved.token || '') : '';
  await refreshPreview();
}

function setStatus(message, type = '') { ui.status.textContent = message; ui.status.className = type; }
async function refreshPreview() {
  ui.refresh.disabled = true;
  setStatus('Reading Firefox tabs…');
  try {
    await readTabs();
    setStatus(`${preview.tabs.length} transferable tabs ready.`);
  } catch (error) { setStatus(error.message || String(error), 'error'); }
  finally { ui.refresh.disabled = false; }
}

async function testBridgeConnection() {
  ui.testConnection.disabled = true;
  setStatus('Testing the TabDeck bridge…');
  try {
    const settings = await saveSettings();
    if (!settings.endpoint) throw new Error('Enter the endpoint shown in TabDeck Connect.');
    const destination = endpointInfo(settings.endpoint);
    const granted = await browser.permissions.request({ origins: [destination.permission] });
    if (!granted) throw new Error('Bridge endpoint permission was not granted.');
    ui.endpoint.value = destination.url;
    await browser.storage.local.set({ endpoint: destination.url });
    const health = new URL(destination.url);
    health.pathname = '/health'; health.search = ''; health.hash = '';
    const response = await fetchWithTimeout(health.toString(), { method: 'GET', headers: { 'Accept': 'application/json' } }, 7000);
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok !== true) throw new Error(payload.error || `Bridge returned HTTP ${response.status}.`);
    const expiry = Number(payload.expiresAtEpochMs);
    const remaining = Number.isFinite(expiry) ? Math.max(0, Math.ceil((expiry - Date.now()) / 60000)) : null;
    setStatus(`Bridge ready · API v${payload.version ?? 3}${remaining === null ? '' : ` · ${remaining} min remaining`}.`, 'success');
  } catch (error) { setStatus(error.message || String(error), 'error'); }
  finally { ui.testConnection.disabled = false; }
}

async function sendSnapshot() {
  ui.send.disabled = true;
  setStatus('Preparing a fresh session snapshot…');
  try {
    const settings = await saveSettings();
    if (!settings.endpoint || !settings.token) throw new Error('Enter the endpoint and token shown in TabDeck Connect.');
    const destination = endpointInfo(settings.endpoint);
    const granted = await browser.permissions.request({ origins: [destination.permission] });
    if (!granted) throw new Error('Bridge endpoint permission was not granted.');
    ui.endpoint.value = destination.url;
    await browser.storage.local.set({ endpoint: destination.url });
    await readTabs();
    if (!preview.tabs.length) throw new Error('No transferable HTTP(S) tabs were found.');
    const capturedAt = Date.now();
    const body = {
      browser: settings.browser, completeSnapshot: settings.scope === 'all' && !settings.excludePinned,
      sourceLabel: `${settings.browser} Android`,
      deviceName: settings.deviceName || 'Android device',
      capturedAt,
      tabs: preview.tabs.map(tab => ({
        id: String(tab.id), url: tab.url, title: tab.title || '',
        group: `Firefox window ${tab.windowId}`, pinned: Boolean(tab.pinned), active: Boolean(tab.active),
        createdAt: tab.lastAccessed || capturedAt, lastSeenAt: capturedAt,
        deviceId: settings.deviceName || 'Android device'
      }))
    };
    setStatus(`Sending ${body.tabs.length} tabs…`);
    const response = await fetchWithTimeout(destination.url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-TabDeck-Token': settings.token, 'X-TabDeck-Request-Id': crypto.randomUUID?.() || `${Date.now()}-${Math.random()}` },
      body: JSON.stringify(body)
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || `Bridge returned HTTP ${response.status}.`);
    setStatus(`Imported ${payload.imported ?? body.tabs.length} tabs into TabDeck.`, 'success');
  } catch (error) { setStatus(error.message || String(error), 'error'); }
  finally { ui.send.disabled = false; }
}

async function cleanupDuplicates() {
  await readTabs();
  const ids = preview.duplicates.map(tab => tab.id).filter(Number.isInteger);
  if (!ids.length) return setStatus('No duplicate copies remain.', 'success');
  const approved = confirm(`Close ${ids.length} duplicate tab ${ids.length === 1 ? 'copy' : 'copies'}?\n\nPinned, active, and most recently accessed tabs will be preserved.`);
  if (!approved) return;
  ui.cleanup.disabled = true;
  try {
    for (let i = 0; i < ids.length; i += 200) await browser.tabs.remove(ids.slice(i, i + 200));
    await refreshPreview();
    setStatus(`Closed ${ids.length} duplicate copies in Firefox.`, 'success');
  } catch (error) { setStatus(error.message || String(error), 'error'); }
  finally { ui.cleanup.disabled = false; }
}

ui.send.addEventListener('click', sendSnapshot);
ui.cleanup.addEventListener('click', cleanupDuplicates);
ui.refresh.addEventListener('click', refreshPreview);
ui.testConnection.addEventListener('click', testBridgeConnection);
[ui.scope, ui.excludePinned, ui.stripTrackers].forEach(control => control.addEventListener('change', refreshPreview));
restore().catch(error => setStatus(error.message || String(error), 'error'));
