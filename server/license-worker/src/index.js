// Servidor de licenças Premium do TartaTV (#29) — Cloudflare Worker + D1.
//
// POST /v1/activate  {"key": "ABCD1234EFGH5678", "deviceId": "AA:BB:CC:DD:EE:FF"}
//   200 {"token": "<payload>.<assinatura>"}      chave válida (vincula na 1ª vez)
//   404 {"error": "invalid_key"}                  chave inexistente ou revogada
//   409 {"error": "bound_to_other_device", ...}   chave já vinculada a outro aparelho
// GET  /v1/ads        configuração de anúncios (variável ADS_CONFIG, JSON)
//
// O vínculo é atômico (UPDATE condicional no D1): duas ativações simultâneas
// da mesma chave em aparelhos diferentes nunca vinculam os dois.
// O token é ECDSA P-256/SHA-256 sobre o payload em base64url — o app valida
// offline com a chave pública (gradle: LICENSE_PUBLIC_KEY).

const KEY_RE = /^[A-Z0-9]{16}$/;
const DEVICE_RE = /^[0-9A-F]{2}(:[0-9A-F]{2}){5}$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (request.method === 'POST' && url.pathname === '/v1/activate') {
        return await activate(request, env);
      }
      if (request.method === 'GET' && url.pathname === '/v1/ads') {
        return json(env.ADS_CONFIG ? JSON.parse(env.ADS_CONFIG) : {}, 200, { 'Cache-Control': 'public, max-age=3600' });
      }
      return json({ error: 'not_found' }, 404);
    } catch (e) {
      return json({ error: 'server_error' }, 500);
    }
  },
};

async function activate(request, env) {
  const body = await request.json().catch(() => ({}));
  const key = String(body.key || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  const deviceId = String(body.deviceId || '').toUpperCase();
  if (!KEY_RE.test(key) || !DEVICE_RE.test(deviceId)) return json({ error: 'invalid_key' }, 404);

  const now = Math.floor(Date.now() / 1000);
  // Vincula só se ainda livre (ou já deste aparelho) — atômico no D1.
  await env.DB.prepare(
    `UPDATE licenses SET device_id = ?1, activated_at = COALESCE(activated_at, ?2)
     WHERE license_key = ?3 AND revoked = 0 AND (device_id IS NULL OR device_id = ?1)`
  ).bind(deviceId, now, key).run();

  const row = await env.DB.prepare(
    'SELECT device_id, expires_at, revoked FROM licenses WHERE license_key = ?1'
  ).bind(key).first();
  if (!row || row.revoked) return json({ error: 'invalid_key' }, 404);
  if (row.device_id !== deviceId) {
    return json({ error: 'bound_to_other_device', message: 'Licença já vinculada a outro dispositivo' }, 409);
  }
  if (row.expires_at && row.expires_at <= now) return json({ error: 'invalid_key' }, 404);

  const token = await signToken(env.LICENSE_PRIVATE_JWK, {
    v: 1, k: key, d: deviceId, iat: now, exp: row.expires_at || 0,
  });
  return json({ token });
}

export async function signToken(privateJwk, payload) {
  const key = await crypto.subtle.importKey(
    'jwk', typeof privateJwk === 'string' ? JSON.parse(privateJwk) : privateJwk,
    { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign']
  );
  const payloadB64 = b64url(new TextEncoder().encode(JSON.stringify(payload)));
  const sig = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' }, key, new TextEncoder().encode(payloadB64)
  );
  return `${payloadB64}.${b64url(new Uint8Array(sig))}`;
}

function b64url(bytes) {
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function json(obj, status = 200, headers = {}) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', ...headers },
  });
}
