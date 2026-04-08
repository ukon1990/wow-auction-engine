# Production Memory Footprint and Cost Action Criteria

This document captures a point-in-time production memory baseline and a repeatable check process.

- Baseline date: **2026-04-08 (UTC)**
- Scope: `eu-west-1`, `us-west-1`, `ap-northeast-2`
- Source: CloudWatch logs + SSM `docker stats` probes

For deployment and incident context, see [infra/README.md](../infra/README.md). For project overview, see [README.md](../README.md).

## Baseline Snapshot (2026-04-08)

| Region | AWS region | Instance type | Instance RAM | Live container memory | JVM used avg / p95 / max (MB) | JVM total max (MB) | JVM configured max (MB) | GC p95 / max pause | Notable signals |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| EU | `eu-west-1` | `t4g.small` | 2048 MiB | `721.4 MiB / 1.803 GiB` (39.06%) | `171 / 258 / 312` | `354` | `1202` | `24.1 ms / 40.0 ms` | Stable, frequent humongous-allocation cycles but short pauses |
| US | `us-west-1` | `t4g.small` | 2048 MiB | `705.8 MiB / 1.803 GiB` (38.22%) | `152 / 234 / 292` | `379` | `1202` | `19.6 ms / 31.2 ms` | Stable, similar pattern to EU |
| APAC | `ap-northeast-2` | `t4g.micro` | 1024 MiB | `466.7 MiB / 916.9 MiB` (50.90%) | `112 / 139 / 140` | `185` | `576` | `28.1 ms / 255.5 ms` | Tighter headroom; occasional Full GC observed |

## Decision Rubric (Conservative Default)

Treat memory/cost tuning as **action required** when either condition is true:

1. Sustained container memory is **> 70%** across **3+ daily windows**.
2. GC max pause is **> 250 ms** repeatedly (not a single isolated event).

Suggested daily windows:

- morning peak
- mid-day peak
- evening peak

## What This Means Right Now

- EU and US look overprovisioned for heap pressure and currently stable.
- APAC has tighter relative headroom on `t4g.micro`; keep it under closer watch before any further downsizing.
- Current footprint does not indicate immediate emergency action.

## Likely Causes and Non-Causes

- Auction payload processing is already stream-oriented in the code path (download to temp file + streamed JSON parsing), not full-file materialization.
- The larger memory driver is likely aggregation and runtime footprint (heap working set + metaspace + direct/native memory), not payload file loading itself.

## Future Check Runbook

Run these from your local machine with AWS CLI credentials that can read CloudWatch and SSM.

### 1. Identify instance IDs

```bash
aws cloudformation describe-stacks \
  --region eu-west-1 \
  --stack-name wah-wow-auction-engine-prod-eu-west-1 \
  --query "Stacks[0].Outputs[?OutputKey=='AppInstanceId'].OutputValue" \
  --output text

aws cloudformation describe-stacks \
  --region us-west-1 \
  --stack-name wah-wow-auction-engine-prod-us-west-1 \
  --query "Stacks[0].Outputs[?OutputKey=='AppInstanceId'].OutputValue" \
  --output text

aws cloudformation describe-stacks \
  --region ap-northeast-2 \
  --stack-name wah-wow-auction-engine-prod-ap-northeast-2 \
  --query "Stacks[0].Outputs[?OutputKey=='AppInstanceId'].OutputValue" \
  --output text
```

### 2. Pull recent application logs (GC + JVM memory markers)

```bash
aws logs tail /aws/ec2/wow-auction-engine/prod/eu-west-1 --since 30m
aws logs tail /aws/ec2/wow-auction-engine/prod/us-west-1 --since 30m
aws logs tail /aws/ec2/wow-auction-engine/prod/ap-northeast-2 --since 30m
```

### 3. Run live container memory probe through SSM

Replace `<INSTANCE_ID>` and `<REGION>` for each target region.

```bash
aws ssm send-command \
  --region <REGION> \
  --instance-ids <INSTANCE_ID> \
  --document-name AWS-RunShellScript \
  --comment "Targeted memory probe" \
  --parameters commands='["set -euo pipefail","CID=$(docker ps -q --filter name=^/wow-auction-engine$ | head -n1)","echo CID=$CID","docker stats --no-stream wow-auction-engine"]' \
  --query 'Command.CommandId' \
  --output text
```

Then fetch output:

```bash
aws ssm list-command-invocations \
  --region <REGION> \
  --command-id <COMMAND_ID> \
  --details
```

## Interpretation Checklist

- `docker stats` memory `%`:
  - under `70%`: usually acceptable for this service profile
  - over `70%` across repeated windows: start right-sizing/capacity action
- GC pauses:
  - mostly sub-100ms: normal for current workload
  - repeated spikes over `250ms`: action required
- Region-specific context:
  - compare APAC separately from EU/US because APAC runs on a smaller instance class
