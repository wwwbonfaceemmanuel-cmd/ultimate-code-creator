"""Static validation of project files.

Python and JSON get real syntax checks. HTML gets a tag-nesting check. Other
languages get a comment/string-aware bracket-balance check only - nothing is
compiled or executed, so a clean result is not proof that the code runs.
"""
from __future__ import annotations

import ast
import json
import posixpath
from html.parser import HTMLParser

from ..schemas import Issue, ProjectFile

NOTE = (
    "Python and JSON are syntax-checked; HTML is checked for tag nesting and missing local files; "
    "other languages get a bracket-balance check only. Code is not compiled or run."
)

VOID_TAGS = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}
# Tags whose end tag is optional in HTML; never reported as unclosed.
OPTIONAL_END = {"p", "li", "dt", "dd", "tr", "td", "th", "thead", "tbody", "tfoot", "option", "optgroup", "colgroup", "html", "head", "body"}

BRACKET_EXTS = {"js", "mjs", "cjs", "ts", "java", "kt", "kts", "php", "cpp", "cc", "cxx", "c", "h", "hpp", "cs", "dart", "css", "scss"}
HASH_COMMENT_EXTS = {"php"}
BACKTICK_EXTS = {"js", "mjs", "cjs", "ts"}
TRIPLE_QUOTE_EXTS = {"kt", "kts", "dart"}


def _ext(path: str) -> str:
    name = path.rsplit("/", 1)[-1]
    return name.rsplit(".", 1)[-1].lower() if "." in name else ""


def _check_python(f: ProjectFile) -> list[Issue]:
    try:
        ast.parse(f.content, filename=f.path)
    except SyntaxError as e:
        return [Issue(path=f.path, line=e.lineno or 1, column=e.offset or 1, severity="error", message=f"Syntax error: {e.msg}")]
    except ValueError as e:
        return [Issue(path=f.path, severity="error", message=str(e))]
    return []


def _check_json(f: ProjectFile) -> list[Issue]:
    try:
        json.loads(f.content)
    except json.JSONDecodeError as e:
        return [Issue(path=f.path, line=e.lineno, column=e.colno, severity="error", message=f"Invalid JSON: {e.msg}")]
    return []


class _HtmlChecker(HTMLParser):
    def __init__(self, path: str):
        super().__init__(convert_charrefs=True)
        self.path = path
        self.stack: list[tuple[str, int, int]] = []
        self.issues: list[Issue] = []
        self.refs: list[tuple[str, int]] = []

    def handle_starttag(self, tag, attrs):
        line, col = self.getpos()
        attr = dict(attrs)
        ref = None
        if tag == "script":
            ref = attr.get("src")
        elif tag == "link" and "stylesheet" in (attr.get("rel") or "").lower():
            ref = attr.get("href")
        elif tag == "img":
            ref = attr.get("src")
        if ref:
            self.refs.append((ref, line))
        if tag not in VOID_TAGS:
            self.stack.append((tag, line, col + 1))

    def handle_endtag(self, tag):
        line, col = self.getpos()
        if tag in VOID_TAGS:
            return
        names = [t for t, _, _ in self.stack]
        if tag not in names:
            self.issues.append(Issue(path=self.path, line=line, column=col + 1, severity="error", message=f"Closing tag </{tag}> has no matching opening tag"))
            return
        while self.stack:
            open_tag, oline, ocol = self.stack.pop()
            if open_tag == tag:
                break
            if open_tag not in OPTIONAL_END:
                self.issues.append(Issue(path=self.path, line=oline, column=ocol, severity="error", message=f"<{open_tag}> is not closed before </{tag}>"))

    def finish(self):
        for tag, line, col in self.stack:
            if tag not in OPTIONAL_END:
                self.issues.append(Issue(path=self.path, line=line, column=col, severity="error", message=f"<{tag}> is never closed"))


