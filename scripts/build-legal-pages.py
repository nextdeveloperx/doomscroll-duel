#!/usr/bin/env python3
"""Builds web/privacy.html, web/terms.html and web/delete-account.html from docs/play/*.md.

  cp scripts/legal-config.template.json scripts/legal-config.json   # then fill it in
  python3 scripts/build-legal-pages.py

Without a config the {{PLACEHOLDERS}} stay visible in the pages, and `check_play_readiness.py --release` refuses them.
"""
import html
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PAGES = {"privacy-policy.md": "privacy.html", "terms-of-use.md": "terms.html", "account-deletion-page.md": "delete-account.html"}

CSS = """
:root{color-scheme:light dark;--bg:#fff;--fg:#1b1037;--muted:#5b4d86;--line:#d9d2f0;--link:#5a2fd0}
@media (prefers-color-scheme:dark){:root{--bg:#1b1037;--fg:#f4f0ff;--muted:#c5b8f0;--line:#3b2a73;--link:#9ee7ff}}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:46rem;margin:0 auto;padding:1.25rem 1rem 4rem}
h1{font-size:1.9rem;line-height:1.2}h2{font-size:1.3rem;margin-top:2rem;border-top:2px solid var(--line);padding-top:1rem}h3{font-size:1.1rem}
a{color:var(--link)}table{border-collapse:collapse;width:100%;display:block;overflow-x:auto}
th,td{border:1px solid var(--line);padding:.5rem .6rem;text-align:left;vertical-align:top}th{background:rgba(127,127,127,.12)}
code{background:rgba(127,127,127,.18);padding:.1rem .3rem;border-radius:4px}
"""


def inline(text):
    text = html.escape(text, quote=False)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", text)
    text = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", r'<a href="\2">\1</a>', text)
    return text


def convert(md):
    out, lines, i = [], md.split("\n"), 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
        elif line.startswith("#"):
            level = len(line) - len(line.lstrip("#"))
            out.append(f"<h{level}>{inline(line[level:].strip())}</h{level}>")
            i += 1
        elif line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append([c.strip() for c in lines[i].strip().strip("|").split("|")])
                i += 1
            head, body = rows[0], [r for r in rows[2:]]
            out.append("<table><thead><tr>" + "".join(f"<th>{inline(c)}</th>" for c in head) + "</tr></thead><tbody>")
            for r in body:
                out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>")
            out.append("</tbody></table>")
        elif re.match(r"^\s*(-|\d+\.)\s", line):
            ordered = bool(re.match(r"^\s*\d+\.", line))
            tag = "ol" if ordered else "ul"
            items = []
            while i < len(lines) and re.match(r"^\s*(-|\d+\.)\s", lines[i]):
                items.append(re.sub(r"^\s*(-|\d+\.)\s", "", lines[i]))
                i += 1
            out.append(f"<{tag}>" + "".join(f"<li>{inline(x)}</li>" for x in items) + f"</{tag}>")
        elif line.startswith(">"):
            quote = []
            while i < len(lines) and lines[i].startswith(">"):
                quote.append(lines[i].lstrip("> "))
                i += 1
            out.append("<blockquote>" + inline(" ".join(quote)) + "</blockquote>")
        else:
            para = []
            while i < len(lines) and lines[i].strip() and not lines[i].startswith(("#", "|", ">")) and not re.match(r"^\s*(-|\d+\.)\s", lines[i]):
                para.append(lines[i])
                i += 1
            out.append("<p>" + "<br>\n".join(inline(p) for p in para) + "</p>")
    return "\n".join(out)


def main():
    config_path = ROOT / "scripts/legal-config.json"
    config = json.loads(config_path.read_text()) if config_path.exists() else {}
    if not config:
        print("note: scripts/legal-config.json not found, placeholders stay in the pages", file=sys.stderr)
    out_dir = ROOT / "web"
    out_dir.mkdir(exist_ok=True)
    for src, dest in PAGES.items():
        md = (ROOT / "docs/play" / src).read_text(encoding="utf-8")
        for key, value in config.items():
            md = md.replace("{{" + key + "}}", value)
        title = re.match(r"# (.+)", md).group(1)
        page = (
            '<!doctype html>\n<html lang="en"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            f"<title>{html.escape(title)} - Doomscroll Duel</title><style>{CSS}</style></head>"
            f"<body><main>\n{convert(md)}\n</main></body></html>\n"
        )
        (out_dir / dest).write_text(page, encoding="utf-8")
        print("wrote", (out_dir / dest).relative_to(ROOT))


main()
