import sys
with open('src/main/resources/static/assets/viewer/demo.js', 'r') as f:
    content = f.read()

search = """  const fragment = document.createDocumentFragment();

  for (const job of history) {"""
replace = """  // ⚡ Bolt: Use DocumentFragment to batch DOM insertions and prevent multiple reflows/repaints.
  // Expected impact: Significant reduction in rendering time and layout thrashing.
  const fragment = document.createDocumentFragment();

  for (const job of history) {"""
content = content.replace(search, replace)

with open('src/main/resources/static/assets/viewer/demo.js', 'w') as f:
    f.write(content)
