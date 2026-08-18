#!/usr/bin/env python3
"""批量将 k8s/base/*.yaml 的 HTTP readiness 探针改为 TCP 探针（部分服务无 actuator）"""
import os, re

BASE = "/Users/yongzx/IdeaProjects/web3-exchange/k8s/base"
PATTERN = re.compile(
    r'readinessProbe:\n(\s+)httpGet:\n\s+path: /actuator/health\n\s+port: (\d+)\n(\s+)initialDelaySeconds: (\d+)\n(\s+)periodSeconds: (\d+)'
)

def fix(path: str) -> bool:
    s = open(path).read()
    changed = False
    def repl(m):
        nonlocal changed
        changed = True
        indent, port, i2, delay, i3, period = m.groups()
        return (f"readinessProbe:\n{indent}tcpSocket:\n{indent}  port: {port}\n"
                f"{i2}initialDelaySeconds: {delay}\n{i3}periodSeconds: {period}")
    s2 = PATTERN.sub(repl, s)
    if changed:
        open(path, "w").write(s2)
    return changed

for f in sorted(os.listdir(BASE)):
    if f.endswith(".yaml") and f not in ("namespace.yaml", "kustomization.yaml"):
        if fix(os.path.join(BASE, f)):
            print(f"已改 {f}")
print("完成")