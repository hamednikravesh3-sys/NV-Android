import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.js';

const REGISTRY_BASE = 5_000_000_000_000;
const REGISTRY_LIMIT = 6_000_000_000_000;

class FakeDb {
  constructor() {
    this.byLocation = new Map();
    this.byCode = new Map();
  }

  prepare(sql) {
    const db = this;
    return {
      bind(...args) {
        return {
          async first() {
            if (sql.includes('WHERE location_key=?1')) return db.byLocation.get(args[0]) ?? null;
            if (sql.includes('WHERE code=?1')) return db.byCode.get(Number(args[0])) ?? null;
            throw new Error(`Unexpected SELECT: ${sql}`);
          },
          async run() {
            if (!sql.includes('INSERT OR IGNORE INTO nv_codes')) throw new Error(`Unexpected write: ${sql}`);
            const [code, locationKey, name, latitude, longitude] = args;
            if (!db.byLocation.has(locationKey) && !db.byCode.has(Number(code))) {
              const row = {
                code: Number(code),
                name,
                latitude,
                longitude,
                created_at: 1
              };
              db.byLocation.set(locationKey, row);
              db.byCode.set(row.code, row);
            }
            return { success: true };
          }
        };
      }
    };
  }
}

const env = () => ({ DB: new FakeDb() });
const json = async response => JSON.parse(await response.text());

test('health endpoint is explicit and no-store', async () => {
  const response = await worker.fetch(new Request('https://registry.example/health'), env());
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.equal(response.headers.get('x-content-type-options'), 'nosniff');
  assert.equal((await json(response)).allocation, 'online-unique');
});

test('unknown paths return 404 instead of a false healthy response', async () => {
  const response = await worker.fetch(new Request('https://registry.example/anything'), env());
  assert.equal(response.status, 404);
  assert.deepEqual(await json(response), { error: 'not_found' });
});

test('allocation rejects malformed JSON and wrong methods', async () => {
  const malformed = await worker.fetch(new Request('https://registry.example/v1/codes/allocate', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: '{'
  }), env());
  assert.equal(malformed.status, 400);
  assert.deepEqual(await json(malformed), { error: 'invalid_json' });

  const wrongMethod = await worker.fetch(new Request('https://registry.example/v1/codes/allocate'), env());
  assert.equal(wrongMethod.status, 405);
  assert.equal(wrongMethod.headers.get('allow'), 'POST');
});

test('allocation is idempotent and uses the central reserved range', async () => {
  const dbEnv = env();
  const allocate = (latitude, longitude, name) => worker.fetch(new Request('https://registry.example/v1/codes/allocate', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ latitude, longitude, name })
  }), dbEnv);

  const first = await allocate(35.6891984, 51.3889736, 'تهران');
  assert.equal(first.status, 201);
  const firstBody = await json(first);
  assert.equal(firstBody.status, 'allocated');
  assert.ok(firstBody.code >= REGISTRY_BASE && firstBody.code < REGISTRY_LIMIT);

  const second = await allocate(35.68919849, 51.38897359, 'نام دیگر');
  assert.equal(second.status, 200);
  const secondBody = await json(second);
  assert.equal(secondBody.status, 'existing');
  assert.equal(secondBody.code, firstBody.code);
  assert.equal(secondBody.name, 'تهران');
});

test('lookup validates codes and returns allocated rows', async () => {
  const dbEnv = env();
  const allocated = await worker.fetch(new Request('https://registry.example/v1/codes/allocate', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ latitude: 36.260462, longitude: 59.616755, name: 'مشهد' })
  }), dbEnv);
  const allocation = await json(allocated);

  const found = await worker.fetch(new Request(`https://registry.example/v1/codes/${allocation.code}`), dbEnv);
  assert.equal(found.status, 200);
  assert.equal((await json(found)).name, 'مشهد');

  const invalid = await worker.fetch(new Request('https://registry.example/v1/codes/1abc'), dbEnv);
  assert.equal(invalid.status, 400);

  const missing = await worker.fetch(new Request('https://registry.example/v1/codes/5999999999999'), dbEnv);
  assert.equal(missing.status, 404);
});

test('separate locations receive separate registry codes', async () => {
  const dbEnv = env();
  const responseA = await worker.fetch(new Request('https://registry.example/v1/codes/allocate', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ latitude: 35.7, longitude: 51.4, name: 'A' })
  }), dbEnv);
  const responseB = await worker.fetch(new Request('https://registry.example/v1/codes/allocate', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ latitude: 36.3, longitude: 59.6, name: 'B' })
  }), dbEnv);
  const a = await json(responseA);
  const b = await json(responseB);
  assert.notEqual(a.code, b.code);
});
