#!/usr/bin/env python3
"""Creates a jar from a staged directory using only the Python standard library.

Only needed on machines that have a JRE but no JDK (no `jar` tool) — for example the offline
verification environment. CI and normal developer machines use the JDK's own `jar`.
"""

import os
import sys
import zipfile


def main():
    target, stage, manifest = sys.argv[1], sys.argv[2], sys.argv[3]
    with open(manifest, "rb") as handle:
        manifest_bytes = handle.read()
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/MANIFEST.MF", manifest_bytes)
        for root, _dirs, files in os.walk(stage):
            for name in files:
                path = os.path.join(root, name)
                relative = os.path.relpath(path, stage)
                if relative.replace("\\", "/") == "META-INF/MANIFEST.MF":
                    continue
                archive.write(path, relative)
    print("wrote", target, os.path.getsize(target), "bytes")


if __name__ == "__main__":
    main()
