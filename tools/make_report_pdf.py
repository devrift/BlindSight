#!/usr/bin/env python3
"""Render docs/PROJECT_REPORT.md into a print-ready PDF.

Single source of truth: the Markdown file. This script is a small,
dependency-light typesetter (reportlab only, no network) so the report can be
regenerated after any edit:

    python tools/make_report_pdf.py [input.md] [output.pdf]

Supported constructs: # / ## / ### headings, "- " bullets, "> " quoted blocks
(certificates, declarations), pipe tables with a header row, "<!-- pagebreak -->"
page breaks, and **bold** / *italic* spans. Everything else becomes a justified
body paragraph.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

REPO_ROOT = Path(__file__).resolve().parents[1]


def styles() -> dict[str, ParagraphStyle]:
    base = getSampleStyleSheet()
    return {
        "h1": ParagraphStyle("h1", parent=base["Title"], fontSize=24, spaceAfter=10),
        "h2": ParagraphStyle(
            "h2", parent=base["Heading2"], fontSize=15, spaceBefore=14, spaceAfter=6
        ),
        "h3": ParagraphStyle(
            "h3", parent=base["Heading3"], fontSize=12.5, spaceBefore=10, spaceAfter=4
        ),
        "body": ParagraphStyle(
            "body",
            parent=base["BodyText"],
            fontSize=10.5,
            leading=15.5,
            alignment=4,  # justified
            spaceAfter=6,
        ),
        "bullet": ParagraphStyle(
            "bullet", parent=base["BodyText"], fontSize=10.5, leading=15.5,
            leftIndent=14, bulletIndent=4, spaceAfter=4, alignment=4,
        ),
        "quote": ParagraphStyle(
            "quote", parent=base["BodyText"], fontSize=10.5, leading=16,
            leftIndent=16, rightIndent=12, spaceAfter=6, fontName="Times-Italic",
        ),
        "cell": ParagraphStyle("cell", parent=base["BodyText"], fontSize=8.6,
                               leading=11.5, spaceAfter=0),
        "cellhead": ParagraphStyle("cellhead", parent=base["BodyText"], fontSize=8.8,
                                   leading=11.5, spaceAfter=0, fontName="Helvetica-Bold"),
    }


def inline(text: str) -> str:
    text = escape(text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"(?<!\*)\*([^*]+?)\*(?!\*)", r"<i>\1</i>", text)
    text = re.sub(r"`([^`]+?)`", r"<font name='Courier'>\1</font>", text)
    return text


def parse_table(lines: list[str], st: dict[str, ParagraphStyle]) -> Table:
    rows: list[list[str]] = []
    for line in lines:
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if all(re.fullmatch(r":?-{2,}:?", c) for c in cells):
            continue  # separator row
        rows.append(cells)
    head, body = rows[0], rows[1:]
    data = [[Paragraph(inline(c), st["cellhead"]) for c in head]]
    data += [[Paragraph(inline(c), st["cell"]) for c in row] for row in body]
    width = A4[0] - 36 * mm
    n = len(head)
    table = Table(data, colWidths=[width / n] * n, repeatRows=1)
    table.setStyle(TableStyle([
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#8899aa")),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#e8eef4")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ]))
    return table


def build(md_path: Path, pdf_path: Path) -> int:
    st = styles()
    lines = md_path.read_text(encoding="utf-8").splitlines()
    story: list[object] = []
    i = 0
    while i < len(lines):
        line = lines[i].rstrip()
        if not line.strip():
            i += 1
            continue
        if line.strip() == "<!-- pagebreak -->":
            story.append(PageBreak())
        elif line.startswith("### "):
            story.append(Paragraph(inline(line[4:]), st["h3"]))
        elif line.startswith("## "):
            story.append(Paragraph(inline(line[3:]), st["h2"]))
        elif line.startswith("# "):
            story.append(Paragraph(inline(line[2:]), st["h1"]))
        elif line.startswith("> "):
            block = [line[2:]]
            while i + 1 < len(lines) and lines[i + 1].startswith("> "):
                i += 1
                block.append(lines[i][2:])
            story.append(Paragraph("<br/>".join(inline(b) for b in block), st["quote"]))
        elif line.startswith("- "):
            story.append(Paragraph(inline(line[2:]), st["bullet"], bulletText="•"))
        elif line.startswith("|"):
            block = [line]
            while i + 1 < len(lines) and lines[i + 1].startswith("|"):
                i += 1
                block.append(lines[i].rstrip())
            story.append(Spacer(1, 4))
            story.append(parse_table(block, st))
            story.append(Spacer(1, 8))
        else:
            story.append(Paragraph(inline(line), st["body"]))
        i += 1

    def footer(canvas: object, doc: object) -> None:
        canvas.saveState()
        canvas.setFont("Helvetica", 8)
        canvas.setFillColor(colors.HexColor("#667788"))
        canvas.drawString(18 * mm, 12 * mm, "BlindSight — Project Report")
        canvas.drawRightString(A4[0] - 18 * mm, 12 * mm, f"page {doc.page}")
        canvas.restoreState()

    doc = SimpleDocTemplate(
        str(pdf_path), pagesize=A4, title="BlindSight Project Report",
        author="BlindSight project", leftMargin=18 * mm, rightMargin=18 * mm,
        topMargin=18 * mm, bottomMargin=20 * mm,
    )
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    return doc.page


if __name__ == "__main__":
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else REPO_ROOT / "docs/PROJECT_REPORT.md"
    dst = Path(sys.argv[2]) if len(sys.argv) > 2 else src.with_suffix(".pdf")
    pages = build(src, dst)
    print(f"wrote {dst} ({pages} pages)")
