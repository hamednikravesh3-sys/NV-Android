const JSON_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
  'x-content-type-options': 'nosniff'
};

const REGISTRY_CODE_BASE = 5_000_000_000_000n;
const REGISTRY_CODE_SPAN = 1_000_000_000_000n;
const ALLOCATION_ATTEMPTS = 8;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'GET' && url.pathname === '/health') {
      return json({ service: 'NV Code Registry', status: 'ok', allocation: 'online-unique' }, 200);
    }

    if (url.pathname === '/v1/codes/allocate') {
      if (request.method !== 'POST') {
        return json({ error: 'method_not_allowed' }, 405, { allow: 'POST' });
      }

      let body;
      try {
        body = await request.json();
      } catch (_) {
        return json({ error: 'invalid_json' }, 400);
      }

      const name = String(body?.name || 'NV Place').trim().slice(0, 80);
      const latitude = Number(body?.latitude);
      const longitude = Number(body?.longitude);

      if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
        return json({ error: 'invalid_request' }, 400);
      }

      const locationKey = `${latitude.toFixed(6)},${longitude.toFixed(6)}`;

      try {
        const existing = await findByLocation(env.DB, locationKey);
        if (existing) return json({ status: 'existing', ...existing }, 200);

        // Allocate explicitly in the 5T..6T namespace. This remains correct even if a
        // deployment has not yet applied the sqlite_sequence migration, and the PK +
        // location_key UNIQUE constraints make collisions safe to retry.
        for (let attempt = 0; attempt < ALLOCATION_ATTEMPTS; attempt += 1) {
          const code = newRegistryCode();
          await env.DB.prepare(
            'INSERT OR IGNORE INTO nv_codes(code,location_key,name,latitude,longitude,created_at) VALUES(?1,?2,?3,?4,?5,unixepoch())'
          ).bind(code, locationKey, name || 'NV Place', latitude, longitude).run();

          const row = await findByLocation(env.DB, locationKey);
          if (row) return json({ status: 'allocated', ...row }, 201);
        }

        return json({ error: 'allocation_failed' }, 503);
      } catch (e) {
        console.error('NV Code allocation failed', e);
        return json({ error: 'server_error' }, 500);
      }
    }

    if (url.pathname.startsWith('/v1/codes/')) {
      if (request.method !== 'GET') {
        return json({ error: 'method_not_allowed' }, 405, { allow: 'GET' });
      }

      const rawCode = url.pathname.slice('/v1/codes/'.length);
      if (!/^[1-9]\d*$/.test(rawCode)) return json({ error: 'invalid_code' }, 400);
      const code = Number(rawCode);
      if (!Number.isSafeInteger(code)) return json({ error: 'invalid_code' }, 400);

      try {
        const row = await env.DB.prepare(
          'SELECT code,name,latitude,longitude,created_at FROM nv_codes WHERE code=?1'
        ).bind(code).first();
        return row ? json(row, 200) : json({ error: 'not_found' }, 404);
      } catch (e) {
        console.error('NV Code lookup failed', e);
        return json({ error: 'server_error' }, 500);
      }
    }

    return json({ error: 'not_found' }, 404);
  }
};

async function findByLocation(db, locationKey) {
  return db.prepare(
    'SELECT code,name,latitude,longitude,created_at FROM nv_codes WHERE location_key=?1'
  ).bind(locationKey).first();
}

function newRegistryCode() {
  const values = new Uint32Array(2);
  crypto.getRandomValues(values);
  const random64 = (BigInt(values[0]) << 32n) | BigInt(values[1]);
  return Number(REGISTRY_CODE_BASE + (random64 % REGISTRY_CODE_SPAN));
}

function json(value, status, headers = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { ...JSON_HEADERS, ...headers }
  });
}