def _check_html(f: ProjectFile, all_paths: set[str]) -> list[Issue]:
    checker = _HtmlChecker(f.path)
    try:
        checker.feed(f.content)
        checker.close()
    except Exception as e:  # html.parser is lenient; this is a safety net
        return [Issue(path=f.path, severity="error", message=f"Could not parse HTML: {e}")]
    checker.finish()
    issues = checker.issues
    base = posixpath.dirname(f.path)
    for ref, line in checker.refs:
        if ref.startswith(("http:", "https:", "//", "data:", "#", "mailto:", "blob:")):
            continue
        target = posixpath.normpath(posixpath.join(base, ref.split("?")[0].split("#")[0])).lstrip("/")
        if target not in all_paths:
            issues.append(Issue(path=f.path, line=line, severity="warning", message=f"References '{ref}', which is not in the project"))
    return issues


PAIRS = {")": "(", "]": "[", "}": "{"}


def _check_brackets(f: ProjectFile, ext: str) -> list[Issue]:
    src = f.content
    stack: list[tuple[str, int, int]] = []
    issues: list[Issue] = []
    i, n, line, col = 0, len(src), 1, 1

    def advance(count: int = 1):
        nonlocal i, line, col
        for _ in range(count):
            if i < n and src[i] == "\n":
                line, col = line + 1, 1
            else:
                col += 1
            i += 1

    while i < n:
        c = src[i]
        two = src[i : i + 2]
        if two == "//" and ext != "css" or (c == "#" and ext in HASH_COMMENT_EXTS):
            while i < n and src[i] != "\n":
                advance()
            continue
        if two == "/*":
            start = (line, col)
            end = src.find("*/", i + 2)
            if end == -1:
                issues.append(Issue(path=f.path, line=start[0], column=start[1], severity="warning", message="Block comment is never closed"))
                break
            advance(end + 2 - i)
            continue
        if ext in TRIPLE_QUOTE_EXTS and src[i : i + 3] in ('"""', "'''"):
            q = src[i : i + 3]
            end = src.find(q, i + 3)
            advance((end + 3 - i) if end != -1 else (n - i))
            continue
        if c in "\"'" or (c == "`" and ext in BACKTICK_EXTS):
            quote, start = c, (line, col)
            advance()
            closed = False
            while i < n:
                if src[i] == "\\":
                    advance(2)
                    continue
                if src[i] == quote:
                    advance()
                    closed = True
                    break
                if src[i] == "\n" and quote != "`":
                    break
                advance()
            if not closed and quote == "`":
                issues.append(Issue(path=f.path, line=start[0], column=start[1], severity="warning", message="Template string may be unterminated"))
            continue
        if c in "([{":
            stack.append((c, line, col))
        elif c in PAIRS:
            if not stack:
                issues.append(Issue(path=f.path, line=line, column=col, severity="warning", message=f"Unexpected '{c}' with no matching opener"))
            elif stack[-1][0] != PAIRS[c]:
                o, oline, _ = stack.pop()
                issues.append(Issue(path=f.path, line=line, column=col, severity="warning", message=f"'{c}' does not match '{o}' opened on line {oline}"))
            else:
                stack.pop()
        advance()
        if len(issues) >= 5:
            return issues
    for o, oline, ocol in stack[-5:]:
        issues.append(Issue(path=f.path, line=oline, column=ocol, severity="warning", message=f"'{o}' is never closed"))
    return issues


def validate_files(files: list[ProjectFile]) -> list[Issue]:
    all_paths = {f.path for f in files}
    issues: list[Issue] = []
    for f in files:
        ext = _ext(f.path)
        if not f.content.strip():
            issues.append(Issue(path=f.path, severity="warning", message="File is empty"))
            continue
        if ext == "py":
            issues += _check_python(f)
        elif ext == "json":
            issues += _check_json(f)
        elif ext in ("html", "htm"):
            issues += _check_html(f, all_paths)
        elif ext in BRACKET_EXTS:
            issues += _check_brackets(f, ext)
    return issues
