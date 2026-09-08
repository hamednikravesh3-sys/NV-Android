const json = (body, status = 200, extraHeaders = {}) => new Response(JSON.stringify(body), {
  status,
  headers: {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
    ...extraHeaders
  }
});

async function userIdFromRequest(request) {
  const auth = request.headers.get('authorization') || '';
  if (!auth.startsWith('Bearer ')) throw new Error('unauthorized');
  const token = auth.slice(7).trim();
  if (token.length < 16) throw new Error('unauthorized');
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(token));
  return Array.from(new Uint8Array(digest)).map(v => v.toString(16).padStart(2, '0')).join('');
}

function validPlace(place) {
  return place && /^[0-9]+$/.test(String(place.code || '')) &&
    typeof place.latitude === 'number' && Number.isFinite(place.latitude) && place.latitude >= -90 && place.latitude <= 90 &&
    typeof place.longitude === 'number' && Number.isFinite(place.longitude) && place.longitude >= -180 && place.longitude <= 180;
}

async function ensureUser(db, userId) {
  await db.prepare('INSERT OR IGNORE INTO users(id) VALUES (?)').bind(userId).run();
  await db.prepare('INSERT OR IGNORE INTO sync_state(user_id, revision) VALUES (?, 0)').bind(userId).run();
}

async function snapshot(db, userId) {
  const state = await db.prepare('SELECT revision FROM sync_state WHERE user_id = ?').bind(userId).first();
  const places = await db.prepare(
    'SELECT code, name, latitude, longitude, updated_at AS updatedAt FROM places WHERE user_id = ? ORDER BY updated_at DESC'
  ).bind(userId).all();
  return { revision: Number(state?.revision || 0), places: places.results || [] };
}

async function readJson(request) {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);

      if (url.pathname === '/health') {
        if (request.method !== 'GET') return json({ error: 'method_not_allowed' }, 405, { allow: 'GET' });
        return json({ ok: true, service: 'nv-cloud-sync' });
      }

      if (url.pathname !== '/v1/sync') return json({ error: 'not_found' }, 404);
      if (request.method !== 'GET' && request.method !== 'POST') {
        return json({ error: 'method_not_allowed' }, 405, { allow: 'GET, POST' });
      }

      const userId = await userIdFromRequest(request);
      await ensureUser(env.DB, userId);

      if (request.method === 'GET') return json(await snapshot(env.DB, userId));

      const body = await readJson(request);
      if (!body || typeof body !== 'object' || Array.isArray(body)) {
        return json({ error: 'invalid_json' }, 400);
      }

      const expectedRevision = Number(body.revision);
      const places = Array.isArray(body.places) ? body.places : [];
      if (!Number.isInteger(expectedRevision) || expectedRevision < 0) {
        return json({ error: 'invalid_revision' }, 400);
      }
      if (places.length > 500 || places.some(place => !validPlace(place))) {
        return json({ error: 'invalid_places' }, 400);
      }

      const current = await env.DB.prepare('SELECT revision FROM sync_state WHERE user_id = ?').bind(userId).first();
      const currentRevision = Number(current?.revision || 0);
      if (currentRevision !== expectedRevision) {
        return json({ error: 'revision_conflict', ...(await snapshot(env.DB, userId)) }, 409);
      }

      const nextRevision = currentRevision + 1;
      const statements = places.map(place => env.DB.prepare(
        `INSERT INTO places(user_id, code, name, latitude, longitude, updated_at)
         VALUES (?, ?, ?, ?, ?, ?)
         ON CONFLICT(user_id, code) DO UPDATE SET
           name=excluded.name, latitude=excluded.latitude, longitude=excluded.longitude,
           updated_at=MAX(places.updated_at, excluded.updated_at)`
      ).bind(
        userId,
        String(place.code),
        String(place.name || `مکان NV ${place.code}`).slice(0, 180),
        place.latitude,
        place.longitude,
        Number(place.updatedAt || Math.floor(Date.now() / 1000))
      ));
      statements.push(
        env.DB.prepare('UPDATE sync_state SET revision = ?, updated_at = unixepoch() WHERE user_id = ? AND revision = ?')
          .bind(nextRevision, userId, currentRevision)
      );
      await env.DB.batch(statements);
      return json(await snapshot(env.DB, userId));
    } catch (error) {
      if (String(error?.message) === 'unauthorized') return json({ error: 'unauthorized' }, 401);
      return json({ error: 'internal_error' }, 500);
    }
  }
};
