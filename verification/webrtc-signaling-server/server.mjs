import http from 'node:http';
import { randomUUID } from 'node:crypto';

const HOST = process.env.MASON_SIGNALING_HOST || '127.0.0.1';
const PORT = Number(process.env.MASON_SIGNALING_PORT || 48732);
const MAX_BODY_BYTES = 512 * 1024;
const MAX_SDP_BYTES = 256 * 1024;
const MAX_CANDIDATES = 256;
const MAX_OFFER_TTL_MS = 10 * 60 * 1000;
const offers = new Map();

function json(response, status, value) {
  const body = JSON.stringify(value);
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'content-type',
    'Access-Control-Allow-Methods': 'GET,POST,DELETE,OPTIONS',
  });
  response.end(body);
}

function empty(response, status) {
  response.writeHead(status, {
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'content-type',
    'Access-Control-Allow-Methods': 'GET,POST,DELETE,OPTIONS',
  });
  response.end();
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    let size = 0;
    let body = '';
    request.setEncoding('utf8');
    request.on('data', chunk => {
      size += Buffer.byteLength(chunk);
      if (size > MAX_BODY_BYTES) {
        reject(new Error('signaling request too large'));
        request.destroy();
        return;
      }
      body += chunk;
    });
    request.on('end', () => {
      try {
        resolve(JSON.parse(body));
      } catch {
        reject(new Error('request body must be valid JSON'));
      }
    });
    request.on('error', reject);
  });
}

function validOfferId(value) {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{8,128}$/.test(value);
}

function getOffer(offerId) {
  const offer = offers.get(offerId);
  if (!offer) return null;
  if (offer.expiresAt <= Date.now()) {
    offers.delete(offerId);
    return null;
  }
  return offer;
}

function publicOffer(offer) {
  return {
    offerId: offer.offerId,
    type: offer.type,
    sdp: offer.sdp,
    expiresAt: offer.expiresAt,
    revision: offer.revision,
    candidates: offer.candidates.desktop,
  };
}

function publicAnswer(offer) {
  return offer.answer ? {
    offerId: offer.offerId,
    type: offer.answer.type,
    sdp: offer.answer.sdp,
    revision: offer.answer.revision,
  } : null;
}

function candidateFrom(input) {
  if (!input || typeof input.candidate !== 'string' || input.candidate.length > 16 * 1024) {
    throw new Error('ICE candidate is invalid');
  }
  const index = Number(input.sdpMLineIndex);
  if (!Number.isInteger(index) || index < 0 || index > 1024) {
    throw new Error('ICE candidate sdpMLineIndex is invalid');
  }
  return {
    candidate: input.candidate,
    sdpMid: typeof input.sdpMid === 'string' ? input.sdpMid : null,
    sdpMLineIndex: index,
  };
}

function route(request) {
  const url = new URL(request.url, 'http://signaling.invalid');
  const parts = url.pathname.split('/').filter(Boolean);
  if (parts[0] !== 'v1' || parts[1] !== 'offers' || !validOfferId(parts[2])) return null;
  return { offerId: parts[2], action: parts[3] || '', query: url.searchParams };
}

