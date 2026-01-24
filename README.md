# WorkSight Backend (Java/Spring Boot)

Seed project to replace Supabase with a Java service. Built with Spring Boot 3, Gradle, and Postgres.

## Structure
- `build.gradle` – Spring Boot, Web, Security, Validation, JPA, Flyway, Postgres.
- `src/main/java/com/schoolable/backend/WorkSightBackendApplication.java` – entry point.
- `src/main/resources/application.yml` – default config (Postgres + Flyway).

## Getting Started
```bash
cd /Users/mofolasayo-osikoya/schoolable_backend
./gradlew bootRun      # or: gradle wrapper first if gradle not present
```

If you don't have the Gradle wrapper yet, generate it once:
```bash
gradle wrapper
```

## Next Steps
- Add Flyway migrations under `src/main/resources/db/migration` to mirror the current Supabase schema.
- Implement auth (JWT) and domain modules: profiles, tasks, announcements, chat, attendance.
- Expose REST endpoints matching the Flutter/Next.js clients, then switch the apps to call this service.
