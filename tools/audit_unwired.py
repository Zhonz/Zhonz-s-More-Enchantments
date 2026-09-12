import re, sys, os

path = sys.argv[1] if len(sys.argv) > 1 else r"src/main/java/com/zhonz/moreenchantments/event/ModEventHandlers.java"
src = open(path, encoding="utf-8", errors="ignore").read()
lines = src.splitlines()

def_pat = re.compile(r"(?:private|public|protected)\s+static\s+[\w<>\[\],. ]+\s+(\w+)\s*\(")
defs = set()
for l in lines:
    m = def_pat.search(l)
    if m:
        defs.add(m.group(1))

unwired = []
for name in sorted(defs):
    calls = 0
    for l in lines:
        if name + "(" in l:
            if def_pat.search(l):
                continue
            st = l.strip()
            if st.startswith("*") or st.startswith("//") or st.startswith("/*"):
                continue
            calls += 1
    if calls == 0:
        unwired.append(name)

print("方法总数:", len(defs))
print("无调用点:", len(unwired))
for u in unwired:
    print("  ", u)
