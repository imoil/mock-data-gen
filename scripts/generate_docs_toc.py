#!/usr/bin/env python3
"""Generate and validate the static Table of Contents inside docs/index.html."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from html import escape
from html.parser import HTMLParser
from pathlib import Path
import re
import sys
from typing import Callable

TOC_START_MARKER = "<!-- TOC:START -->"
TOC_END_MARKER = "<!-- TOC:END -->"
TOC_COUNT_START_MARKER = "<!-- TOC_COUNT:START -->"
TOC_COUNT_END_MARKER = "<!-- TOC_COUNT:END -->"

SECTION_METADATA: dict[str, str] = {
    "": "개요 및 온보딩",
    "adr": "아키텍처 결정 기록 (ADR)",
    "agents": "에이전트 작업 규칙",
    "diagrams": "아키텍처 다이어그램",
}

DEFAULT_SECTION_ORDER: tuple[str, ...] = ("", "adr", "agents", "diagrams")

_MD_HEADING_RE = re.compile(r"^#\s+(.+?)\s*$")
_MD_ANY_HEADING_RE = re.compile(r"^#{1,6}\s")
_MD_FENCE_RE = re.compile(r"^\s*(```+|~~~+)")
_MD_LINK_RE = re.compile(r"\[([^\]]+)\]\([^)]+\)")
_MD_BOLD_RE = re.compile(r"\*\*([^*]+)\*\*|__([^_]+)__")
_MD_CODE_RE = re.compile(r"`([^`]+)`")
_MD_HR_RE = re.compile(r"^\s*(?:[-*_]\s*){3,}$")
_MD_LIST_OR_QUOTE_RE = re.compile(r"^\s*(?:[-*+]\s|\d+[.)]\s|>)")


@dataclass(frozen=True)
class TocEntry:
    """Single indexed documentation entry."""

    title: str
    summary: str
    view_href: str
    source_href: str
    file_format: str
    section_key: str


@dataclass(frozen=True)
class TocSection:
    """Group of documentation entries sharing a top-level docs/ directory."""

    key: str
    title: str
    entries: tuple[TocEntry, ...]


class _HtmlMetadataParser(HTMLParser):
    """Extract <title>, <p class="subtitle">, and <meta name="description">."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._in_title = False
        self._in_subtitle = False
        self._subtitle_depth = 0
        self._title_parts: list[str] = []
        self._subtitle_parts: list[str] = []
        self.meta_description = ""
        self.subtitle = ""

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attr_map = {k.lower(): (v or "") for k, v in attrs}
        lower_tag = tag.lower()
        if lower_tag == "title" and not self._title_parts:
            self._in_title = True
        elif lower_tag == "meta":
            if attr_map.get("name", "").strip().lower() == "description" and not self.meta_description:
                self.meta_description = _normalize_whitespace(attr_map.get("content", ""))
        elif lower_tag == "p" and not self.subtitle:
            classes = attr_map.get("class", "").split()
            if "subtitle" in classes:
                self._in_subtitle = True
                self._subtitle_depth = 1
        elif self._in_subtitle:
            self._subtitle_depth += 1

    def handle_endtag(self, tag: str) -> None:
        lower_tag = tag.lower()
        if lower_tag == "title" and self._in_title:
            self._in_title = False
        elif self._in_subtitle:
            self._subtitle_depth -= 1
            if lower_tag == "p" and self._subtitle_depth <= 0:
                self._in_subtitle = False
                self.subtitle = _normalize_whitespace("".join(self._subtitle_parts))

    def handle_data(self, data: str) -> None:
        if self._in_title:
            self._title_parts.append(data)
        if self._in_subtitle:
            self._subtitle_parts.append(data)

    @property
    def title(self) -> str:
        return _normalize_whitespace("".join(self._title_parts))

    @property
    def summary(self) -> str:
        return self.subtitle or self.meta_description


def _normalize_whitespace(text: str) -> str:
    return " ".join(text.split())


def _strip_inline_markdown(text: str) -> str:
    cleaned = _MD_LINK_RE.sub(r"\1", text)
    cleaned = _MD_CODE_RE.sub(r"\1", cleaned)
    cleaned = _MD_BOLD_RE.sub(lambda m: m.group(1) or m.group(2) or "", cleaned)
    return _normalize_whitespace(cleaned)


def _skip_yaml_frontmatter(lines: list[str]) -> int:
    if not lines or lines[0].strip() != "---":
        return 0
    for idx in range(1, len(lines)):
        if lines[idx].strip() in ("---", "..."):
            return idx + 1
    return 0


