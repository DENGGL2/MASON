import test from 'node:test';
import assert from 'node:assert/strict';
import { createSignalingServer, clearOffers } from './server.mjs';

let server;
let baseUrl;

test.before(async () => {
  clearOffers();
  ({ server } = createSignalingServer({ host: '127.0.0.1', port: 0 }));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => {
  clearOffers();
  await new Promise(resolve => server.close(resolve));
});

async function request(path, options) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: { 'content-type': 'application/json', ...(options?.headers || {}) },
  });
  const text = await response.text();
  return { response, body: text ? JSON.parse(text) : null };
}

test('stores only short-lived SDP and exchanges answer and candidates', async () => {
  const offerId = 'offer_test_123456';
  const expiresAt = Date.now() + 60_000;
  const offer = await request(`/v1/offers/${offerId}`, {
    method: 'POST',
    body: JSON.stringify({ offerId, type: 'offer', sdp: 'v=0\r\n', expiresAt }),
  });
  assert.equal(offer.response.status, 201);

  const fetched = await request(`/v1/offers/${offerId}`, { method: 'GET' });
  assert.equal(fetched.response.status, 200);
  assert.equal(fetched.body.sdp, 'v=0\r\n');
  assert.equal(fetched.body.candidates.length, 0);

  const candidate = await request(`/v1/offers/${offerId}/candidates`, {
    method: 'POST',
    body: JSON.stringify({
      side: 'mobile',
      candidate: 'candidate:1 1 UDP 1 192.0.2.1 1234 typ host',
      sdpMid: '0',
      sdpMLineIndex: 0,
    }),
  });
  assert.equal(candidate.response.status, 201);

  const candidates = await request(`/v1/offers/${offerId}/candidates?side=desktop`, { method: 'GET' });
  assert.equal(candidates.response.status, 200);
  assert.equal(candidates.body.candidates.length, 1);

  const answer = await request(`/v1/offers/${offerId}`, {
    method: 'POST',
    body: JSON.stringify({ type: 'answer', sdp: 'v=0-answer\r\n', expiresAt }),
  });
  assert.equal(answer.response.status, 201);

  const fetchedAnswer = await request(`/v1/offers/${offerId}/answer`, { method: 'GET' });
  assert.equal(fetchedAnswer.response.status, 200);
  assert.equal(fetchedAnswer.body.sdp, 'v=0-answer\r\n');
  assert.equal(typeof fetchedAnswer.body.revision, 'number');
});

test('rejects expired offers and deletes temporary state', async () => {
  const expired = await request('/v1/offers/expired_123456', {
    method: 'POST',
    body: JSON.stringify({ type: 'offer', sdp: 'v=0', expiresAt: Date.now() - 1 }),
  });
  assert.equal(expired.response.status, 400);

  const deleted = await request('/v1/offers/offer_test_123456', { method: 'DELETE' });
  assert.equal(deleted.response.status, 204);
  const missing = await request('/v1/offers/offer_test_123456', { method: 'GET' });
  assert.equal(missing.response.status, 404);
});
