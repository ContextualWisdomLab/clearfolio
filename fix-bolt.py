import sys

with open(".jules/bolt.md", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.startswith("## 2026-07-13 - String replacement"):
        break
    new_lines.append(line)

with open(".jules/bolt.md", "w") as f:
    f.writelines(new_lines)
