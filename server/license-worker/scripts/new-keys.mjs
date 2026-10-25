// Gera N chaves de ativação e o SQL para inserir no D1.
//   node scripts/new-keys.mjs 10 "pedido 123" [validade-em-dias]
//   wrangler d1 execute tartatv-licenses --remote --command "<SQL impresso>"
import { randomInt } from 'node:crypto';
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // sem 0/O/1/I
const [n = '1', note = '', days = '0'] = process.argv.slice(2);
const exp = Number(days) > 0 ? Math.floor(Date.now() / 1000) + Number(days) * 86400 : 0;
const rows = [];
for (let i = 0; i < Number(n); i++) {
  let k = '';
  for (let j = 0; j < 16; j++) k += ALPHABET[randomInt(ALPHABET.length)];
  rows.push(k);
  console.error(k.match(/.{4}/g).join('-'));
}
const esc = (s) => s.replace(/'/g, "''");
console.log('INSERT INTO licenses (license_key, expires_at, note) VALUES ' +
  rows.map((k) => `('${k}', ${exp}, '${esc(note)}')`).join(', ') + ';');
