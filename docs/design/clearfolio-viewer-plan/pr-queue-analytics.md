# Clearfolio PR Queue Analytics

Date: 2026-07-02

## Executive Summary

- **The queue is already saturated.** The live query returned 55 open PRs.
  Thirty are `BLOCKED`; 25 are `DIRTY`.
- **The immediate bottleneck is consolidation, not ideation.** UX/Palette and
  Performance/Bolt PRs repeat the same product themes across many branches.
- **New viewer UI code should wait.** The safe next step is to pick the best
  existing UX branch or extract one minimal patch after duplicate PRs are
  reconciled.
- **Security work remains a parallel lane.** Security/Sentinel PRs are mostly
  `DIRTY`, so they need current-base reconciliation before design-driven
  viewer changes should compete for reviewer attention.

## Source Query

The dataset was collected with:

```bash
gh pr list --repo ContextualWisdomLab/clearfolio \
  --state open \
  --limit 200 \
  --json number,title,headRefName,baseRefName,isDraft,mergeStateStatus,reviewDecision,updatedAt,createdAt,author,labels,url
```

The query returned 55 open PRs.

## Queue State

| Merge state | Count |
| --- | ---: |
| `BLOCKED` | 30 |
| `DIRTY` | 25 |

| Review decision | Count |
| --- | ---: |
| `APPROVED` | 3 |
| `CHANGES_REQUESTED` | 30 |
| `REVIEW_REQUIRED` | 22 |

## Theme Segmentation

| Theme | Count | Blocked | Dirty | Changes requested | Review required | Approved |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Performance/Bolt | 20 | 20 | 0 | 14 | 5 | 1 |
| UX/Palette | 19 | 5 | 14 | 13 | 4 | 2 |
| Security/Sentinel | 14 | 3 | 11 | 3 | 11 | 0 |
| Product/Platform | 2 | 2 | 0 | 0 | 2 | 0 |

## Interpretation

### UX/Palette

Nineteen PRs reference viewer UX, accessibility, refresh behavior, loading
states, link treatment, or action grouping. This is enough overlap that a new
implementation branch would probably add review noise. The design path should
select one canonical UX direction, then reduce the branch queue around it.

### Security/Sentinel

Fourteen PRs focus on XSS, null-byte handling, HSTS, or security headers. Most
are `DIRTY`, so they likely need base reconciliation. These PRs may change the
same viewer JS and controller files that a design patch would touch.

### Performance/Bolt

Twenty PRs focus on hash/hex-format or allocation optimizations. All are
`BLOCKED`. This lane is probably lower design risk, but it still consumes merge
capacity and should not be mixed into viewer UX changes.

## Recommendation

Use this order:

1. Pick one canonical viewer UX branch or extract one minimal patch from the
   Palette lane.
2. Rebase or close duplicate UX branches after the canonical decision is made.
3. Keep security fixes isolated from UX polish unless they already touch the
   exact same viewer link or URL-safety path.
4. Only after the queue narrows, implement the smallest code patch needed to
   match the approved Figma direction.

## Caveats

- This analysis uses GitHub PR metadata and titles. It does not replace review
  thread inspection for a merge decision.
- The live queue can change after this snapshot. Re-run the source query before
  opening or merging a follow-up implementation PR.
- Product usage metrics are not available in this repo. Operational KPIs such
  as conversion success rate and time-to-preview need runtime logs or exported
  event data.
