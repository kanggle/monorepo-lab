---
name: write-tests
description: Write tests for the specified service or class
---

# write-tests

Write tests for the specified service or class.

## Usage

```
/write-tests <service>                                     # write tests for entire service
/write-tests <service> <class>                             # write tests for a specific class
```

Examples:

```
/write-tests <service-name>
/write-tests <service-name> <ServiceClass>
```

## Procedure

1. Read `CLAUDE.md` and complete project classification (load `PROJECT.md` + the applicable `rules/common.md` / `rules/domains/<domain>.md` / `rules/traits/<trait>.md` per `platform/entrypoint.md` Step 0) — active traits (e.g. `transactional`) impose test mandates.
2. Read `platform/testing-strategy.md`
3. Read `specs/services/<service>/architecture.md` for the target service and check its declared `Service Type`.
4. Read the skill matching that Service Type: `.claude/skills/backend/testing-backend/SKILL.md` for a backend
   service type (`rest-api`, `event-consumer`, `batch-job`, `grpc-service`, `graphql-service`,
   `identity-platform`), or `.claude/skills/frontend/testing-frontend/SKILL.md` for `frontend-app`.
5. Check whether test files already exist for the target code
6. Determine required test levels based on the architecture style
7. Write tests
8. Run tests and verify all pass

## Test Levels (per testing-strategy.md)

### Backend service types

| Level | Target | Annotation |
|---|---|---|
| Unit | Domain logic, service logic | `@ExtendWith(MockitoExtension.class)` |
| Controller Slice | HTTP mapping, request/response conversion | `@WebMvcTest` + `MockMvc` |
| Integration | DB/cache integration, end-to-end flow | `@SpringBootTest` + `@Testcontainers` |
| Event | Event publishing/consumption | Kafka Testcontainers |

### `frontend-app` service type

| Level | Target | Tooling |
|---|---|---|
| Hook | Custom hooks, data-fetching logic | Vitest + `renderHook` (`@testing-library/react`) |
| Component | Rendered output, user interaction | Vitest + `@testing-library/react` + `@testing-library/user-event` |
| E2E | Full user flow across pages | `.claude/skills/testing/e2e-test/SKILL.md` (Playwright) |

## Rules

- No H2 or in-memory substitutes for integration tests — use real Testcontainers (the one exception: a non-authoritative `@DataJpaTest` H2 slice alongside an authoritative Testcontainers IT, per `platform/testing-strategy.md § H2 auxiliary-slice exception`)
- Test method naming: `{scenario}_{condition}_{expectedResult}`
- `@DisplayName` must describe business behavior in Korean
- Mockito STRICT_STUBS mode
- Data isolation between tests: use `UUID.randomUUID()` or unique identifiers
- Do not rely on `@Transactional` rollback for cleanup
