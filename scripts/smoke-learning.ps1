python - <<'EOF'
import json, io, glob, os
best = None
for path in glob.glob(r"C:/Users/one/.zcode/cli/rollout/model-io-sess_*.jsonl"):
    with io.open(path, encoding='utf-8', errors='ignore') as fh:
        for line in fh:
            if 'smoke-learning.ps1' not in line:
                continue
            try:
                obj = json.loads(line)
            except Exception:
                continue
            def walk(o):
                if isinstance(o, dict):
                    for v in o.values():
                        r = walk(v)
                        if r: return r
                elif isinstance(o, list):
                    for v in o:
                        r = walk(v)
                        if r: return r
                elif isinstance(o, str) and "=== 9." in o and "sandbox" in o and "Assert" in o:
                    return o
                return None
            c = walk(obj)
            if c and (best is None or len(c) > len(best[0])):
                best = (len(c), path, c)
if best:
    size, path, content = best
    os.makedirs(r"E:\web\web\源程序（包含数据库）\scripts", exist_ok=True)
    io.open(r"E:\web\web\源程序（包含数据库）\scripts\smoke-learning.ps1", 'w', encoding='utf-8-sig', newline='').write(content)
    print("recovered", size, "chars from", path.split("sess_")[-1][:12])
else:
    print("no latest version found in rollouts")
EOF