# AWS Deployment

This repository includes a minimal single-instance regional deployment path for the WoW Auction Engine.

It is intended for:

- one EC2 instance per region
- Docker on EC2
- GitHub Actions deployment from `master`
- AWS Systems Manager for in-place restarts
- SSM Parameter Store for runtime configuration

It is not a Kubernetes or EKS deployment.

## Layout

- `infra/regions.json`: enabled regions and per-region overrides
- `infra/cloudformation/app-region.yaml`: reusable per-region CloudFormation stack
- `scripts/deploy/sync-ssm-parameters.sh`: writes runtime configuration to SSM Parameter Store
- `scripts/deploy/restart-container.sh`: restarts the app container on the EC2 instance through SSM
- `.github/workflows/backend-ci.yml`: verifies the backend and uploads the deployable `.war`
- `.github/workflows/deploy-production.yml`: orchestrates production deployment from `master`
- `.github/workflows/reusable-build-image.yml`: builds the runtime image from the `.war` artifact
- `.github/workflows/reusable-deploy-region.yml`: deploys one region at a time

## What Happens On Push To `master`

1. `Backend PR Checks` always starts with a lightweight change-classification job.
2. If the commit changed backend-relevant files, the backend verify job runs Maven `verify` and uploads:
   - coverage artifacts
   - the deployable `.war` artifact
3. If the commit only changed clearly irrelevant files, the expensive backend job is skipped and the workflow still completes successfully.
4. If `Backend PR Checks` finishes successfully on `master`, `Deploy Production` starts with the same conservative change classifier.
5. The deploy workflow reads enabled regions from `infra/regions.json` only when deployment-relevant files changed.
6. For each enabled region, sequentially:
   - app-only changes:
     - push the Docker image to the region's ECR repository
     - restart the EC2-hosted container through SSM
     - verify the `/health` endpoint
   - infra-affecting changes:
     - sync runtime env vars into SSM Parameter Store
     - run `aws cloudformation deploy`
     - push the Docker image to the region's ECR repository
     - restart the EC2-hosted container through SSM
     - verify the `/health` endpoint

The expensive jobs therefore show as `skipped` when a change is clearly irrelevant, and CloudFormation only runs when the stack or deploy contract changed.

## Change Classification

The shared classifier lives in `scripts/deploy/classify_changes.py`.

It conservatively enables work when files could affect:

- backend verification:
  - `src/**`
  - `pom.xml`
  - `mvnw`
  - `.mvn/**`
  - `Dockerfile`
  - backend CI workflow inputs such as `.github/actions/**`
- app rollout:
  - runtime/build-affecting code and resources
  - `Dockerfile`
  - deploy workflows
  - `.github/actions/**`
  - `scripts/deploy/**`
  - `infra/regions.json`
- CloudFormation:
  - `infra/cloudformation/**`
  - `infra/regions.json`
  - deploy workflows and shared deploy actions
  - deploy scripts that can affect stack/bootstrap assumptions

When in doubt, the classifier runs the relevant work instead of skipping it.

## Manual Infra Sync

The repository also includes `.github/workflows/manual-infra-sync.yml`.

Use it when you want to force CloudFormation without relying on changed-file detection. It supports:

- a target `ref`
- one region or `all`
- an option to skip the app rollout and perform infra-only sync

## GitHub Setup

### Required GitHub Secrets

Repository or environment secrets:

- `AWS_DEPLOY_ROLE_ARN`
- `PROD_BLIZZARD_CLIENT_ID`
- `PROD_BLIZZARD_CLIENT_SECRET`
- `PROD_AUCTION_ENGINE_DB_URL`
- `PROD_AUCTION_ENGINE_DB_USERNAME`
- `PROD_AUCTION_ENGINE_DB_PASSWORD`

Optional secrets:

- `PROD_AWS_ACCESS_KEY`
- `PROD_AWS_SECRET_KEY`
- `PROD_WAE_DYNAMODB_ENDPOINT`
- `PROD_WAE_S3_ENDPOINT`
- `PROD_JAVA_TOOL_OPTIONS`

Notes:

- `AWS_DEPLOY_ROLE_ARN` is the IAM role assumed by GitHub Actions through OIDC
- `PROD_AWS_ACCESS_KEY` and `PROD_AWS_SECRET_KEY` are optional; the running application can use the EC2 instance role instead
- the deploy job uses the GitHub Environment `production`
- the deploy role is separate from the EC2 instance role created by CloudFormation

### Recommended GitHub Environment

Create a GitHub Environment named `production` if you want:

- environment-scoped secrets
- manual approvals
- deployment visibility in GitHub UI

## AWS Setup

### Manual AWS Setup Checklist

1. Create the GitHub OIDC provider in IAM.
2. Create the GitHub deploy role.
3. Add the trust policy shown below to that role.
4. Add the permissions policy shown below to that role.
5. Copy the role ARN into the GitHub secret `AWS_DEPLOY_ROLE_ARN`.
6. Make sure the target regions are enabled in your AWS account.
7. Make sure the account has a default VPC and subnet in each target region, or extend the workflow/template to pass explicit `VpcId` and `SubnetId`.
8. Add the required GitHub repository/environment secrets.
9. Make sure the external MariaDB instance accepts traffic from the deployed EC2 instances.
10. Make sure the DynamoDB tables and S3 buckets expected by the app already exist.

### 1. Create the GitHub OIDC Provider

In AWS IAM, create an OpenID Connect provider if one does not already exist:

- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

### 2. Create the Deploy Role Trust Policy

Create an IAM role for GitHub Actions. This is the role whose ARN goes into `AWS_DEPLOY_ROLE_ARN`.

Use this trust policy, replacing:

- `<ACCOUNT_ID>` with your 12-digit AWS account ID
- `<OWNER>` with your GitHub username or organization
- `<REPO>` with your repository name

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": [
            "repo:<OWNER>/<REPO>:environment:production",
            "repo:<OWNER>/<REPO>:ref:refs/heads/master"
          ]
        }
      }
    }
  ]
}
```

Common mistake:

- the `sub` string must match exactly
- accidental spaces in `repo:<OWNER>/<REPO>:...` will break OIDC authentication

### 3. Deploy Role Permissions Policy

Attach this full permissions policy to the GitHub deploy role:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "CloudFormationDeploy",
      "Effect": "Allow",
      "Action": [
        "cloudformation:CreateStack",
        "cloudformation:UpdateStack",
        "cloudformation:DeleteStack",
        "cloudformation:DescribeStacks",
        "cloudformation:DescribeStackEvents",
        "cloudformation:DescribeStackResources",
        "cloudformation:GetTemplateSummary",
        "cloudformation:ValidateTemplate",
        "cloudformation:CreateChangeSet",
        "cloudformation:DescribeChangeSet",
        "cloudformation:ExecuteChangeSet",
        "cloudformation:DeleteChangeSet",
        "cloudformation:ContinueUpdateRollback"
      ],
      "Resource": "*"
    },
    {
      "Sid": "IamForStackResources",
      "Effect": "Allow",
      "Action": [
        "iam:CreateRole",
        "iam:DeleteRole",
        "iam:GetRole",
        "iam:PassRole",
        "iam:AttachRolePolicy",
        "iam:DetachRolePolicy",
        "iam:PutRolePolicy",
        "iam:DeleteRolePolicy",
        "iam:CreateInstanceProfile",
        "iam:DeleteInstanceProfile",
        "iam:GetInstanceProfile",
        "iam:AddRoleToInstanceProfile",
        "iam:RemoveRoleFromInstanceProfile",
        "iam:TagRole"
      ],
      "Resource": "*"
    },
    {
      "Sid": "Ec2Infrastructure",
      "Effect": "Allow",
      "Action": [
        "ec2:RunInstances",
        "ec2:TerminateInstances",
        "ec2:StartInstances",
        "ec2:StopInstances",
        "ec2:ModifyInstanceAttribute",
        "ec2:DescribeInstances",
        "ec2:DescribeImages",
        "ec2:DescribeSecurityGroups",
        "ec2:DescribeVolumes",
        "ec2:CreateSecurityGroup",
        "ec2:DeleteSecurityGroup",
        "ec2:AuthorizeSecurityGroupIngress",
        "ec2:RevokeSecurityGroupIngress",
        "ec2:AuthorizeSecurityGroupEgress",
        "ec2:RevokeSecurityGroupEgress",
        "ec2:CreateTags",
        "ec2:DescribeTags",
        "ec2:AllocateAddress",
        "ec2:AssociateAddress",
        "ec2:DisassociateAddress",
        "ec2:ReleaseAddress",
        "ec2:DescribeAddresses",
        "ec2:DescribeVpcs",
        "ec2:DescribeSubnets"
      ],
      "Resource": "*"
    },
    {
      "Sid": "EcrRepositoriesAndImages",
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:CreateRepository",
        "ecr:DescribeRepositories",
        "ecr:TagResource",
        "ecr:DeleteRepository",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:BatchCheckLayerAvailability",
        "ecr:BatchGetImage"
      ],
      "Resource": "*"
    },
    {
      "Sid": "SsmDeployment",
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameters",
        "ssm:PutParameter",
        "ssm:SendCommand",
        "ssm:ListCommandInvocations",
        "ssm:DescribeInstanceInformation"
      ],
      "Resource": "*"
    },
    {
      "Sid": "CloudWatchLogs",
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:DeleteLogGroup",
        "logs:PutRetentionPolicy",
        "logs:TagResource",
        "logs:ListTagsForResource",
        "logs:DescribeLogGroups"
      ],
      "Resource": "*"
    }
  ]
}
```

