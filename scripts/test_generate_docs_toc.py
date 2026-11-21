"""Unit tests for scripts/generate_docs_toc.py."""

from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))

import generate_docs_toc as toc  # noqa: E402


class GenerateDocsTocTests(unittest.TestCase):
    def _write_file(self, path: Path, content: str) -> Path:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")
        return path

    def _create_template(self, index_path: Path) -> None:
        self._write_file(index_path, toc.build_default_index_template())

    def test_discovers_nested_and_unknown_subdirectories_and_ignores_hidden_and_index(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs_dir = Path(tmp) / "docs"
            index_path = docs_dir / "index.html"

            self._write_file(
                docs_dir / "README.md",
                "# 루트 문서\n\n루트 요약 문단입니다.\n",
            )
            self._write_file(
                docs_dir / "adr" / "0001-choice.md",
                "# 0001. 핵심 결정\n\nStatus: accepted\n\n결정 배경 요약입니다.\n",
            )
            self._write_file(
                docs_dir / "guides" / "setup.md",
                "# 로컬 설정 가이드\n\n개발 환경 설정 순서를 설명합니다.\n",
            )
            self._write_file(
                docs_dir / "diagrams" / "overview.html",
                (
                    "<!DOCTYPE html><html><head><title>시스템 개요</title></head>"
                    '<body><p class="subtitle">전체 모듈 다이어그램</p></body></html>'
                ),
            )
            self._write_file(docs_dir / ".hidden.md", "# 숨김 파일\n\n보이면 안 됩니다.\n")
            self._write_file(docs_dir / ".private" / "secret.md", "# 비밀 문서\n\n무시 대상.\n")
            self._write_file(docs_dir / "notes.txt", "plain text ignored")
            self._create_template(index_path)

            sections = toc.collect_sections(docs_dir, index_path)
            section_map = {section.key: section for section in sections}

            self.assertEqual(set(section_map.keys()), {"", "adr", "diagrams", "guides"})
            self.assertEqual(section_map[""].title, "개요 및 온보딩")
            self.assertEqual(section_map["adr"].title, "아키텍처 결정 기록 (ADR)")
            self.assertEqual(section_map["diagrams"].title, "아키텍처 다이어그램")
            self.assertEqual(section_map["guides"].title, "guides")

            guide_entry = section_map["guides"].entries[0]
            self.assertEqual(guide_entry.title, "로컬 설정 가이드")
            self.assertEqual(guide_entry.summary, "개발 환경 설정 순서를 설명합니다.")
            self.assertEqual(guide_entry.view_href, "guides/setup.html")
            self.assertEqual(guide_entry.source_href, "guides/setup.md")

            diagram_entry = section_map["diagrams"].entries[0]
            self.assertEqual(diagram_entry.title, "시스템 개요")
            self.assertEqual(diagram_entry.summary, "전체 모듈 다이어그램")
            self.assertEqual(diagram_entry.view_href, "diagrams/overview.html")
            self.assertEqual(diagram_entry.source_href, "diagrams/overview.html")

    def test_extracts_titles_summaries_fallbacks_and_escapes_html(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs_dir = Path(tmp) / "docs"
            index_path = docs_dir / "index.html"

            self._write_file(
                docs_dir / "no-heading.md",
                (
                    "```bash\n"
                    "# ignored heading inside code fence\n"
                    "```\n\n"
                    "Status: draft\n\n"
                    "| col1 | col2 |\n"
                    "| --- | --- |\n\n"
                    '첫 번째 <script>alert("xss")</script> & 요약입니다.\n'
                ),
            )
            self._write_file(
                docs_dir / "meta-only.html",
                (
                    "<!DOCTYPE html><html><head>"
                    '<meta name="description" content="메타 설명 &amp; 특수문자">'
                    "</head><body><h1>본문 제목</h1></body></html>"
                ),
            )
            self._write_file(
                docs_dir / "escaped.md",
                "# A & B <Tag>\n\n요약에 \"인용\"과 <꺽쇠>가 포함됩니다.\n",
            )
            self._create_template(index_path)

            exit_code = toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path)])
            self.assertEqual(exit_code, 0)

            rendered = index_path.read_text(encoding="utf-8")
            self.assertIn(">no-heading</a>", rendered)
            self.assertIn(">meta-only</a>", rendered)
            self.assertIn("메타 설명 &amp; 특수문자", rendered)
            self.assertIn("A &amp; B &lt;Tag&gt;", rendered)
            self.assertIn("요약에 &quot;인용&quot;과 &lt;꺽쇠&gt;가 포함됩니다.", rendered)
            self.assertNotIn('<script>alert("xss")</script>', rendered)
            self.assertIn("&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;", rendered)
            self.assertIn('href="escaped.html" data-source-href="escaped.md"', rendered)
            self.assertIn('<a class="entry-source-link" href="escaped.md">원문 (.md)</a>', rendered)
            self.assertIn('data-doc-count="3">3</span>', rendered)

    def test_generation_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs_dir = Path(tmp) / "docs"
            index_path = docs_dir / "index.html"

            self._write_file(docs_dir / "intro.md", "# 소개\n\n첫 문단입니다.\n")
            self._write_file(
                docs_dir / "diagrams" / "flow.html",
                '<html><head><title>흐름도</title></head><body><p class="subtitle">설명</p></body></html>',
            )
            self._create_template(index_path)

            self.assertEqual(toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path)]), 0)
            first_bytes = index_path.read_bytes()

            self.assertEqual(toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path)]), 0)
            second_bytes = index_path.read_bytes()

            self.assertEqual(first_bytes, second_bytes)

    def test_check_flag_detects_missing_and_stale_index(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs_dir = Path(tmp) / "docs"
            index_path = docs_dir / "index.html"

            self._write_file(docs_dir / "guide.md", "# 가이드\n\n초기 요약입니다.\n")

            # Missing index.html -> --check fails
            self.assertNotEqual(
                toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path), "--check"]),
                0,
            )

            # Generate -> --check passes
            self.assertEqual(toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path)]), 0)
            self.assertEqual(
                toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path), "--check"]),
                0,
            )

            # Add a new document without regenerating -> --check fails
            self._write_file(docs_dir / "guides" / "new.md", "# 새 문서\n\n추가된 내용.\n")
            self.assertNotEqual(
                toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path), "--check"]),
                0,
            )

            # Regenerate -> --check passes again
            self.assertEqual(toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path)]), 0)
            self.assertEqual(
                toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path), "--check"]),
                0,
            )

            # Modify an existing document summary -> --check fails
            self._write_file(docs_dir / "guide.md", "# 가이드\n\n변경된 요약입니다.\n")
            self.assertNotEqual(
                toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path), "--check"]),
                0,
            )

            # Regenerate -> --check passes
            self.assertEqual(toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path)]), 0)
            self.assertEqual(
                toc.main(["--docs-dir", str(docs_dir), "--index", str(index_path), "--check"]),
                0,
            )


if __name__ == "__main__":
    unittest.main()
