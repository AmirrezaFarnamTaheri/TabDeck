// Generated copies in each extension root are synchronized from this canonical source.
(() => {
  function endpointInfo(value) {
    const url = new URL(value);
    if (url.protocol !== 'http:') throw new Error('Use TabDeck’s HTTP bridge endpoint.');
    if (url.hostname !== '127.0.0.1' || url.port !== '48721') {
      throw new Error('Use the supported loopback bridge endpoint at http://127.0.0.1:48721.');
    }
    if (!['/api/v1/import', '/api/v2/import', '/api/v3/import'].includes(url.pathname)) throw new Error('Endpoint path must be /api/v3/import.');
    url.pathname = '/api/v3/import';
    url.search = '';
    url.hash = '';
    return { url: url.toString(), permission: `${url.origin}/*` };
  }

  async function getSourceSession(storage) {
    const key = 'tabdeckSourceSessionId';
    const stored = await storage.get({ [key]: '' });
    if (stored[key]) return { id: stored[key], reliable: storage.reliable };
    const id = crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    await storage.set({ [key]: id });
    return { id, reliable: storage.reliable };
  }

  globalThis.TabDeckBridgeRuntime = Object.freeze({ endpointInfo, getSourceSession });
})();
