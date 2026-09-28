"""Inventory port-added implementation warning signs without calling them bugs."""
import collections
import json
from pathlib import Path
import re
import sys

sys.dont_write_bytecode = True
from compare_upstream import BASE, UPSTREAM, dump, entries, source_files

original = entries(BASE / "upstream" / (UPSTREAM + "-sources.jar"), lambda p: p.endswith(".java"))
port = source_files()
patterns = {
    "port_or_disabled_marker": re.compile(r"\b(?:TODO|FIXME|stub|unsupported|unimplemented|disabled|disable|temporarily|temporary|porting|ported)\b|not (?:yet )?(?:implemented|supported)|26\.1", re.I),
    "commented_gameplay_call": re.compile(r"^\s*//.*(?:\.set\w+\(|\.send\w*\(|\.add\w*\(|\.play\w*\(|\.remove\w*\(|return\b)"),
    "empty_or_constant_return": re.compile(r"\breturn\s+(?:null|false|0|ItemStack\.EMPTY|List\.of\(\)|Collections\.empty\w*\(\))\s*;"),
    "unconditional_disabled_branch": re.compile(r"\bif\s*\(\s*(?:false|true)\s*\)"),
}
records = []
for path, data in sorted(port.items()):
    lines = data.decode("utf-8").splitlines()
    old_lines = set(line.strip() for line in original.get(path, b"").decode("utf-8").splitlines())
    for index, line in enumerate(lines):
        if line.strip() in old_lines:
            continue
        kinds = [name for name, pattern in patterns.items() if pattern.search(line)]
        if kinds:
            records.append({"path": path, "line": index + 1, "signals": kinds, "text": line.strip(),
                            "context": "\n".join(f"{i+1}: {lines[i]}" for i in range(max(0,index-3), min(len(lines),index+5))),
                            "status": "unreviewed_candidate_not_a_defect"})
dump("implementation-risk-candidates.json", records)
counts = collections.Counter(signal for row in records for signal in row["signals"])
dump("implementation-risk-summary.json", {"candidates": len(records), "signal_counts": dict(counts),
    "method": "Scan every pinned main Java file for lines absent verbatim (trimmed) from the same upstream file; retain contexts for warning markers, commented calls, constant returns and disabled branches.",
    "limits": ["Heuristic candidates are not defects. New guard returns and optional hooks are often correct.",
               "Does not prove absence of stubs, nor review the semantics of all 1527 changed source files.",
               "Line comparison can flag moved equivalent code and miss semantically changed surrounding code.",
               "All unverified changed implementations remain in review-queue.json."]})
print(json.dumps({"candidates": len(records), "signal_counts": dict(counts)}, indent=2))
