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
3. Read `.claude/skills/backend/testing-backend/SKILL.md`
4. Read `specs/services/<service>/architecture.md` for the target service
5. Check whether test files already exist for the target code
6. Determine required test levels based on the architecture style
7. Write tests
8. Run tests and verify all pass

## Test Levels (per testing-strategy.md)

| Level | Target | Annotation |
|---|---|---|
| Unit | Domain logic, service logic | `@ExtendWith(MockitoExtension.class)` |
| Controller Slice | HTTP mapping, request/response conversion | `@WebMvcTest` + `MockMvc` |
| Integration | DB/cache integration, end-to-end flow | `@SpringBootTest` + `@Testcontainers` |
| Event | Event publishing/consumption | Kafka Testcontainers |

## Rules

- No H2 or in-memory substitutes — use real Testcontainers
- Test method naming: `{scenario}_{condition}_{expectedResult}`
- `@DisplayName` must describe business behavior in Korean
- Mockito STRICT_STUBS mode
- Data isolation between tests: use `UUID.randomUUID()` or unique identifiers
- Do not rely on `@Transactional` rollback for cleanup
