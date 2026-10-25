-- Uma linha por chave vendida. device_id fica NULL até a primeira ativação.
CREATE TABLE IF NOT EXISTS licenses (
  license_key  TEXT PRIMARY KEY,          -- 16 caracteres A-Z0-9, sem hífens
  device_id    TEXT,                      -- AA:BB:CC:DD:EE:FF (MAC ou ID derivado)
  expires_at   INTEGER NOT NULL DEFAULT 0,-- epoch s; 0 = vitalícia
  revoked      INTEGER NOT NULL DEFAULT 0,
  note         TEXT,                      -- ex.: pedido/cliente
  created_at   INTEGER NOT NULL DEFAULT (unixepoch()),
  activated_at INTEGER
);