def parse_markdown(path: Path) -> tuple[str, str]:
    """Extract (title, summary) from a Markdown file."""
    content = path.read_text(encoding="utf-8")
    lines = content.splitlines()
    start_idx = _skip_yaml_frontmatter(lines)

    title = ""
    summary = ""
    in_fence = False
    fence_marker = ""

    for raw_line in lines[start_idx:]:
        stripped = raw_line.strip()

        fence_match = _MD_FENCE_RE.match(raw_line)
        if fence_match:
            marker = fence_match.group(1)
            if not in_fence:
                in_fence = True
                fence_marker = marker[0]
            elif marker.startswith(fence_marker):
                in_fence = False
                fence_marker = ""
            continue

        if in_fence or not stripped:
            continue

        if not title:
            heading_match = _MD_HEADING_RE.match(stripped)
            if heading_match:
                title = _strip_inline_markdown(heading_match.group(1))
                continue

        if (
            _MD_ANY_HEADING_RE.match(stripped)
            or stripped.startswith("Status:")
            or stripped.startswith("|")
            or stripped.startswith("<!--")
            or _MD_HR_RE.match(stripped)
            or _MD_LIST_OR_QUOTE_RE.match(raw_line)
        ):
            continue

        summary = _strip_inline_markdown(stripped)
        if summary:
            break

    return title or path.stem, summary


def parse_html(path: Path) -> tuple[str, str]:
    """Extract (title, summary) from an HTML file."""
    content = path.read_text(encoding="utf-8")
    parser = _HtmlMetadataParser()
    parser.feed(content)
    parser.close()
    return parser.title or path.stem, parser.summary


SUFFIX_PARSERS: dict[str, Callable[[Path], tuple[str, str]]] = {
    ".md": parse_markdown,
    ".html": parse_html,
}


def resolve_section_title(section_key: str) -> str:
    """Resolve human-readable section title with uniform directory-name fallback."""
    return SECTION_METADATA.get(section_key, section_key)


def _is_hidden_path(relative_path: Path) -> bool:
    return any(part.startswith(".") for part in relative_path.parts)


def _section_sort_key(section_key: str) -> tuple[int, str]:
    if section_key in DEFAULT_SECTION_ORDER:
        return (DEFAULT_SECTION_ORDER.index(section_key), section_key)
    return (len(DEFAULT_SECTION_ORDER), section_key)


def collect_sections(docs_dir: Path, index_path: Path) -> list[TocSection]:
    """Scan docs_dir deterministically and group entries into TocSection objects."""
    if not docs_dir.exists():
        raise FileNotFoundError(f"Docs directory not found: {docs_dir}")

    resolved_docs = docs_dir.resolve()
    resolved_index = index_path.resolve()

    candidates: list[tuple[Path, Path]] = []
    for path in docs_dir.rglob("*"):
        if not path.is_file():
            continue
        if path.resolve() == resolved_index:
            continue
        rel = path.relative_to(docs_dir)
        if rel.as_posix() == "index.html" and resolved_index.parent == resolved_docs:
            continue
        if _is_hidden_path(rel):
            continue
        if path.suffix.lower() not in SUFFIX_PARSERS:
            continue
        candidates.append((rel, path))

    candidates.sort(key=lambda item: item[0].as_posix())

    grouped: dict[str, list[TocEntry]] = {}
    for rel, path in candidates:
        suffix = path.suffix.lower()
        parser = SUFFIX_PARSERS[suffix]
        title, summary = parser(path)
        source_href = rel.as_posix()
        view_href = rel.with_suffix(".html").as_posix() if suffix == ".md" else source_href
        section_key = rel.parts[0] if len(rel.parts) > 1 else ""
        entry = TocEntry(
            title=title,
            summary=summary,
            view_href=view_href,
            source_href=source_href,
            file_format=suffix.lstrip("."),
            section_key=section_key,
        )
        grouped.setdefault(section_key, []).append(entry)

    sections: list[TocSection] = []
    for key in sorted(grouped, key=_section_sort_key):
        sections.append(
            TocSection(
                key=key,
                title=resolve_section_title(key),
                entries=tuple(grouped[key]),
            )
        )
    return sections