async function handle(request, response) {
  if (request.method === 'OPTIONS') {
    empty(response, 204);
    return;
  }
  if (request.method === 'GET' && request.url === '/health') {
    json(response, 200, { status: 'ok', service: 'mason-webrtc-signaling', persistentBusinessData: false });
    return;
  }

  const target = route(request);
  if (!target) {
    json(response, 404, { error: 'not found' });
    return;
  }
  const { offerId } = target;

  if (request.method === 'POST' && target.action === '') {
    const input = await readJson(request);
    if (input.offerId && input.offerId !== offerId) {
      json(response, 400, { error: 'offerId does not match path' });
      return;
    }
    if (!['offer', 'answer'].includes(input.type) || typeof input.sdp !== 'string' ||
        input.sdp.length === 0 || Buffer.byteLength(input.sdp) > MAX_SDP_BYTES) {
      json(response, 400, { error: 'SDP description is invalid' });
      return;
    }
    const expiresAt = Number(input.expiresAt);
    if (!Number.isSafeInteger(expiresAt) || expiresAt <= Date.now() ||
        expiresAt > Date.now() + MAX_OFFER_TTL_MS) {
      json(response, 400, { error: 'offer expiry is invalid' });
      return;
    }
    const existing = getOffer(offerId);
    const offer = existing || {
      offerId,
      type: 'offer',
      sdp: '',
      expiresAt,
      revision: 0,
      candidates: { desktop: [], mobile: [] },
      answer: null,
    };
    if (input.type === 'offer') {
      offer.type = input.type;
      offer.sdp = input.sdp;
      offer.expiresAt = expiresAt;
      offer.answer = null;
      offer.candidates = { desktop: [], mobile: [] };
    } else {
      if (!existing || existing.sdp.length === 0) {
        json(response, 409, { error: 'offer is not registered' });
        return;
      }
      offer.answer = { type: input.type, sdp: input.sdp, revision: offer.revision + 1 };
    }
    offer.revision += 1;
    offers.set(offerId, offer);
    json(response, 201, { ok: true, offerId, revision: offer.revision, expiresAt: offer.expiresAt });
    return;
  }

  const offer = getOffer(offerId);
  if (!offer) {
    json(response, 404, { error: 'offer not found or expired' });
    return;
  }
  if (request.method === 'GET' && target.action === '') {
    json(response, 200, publicOffer(offer));
    return;
  }
  if (request.method === 'GET' && target.action === 'answer') {
    const answer = publicAnswer(offer);
    if (!answer) {
      json(response, 404, { error: 'answer not available' });
      return;
    }
    json(response, 200, answer);
    return;
  }
  if (request.method === 'GET' && target.action === 'candidates') {
    const side = target.query.get('side');
    if (!['desktop', 'mobile'].includes(side)) {
      json(response, 400, { error: 'side must be desktop or mobile' });
      return;
    }
    json(response, 200, {
      offerId,
      revision: offer.revision,
      candidates: offer.candidates[side === 'desktop' ? 'mobile' : 'desktop'],
    });
    return;
  }
  if (request.method === 'POST' && target.action === 'candidates') {
    const input = await readJson(request);
    if (!['desktop', 'mobile'].includes(input.side)) {
      json(response, 400, { error: 'side must be desktop or mobile' });
      return;
    }
    if (offer.candidates[input.side].length >= MAX_CANDIDATES) {
      json(response, 413, { error: 'too many ICE candidates' });
      return;
    }
    offer.candidates[input.side].push(candidateFrom(input));
    offer.revision += 1;
    json(response, 201, { ok: true, revision: offer.revision });
    return;
  }
  if (request.method === 'DELETE' && target.action === '') {
    offers.delete(offerId);
    empty(response, 204);
    return;
  }
  json(response, 404, { error: 'not found' });
}

export function createSignalingServer({ host = HOST, port = PORT } = {}) {
  const server = http.createServer((request, response) => {
    handle(request, response).catch(error => {
      if (!response.headersSent) json(response, 400, { error: error.message });
      else response.destroy();
    });
  });
  return { server, host, port };
}

export function clearOffers() {
  offers.clear();
}

const cleanupTimer = setInterval(() => {
  for (const [offerId, offer] of offers) {
    if (offer.expiresAt <= Date.now()) offers.delete(offerId);
  }
}, 30_000);
cleanupTimer.unref();

if (process.argv[1] && new URL(`file://${process.argv[1]}`).pathname.endsWith('/server.mjs')) {
  const { server, host, port } = createSignalingServer();
  server.listen(port, host, () => {
    console.log(`MASON_SIGNALING_READY http://${host}:${port}`);
  });
}
