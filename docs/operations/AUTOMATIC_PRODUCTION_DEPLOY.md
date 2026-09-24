# Automatic production deployment

Troquim production deploys are designed to run without SSH keys in GitHub.

## Architecture

```
merge/push main
      |
      v
      CI
      |
      | success
      v
Deploy Production workflow
      |
      | GitHub OIDC -> short-lived AWS credentials
      v
AWS Systems Manager SendCommand
      |
      v
EC2 production
      |
      +--> exact immutable Git SHA
      +--> Docker build
      +--> fresh validated pg_dump
      +--> temporary PostgreSQL restore
      +--> temporary application boot + Flyway validation
      +--> canonical deploy-prod-release.sh
      +--> local/public health + final Flyway validation
```

No private SSH key or long-lived AWS access key belongs in GitHub.

GitHub documents OIDC as the mechanism for exchanging a workflow identity for
short-lived AWS credentials. AWS Systems Manager Run Command is the remote execution
channel.

## Safety gates

Automatic deployment only runs when:

1. the `CI` workflow completed successfully;
2. the triggering CI event was a `push` to `main`;
3. repository variable `PROD_AUTO_DEPLOY_ENABLED` is exactly `true`;
4. repository variables `AWS_DEPLOY_ROLE_ARN` and `PROD_EC2_INSTANCE_ID` exist.

Without the explicit `PROD_AUTO_DEPLOY_ENABLED=true` switch and both AWS identifiers, the workflow is intentionally unarmed and exits without touching AWS.

The EC2-side script is idempotent and refuses to continue when the current production
backend is unhealthy.

Before production migration, it always:

1. builds the exact Git SHA;
2. creates and validates a fresh database dump;
3. restores that dump into temporary PostgreSQL;
4. boots the new image against the temporary copy;
5. requires health UP;
6. requires the expected final Flyway version and zero failed migrations.

Only after those gates pass is `scripts/deploy-prod-release.sh` called.

## One-time AWS setup

### 1. Make the EC2 instance a Systems Manager managed node

The EC2 instance needs an IAM instance profile that includes:

```
arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
```

Also make sure SSM Agent is running on the instance.

### 2. Add GitHub as an AWS OIDC provider

Provider URL:

```
https://token.actions.githubusercontent.com
```

Audience:

```
sts.amazonaws.com
```

### 3. Create a deploy role

The trust policy must restrict assumption to this repository and `main`.

Because GitHub supports both the traditional and immutable repository subject formats,
allow the exact subject form used by the repository account configuration. Do not use a
wildcard repository trust.

Traditional subject:

```
repo:guilhermerichaard/troquim-bot:ref:refs/heads/main
```

Immutable repository subject:

```
repo:guilhermerichaard@153012443/troquim-bot@1281801040:ref:refs/heads/main
```

The deploy role only needs permission to send `AWS-RunShellScript` to the production
EC2 instance and inspect that command's result. Keep its resource scope limited to the
production instance wherever the AWS API supports resource-level restriction.

### 4. Configure GitHub repository variables

In GitHub repository Actions variables configure:

```
PROD_AUTO_DEPLOY_ENABLED=false
AWS_DEPLOY_ROLE_ARN=arn:aws:iam::<account-id>:role/<deploy-role>
PROD_EC2_INSTANCE_ID=i-005c67b440dc22794
```

These values are identifiers, not private credentials.

## Manual recovery

The existing SSH/runbook path remains supported. Automatic deployment deliberately
reuses the same canonical release script rather than maintaining a second production
deployment implementation.


## Arming production

Keep `PROD_AUTO_DEPLOY_ENABLED=false` while SSM/OIDC are being configured or tested. Change it to `true` only after the EC2 instance appears as a healthy SSM managed node and the restricted OIDC role has been verified. Turning the switch back to `false` immediately disables automatic production deploys without changing code.
