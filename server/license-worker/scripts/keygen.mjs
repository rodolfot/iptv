// Gera o par de chaves ECDSA P-256 da licença.
//   node scripts/keygen.mjs
// - PRIVATE_JWK  → wrangler secret put LICENSE_PRIVATE_JWK  (nunca versionar)
// - PUBLIC_SPKI  → propriedade gradle LICENSE_PUBLIC_KEY (secret do CI)
const { publicKey, privateKey } = await crypto.subtle.generateKey(
  { name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
const jwk = await crypto.subtle.exportKey('jwk', privateKey);
const spki = Buffer.from(await crypto.subtle.exportKey('spki', publicKey)).toString('base64');
console.log('PRIVATE_JWK=' + JSON.stringify(jwk));
console.log('PUBLIC_SPKI=' + spki);
