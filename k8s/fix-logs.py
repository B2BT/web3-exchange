#!/usr/bin/env python3
"""批量给 k8s/base/*.yaml 的 Deployment 加 /logs emptyDir 挂载（修复 Logback 日志目录不存在问题）"""
import os, re

BASE = "/Users/yongzx/IdeaProjects/web3-exchange/k8s/base"

MOUNT = """          volumeMounts:
            - name: logs
              mountPath: /logs"""

VOLUME = """      volumes:
        - name: logs
          emptyDir: {}"""

def fix(path: str) -> bool:
    s = open(path).read()
    changed = False
    # 在 resources 前插入 volumeMounts（容器内）
    # 在 template.spec 末尾（最后一个容器配置后）插入 volumes
    if "volumeMounts:" in s:
        return False  # 已处理
    # 加 volumeMount：在 readinessProbe 块结束后插入
    m = re.search(r'(readinessProbe:.*?periodSeconds: \d+\n)', s, re.S)
    if m:
        s = s[:m.end()] + "\n" + MOUNT + "\n" + s[m.end():]
        changed = True
    # 加 volumes：在 deployment 的 spec.template.spec 末尾（Service 的 --- 之前）
    m2 = re.search(r'(\n---\napiVersion: v1\nkind: Service)', s)
    if m2:
        s = s[:m2.start()] + "\n" + VOLUME + "\n" + s[m2.start():]
        changed = True
    if changed:
        open(path, "w").write(s)
    return changed

for f in sorted(os.listdir(BASE)):
    if f.endswith(".yaml") and f not in ("namespace.yaml", "kustomization.yaml"):
        if fix(os.path.join(BASE, f)):
            print(f"已改 {f}")
print("完成")