The deploy workflow uses OIDC. It does not require long-lived AWS access keys.

### 4. Verify Target Regions

Make sure the AWS account can deploy into the regions listed in `infra/regions.json`, for example:

- `eu-west-1`
- `us-east-1`
- `ap-northeast-2`

Also verify that your account has quota and access for:

- `t4g.micro`
- Elastic IPs
- ECR
- SSM
- CloudFormation

### 5. Verify Existing Application Dependencies

This repository does not provision the MariaDB database. If you use an external database, make sure it accepts connections from the deployed EC2 instances.

If the application uses production S3 buckets or DynamoDB tables, make sure:

- those resources exist
- the EC2 instance role created by CloudFormation is allowed to access them

The current code expects at least:

- DynamoDB table `wah_auction_houses`
- DynamoDB table `wah_auction_houses_update_log`
- S3 buckets matching `wah-data*`

## Runtime Configuration

The runtime configuration is written to SSM under:

`/<app-name>/<environment>/<aws-region>/...`

Example:

`/wow-auction-engine/prod/eu-west-1/BLIZZARD_CLIENT_ID`

Important keys include:

- `SPRING_PROFILES_ACTIVE`
- `WAE_AWS_REGION`
- `WAE_BLIZZARD_REGIONS`
- `WAE_BLIZZARD_REGION` (compatibility fallback)
- `AUCTION_ENGINE_DB_URL`
- `AUCTION_ENGINE_DB_USERNAME`
- `AUCTION_ENGINE_DB_PASSWORD`
- `BLIZZARD_CLIENT_ID`
- `BLIZZARD_CLIENT_SECRET`
- `JVM_MAX_RAM_PERCENTAGE`
- optional endpoint overrides for S3 and DynamoDB

## Scaling Regions Up Or Down

To add, remove, or disable a region:

1. update `infra/regions.json`
2. commit the change
3. push to `master`

The deploy workflow will only act on regions marked as enabled in that file.

Typical changes:

- change `instance_type` to scale vertically
- change `enabled` to include or exclude a region
- adjust `allowed_ingress_cidr`
- adjust JVM settings per region

## Production Region Layout

The deploy contract now distinguishes the AWS deployment region from the Blizzard regions handled by that instance.

- `eu-west-1` deploys the Europe stack with `blizzard_regions: ["Europe"]`
- `us-west-1` deploys the North America stack with `blizzard_regions: ["NorthAmerica"]`
- `ap-northeast-2` deploys the Asia stack with `blizzard_regions: ["Korea", "Taiwan"]`

The Asia stack is intentionally a single Seoul deployment that updates both Korea and Taiwan auction houses.

## regions.json Contract

Each enabled entry in [`infra/regions.json`](C:/Users/jonas/.codex/worktrees/173c/wow-auction-engine/infra/regions.json) should declare:

- `aws_region`
- `blizzard_regions`
- `instance_type`
- `allowed_ingress_cidr`
- `jvm_max_ram_percentage`
- `container_port`
- `health_check_path`
- `instance_enabled`
- `stack_name`

