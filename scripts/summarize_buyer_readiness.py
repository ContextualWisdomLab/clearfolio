#!/usr/bin/env python3
"""Generate a buyer-readiness scorecard from the data-room manifest."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


STATUSES = ("ready", "partial", "external")


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def count_statuses(items: list[dict]) -> dict[str, int]:
    counts = {status: 0 for status in STATUSES}
    for item in items:
        status = str(item.get("status", "")).strip()
        counts[status] = counts.get(status, 0) + 1
    return counts


def evidence_statuses(gate: dict, artifacts_by_id: dict[str, dict]) -> list[str]:
    statuses: list[str] = []
    for artifact_id in gate.get("evidence", []):
        artifact = artifacts_by_id.get(str(artifact_id), {})
        status = str(artifact.get("status", "missing")).strip() or "missing"
        statuses.append(f"{artifact_id}={status}")
    return statuses


def ready_gate_evidence_violations(gates: list[dict], artifacts_by_id: dict[str, dict]) -> list[dict]:
    violations: list[dict] = []
    for gate in gates:
        if str(gate.get("status", "")).strip() != "ready":
            continue
        gate_id = str(gate.get("id", ""))
        for artifact_id in gate.get("evidence", []):
            artifact_status = str(
                artifacts_by_id.get(str(artifact_id), {}).get("status", "missing")
            ).strip() or "missing"
            if artifact_status != "ready":
                violations.append({
                    "gateId": gate_id,
                    "artifactId": str(artifact_id),
                    "artifactStatus": artifact_status,
                })
    return violations


def buyer_interpretation(status: str) -> str:
    if status == "ready":
        return "Ready for buyer walkthrough."
    if status == "external":
        return "External dependency before release claim."
    return "Discount risk until closed."


def summarize_manifest(manifest: dict) -> dict:
    artifacts = list(manifest.get("artifacts", []))
    gates = list(manifest.get("readinessGates", []))
    artifacts_by_id = {str(artifact.get("id")): artifact for artifact in artifacts}
    gate_status_counts = count_statuses(gates)
    gate_count = len(gates)
    ready_gate_count = gate_status_counts.get("ready", 0)
    readiness_percent = round((ready_gate_count / gate_count) * 100) if gate_count else 0
    ready_evidence_violations = ready_gate_evidence_violations(gates, artifacts_by_id)

    gate_summaries = [
        {
            "id": str(gate.get("id", "")),
            "status": str(gate.get("status", "")),
            "evidenceStatuses": evidence_statuses(gate, artifacts_by_id),
            "buyerInterpretation": buyer_interpretation(str(gate.get("status", ""))),
        }
        for gate in gates
    ]

    return {
        "packageName": str(manifest.get("packageName", "")),
        "updatedAt": str(manifest.get("updatedAt", "")),
        "artifactCount": len(artifacts),
        "artifactStatusCounts": count_statuses(artifacts),
        "gateCount": gate_count,
        "gateStatusCounts": gate_status_counts,
        "conservativeGateReadinessPercent": readiness_percent,
        "readyGateEvidenceIntegrity": not ready_evidence_violations,
        "readyGateEvidenceViolations": ready_evidence_violations,
        "gateSummaries": gate_summaries,
        "discountRiskGates": [
            {"id": gate["id"], "status": gate["status"]}
            for gate in gate_summaries
            if gate["status"] != "ready"
        ],
    }


def format_counts(counts: dict[str, int]) -> str:
    return ", ".join(f"{status}={counts.get(status, 0)}" for status in STATUSES)


def format_integrity(summary: dict) -> str:
    if summary["readyGateEvidenceIntegrity"]:
        return "Pass: all ready gates cite ready artifacts"
    return f"Fail: {len(summary['readyGateEvidenceViolations'])} violation(s)"


def render_markdown(summary: dict) -> str:
    lines = [
        "# Buyer Readiness Scorecard",
        "",
        f"Package: {summary['packageName']}",
        f"Updated: {summary['updatedAt']}",
        "",
        "This is an engineering readiness scorecard, not a valuation opinion.",
        "Only `ready` gates count toward the conservative readiness percentage.",
        "",
        "## Rollup",
        "",
        "| Metric | Result |",
        "| --- | --- |",
        f"| Artifacts | {summary['artifactCount']} total; {format_counts(summary['artifactStatusCounts'])} |",
        f"| Readiness gates | {summary['gateCount']} total; {format_counts(summary['gateStatusCounts'])} |",
        f"| Conservative gate readiness | {summary['conservativeGateReadinessPercent']} percent |",
        f"| Ready gate evidence integrity | {format_integrity(summary)} |",
        "",
        "## Gate Matrix",
        "",
        "| Gate | Status | Evidence status | Buyer interpretation |",
        "| --- | --- | --- | --- |",
    ]
    for gate in summary["gateSummaries"]:
        evidence = ", ".join(gate["evidenceStatuses"]) or "none"
        lines.append(
            f"| {gate['id']} | {gate['status']} | {evidence} | {gate['buyerInterpretation']} |"
        )

    lines.extend([
        "",
        "## Remaining Discount Risks",
        "",
    ])
    if summary["discountRiskGates"]:
        for gate in summary["discountRiskGates"]:
            lines.append(f"- `{gate['id']}` remains `{gate['status']}`.")
    else:
        lines.append("- None in the current manifest.")

    lines.extend([
        "",
        "## Ready Gate Evidence Integrity",
        "",
    ])
    if summary["readyGateEvidenceViolations"]:
        for violation in summary["readyGateEvidenceViolations"]:
            lines.append(
                f"- `{violation['gateId']}` cites "
                f"`{violation['artifactId']}={violation['artifactStatus']}` "
                "while marked `ready`."
            )
    else:
        lines.append("- All `ready` gates cite only `ready` artifacts.")

    lines.extend([
        "",
        "## Claim Boundary",
        "",
        "- This scorecard measures repository evidence readiness, not enterprise value.",
        "- Seeded demo and local ledger evidence remain local proof until backed by durable production stores.",
        "- Review process and queued GitHub checks are not counted as engineering blockers for continued readiness work.",
        "",
    ])
    return "\n".join(lines)


def write_if_changed(path: Path, content: str) -> bool:
    current = path.read_text(encoding="utf-8") if path.exists() else None
    if current == content:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return True


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--summary", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)

    summary = summarize_manifest(load_json(args.manifest))
    markdown = render_markdown(summary)
    summary_json = json.dumps(summary, indent=2, sort_keys=True) + "\n"

    if args.check:
        errors: list[str] = []
        if not args.output.exists() or args.output.read_text(encoding="utf-8") != markdown:
            errors.append(f"scorecard is out of date: {args.output}")
        if args.summary and (
            not args.summary.exists() or args.summary.read_text(encoding="utf-8") != summary_json
        ):
            errors.append(f"summary is out of date: {args.summary}")
        if errors:
            print("\n".join(errors), file=sys.stderr)
            return 2
        print(f"scorecard is current: {args.output}")
        return 0

    changed = [str(args.output)] if write_if_changed(args.output, markdown) else []
    if args.summary:
        changed.extend([str(args.summary)] if write_if_changed(args.summary, summary_json) else [])
    print(json.dumps({"changed": changed, **summary}, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
