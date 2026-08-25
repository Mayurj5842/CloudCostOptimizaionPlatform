# AWS resource discovery

Resource discovery is read-only and uses the short-lived connection session created by `POST /api/aws/connect`. Credentials are retained only in backend memory for the session lifetime; they are not stored in PostgreSQL, browser storage, Zustand persistence, or API responses.

The AWS identity used for discovery needs these read permissions:

- `ec2:DescribeInstances`
- `ec2:DescribeVolumes`
- `ec2:DescribeVpcs`
- `ec2:DescribeSecurityGroups`
- `s3:ListAllMyBuckets`
- `s3:GetBucketLocation`
- `rds:DescribeDBInstances`
- `lambda:ListFunctions`
- `ce:GetCostAndUsage`
- `budgets:ViewBudget`
- `ce:GetCostForecast`

Dashboard cost metrics use the AWS Cost Explorer `UnblendedCost` metric with
`MONTHLY` granularity. Cost Explorer and Budgets are queried in `us-east-1`,
which is the AWS control-plane region for these services. Estimated savings is
returned as unavailable until a supported read-only optimization recommendation
API is enabled in the project SDK and account.

No write permissions are required. In particular, do not grant instance start/stop/terminate, bucket deletion, database deletion, or function deletion permissions.
