#!/usr/bin/env bash
# Publica uma versão nova do MeuGanho em https://innexar.com.br/meuganho
# SEM rebuild do site: só copia o APK e reescreve latest.json + página.
# O app instalado detecta a versão nova sozinho (verificação a cada 6 h ou em
# Configurações > Atualizações > Verificar).
#
# Uso:  scripts/publish.sh "O que mudou nesta versão"
# Antes: suba versionCode e versionName em app/build.gradle.kts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="${MEUGANHO_DIST:-/srv/innexar/production/meuganho-dist/public}"
CERT_SHA256="69e646a301618ed60cd650daee352b7f62842ad8dd170eaea712fc91901aedee"
APKSIGNER="${ANDROID_HOME:-/root/android-sdk}/build-tools/36.0.0/apksigner"
NOTES="${1:?Informe as notas da versão: scripts/publish.sh \"o que mudou\"}"

cd "$ROOT"
CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
NAME=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
CURRENT=$(python3 -c "import json;print(json.load(open('$DIST/latest.json'))['versionCode'])" 2>/dev/null || echo 0)
if (( CODE <= CURRENT )); then
  echo "✗ versionCode $CODE não é maior que o publicado ($CURRENT). Suba a versão em app/build.gradle.kts." >&2
  exit 1
fi
TARGET="$DIST/apk/meuganho-$NAME.apk"
[[ -e "$TARGET" ]] && { echo "✗ $TARGET já existe. Use outro versionName." >&2; exit 1; }

echo "▶ Testes + simulação de turnos + build de release ($NAME / $CODE)…"
# A simulação (ShiftSimulationTest) roda junto: se a precisão cair abaixo do piso, nada é publicado.
./gradlew -q -PskipScreens -Psim.out="$ROOT/app/build/sim-report" testDebugUnitTest assembleRelease
"$ROOT/scripts/sim_publish.sh" "$ROOT/app/build/sim-report" "$DIST"
APK=app/build/outputs/apk/release/app-release.apk

echo "▶ Conferindo assinatura…"
SIGNED=$("$APKSIGNER" verify --print-certs "$APK" | grep -oP 'SHA-256 digest: \K[0-9a-f]+' | head -1)
[[ "$SIGNED" == "$CERT_SHA256" ]] || { echo "✗ APK assinado com chave errada ($SIGNED)." >&2; exit 1; }

echo "▶ Publicando…"
mkdir -p "$DIST/apk"
cp "$APK" "$TARGET.tmp" && mv "$TARGET.tmp" "$TARGET"
SHA=$(sha256sum "$TARGET" | cut -d' ' -f1)
SIZE=$(stat -c %s "$TARGET")
python3 - "$DIST" "$CODE" "$NAME" "$SHA" "$SIZE" "$NOTES" <<'PY'
import json, os, sys
from datetime import datetime, timezone
dist, code, name, sha, size, notes = sys.argv[1:]
manifest = {
    "versionCode": int(code), "versionName": name,
    "apkUrl": f"apk/meuganho-{name}.apk", "sha256": sha, "sizeBytes": int(size),
    "notes": notes, "publishedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
}
# Histórico para a página de download.
hist_path = os.path.join(dist, "history.json")
history = json.load(open(hist_path)) if os.path.exists(hist_path) else []
history.insert(0, manifest)
for path, data in ((hist_path, history), (os.path.join(dist, "latest.json"), manifest)):
    with open(path + ".tmp", "w") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(path + ".tmp", path)  # troca atômica: o app nunca lê arquivo pela metade
PY
python3 "$ROOT/scripts/dist/render_page.py" "$DIST"
chmod -R a+rX "$DIST"
echo "✓ MeuGanho $NAME publicado: https://innexar.com.br/meuganho  (sha256 $SHA)"
