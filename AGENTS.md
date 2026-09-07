# Backend Instructions

## Stack

- Java 17
- Spring Boot
- Gradle Wrapper
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- JWT authentication

Use Java 17 as the authoritative runtime target.
Do not treat behavior observed only under a different host JDK as production behavior.

## Architecture

Application code lives under:

`src/main/java/com/se_lab/project/`

Follow the existing package structure:

- `controller`: HTTP request/response handling
- `service`: business logic and external API orchestration
- `repository`: persistence access
- `entity`: JPA entities
- `dto`: API and service data transfer objects
- `planner`: route planning logic
- `config`: application configuration
- `global`: shared infrastructure and error handling

Keep controllers thin.
Business logic and external API behavior belong in services.

## API Changes

Before changing an endpoint:

1. Inspect the controller.
2. Inspect the corresponding service.
3. Inspect DTOs used by the endpoint.
4. Inspect existing tests.
5. Check whether frontend clients depend on the response contract.

Do not rename response fields or change response types without identifying frontend impact.

## Database

- PostgreSQL is the database.
- Persistence uses Spring Data JPA.
- Do not modify database schema or entity mappings unless required.
- Identify migration/schema impact before changing persistent fields.
- Do not assume production data can safely be deleted or recreated.

## Authentication

Authentication and authorization changes are security-sensitive.

Before modifying them:

- inspect Spring Security configuration,
- inspect JWT handling,
- inspect public/protected endpoint rules,
- verify user-owned data remains isolated between users.

Never weaken authorization merely to make a request succeed.

## External APIs

External tourism/routing APIs must remain behind service-layer abstractions.

When modifying integrations:

- preserve required query parameters,
- distinguish remote API failure from valid empty results where possible,
- do not cache failures as successful results without explicit justification.

## Testing

Run backend validation using Java 17.

Preferred checks:

`./gradlew test`

and when relevant:

`./gradlew clean bootJar`

Do not claim tests passed unless they were executed.

Add or update tests for changes involving:

- validation,
- authorization,
- persistence,
- API response contracts,
- route-planning behavior,
- external API parsing.

## Environment

Backend secrets belong in `backend/.env`.

Use `.env.example` for documenting variable names.

Never commit:

- `.env`
- passwords
- JWT secrets
- API keys
- database credentials


## Documentation

When behavior changes, check whether documentation under `docs/`
also requires an update.

In particular, document changes affecting:

- API contracts
- environment variables
- deployment
- authentication
- database behavior
