export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === 'POST' && url.pathname === '/v1/codes/reserve') {
      const body = await request.json();
      const code = String(body.code || '').trim();
      const name = String(body.name || 'NV Place').trim();
      const latitude = Number(body.latitude);
      const longitude = Number(body.longitude);
      if (!/^\d{1,9}$/.test(code) || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        return json({ error: 'invalid_request' }, 400);
      }
      try {
        await env.DB.prepare(
          'INSERT INTO nv_codes(code,name,latitude,longitude,created_at) VALUES(?1,?2,?3,?4,unixepoch())'
        ).bind(code, name, latitude, longitude).run();
        return json({ status: 'reserved', code }, 201);
      } catch (e) {
        if (String(e).toLowerCase().includes('unique')) {
          return json({ status: 'duplicate', code }, 409);
        }
        return json({ error: 'server_error' }, 500);
      }
    }
    if (request.method === 'GET' && url.pathname.startsWith('/v1/codes/')) {
      const code = url.pathname.split('/').pop();
      const row = await env.DB.prepare(
        'SELECT code,name,latitude,longitude,created_at FROM nv_codes WHERE code=?1'
      ).bind(code).first();
      return row ? json(row, 200) : json({ error: 'not_found' }, 404);
    }
    return json({ service: 'NV Code Registry', status: 'ok' }, 200);
  }
};

function json(value, status) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8' }
  });
}
