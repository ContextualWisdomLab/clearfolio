with open('src/main/java/com/clearfolio/viewer/analytics/KpiSnapshotLedger.java', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("                .append(field(record.p95TimeToPreviewMs()))\n                .toString();\n    }", "                .append(field(record.p95TimeToPreviewMs()))\n                .toString();\n    }")
with open('src/main/java/com/clearfolio/viewer/analytics/KpiSnapshotLedger.java', 'w', encoding='utf-8') as f:
    f.write(content)
