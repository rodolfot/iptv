#!/usr/bin/env bash
# Dump completo do servidor Xtream em arquivos JSON para análise manual.
#
# Uso:
#   export XT_HOST="http://seu-host:8080"
#   export XT_USER="seu-usuario"
#   export XT_PASS="sua-senha"
#   bash scripts/dump-xtream.sh
#
# Saídas em: ./xtream-dump/
#   - account_info.json        (player_api.php?action=...)
#   - live_categories.json
#   - live_streams.json
#   - vod_categories.json
#   - vod_streams.json
#   - series_categories.json
#   - series.json
#   - series_info_<seriesId>.json   (todas as séries; demora)
#
# Para baixar apenas uma série específica (ex.: aquela com temporada vazia):
#   bash scripts/dump-xtream.sh SERIES_ID
#
# Requer: bash + curl + jq (jq opcional, só usado para pretty-print)

set -euo pipefail

: "${XT_HOST:?Defina XT_HOST=http://host:porta}"
: "${XT_USER:?Defina XT_USER=usuario}"
: "${XT_PASS:?Defina XT_PASS=senha}"

OUT_DIR="xtream-dump"
mkdir -p "$OUT_DIR"

# UA igual ao app — alguns provedores filtram por User-Agent
UA="VLC/3.0.20 LibVLC/3.0.20"

API="$XT_HOST/player_api.php?username=$XT_USER&password=$XT_PASS"

fetch() {
    local action="$1"
    local extra="${2:-}"
    local out_file="$OUT_DIR/$3"
    local url="$API&action=$action$extra"
    echo "→ $action $extra"
    # --compressed: aceita gzip (alguns servers só servem comprimido)
    # -L: segue redirect; -fSs: silencioso mas falha em HTTP error
    if curl -fSsL --compressed -A "$UA" "$url" -o "$out_file"; then
        local size
        size=$(wc -c < "$out_file")
        echo "   ✓ $size bytes → $out_file"
        # Pretty-print se jq disponível, mantém o original como .raw
        if command -v jq >/dev/null 2>&1; then
            if jq . "$out_file" > "$out_file.pretty" 2>/dev/null; then
                mv "$out_file.pretty" "$out_file"
            fi
        fi
    else
        echo "   ✗ FALHOU"
    fi
}

# Modo "baixar uma série só" — útil quando você já identificou a problemática
if [[ $# -gt 0 ]]; then
    SERIES_ID="$1"
    fetch "get_series_info" "&series_id=$SERIES_ID" "series_info_${SERIES_ID}.json"
    echo
    echo "Arquivo salvo em $OUT_DIR/series_info_${SERIES_ID}.json"
    exit 0
fi

# Dump completo
fetch "" "" "account_info.json"
fetch "get_live_categories" "" "live_categories.json"
fetch "get_vod_categories" "" "vod_categories.json"
fetch "get_series_categories" "" "series_categories.json"
fetch "get_live_streams" "" "live_streams.json"
fetch "get_vod_streams" "" "vod_streams.json"
fetch "get_series" "" "series.json"

echo
echo "Dump base completo. Lista os IDs das séries com:"
echo "  jq -r '.[] | \"\\(.series_id)\\t\\(.name)\"' $OUT_DIR/series.json | head -40"
echo
echo "Depois baixe series_info de uma específica com:"
echo "  bash scripts/dump-xtream.sh <series_id>"
echo
echo "Para baixar series_info de TODAS (atenção: pode demorar muito):"
echo "  jq -r '.[].series_id' $OUT_DIR/series.json | while read id; do \\"
echo "    bash scripts/dump-xtream.sh \"\$id\"; \\"
echo "  done"
