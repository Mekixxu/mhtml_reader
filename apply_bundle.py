#!/usr/bin/env python3
"""
apply_bundle.py

用法:
  python3 apply_bundle.py <bundle文件路径> [目标根目录]

示例:
  python3 apply_bundle.py bundle.txt .
  python3 apply_bundle.py bundle.txt ~/projects/mht-reader

bundle.txt 格式约定:
  ### FILE: path/to/file.kt
  <文件内容>
  ### END

  ### DELETE: path/to/file.kt

规则:
1) "### FILE:" 行标记一个文件的开始，路径为相对路径
2) "### END" 行标记文件内容结束
3) FILE/END 之间的内容原样写入文件（保留换行）
4) "### DELETE:" 表示删除文件（仅在不处于文件块时生效）
5) 顶层（不在文件块内）只允许：
   - 空行
   - 以 "#" 开头的注释行
   - FILE/END/DELETE 标记行
   其他顶层文本将给出警告（并计入 skipped）
6) 安全：拒绝绝对路径、盘符路径、以及任何试图越界 root 的路径
"""

from __future__ import annotations

import os
import sys
from typing import List, Optional, Tuple

MARKER_FILE = "### FILE:"
MARKER_END = "### END"
MARKER_DELETE = "### DELETE:"
MARKER_COMMENT = "#"


def resolve_root(target_root: str) -> str:
    root = os.path.expanduser(target_root)
    root = os.path.abspath(root)
    return root


def _is_windows_drive_path(p: str) -> bool:
    drive, _ = os.path.splitdrive(p)
    return bool(drive)


def safe_join(root: str, rel_path: str) -> Optional[str]:
    """
    将相对路径安全拼接到 root 下。
    返回绝对路径；若非法返回 None。
    """
    rel = rel_path.strip()
    if not rel:
        return None

    # 统一分隔符（保守处理；最终仍交给 os.path 规范化）
    rel = rel.replace("\x00", "")  # 防御：去除 NUL

    # 拒绝绝对路径/盘符路径
    if os.path.isabs(rel) or _is_windows_drive_path(rel):
        return None

    abs_path = os.path.abspath(os.path.join(root, rel))

    # 必须严格在 root 目录下（不允许等于 root 本身）
    root_prefix = root + os.sep
    if not abs_path.startswith(root_prefix):
        return None
    if abs_path == root:
        return None

    return abs_path


def write_file(root: str, rel_path: str, lines: List[str]) -> bool:
    abs_path = safe_join(root, rel_path)
    if abs_path is None:
        print(f"  [SECURITY] Skip illegal path: {rel_path!r}")
        return False

    parent = os.path.dirname(abs_path)
    if parent:
        os.makedirs(parent, exist_ok=True)

    with open(abs_path, "w", encoding="utf-8") as f:
        f.writelines(lines)

    print(f"  [WRITE] {rel_path}  ({len(lines)} lines)")
    return True


def delete_file(root: str, rel_path: str) -> Tuple[bool, str]:
    """
    返回 (是否实际删除, 状态字符串)
    状态: deleted / missing / illegal
    """
    abs_path = safe_join(root, rel_path)
    if abs_path is None:
        print(f"  [SECURITY] Skip illegal delete path: {rel_path!r}")
        return (False, "illegal")

    if os.path.isfile(abs_path):
        os.remove(abs_path)
        print(f"  [DELETE] {rel_path}")
        return (True, "deleted")

    print(f"  [WARN] File not found, skip delete: {rel_path}")
    return (False, "missing")


def apply_bundle(bundle_path: str, target_root: str) -> None:
    bundle_path = os.path.expanduser(bundle_path)
    if not os.path.isfile(bundle_path):
        print(f"[ERROR] Bundle file not found: {bundle_path}")
        sys.exit(1)

    root = resolve_root(target_root)
    print(f"[BUNDLE] {bundle_path}")
    print(f"[ROOT]   {root}")
    print("-" * 60)

    written = 0
    deleted = 0
    skipped = 0
    warned = 0

    current_path: Optional[str] = None
    current_lines: List[str] = []
    in_file_block = False

    def _force_flush_unclosed(lineno: int) -> None:
        nonlocal written, skipped, warned, in_file_block, current_path, current_lines
        # 未闭合块：给警告并强制写入（更符合“尽力而为”原则）
        warned += 1
        print(
            f"  [WARN] Line {lineno}: previous FILE block not closed by '{MARKER_END}', "
            f"force write: {current_path}"
        )
        if current_path is not None:
            if write_file(root, current_path, current_lines):
                written += 1
            else:
                skipped += 1
        in_file_block = False
        current_path = None
        current_lines = []

    with open(bundle_path, "r", encoding="utf-8") as f:
        for lineno, raw_line in enumerate(f, start=1):
            # 保留原始换行符，以便原样写入文件
            line = raw_line

            # FILE block start
            if raw_line.startswith(MARKER_FILE):
                if in_file_block:
                    _force_flush_unclosed(lineno)

                rel = raw_line[len(MARKER_FILE) :].strip()
                if not rel:
                    warned += 1
                    skipped += 1
                    print(f"  [WARN] Line {lineno}: empty path after '{MARKER_FILE}', skip")
                    in_file_block = False
                    continue

                current_path = rel
                current_lines = []
                in_file_block = True
                continue

            # FILE block end
            if raw_line.startswith(MARKER_END):
                if not in_file_block:
                    warned += 1
                    print(f"  [WARN] Line {lineno}: unexpected '{MARKER_END}', ignore")
                    continue

                if current_path is not None and write_file(root, current_path, current_lines):
                    written += 1
                else:
                    skipped += 1

                in_file_block = False
                current_path = None
                current_lines = []
                continue

            # DELETE instruction (only valid at top-level)
            if raw_line.startswith(MARKER_DELETE) and not in_file_block:
                rel = raw_line[len(MARKER_DELETE) :].strip()
                if not rel:
                    warned += 1
                    skipped += 1
                    print(f"  [WARN] Line {lineno}: empty path after '{MARKER_DELETE}', skip")
                    continue

                did_delete, status = delete_file(root, rel)
                if status == "deleted":
                    deleted += 1
                elif status == "illegal":
                    skipped += 1
                # missing: not counted as deleted
                continue

            # inside file block: keep content
            if in_file_block:
                current_lines.append(line)
                continue

            # top-level: allow empty lines or comments; otherwise warn
            if raw_line.strip() == "":
                continue
            if raw_line.startswith(MARKER_COMMENT):
                continue

            warned += 1
            skipped += 1
            print(f"  [WARN] Line {lineno}: unexpected top-level content ignored: {raw_line.rstrip()}")

    # EOF: flush if unclosed
    if in_file_block:
        warned += 1
        print(f"  [WARN] EOF: FILE block not closed, force write: {current_path}")
        if current_path is not None and write_file(root, current_path, current_lines):
            written += 1
        else:
            skipped += 1

    print("-" * 60)
    print(f"[DONE] written={written}, deleted={deleted}, skipped={skipped}, warnings={warned}")


def main() -> None:
    if len(sys.argv) < 2:
        print("Usage: python3 apply_bundle.py <bundle_file> [target_root]")
        sys.exit(1)

    bundle_path = sys.argv[1]
    target_root = sys.argv[2] if len(sys.argv) >= 3 else "."
    apply_bundle(bundle_path, target_root)


if __name__ == "__main__":
    main()
