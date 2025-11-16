import argparse
import pathlib
import re
import sys
from typing import Set

INCLUDE_RE = re.compile(r"^\s*#include\s+\"([^\"]+)\"\s*$")


def expand(path: pathlib.Path, root: pathlib.Path, stack: Set[pathlib.Path]) -> str:
    if path in stack:
        raise RuntimeError(f"Include cycle detected: {' -> '.join(str(p) for p in stack)} -> {path}")
    stack.add(path)
    try:
        lines = []
        text = path.read_text(encoding="utf-8")
        for line in text.splitlines(keepends=True):
            match = INCLUDE_RE.match(line)
            if match:
                include_target = match.group(1)
                if include_target.startswith('/'):
                    include_path = (root / include_target[1:]).resolve()
                else:
                    include_path = (path.parent / include_target).resolve()
                lines.append(f"// BEGIN INCLUDE {include_target}\n")
                lines.append(expand(include_path, root, stack))
                lines.append(f"// END INCLUDE {include_target}\n")
            else:
                lines.append(line)
        return ''.join(lines)
    finally:
        stack.remove(path)


def main() -> None:
    parser = argparse.ArgumentParser(description="Dump a shader file with includes expanded.")
    parser.add_argument('--root', required=True, help='Root directory of the shader pack (the folder that contains shaders).')
    parser.add_argument('shader', help='Shader file to expand, relative to the root directory.')
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve()
    shader_path = (root / args.shader).resolve()
    if not shader_path.is_file():
        print(f"Shader file not found: {shader_path}", file=sys.stderr)
        sys.exit(1)

    expanded = expand(shader_path, root, set())
    sys.stdout.write(expanded)


if __name__ == '__main__':
    main()
