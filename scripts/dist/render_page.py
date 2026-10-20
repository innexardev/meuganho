"""Gera public/index.html (página de download) a partir de latest.json e history.json."""
import html
import json
import sys
from pathlib import Path

dist = Path(sys.argv[1])
latest = json.loads((dist / "latest.json").read_text())
history = json.loads((dist / "history.json").read_text()) if (dist / "history.json").exists() else [latest]
template = (Path(__file__).parent / "index.template.html").read_text()

size_mb = f"{latest['sizeBytes'] / 1048576:.1f}".replace(".", ",")
items = "\n".join(
    f"<li><strong>{html.escape(h['versionName'])}</strong> · {html.escape(h.get('publishedAt', '')[:10])} — {html.escape(h.get('notes', ''))}</li>"
    for h in history[:10]
)
page = (template
        .replace("{{VERSION}}", html.escape(latest["versionName"]))
        .replace("{{APK_URL}}", html.escape(latest["apkUrl"]))
        .replace("{{SIZE_MB}}", size_mb)
        .replace("{{SHA256}}", html.escape(latest["sha256"]))
        .replace("{{NOTES}}", html.escape(latest.get("notes", "")))
        .replace("{{HISTORY}}", items))
tmp = dist / "index.html.tmp"
tmp.write_text(page)
tmp.replace(dist / "index.html")
