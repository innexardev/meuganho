#!/usr/bin/env bash
# Empacota o projeto do iPhone (ios/) com a chave de envio de amostras e publica num link PRIVADO
# (endereço aleatório, fora da página pública): https://innexar.com.br/meuganho/ios/<token>.zip
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="${MEUGANHO_DIST:-/srv/innexar/production/meuganho-dist/public}"
KEY=$(grep -oP '^MEUGANHO_UPLOAD_KEY=\K.*' /srv/innexar/secrets/meuganho-dist.env)
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/MeuGanho-iOS"
cp -r "$ROOT/ios/." "$TMP/MeuGanho-iOS/"
rm -rf "$TMP/MeuGanho-iOS/"*.xcodeproj
cat > "$TMP/MeuGanho-iOS/Shared/Secrets.swift" <<SW
// Gerado por scripts/ios_package.sh. Não compartilhe este arquivo.
enum Secrets {
    static let uploadKey = "$KEY"
    static let appVersion = "ios-0.1.0"
}
SW
TOKEN_FILE="$ROOT/.ios_package_token"
[[ -f "$TOKEN_FILE" ]] || head -c 16 /dev/urandom | xxd -p > "$TOKEN_FILE"
TOKEN=$(cat "$TOKEN_FILE")
mkdir -p "$DIST/ios"
python3 -c "import shutil,sys; shutil.make_archive(sys.argv[1], 'zip', sys.argv[2], 'MeuGanho-iOS')" "$DIST/ios/$TOKEN.tmp" "$TMP"
mv "$DIST/ios/$TOKEN.tmp.zip" "$DIST/ios/$TOKEN.zip"
chmod -R a+rX "$DIST/ios"
echo "https://innexar.com.br/meuganho/ios/$TOKEN.zip"