def render_toc_markup(sections: list[TocSection]) -> str:
    """Render all TocSections as semantic HTML cards."""
    if not sections:
        return '<p class="empty-state">등록된 문서가 없습니다.</p>'

    blocks: list[str] = []
    for section in sections:
        safe_key = escape(section.key, quote=True)
        safe_title = escape(section.title)
        count = len(section.entries)

        cards: list[str] = []
        for entry in section.entries:
            safe_entry_title = escape(entry.title)
            safe_view_href = escape(entry.view_href, quote=True)
            safe_source_href = escape(entry.source_href, quote=True)
            safe_format = escape(entry.file_format, quote=True)
            format_label = "MD" if entry.file_format == "md" else "HTML"

            search_blob = escape(
                f"{entry.title} {entry.summary} {entry.source_href} {entry.view_href} {section.title}".lower(),
                quote=True,
            )

            if entry.file_format == "md":
                primary_attrs = (
                    f'href="{safe_view_href}" '
                    f'data-source-href="{safe_source_href}" '
                    f'data-format="{safe_format}"'
                )
                source_link_html = (
                    f'              <a class="entry-source-link" href="{safe_source_href}">원문 (.md)</a>'
                )
            else:
                primary_attrs = (
                    f'href="{safe_view_href}" '
                    f'data-format="{safe_format}"'
                )
                source_link_html = ""

            summary_html = (
                f'          <p class="entry-summary">{escape(entry.summary)}</p>'
                if entry.summary
                else ""
            )

            meta_lines = [
                f'          <div class="entry-meta">',
                f'            <span class="entry-path">{safe_source_href}</span>',
                f'            <span class="entry-actions">',
                f'              <span class="format-badge format-{safe_format}">{format_label}</span>',
            ]
            if source_link_html:
                meta_lines.append(source_link_html)
            meta_lines.extend(
                [
                    "            </span>",
                    "          </div>",
                ]
            )

            card_lines = [
                f'        <article class="entry-card" data-section="{safe_key}" data-search="{search_blob}">',
                f'          <h3 class="entry-title"><a class="entry-link" {primary_attrs}>{safe_entry_title}</a></h3>',
            ]
            if summary_html:
                card_lines.append(summary_html)
            card_lines.extend(meta_lines)
            card_lines.append("        </article>")
            cards.append("\n".join(card_lines))

        section_html = "\n".join(
            [
                f'      <section class="toc-section" data-section="{safe_key}" data-section-title="{escape(section.title, quote=True)}">',
                '        <div class="section-header">',
                f'          <h2 class="section-title">{safe_title}</h2>',
                f'          <span class="section-count" data-section-count="{count}">{count}개 문서</span>',
                "        </div>",
                '        <div class="entry-grid">',
                "\n".join(cards),
                "        </div>",
                "      </section>",
            ]
        )
        blocks.append(section_html)

    return "\n".join(blocks)


def render_count_markup(total_count: int) -> str:
    """Render total document count markup inside TOC_COUNT markers."""
    return f'<span id="total-count" data-doc-count="{total_count}">{total_count}</span>'


def _replace_between_markers(
    document: str, start_marker: str, end_marker: str, replacement: str
) -> str:
    start_idx = document.find(start_marker)
    end_idx = document.find(end_marker)
    if start_idx == -1 or end_idx == -1 or end_idx < start_idx:
        raise ValueError(f"Missing or malformed markers: {start_marker} ... {end_marker}")

    before = document[: start_idx + len(start_marker)]
    after = document[end_idx:]
    return f"{before}{replacement}{after}"


def build_default_index_template() -> str:
    """Return minimal fallback HTML5 template when docs/index.html does not yet exist."""
    return f"""<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>mock-data-gen 문서 허브</title>
</head>
<body>
  <main>
    <p>총 {TOC_COUNT_START_MARKER}<span id="total-count" data-doc-count="0">0</span>{TOC_COUNT_END_MARKER}개 문서</p>
    {TOC_START_MARKER}
    {TOC_END_MARKER}
  </main>
</body>
</html>
"""


def generate_index_content(docs_dir: Path, index_path: Path) -> str:
    """Produce updated index.html content without modifying files on disk."""
    sections = collect_sections(docs_dir, index_path)
    total_count = sum(len(section.entries) for section in sections)

    if index_path.exists():
        template = index_path.read_text(encoding="utf-8")
    else:
        template = build_default_index_template()

    toc_markup = "\n" + render_toc_markup(sections) + "\n      "
    updated = _replace_between_markers(template, TOC_START_MARKER, TOC_END_MARKER, toc_markup)
    updated = _replace_between_markers(
        updated,
        TOC_COUNT_START_MARKER,
        TOC_COUNT_END_MARKER,
        render_count_markup(total_count),
    )
    return updated


def run(docs_dir: Path, index_path: Path, check_only: bool) -> int:
    """Execute generation or --check verification. Returns process exit code."""
    try:
        expected = generate_index_content(docs_dir, index_path)
    except (FileNotFoundError, ValueError) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1

    if check_only:
        if not index_path.exists():
            print(f"Out of date: {index_path} does not exist.", file=sys.stderr)
            return 1
        current = index_path.read_text(encoding="utf-8")
        if current != expected:
            print(
                f"Out of date: {index_path} needs regeneration. Run python scripts/generate_docs_toc.py.",
                file=sys.stderr,
            )
            return 1
        print(f"Up to date: {index_path}")
        return 0

    index_path.parent.mkdir(parents=True, exist_ok=True)
    if index_path.exists():
        current = index_path.read_text(encoding="utf-8")
        if current == expected:
            print(f"Unchanged: {index_path}")
            return 0

    index_path.write_text(expected, encoding="utf-8", newline="\n")
    print(f"Updated: {index_path}")
    return 0


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate or verify the Table of Contents in docs/index.html."
    )
    parser.add_argument(
        "--docs-dir",
        type=Path,
        default=Path("docs"),
        help="Root documentation directory to scan (default: docs)",
    )
    parser.add_argument(
        "--index",
        type=Path,
        default=Path("docs/index.html"),
        help="Target index.html path to update or verify (default: docs/index.html)",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Exit with non-zero status if index.html is missing or out of date.",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    return run(docs_dir=args.docs_dir, index_path=args.index, check_only=args.check)


if __name__ == "__main__":
    raise SystemExit(main())
