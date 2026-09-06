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

      try {
        const row = await env.DB.prepare(
          'INSERT INTO nv_codes(name,latitude,longitude,created_at) VALUES(?1,?2,?3,unixepoch()) RETURNING code,name,latitude,longitude,created_at'
        ).bind(name || 'NV Place', latitude, longitude).first();
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

    return json({ service: 'NV Code Registry', status: 'ok', allocation: 'sequential-1-to-N' }, 200);
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