`blizzard_regions` is an array in source control and is written to runtime as `WAE_BLIZZARD_REGIONS` using comma-separated values.

## First-Deploy Caveats

- The current stack is intentionally small and EC2-focused
- it does not create a full VPC layout
- the workflow currently looks up the default VPC and one default subnet in the target region and passes them into CloudFormation
- if your AWS account does not have a default VPC in that region, the deploy will fail and the template/workflow must be extended to pass explicit `VpcId` and `SubnetId`

## Troubleshooting

### OIDC Fails With `Not authorized to perform sts:AssumeRoleWithWebIdentity`

Usually caused by one of:

- wrong `AWS_DEPLOY_ROLE_ARN`
- wrong trust relationship on the deploy role
- `sub` condition does not match the repo, branch, or environment
- GitHub OIDC provider created in the wrong AWS account

Start by verifying the trust policy matches the exact GitHub repo and the `production` environment.

### CloudFormation Fails With `ROLLBACK_COMPLETE`

CloudFormation cannot update a stack in `ROLLBACK_COMPLETE`.

The deploy workflow deletes rollback-only stacks automatically before retrying create/update.

### CloudFormation Fails With `UPDATE_ROLLBACK_FAILED`

CloudFormation cannot update a stack in `UPDATE_ROLLBACK_FAILED`.

The deploy workflow attempts to recover this automatically with `ContinueUpdateRollback` before retrying the deploy.

If the stack is still stuck, run:

```bash
aws cloudformation continue-update-rollback \
  --region <aws-region> \
  --stack-name <stack-name>
```

If the rollback remains stuck because of an earlier bad EC2 instance update, ensure the deploy role still has:

- `ec2:StopInstances`
- `ec2:StartInstances`
- `ec2:ModifyInstanceAttribute`
- `ec2:DescribeVolumes`

### CloudFormation Fails Resolving The Amazon Linux AMI

If you see an error mentioning:

`not authorized to perform: ssm:GetParameters on resource: arn:aws:ssm:...:parameter/aws/service/ami-amazon-linux-latest/...`

the deploy role is missing SSM read access for AWS-managed public parameters. The template resolves the latest Amazon Linux 2023 ARM64 AMI through a public SSM parameter.

### CloudFormation Fails Creating `AppSecurityGroup`

If you see an error like:

`SecurityGroupEgress cannot be specified without VpcId`

it means the stack was missing VPC context. The workflow now resolves the default VPC and a subnet automatically. If your AWS account has no default VPC in that region, you must either create one or extend the workflow/template to pass explicit `VpcId` and `SubnetId`.

### CloudFormation Fails Creating `AppRepository`

If you see an error mentioning:

`not authorized to perform: ecr:TagResource`

the GitHub deploy role is missing ECR tagging permissions.

If rollback later fails with:

`not authorized to perform: ecr:DeleteRepository`

the deploy role is missing ECR repository deletion permissions needed for CloudFormation rollback.

### CloudFormation Fails Creating `AppInstanceRole`

If you see an error mentioning:

`Access denied for operation 'logs:DescribeLogGroups'`

the GitHub deploy role is missing CloudWatch Logs describe permissions needed while CloudFormation evaluates the log group reference in the IAM policy.

### CloudFormation Fails Updating `AppInstance`

If you see an error mentioning:

- `not authorized to perform: ec2:StopInstances`
- `not authorized to perform: ec2:ModifyInstanceAttribute`
- `not authorized to perform: ec2:DescribeVolumes`

that usually means the stack is still recovering from an older revision that tried to push application image changes through EC2 instance `UserData`, which forced a CloudFormation instance update.

The current deployment design avoids that by:

- keeping the app version rollout out of CloudFormation
- pushing the image to ECR separately
- restarting the Docker container on the instance through SSM

### Deploy Workflow Does Not Start

`Deploy Production` only runs after `Backend PR Checks` succeeds on `master`.

Make sure:

- the push actually reached `master`
- `Backend PR Checks` finished successfully
- the workflow name still matches `Backend PR Checks`

### Docker Build Rebuilds The Entire App

The current setup avoids that by uploading the `.war` from CI and building the Docker image from that artifact. If you see Maven or Kotlin compilation inside the Docker build, the workflow likely reverted away from the artifact-based path.
