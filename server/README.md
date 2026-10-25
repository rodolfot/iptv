# Servidor de licenças e anúncios (#29)

O app funciona sem nada disto: com `LICENSE_API_URL`, `LICENSE_PUBLIC_KEY` e
`ADS_CONFIG_URL` vazios, não há anúncios e a tela Premium informa que a ativação não
está disponível. Para ligar a monetização:

## 1. Servidor de licenças (Cloudflare Worker + D1)

```bash
cd server/license-worker
cp wrangler.toml.example wrangler.toml
npx wrangler d1 create tartatv-licenses          # copie o database_id para o wrangler.toml
npx wrangler d1 execute tartatv-licenses --remote --file schema.sql
node scripts/keygen.mjs                           # gera PRIVATE_JWK e PUBLIC_SPKI
npx wrangler secret put LICENSE_PRIVATE_JWK       # cole o PRIVATE_JWK
npx wrangler deploy                               # anote a URL, ex.: https://tartatv-license.<conta>.workers.dev
```

Emitir chaves (impressas como `ABCD-EFGH-…` no stderr; o SQL vai para o stdout):

```bash
node scripts/new-keys.mjs 10 "pedido 123"          # vitalícias
node scripts/new-keys.mjs 1 "teste" 30             # válida por 30 dias
npx wrangler d1 execute tartatv-licenses --remote --command "<SQL impresso>"
```

Revogar: `UPDATE licenses SET revoked = 1 WHERE license_key = '...'` (vale na próxima
ativação; tokens já emitidos continuam válidos offline até a validade — use chaves com
validade se precisar de revogação efetiva). Desvincular um aparelho (troca de TV):
`UPDATE licenses SET device_id = NULL WHERE license_key = '...'`.

### Como a proteção funciona

- **1 chave = 1 aparelho**: o vínculo é um `UPDATE ... WHERE device_id IS NULL OR
  device_id = ?` atômico; outro aparelho recebe `409 Licença já vinculada a outro
  dispositivo`.
- **Token assinado**: o servidor devolve `base64url(payload).base64url(assinatura)`
  com ECDSA P-256 (`{v, k, d, iat, exp}`); a chave privada só existe no Worker.
- **Validação offline no app** (`premium/LicenseManager.kt`): a cada abertura confere
  assinatura (chave pública embutida), `d` = ID deste aparelho e validade. O token fica
  em EncryptedSharedPreferences (chave no Android Keystore). Editar o arquivo, colar o
  token de outro aparelho ou mudar a validade invalida a licença e o app volta para a
  versão gratuita.
- **ID do aparelho**: MAC da Ethernet/Wi-Fi quando o Android expõe (TV boxes até o
  Android 10); senão um ID no formato MAC derivado do `ANDROID_ID` (SHA-256). Calculado
  uma vez e guardado, para não mudar após atualizações do sistema.

## 2. Anúncios

`ADS_CONFIG_URL` aponta para um JSON no formato de [`ads.example.json`](ads.example.json):
tags **VAST** de pre-roll/post-roll (qualquer ad server: Google Ad Manager, SpringServe,
Revive…), intervalo mínimo entre anúncios em vídeo e banners por posição (`home`,
`detail`). O próprio Worker pode servir isso em `GET /v1/ads` (variável `ADS_CONFIG`).

Regras no app (`ads/AdsManager.kt`): Premium nunca vê anúncio; sem resposta em
`timeoutMs` o conteúdo abre normalmente; zapping não dispara pre-roll a cada troca.

## 3. Build

Defina os secrets do repositório (Settings → Secrets → Actions); o workflow de release
os repassa ao Gradle:

| Secret | Valor |
|---|---|
| `LICENSE_API_URL` | URL do Worker (sem `/v1/...`) |
| `LICENSE_PUBLIC_KEY` | `PUBLIC_SPKI` do `keygen.mjs` |
| `ADS_CONFIG_URL` | URL do JSON de anúncios (ex.: `<worker>/v1/ads`) |

Local: `./gradlew :app:assembleRelease -PLICENSE_API_URL=... -PLICENSE_PUBLIC_KEY=... -PADS_CONFIG_URL=...`.
