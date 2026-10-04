# Production deployment

The production layout keeps the existing application components and places the
public Next.js application on Vercel, the Spring Boot API on Render, and the
existing PostgreSQL schema on Neon. The browser uses the Vercel URL; the API
has its own Render URL and is configured in Vercel through
`NEXT_PUBLIC_API_URL`.

## Build prerequisites

- The backend Maven configuration targets Java 25 and Spring Boot 4.1.
- The frontend uses Next.js 16. Set Vercel's project root to `frontend`.
- Render's native runtimes do not include a JVM. The backend therefore uses
  `backend/Dockerfile`; no compose service is required.
- Backend startup runs the existing Flyway migrations and validates the schema
  against the existing JPA entities. Do not create or edit the production
  schema manually.

## Neon PostgreSQL

Create a PostgreSQL database and configure these Render environment variables
from the provider's connection details:

- `SPRING_DATASOURCE_URL`: JDBC URL, for example `jdbc:postgresql://<host>/<database>?sslmode=require`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Keep all connection values in the hosting provider's secret/environment
settings. Do not commit them. Flyway applies the migrations in
`backend/src/main/resources/db/migration` on startup.

## Render backend

Create a Web Service for the existing GitHub repository with:

- Runtime: Docker
- Root directory: `backend`
- Dockerfile path: `Dockerfile`
- Health check path: `/actuator/health`

Configure:

- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
  `SPRING_DATASOURCE_PASSWORD`
- `CORS_ALLOWED_ORIGINS`: the exact Vercel origin, without a path or trailing
  slash (optionally comma-separated with `http://localhost:3000` for local
  development)
- `AWS_BILLING_REGION`, `AWS_BILLING_S3_BUCKET`, `AWS_BILLING_S3_PREFIX`, and
  `AWS_BILLING_IMPORT_ENABLED` / `AWS_BILLING_IMPORT_INTERVAL_HOURS` as needed
  for the existing CUR 2.0 S3 import
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`,
  `MAIL_SMTP_AUTH`, and `MAIL_SMTP_STARTTLS` if email notifications are used

Render supplies `PORT`; the backend binds to it. `/actuator/health` is already
exposed and permitted by the existing security configuration. CORS continues
to use the existing `CORS_ALLOWED_ORIGINS` setting.

## Vercel frontend

Import the existing GitHub repository and set the project root to `frontend`.
Vercel detects Next.js and uses the existing `npm run build` script. Set the
build-time environment variable:

- `NEXT_PUBLIC_API_URL`: the deployed Render API origin, such as
  `https://<render-service>.onrender.com`, with no `/api` suffix

The application already uses this variable for its backend HTTP clients.

## AWS, CUR, and runtime notes

AWS credentials remain in the existing AWS connection flow and must be entered
in the application; do not set AWS secret access keys in Vercel or commit them.
CUR continues to use the configured AWS region, S3 bucket, and prefix and is
imported into PostgreSQL through the existing importer.

AWS connection sessions are held in the backend process memory and expire
after 24 hours. A backend restart clears active sessions, so reconnect the AWS
account after a restart. The deployment should run one backend instance to
avoid requests being routed to an instance that does not hold the in-memory
session.

## Environment variable template

See the root `.env.example` for the environment variable names only. Populate
production secrets in Vercel/Render settings; do not commit a populated
environment file.