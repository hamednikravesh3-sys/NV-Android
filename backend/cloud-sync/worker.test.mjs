import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.js';

class BoundStatement {
  constructor(db, sql, args = []) {
    this.db = db;
    this.sql = sql;
    this.args = args;
  }

  bind(...args) { return new BoundStatement(this.db, this.sql, args); }

  async run() {
    const [a, b, c, d, e, f] = this.args;
    if (this.sql.includes('INSERT OR IGNORE INTO users')) {
      this.db.users.add(a);
      return { success: true, meta: { changes: 1 } };
    }
    if (this.sql.includes('INSERT OR IGNORE INTO sync_state')) {
      if (!this.db.revisions.has(a)) this.db.revisions.set(a, 0);
      return { success: true, meta: { changes: 1 } };
    }
    if (this.sql.includes('INSERT INTO places')) {
      const key = `${a}:${b}`;
      const previous = this.db.places.get(key);
      if (!previous || Number(f) >= Number(previous.updatedAt)) {
        this.db.places.set(key, {
          code: String(b),
          name: c,
          latitude: d,
          longitude: e,
          updatedAt: Number(f)
        });
      }
      return { success: true, meta: { changes: 1 } };
    }
    if (this.sql.includes('UPDATE sync_state SET revision')) {
      const [nextRevision, userId, expectedRevision] = this.args;
      if (this.db.revisions.get(userId) !== expectedRevision) {
        return { success: true, meta: { changes: 0 } };
      }
      this.db.revisions.set(userId, nextRevision);
      return { success: true, meta: { changes: 1 } };
    }
    throw new Error(`Unexpected run SQL: ${this.sql}`);
  }

  async first() {
    const [userId] = this.args;
    if (this.sql.includes('SELECT revision FROM sync_state')) {
      const revision = this.db.revisions.get(userId);
      return revision === undefined ? null : { revision };
    }
    throw new Error(`Unexpected first SQL: ${this.sql}`);
  }

  async all() {
    const [userId] = this.args;
    if (this.sql.includes('FROM places WHERE user_id')) {
      const rows = [...this.db.places.entries()]
        .filter(([key]) => key.startsWith(`${userId}:`))
        .map(([, value]) => value)
        .sort((x, y) => y.updatedAt - x.updatedAt);
      return { results: rows };
    }
    throw new Error(`Unexpected all SQL: ${this.sql}`);
  }
}

class FakeDb {
  constructor() {
    this.users = new Set();
    this.revisions = new Map();
    this.places = new Map();
  }

  prepare(sql) { return new BoundStatement(this, sql); }

  async batch(statements) {
    const results = [];
    for (const statement of statements) results.push(await statement.run());
    return results;
  }
}

const env = () => ({ DB: new FakeDb() });
const token = '0123456789abcdef0123456789abcdef';
const auth = { authorization: `Bearer ${token}` };
const readJson = async response => JSON.parse(await response.text());

function request(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  return new Request(`https://cloud.example${path}`, { ...options, headers });
}

test('health is GET-only and hardened against caching/sniffing', async () => {
  const ok = await worker.fetch(request('/health'), env());
  assert.equal(ok.status, 200);
  assert.equal(ok.headers.get('cache-control'), 'no-store');
  assert.equal(ok.headers.get('x-content-type-options'), 'nosniff');
  assert.deepEqual(await readJson(ok), { ok: true, service: 'nv-cloud-sync' });

  const wrong = await worker.fetch(request('/health', { method: 'POST' }), env());
  assert.equal(wrong.status, 405);
  assert.equal(wrong.headers.get('allow'), 'GET');
});

test('unknown routes, methods and missing authentication fail closed', async () => {
  const missing = await worker.fetch(request('/missing'), env());
  assert.equal(missing.status, 404);

  const wrongMethod = await worker.fetch(request('/v1/sync', { method: 'PUT' }), env());
  assert.equal(wrongMethod.status, 405);
  assert.equal(wrongMethod.headers.get('allow'), 'GET, POST');

  const unauthorized = await worker.fetch(request('/v1/sync'), env());
  assert.equal(unauthorized.status, 401);
  assert.deepEqual(await readJson(unauthorized), { error: 'unauthorized' });
});

test('malformed JSON and invalid places return 400 rather than 500', async () => {
  const dbEnv = env();
  const malformed = await worker.fetch(request('/v1/sync', {
    method: 'POST',
    headers: { ...auth, 'content-type': 'application/json' },
    body: '{'
  }), dbEnv);
  assert.equal(malformed.status, 400);
  assert.deepEqual(await readJson(malformed), { error: 'invalid_json' });

  const invalid = await worker.fetch(request('/v1/sync', {
    method: 'POST',
    headers: { ...auth, 'content-type': 'application/json' },
    body: JSON.stringify({ revision: 0, places: [{ code: '1', latitude: NaN, longitude: 51 }] })
  }), dbEnv);
  assert.equal(invalid.status, 400);
  assert.deepEqual(await readJson(invalid), { error: 'invalid_places' });
});

test('sync is isolated by bearer-token-derived user and increments revision', async () => {
  const dbEnv = env();
  const first = await worker.fetch(request('/v1/sync', {
    method: 'POST',
    headers: { ...auth, 'content-type': 'application/json' },
    body: JSON.stringify({
      revision: 0,
      places: [{ code: '1845623', name: 'تهران', latitude: 35.6892, longitude: 51.389, updatedAt: 100 }]
    })
  }), dbEnv);
  assert.equal(first.status, 200);
  const body = await readJson(first);
  assert.equal(body.revision, 1);
  assert.equal(body.places.length, 1);
  assert.equal(body.places[0].code, '1845623');

  const get = await worker.fetch(request('/v1/sync', { headers: auth }), dbEnv);
  assert.equal(get.status, 200);
  assert.equal((await readJson(get)).revision, 1);

  const otherAuth = { authorization: 'Bearer abcdefghijklmnopqrstuvwxyz123456' };
  const other = await worker.fetch(request('/v1/sync', { headers: otherAuth }), dbEnv);
  const otherBody = await readJson(other);
  assert.equal(otherBody.revision, 0);
  assert.equal(otherBody.places.length, 0);
});

test('stale revision returns conflict snapshot without applying changes', async () => {
  const dbEnv = env();
  const post = body => worker.fetch(request('/v1/sync', {
    method: 'POST',
    headers: { ...auth, 'content-type': 'application/json' },
    body: JSON.stringify(body)
  }), dbEnv);

  const accepted = await post({
    revision: 0,
    places: [{ code: '1', name: 'A', latitude: 35, longitude: 51, updatedAt: 1 }]
  });
  assert.equal(accepted.status, 200);

  const stale = await post({
    revision: 0,
    places: [{ code: '2', name: 'B', latitude: 36, longitude: 52, updatedAt: 2 }]
  });
  assert.equal(stale.status, 409);
  const conflict = await readJson(stale);
  assert.equal(conflict.error, 'revision_conflict');
  assert.equal(conflict.revision, 1);
  assert.deepEqual(conflict.places.map(place => place.code), ['1']);
});
