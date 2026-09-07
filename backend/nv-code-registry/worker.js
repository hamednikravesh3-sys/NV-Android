export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'POST' && url.pathname === '/v1/codes/allocate') {
      const body = await request.json();
      const name = String(body.name || 'NV Place').trim().slice(0, 80);
      const latitude = Number(body.latitude);
      const longitude = Number(body.longitude);

      if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
        return json({ error: 'invalid_request' }, 400);
      }

      const locationKey = `${latitude.toFixed(6)},${longitude.toFixed(6)}`;

      try {
        const existing = await env.DB.prepare(
          'SELECT code,name,latitude,longitude,created_at FROM nv_codes WHERE location_key=?1'
        ).bind(locationKey).first();
        if (existing) return json({ status: 'existing', ...existing }, 200);

        await env.DB.prepare(
          'INSERT OR IGNORE INTO nv_codes(location_key,name,latitude,longitude,created_at) VALUES(?1,?2,?3,?4,unixepoch())'
        ).bind(locationKey, name || 'NV Place', latitude, longitude).run();

        const row = await env.DB.prepare(
          'SELECT code,name,latitude,longitude,created_at FROM nv_codes WHERE location_key=?1'
        ).bind(locationKey).first();

        if (!row) return json({ error: 'allocation_failed' }, 500);
        return json({ status: 'allocated', ...row }, 201);
      } catch (e) {
        return json({ error: 'server_error', detail: String(e) }, 500);
      }
    }

    if (request.method === 'GET' && url.pathname.startsWith('/v1/codes/')) {
      const code = Number(url.pathname.split('/').pop());
      if (!Number.isInteger(code) || code < 1) return json({ error: 'invalid_code' }, 400);
      const row = await env.DB.prepare(
        'SELECT code,name,latitude,longitude,created_at FROM nv_codes WHERE code=?1'
      ).bind(code).first();
      return row ? json(row, 200) : json({ error: 'not_found' }, 404);
    }

    return json({ service: 'NV Code Registry', status: 'ok', allocation: 'online-unique' }, 200);
  }
};

function json(value, status) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store'
    }
  });
}
