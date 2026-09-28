"""Compare release resources to immutable Git blob IDs, without copying assets."""
import collections
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

BASE = Path(__file__).resolve().parent
PACK = BASE.parent.parent.parent


def git_blob(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


reports = []
for project, pin, jar in [
    ("continuum", "72602856c8403c51f9f488ab1580f1216fa42fda", "ContinuumConstruct-26.1.2-3.12.2.jar"),
    ("continuum-core", "c0f1ecc5f1194f6bf746d230e6eb38d46c42d43c", "ContinuumCore-26.1.2-1.12.0.jar"),
]:
    tree = subprocess.check_output(["git", "ls-tree", "-r", pin], cwd=PACK / "ports" / project, text=True)
    resources = {}
    for prefix in ["src/main/resources/", "src/generated/resources/", "src/generated/client/"]:
        for line in tree.splitlines():
            mode, typ, blob, path = line.split(None, 3)
            if path.startswith(prefix):
                relative = path.removeprefix(prefix)
                if not relative.startswith((".cache/", "assets/tconstruct/debug/")):
                    resources[relative] = {"path": path, "blob": blob}
    counts = collections.Counter()
    differences = []
    artifact = PACK / "downloads" / jar
    with zipfile.ZipFile(artifact) as archive:
        for relative, source in sorted(resources.items()):
            try:
                data = archive.read(relative)
            except KeyError:
                counts["absent_from_jar"] += 1
                differences.append({"path": relative, "source": source, "status": "absent_from_jar"})
                continue
            if git_blob(data) == source["blob"]:
                counts["byte_equal"] += 1
            elif git_blob(data.replace(b"\r\n", b"\n")) == source["blob"]:
                counts["newline_equal"] += 1
            else:
                counts["different"] += 1
                record = {"path": relative, "source": source, "status": "different", "jar_sha256": hashlib.sha256(data).hexdigest()}
                if relative.endswith((".json", ".toml", ".cfg")):
                    record["jar_text"] = data.decode("utf-8-sig")
                    record["source_text"] = subprocess.check_output(["git", "show", pin + ":" + source["path"]], cwd=PACK / "ports" / project, text=True)
                differences.append(record)
        extras = sorted(i.filename for i in archive.infolist() if not i.is_dir() and not i.filename.endswith(".class") and i.filename not in resources)
        manifest = archive.read("META-INF/MANIFEST.MF").decode()
    reports.append({"project": project, "pin": pin, "artifact": str(artifact), "artifact_sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
                    "source_resource_count": len(resources), "counts": dict(counts), "differences": differences,
                    "jar_only_nonclass": extras, "manifest": manifest,
                    "limit": "Resources compare to immutable Git objects. This is not a bytecode/source reproducibility proof."})
(BASE / "source-release-mapping.json").write_text(json.dumps(reports, indent=2) + "\n", encoding="utf-8")
print(json.dumps([{k: v for k, v in report.items() if k in {"project", "pin", "artifact_sha256", "source_resource_count", "counts", "jar_only_nonclass"}} for report in reports], indent=2))
