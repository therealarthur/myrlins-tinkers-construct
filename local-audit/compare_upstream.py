"""Offline inventory audit of pinned official and Continuum archives/source.

This compares presence and JSON structure. It does not prove behavioral parity.
Run with Python 3.13+ from any working directory; all paths derive from this file.
"""
from __future__ import annotations

import collections
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

BASE = Path(__file__).resolve().parent
REPO = BASE.parent
PACK = REPO.parent.parent
UPSTREAM = "TConstruct-1.20.1-3.12.1.231"
PIN = "72602856c8403c51f9f488ab1580f1216fa42fda"


def dump(name, value):
    (BASE / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def sha(value):
    return hashlib.sha256(value).hexdigest()


def entries(path, predicate):
    with zipfile.ZipFile(path) as archive:
        return {i.filename: archive.read(i) for i in archive.infolist()
                if not i.is_dir() and predicate(i.filename)}


def source_files():
    tree = subprocess.check_output(["git", "ls-tree", "-r", PIN, "src/main/java"], cwd=REPO, text=True)
    records = [line.split(None, 3) for line in tree.splitlines()]
    blobs = [r[2] for r in records]
    proc = subprocess.run(["git", "cat-file", "--batch"], input=("\n".join(blobs) + "\n").encode(),
                          cwd=REPO, check=True, stdout=subprocess.PIPE)
    data, offset, result = proc.stdout, 0, {}
    for record in records:
        end = data.index(b"\n", offset)
        count = int(data[offset:end].split()[2])
        result[record[3].removeprefix("src/main/java/")] = data[end + 1:end + 1 + count]
        offset = end + count + 2
    return result


# Only path transformations dictated by Minecraft's registry directory renames.
# This is not a claim that the corresponding content has the same meaning.
FOLDERS = {"recipes": "recipe", "loot_tables": "loot_table", "advancements": "advancement",
           "structures": "structure", "functions": "function"}
TAGS = {"blocks": "block", "items": "item", "fluids": "fluid", "entity_types": "entity_type",
        "game_events": "game_event", "functions": "function"}


def canonical_path(path):
    p = path.split("/")
    if len(p) > 2 and p[0] == "data":
        p[2] = FOLDERS.get(p[2], p[2])
        if p[2] == "forge" and len(p) > 3 and p[3] == "biome_modifier":
            p[2] = "neoforge"
        if p[2] == "tags" and len(p) > 3:
            p[3] = TAGS.get(p[3], p[3])
    return "/".join(p)


def domain(path):
    p = path.split("/")
    if p[0] == "data" and len(p) > 3:
        return "/".join(p[:4] if p[2] in {"tinkering", "tags", "worldgen"} else p[:3])
    if p[0] == "assets" and len(p) > 3:
        return "/".join(p[:4] if p[2] in {"book", "mantle", "tconstruct"} else p[:3])
    if p[0] == "slimeknights":
        return "/".join(p[2:4]) if len(p) > 4 else "/".join(p[2:-1])
    return p[0]


def json_data(data):
    try:
        return json.loads(data.decode("utf-8-sig")), True
    except (ValueError, UnicodeDecodeError):
        return None, False


def adapt(value):
    """Conservative comparison aid, with every rewrite recorded in the manifest.

    Forge/NeoForge namespace and legacy result-count spelling are candidates only.
    Old forge tags can have different membership, so adapted equality is not proof.
    """
    if isinstance(value, str):
        return "neoforge:" + value[6:] if value.startswith("forge:") else value
    if isinstance(value, list):
        return [adapt(v) for v in value]
    if isinstance(value, dict):
        d = {("neoforge:conditions" if k == "conditions" else k): adapt(v) for k, v in value.items()}
        return d
    return value


def differences(left, right, path=""):
    if type(left) is not type(right):
        yield {"pointer": path, "old": left, "new": right}
    elif isinstance(left, dict):
        for key in sorted(left.keys() | right.keys()):
            ptr = path + "/" + key.replace("~", "~0").replace("/", "~1")
            if key not in left:
                yield {"pointer": ptr, "added": right[key]}
            elif key not in right:
                yield {"pointer": ptr, "removed": left[key]}
            else:
                yield from differences(left[key], right[key], ptr)
    elif isinstance(left, list):
        # Preserve order: ingredient/trait order and weight can matter.
        for i in range(max(len(left), len(right))):
            ptr = path + "/" + str(i)
            if i >= len(left):
                yield {"pointer": ptr, "added": right[i]}
            elif i >= len(right):
                yield {"pointer": ptr, "removed": left[i]}
            else:
                yield from differences(left[i], right[i], ptr)
    elif left != right:
        yield {"pointer": path, "old": left, "new": right}


def compare_resources():
    original = entries(BASE / "upstream" / (UPSTREAM + ".jar"), lambda p: not p.endswith(".class"))
    port = entries(PACK / "downloads/ContinuumConstruct-26.1.2-3.12.2.jar", lambda p: not p.endswith(".class"))
    normalized = {}
    for path, data in original.items():
        key = canonical_path(path)
        assert key not in normalized, f"Normalization collision: {path}"
        normalized[key] = (path, data)
    port_hashes = collections.defaultdict(list)
    for path, data in port.items():
        port_hashes[sha(data.replace(b"\r\n", b"\n"))].append(path)
    records, counts, groups = [], collections.Counter(), collections.defaultdict(collections.Counter)
    changes = (BASE / "resource-json-differences.jsonl").open("w", encoding="utf-8")
    for key in sorted(normalized.keys() | port.keys()):
        old_path, old = normalized.get(key, (None, None))
        new = port.get(key)
        record = {"path": key, "upstream_path": old_path, "continuum_path": key if new is not None else None,
                  "domain": domain(key), "upstream_sha256": sha(old) if old is not None else None,
                  "continuum_sha256": sha(new) if new is not None else None}
        if old is None:
            status = "continuum_only"
        elif new is None:
            status = "upstream_only"
            record["same_content_other_paths"] = port_hashes.get(sha(old.replace(b"\r\n", b"\n")), [])
        elif old == new:
            status = "byte_equal"
        elif old.replace(b"\r\n", b"\n") == new.replace(b"\r\n", b"\n"):
            status = "newline_equal"
        else:
            a, a_ok = json_data(old)
            b, b_ok = json_data(new)
            if a_ok and b_ok:
                if a == b:
                    status = "json_equal"
                else:
                    status = "adaptation_candidate" if adapt(a) == adapt(b) else "json_changed"
                    diffs = list(differences(a, b))
                    record["json_difference_count"] = len(diffs)
                    changes.write(json.dumps({"path": key, "status": status, "differences": diffs}, ensure_ascii=False) + "\n")
            else:
                status = "nonjson_changed"
        record["status"] = status
        records.append(record)
        counts[status] += 1
        groups[record["domain"]][status] += 1
    changes.close()
    dump("resource-inventory.json", records)
    dump("resource-domains.json", dict(sorted(groups.items())))
    return {"upstream_resources": len(original), "continuum_resources": len(port), "comparison": dict(counts)}


def compare_sources():
    original = entries(BASE / "upstream" / (UPSTREAM + "-sources.jar"), lambda p: p.endswith(".java"))
    port = source_files()
    records, counts, groups = [], collections.Counter(), collections.defaultdict(collections.Counter)
    for key in sorted(original.keys() | port.keys()):
        old, new = original.get(key), port.get(key)
        status = ("continuum_only" if old is None else "upstream_only" if new is None else "byte_equal" if old == new
                  else "newline_equal" if old.replace(b"\r\n", b"\n") == new.replace(b"\r\n", b"\n") else "source_changed")
        record = {"path": key, "domain": domain(key), "status": status,
                  "upstream_sha256": sha(old) if old is not None else None,
                  "continuum_sha256": sha(new) if new is not None else None}
        if status == "upstream_only":
            simple = Path(key).name
            record["same_filename_candidates"] = [p for p in port if Path(p).name == simple] if simple != "package-info.java" else []
        records.append(record)
        counts[status] += 1
        groups[record["domain"]][status] += 1
    dump("source-inventory.json", records)
    dump("source-domains.json", dict(sorted(groups.items())))
    identifiers = []
    for key in sorted(original.keys() | port.keys()):
        # These are literal registration declarations, not a complete runtime
        # registry dump. Loops and generated IDs require separate review.
        if not key.endswith(".java"):
            continue
        extract = lambda data: sorted(set(re.findall(r'\b(?:register|registerDeferred|registerDynamic|id)\(\s*"([^"\n]+)"', data.decode("utf-8")))) if data else []
        old_ids, new_ids = extract(original.get(key)), extract(port.get(key))
        if old_ids or new_ids:
            identifiers.append({"path": key, "upstream_literals": old_ids, "continuum_literals": new_ids,
                                "upstream_only_literals": sorted(set(old_ids) - set(new_ids)),
                                "continuum_only_literals": sorted(set(new_ids) - set(old_ids))})
    dump("registration-literals.json", identifiers)
    return {"upstream_java": len(original), "continuum_java": len(port), "comparison": dict(counts)}


def summarize_tree():
    tree = json.loads((BASE / "upstream-tree.json").read_text(encoding="utf-8-sig"))
    assert not tree["truncated"]
    groups = collections.Counter()
    for item in tree["tree"]:
        if item["type"] == "blob":
            path = item["path"].split("/")
            groups["/".join(path[:3]) if path[0] == "src" else path[0]] += 1
    dump("upstream-tree-domains.json", dict(sorted(groups.items())))
    return {"commit": tree["sha"], "entries": len(tree["tree"]), "blobs": sum(groups.values()), "truncated": False}


def main():
    summary = {"upstream_tag": "v3.12.1.231", "upstream_commit": "a5a0324954f71b7620f766fadea0a0adf2984ae0",
               "continuum_source_commit": PIN, "continuum_resource_artifact": "ContinuumConstruct-26.1.2-3.12.2.jar",
               "path_normalization": {"data_directory_renames": FOLDERS, "tag_directory_renames": TAGS},
               "adaptation_candidate_rules": ["forge: identifier prefix to neoforge:", "conditions key to neoforge:conditions"],
               "limits": ["Source presence and JSON equality do not prove gameplay parity.",
                          "Main-source comparison excludes upstream tests and build files; complete tree has separate inventory.",
                          "Upstream-only paths can represent renames, registry migrations, omitted content, or optional integrations.",
                          "Adaptation candidates require review; forge/neoforge tag membership is not assumed equivalent.",
                          "No build, client, server, or persistence tests were performed by this audit."],
               "resources": compare_resources(), "sources": compare_sources(), "complete_upstream_tree": summarize_tree()}
    dump("comparison-summary.json", summary)
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
