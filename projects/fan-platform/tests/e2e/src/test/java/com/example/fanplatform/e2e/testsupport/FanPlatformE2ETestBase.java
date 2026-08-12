package com.example.fanplatform.e2e.testsupport;

import com.redis.testcontainers.RedisContainer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Base infrastructure for fan-platform v1 live-trio e2e tests
 * (TASK-FAN-INT-001).
 *
 * <p>Boots onto a shared {@link Network}:
 *
 * <ul>
 *   <li>Postgres 16 alpine — initialises {@code fanplatform_community} and
 *       {@code fanplatform_artist} databases via an embedded init script
 *       (mirrors the docker-compose setup at
 *       {@code projects/fan-platform/infra/postgres/init/01-create-databases.sh}).</li>
 *   <li>Redis 7 alpine — feed cache (community) + rate-limit counters (gateway).</li>
 *   <li>Kafka (KRaft) — community-service + artist-service publish outbox events.</li>
 *   <li>community-service — image resolved from system property
 *       {@code fan.e2e.communityImage} when set (CI pre-build path), otherwise
 *       built on-the-fly via {@link ImageFromDockerfile} (local dev path).</li>
 *   <li>artist-service — same dual-path strategy via {@code fan.e2e.artistImage}.</li>
 *   <li>gateway-service — same dual-path strategy via {@code fan.e2e.gatewayImage}.
 *       Extends the production gateway image with test-only env overrides for
 *       OIDC + downstream URLs (OIDC_ISSUER_URL, JWT_JWKS_URI, COMMUNITY_SERVICE_URI,
 *       ARTIST_SERVICE_URI, REDIS_HOST). RewritePath filters are baked into the
 *       production {@code application.yml} (TASK-FAN-BE-005).</li>
 *   <li>MySQL 8 + iam's auth-service (TASK-FAN-INT-005) — the workload-identity
 *       token source. Same dual-path strategy via {@code fan.e2e.iamImage}.</li>
 * </ul>
 *
 * <h2>Two token planes, deliberately separate (TASK-FAN-INT-005)</h2>
 *
 * <p>The stack signs END-USER tokens with the host-side {@link JwksMockServer}
 * and WORKLOAD ({@code client_credentials}) tokens with a real IAM. That split is
 * the whole point of this suite's shape, and it is expressible only because
 * artist-service reads its {@code /internal/**} decoder from its own
 * {@code fanplatform.internal.jwt.*} keys rather than the end-user ones.
 *
 * <p>Before this, the suite had no token source at all, so community-service ran
 * with {@code COMMUNITY_ARTIST_SERVICE_ENABLED=false} — the follow-target gate
 * switched OFF for every run. That switch has been deleted; the gate is now
 * exercised, which is why the token has to be real.
 *
 * <p>🔴 Note both issuers claim {@code iss=http://iam.local} in production. Here
 * they must NOT: auth-service is given {@code OIDC_ISSUER_URL=http://fan-e2e-iam:8081}
 * so its issuer is network-reachable for JWKS, while the mock keeps
 * {@link JwtTestHelper#SAS_ISSUER}. Two decoders, two issuers, no key sharing.
 *
 * <p>The JWKS stand-in lives in the JVM running the tests — not inside the
 * Docker network. Each service container reaches it via
 * {@code host.docker.internal:{port}}, enabled by
 * {@code withExtraHost("host.docker.internal", "host-gateway")}.
 *
 * <p>Annotated {@link Testcontainers} with {@code disabledWithoutDocker = true}
 * so CI Linux runs pick this up and Windows dev machines without Docker skip
 * gracefully (per TASK-FAN-INT-001 § Failure Scenarios).
 */
@Tag("e2e")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(FanPlatformE2ETestBase.ServiceContainerLogDumper.class)
public abstract class FanPlatformE2ETestBase {

    protected static final Logger log = LoggerFactory.getLogger(FanPlatformE2ETestBase.class);

    protected static final String POSTGRES_IMAGE = "postgres:16-alpine";
    protected static final String REDIS_IMAGE = "redis:7-alpine";
    protected static final String KAFKA_IMAGE = "apache/kafka:3.7.0";
    protected static final String MYSQL_IMAGE = "mysql:8.0";

    protected static final String POSTGRES_ALIAS = "fan-e2e-postgres";
    protected static final String REDIS_ALIAS = "fan-e2e-redis";
    protected static final String KAFKA_ALIAS = "fan-e2e-kafka";
    protected static final String MYSQL_ALIAS = "fan-e2e-mysql";
    protected static final String COMMUNITY_ALIAS = "fan-e2e-community";
    protected static final String ARTIST_ALIAS = "fan-e2e-artist";
    protected static final String MEMBERSHIP_ALIAS = "fan-e2e-membership";
    protected static final String GATEWAY_ALIAS = "fan-e2e-gateway";
    protected static final String IAM_ALIAS = "fan-e2e-iam";

    /**
     * 🔴 TASK-FAN-INT-005 — auth-service's production ENTRYPOINT blocks on
     * {@code until getent hosts mysql && getent hosts kafka && getent hosts redis}
     * BEFORE it execs the JVM (iam's Dockerfile, TASK-BE-048: it exists so Hikari
     * and Flyway never cache a negative DNS lookup during startup). Those three
     * names are HARD-CODED, so on a network where the backing services are only
     * known as {@code fan-e2e-*} the container waits forever and the failure looks
     * like an ordinary startup timeout with no Spring log to read.
     *
     * <p>The fix is to give each backing container a SECOND alias matching the name
     * iam's entrypoint waits for. Aliases are network-scoped, so this is invisible
     * to everything else on the network — fan's own services keep addressing the
     * {@code fan-e2e-*} names.
     */
    private static final String[] IAM_ENTRYPOINT_DNS_ALIASES = { "mysql", "kafka", "redis" };

    protected static final int SERVICE_PORT = 8080;
    /** auth-service listens on 8081, not the fan services' 8080. */
    protected static final int IAM_PORT = 8081;

    /** Internal Kafka listener port reachable inside the docker network. */
    private static final int KAFKA_INTERNAL_PORT = 9095;

    private static final String DB_USERNAME = "fanplatform";
    private static final String DB_PASSWORD = "fanplatform";
    private static final String DB_NAME_COMMUNITY = "fanplatform_community";
    private static final String DB_NAME_ARTIST = "fanplatform_artist";
    /**
     * TASK-FAN-INT-006. The init script already creates this database — it reads
     * {@code POSTGRES_DB_MEMBERSHIP} and has since membership-service existed
     * ({@code infra/postgres/init/01-create-databases.sh}), so the only thing that
     * was missing here is the env var telling it to. Verified rather than assumed:
     * the script's {@code create_db_if_missing} line names this exact variable.
     */
    private static final String DB_NAME_MEMBERSHIP = "fanplatform_membership";

    /** iam auth_db credentials — must match auth-service's `e2e` profile defaults. */
    private static final String IAM_DB_NAME = "auth_db";
    private static final String IAM_DB_USERNAME = "auth_user";
    private static final String IAM_DB_PASSWORD = "auth_pass";

    /**
     * The issuer auth-service mints and artist-service's internal decoder accepts.
     * Network-reachable on purpose: the decoder derives nothing from it, but
     * community's token endpoint URI is built from the same host:port, and keeping
     * one value for both removes an axis where the two could silently disagree.
     */
    private static final String IAM_ISSUER = "http://" + IAM_ALIAS + ":" + IAM_PORT;

    /** Boot jars produced by Gradle's {@code bootJar} task — referenced by the dev fallback path. */
    private static final Path GATEWAY_JAR = locateOptionalJar(
            "apps/gateway-service/build/libs/gateway-service.jar");
    private static final Path COMMUNITY_JAR = locateOptionalJar(
            "apps/community-service/build/libs/community-service.jar");
    private static final Path ARTIST_JAR = locateOptionalJar(
            "apps/artist-service/build/libs/artist-service.jar");
    private static final Path MEMBERSHIP_JAR = locateOptionalJar(
            "apps/membership-service/build/libs/membership-service.jar");
    /** iam lives in a sibling project — repo-root-relative, unlike the three above. */
    private static final Path IAM_JAR = locateOptionalJar(
            "projects/iam-platform/apps/auth-service/build/libs/auth-service.jar");

    /** Dockerfile locations — reused verbatim from production image builds. */
    private static final Path GATEWAY_DOCKERFILE = locateFile("apps/gateway-service/Dockerfile");
    private static final Path COMMUNITY_DOCKERFILE = locateFile("apps/community-service/Dockerfile");
    private static final Path ARTIST_DOCKERFILE = locateFile("apps/artist-service/Dockerfile");
    private static final Path MEMBERSHIP_DOCKERFILE = locateFile("apps/membership-service/Dockerfile");
    private static final Path IAM_DOCKERFILE = locateFile(
            "projects/iam-platform/apps/auth-service/Dockerfile");

    /**
     * The SAS signing keypair. auth-service's {@code application.yml} points
     * {@code auth.jwt.*-key-path} at {@code classpath:keys/*.pem}, but only the
     * {@code .example} files are checked in under {@code src/main/resources} —
     * the real dev keys live under {@code src/test/resources}, which {@code bootJar}
     * does not package. So the production image has NO key on its classpath and JWT
     * bean creation fails at boot unless they are mounted. iam's own
     * {@code docker-compose.e2e.yml} solves it the same way (TASK-MONO-082 cycle 3);
     * this copies rather than bind-mounts because Testcontainers has no host path
     * guarantee on every runner.
     */
    private static final Path IAM_KEYS_DIR = locateFile(
            "projects/iam-platform/apps/auth-service/src/test/resources/keys");

    protected Network network;
    protected PostgreSQLContainer<?> postgres;
    protected MySQLContainer<?> mysql;
    protected GenericContainer<?> redis;
    protected KafkaContainer kafka;
    protected GenericContainer<?> iam;
    protected GenericContainer<?> community;
    protected GenericContainer<?> artist;
    protected GenericContainer<?> membership;
    protected GenericContainer<?> gateway;

    protected JwtTestHelper jwt;
    protected JwksMockServer jwks;

    protected final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeAll
    void startInfrastructure() throws Exception {
        // OpenAI Harness gap #3 Phase 3 (TASK-MONO-067) — when the
        // `-Pobservability=on` Gradle path injects the system property
        // `wms.e2e.observabilityNetwork`, reuse the named docker network
        // that scripts/observability/up.sh created. Property unset → behaviour
        // identical to the pre-Phase-3 path (anonymous Testcontainers
        // network). See:
        // docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md § 2.5 D5
        // tasks/done/TASK-MONO-066-observability-query-skill.md (gateway-service precedent)
        String observabilityNetwork = System.getProperty("wms.e2e.observabilityNetwork");
        if (observabilityNetwork != null && !observabilityNetwork.isBlank()) {
            String netName = observabilityNetwork;
            network = Network.builder()
                    .createNetworkCmdModifier(cmd -> cmd.withName(netName))
                    .build();
        } else {
            network = Network.newNetwork();
        }

        // ----- Postgres with multi-database init script ---------------------
        // Postgres image's docker-entrypoint runs *.sh / *.sql files in
        // /docker-entrypoint-initdb.d on first boot. Copy the project's init
        // script into the container so the two per-service databases exist
        // before community-service / artist-service start their Flyway migrations.
        postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName(DB_NAME_COMMUNITY) // creates the first DB; second DB created by init script
                .withUsername(DB_USERNAME)
                .withPassword(DB_PASSWORD)
                .withNetwork(network)
                .withNetworkAliases(POSTGRES_ALIAS)
                .withEnv("POSTGRES_DB_COMMUNITY", DB_NAME_COMMUNITY)
                .withEnv("POSTGRES_DB_ARTIST", DB_NAME_ARTIST)
                .withEnv("POSTGRES_DB_MEMBERSHIP", DB_NAME_MEMBERSHIP)
                .withCopyFileToContainer(
                        org.testcontainers.utility.MountableFile.forHostPath(
                                locateFile("infra/postgres/init/01-create-databases.sh").toString()),
                        "/docker-entrypoint-initdb.d/01-create-databases.sh");
        postgres.start();

        // ----- MySQL (iam auth_db) ------------------------------------------
        // TASK-FAN-INT-005. Its own engine rather than a schema on the Postgres
        // above: iam is a MySQL lane and its Flyway migrations are MySQL dialect.
        mysql = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
                .withDatabaseName(IAM_DB_NAME)
                .withUsername(IAM_DB_USERNAME)
                .withPassword(IAM_DB_PASSWORD)
                .withNetwork(network)
                .withNetworkAliases(MYSQL_ALIAS, IAM_ENTRYPOINT_DNS_ALIASES[0]);
        mysql.start();

        // ----- Redis --------------------------------------------------------
        // Second alias `redis` — see IAM_ENTRYPOINT_DNS_ALIASES.
        redis = new RedisContainer(DockerImageName.parse(REDIS_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(REDIS_ALIAS, IAM_ENTRYPOINT_DNS_ALIASES[2]);
        redis.start();

        // ----- Kafka (KRaft) ------------------------------------------------
        // Second alias `kafka` — see IAM_ENTRYPOINT_DNS_ALIASES.
        kafka = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(KAFKA_ALIAS, IAM_ENTRYPOINT_DNS_ALIASES[1])
                .withListener(KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT);
        kafka.waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));
        kafka.start();

        // ----- JWKS stand-in (host JVM, reachable via host.docker.internal) -
        // MUST start before any service that performs an eager JWKS fetch on
        // first request (gateway has a startup probe — disabled below via
        // GATEWAY_JWKS_STARTUP_PROBE_ENABLED=false to keep boot deterministic).
        jwt = new JwtTestHelper();
        jwks = new JwksMockServer(jwt);

        // ----- iam auth-service (workload-identity token source) ------------
        // TASK-FAN-INT-005. This container is why the two escape hatches existed:
        // without a token endpoint, community's HttpArtistAccountChecker fails
        // closed on every follow, so the suite used to switch the gate off. It is
        // started before the three fan services purely so a boot failure here is
        // reported against iam rather than surfacing later as an unexplained 403.
        //
        // SPRING_PROFILES_ACTIVE=e2e is iam's own container profile: it is what
        // parameterises the datasource URL (application.yml hard-codes
        // `jdbc:mysql://localhost:3306/auth_db` with no placeholder) and it carries
        // the 60 s Hikari cold-start tolerances tuned for exactly this shape. It
        // also loads the `migration-dev` band — demo credential seeds this suite
        // does not use. Named rather than left implicit: the cost is a couple of
        // extra migrations, and the alternative (default profile) cannot reach the
        // database at all.
        iam = buildServiceContainer("fan.e2e.iamImage", IAM_JAR, IAM_DOCKERFILE,
                        IAM_PORT, "apps/auth-service/build/libs/")
                .withNetwork(network)
                .withNetworkAliases(IAM_ALIAS)
                .withEnv("SPRING_PROFILES_ACTIVE", "e2e")
                .withEnv("SERVER_PORT", String.valueOf(IAM_PORT))
                // The `iss` this SAS mints. artist-service validates the workload
                // token against exactly this string (INTERNAL_JWT_ISSUER below), and
                // community builds its token URI from the same host:port.
                .withEnv("OIDC_ISSUER_URL", IAM_ISSUER)
                .withEnv("DB_HOST", MYSQL_ALIAS)
                .withEnv("DB_PORT", "3306")
                .withEnv("DB_NAME", IAM_DB_NAME)
                .withEnv("DB_USERNAME", IAM_DB_USERNAME)
                .withEnv("DB_PASSWORD", IAM_DB_PASSWORD)
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("REDIS_PASSWORD", "")
                // NOTE the env name differs from the fan services' KAFKA_BOOTSTRAP.
                .withEnv("KAFKA_BOOTSTRAP_SERVERS", KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                // See IAM_KEYS_DIR — without these the JWT beans fail at boot.
                .withEnv("JWT_PRIVATE_KEY_PATH", "file:/app/keys/private.pem")
                .withEnv("JWT_PUBLIC_KEY_PATH", "file:/app/keys/public.pem")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(IAM_KEYS_DIR.resolve("private.pem").toString()),
                        "/app/keys/private.pem")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(IAM_KEYS_DIR.resolve("public.pem").toString()),
                        "/app/keys/public.pem")
                // Longer than the fan services': Flyway replays iam's full migration
                // history against a cold MySQL before the health endpoint answers.
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forPort(IAM_PORT)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(5)));
        iam.start();

        // ----- artist-service ----------------------------------------------
        artist = buildServiceContainer("fan.e2e.artistImage", ARTIST_JAR, ARTIST_DOCKERFILE)
                .withNetwork(network)
                .withNetworkAliases(ARTIST_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SERVER_PORT", String.valueOf(SERVICE_PORT))
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("POSTGRES_HOST", POSTGRES_ALIAS)
                .withEnv("POSTGRES_PORT", "5432")
                .withEnv("POSTGRES_DB_ARTIST", DB_NAME_ARTIST)
                .withEnv("POSTGRES_USER", DB_USERNAME)
                .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("KAFKA_BOOTSTRAP", KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                .withEnv("OIDC_ISSUER_URL", JwtTestHelper.SAS_ISSUER)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("OIDC_REQUIRED_TENANT_ID", JwtTestHelper.DEFAULT_TENANT_ID)
                // TASK-FAN-INT-005 — the /internal/** chain's decoder, pointed at the
                // REAL iam. Without these two it would inherit the end-user JWKS above
                // (the host-side mock), which never signs a client_credentials token,
                // so every follow-target check would 401 and fail closed. This is the
                // seam that makes one stack carry two independently-signed token
                // planes; artist-service gained the two keys in the same commit
                // (its application.yml previously had them only as @Value defaults,
                // so these env names reached nothing).
                .withEnv("INTERNAL_JWT_JWK_SET_URI", IAM_ISSUER + "/oauth2/jwks")
                .withEnv("INTERNAL_JWT_ISSUER", IAM_ISSUER)
                // Outbox: poll every 500 ms so the e2e Awaitility windows
                // (15-30 s) catch publishes promptly (TASK-FAN-INT-001 § Edge Cases).
                .withEnv("OUTBOX_POLLING_INTERVAL_MS", "500")
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        artist.start();

        // ----- membership-service ------------------------------------------
        // TASK-FAN-INT-006. The fourth service, and the one that let the last
        // switchable gate be deleted.
        //
        // 🔵 Two token planes again (the shape TASK-FAN-INT-005 established): the
        // END-USER plane is the host-side JWKS mock (OIDC_ISSUER_URL/JWT_JWKS_URI)
        // because the e2e mints its own reader/subscriber tokens; the WORKLOAD plane
        // is the real iam, because community calls /internal/membership/access with a
        // client_credentials token that only iam can sign. Unlike artist-service,
        // membership-service already declared INTERNAL_JWT_* in its application.yml —
        // so here the env names reach something without a yml change.
        membership = buildServiceContainer(
                "fan.e2e.membershipImage", MEMBERSHIP_JAR, MEMBERSHIP_DOCKERFILE)
                .withNetwork(network)
                .withNetworkAliases(MEMBERSHIP_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SERVER_PORT", String.valueOf(SERVICE_PORT))
                // 🔴 NOT the `portone` profile. MockPaymentGatewayAdapter is
                // @Profile("!portone"), so leaving the profile at `default` is what
                // makes the product subscribe path usable here at all — see AC-0 in
                // TASK-FAN-INT-006 for why that decided the whole approach.
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("POSTGRES_HOST", POSTGRES_ALIAS)
                .withEnv("POSTGRES_PORT", "5432")
                .withEnv("POSTGRES_DB_MEMBERSHIP", DB_NAME_MEMBERSHIP)
                .withEnv("POSTGRES_USER", DB_USERNAME)
                .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
                .withEnv("KAFKA_BOOTSTRAP", KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                .withEnv("OIDC_ISSUER_URL", JwtTestHelper.SAS_ISSUER)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("OIDC_REQUIRED_TENANT_ID", JwtTestHelper.DEFAULT_TENANT_ID)
                .withEnv("INTERNAL_JWT_JWK_SET_URI", IAM_ISSUER + "/oauth2/jwks")
                .withEnv("INTERNAL_JWT_ISSUER", IAM_ISSUER)
                // application.yml declares this one with NO default, so an unset value
                // is an unresolvable placeholder at binding time rather than a runtime
                // 401 — a boot failure that reads like a container problem. The value
                // is never used: the PortOne adapter only exists under the `portone`
                // profile, which is not active here.
                .withEnv("FAN_PAYMENT_PORTONE_API_SECRET", "e2e-unused")
                // Background sweepers off. They are correct in production and pure
                // noise here — an expiry sweep or auto-renew firing mid-suite would
                // mutate the very rows the assertions read, and the resulting flake
                // would look like a gate defect rather than a scheduler.
                .withEnv("EXPIRY_SWEEP_ENABLED", "false")
                .withEnv("AUTO_RENEW_ENABLED", "false")
                .withEnv("OUTBOX_POLLING_INTERVAL_MS", "500")
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        membership.start();

        // ----- community-service -------------------------------------------
        community = buildServiceContainer("fan.e2e.communityImage", COMMUNITY_JAR, COMMUNITY_DOCKERFILE)
                .withNetwork(network)
                .withNetworkAliases(COMMUNITY_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SERVER_PORT", String.valueOf(SERVICE_PORT))
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("POSTGRES_HOST", POSTGRES_ALIAS)
                .withEnv("POSTGRES_PORT", "5432")
                .withEnv("POSTGRES_DB_COMMUNITY", DB_NAME_COMMUNITY)
                .withEnv("POSTGRES_USER", DB_USERNAME)
                .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("KAFKA_BOOTSTRAP", KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                .withEnv("OIDC_ISSUER_URL", JwtTestHelper.SAS_ISSUER)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("OIDC_REQUIRED_TENANT_ID", JwtTestHelper.DEFAULT_TENANT_ID)
                .withEnv("OUTBOX_POLLING_INTERVAL_MS", "500")
                // 🔴 TASK-FAN-INT-006 — the membership hatch is GONE too (bean, property
                // and the `COMMUNITY_MEMBERSHIP_SERVICE_ENABLED=false` that used to sit
                // here). membership-service is in the stack above and every
                // MEMBERS_ONLY/PREMIUM read in this suite now goes through
                // HttpMembershipChecker for real, both ways.
                //
                // 🔴 Same trap as ARTIST_SERVICE_BASE_URL below, and it was equally
                // invisible: the default is `http://membership-service:8080`, a name
                // that does not resolve on this network. While the gate was switched
                // off nothing ever dialled it, so the wrong value sat here harmlessly.
                // It would now be a fail-closed deny on every gated read — which looks
                // exactly like a working gate, and would have made the "deny" half of
                // AC-1 pass for the wrong reason. That is why AC-1 also asserts the
                // ALLOW half in the same run.
                .withEnv("MEMBERSHIP_SERVICE_BASE_URL",
                        "http://" + MEMBERSHIP_ALIAS + ":" + SERVICE_PORT)
                // TASK-FAN-INT-005 — the artist-side hatch is GONE (bean, property and
                // this env). community now mints a real client_credentials token from
                // the iam container above and every follow in this suite goes through
                // HttpArtistAccountChecker for real.
                .withEnv("IAM_TOKEN_URI", IAM_ISSUER + "/oauth2/token")
                // 🔴 Load-bearing, and it was invisible until the hatch came out: the
                // default is `http://artist-service:8080`, a name that does not resolve
                // on this network. With the gate switched off nothing ever dialled it,
                // so the wrong URL sat here harmlessly. Now it would be a fail-closed
                // deny on every follow.
                .withEnv("ARTIST_SERVICE_BASE_URL", "http://" + ARTIST_ALIAS + ":" + SERVICE_PORT)
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        community.start();

        // ----- gateway-service ---------------------------------------------
        gateway = buildServiceContainer("fan.e2e.gatewayImage", GATEWAY_JAR, GATEWAY_DOCKERFILE)
                .withNetwork(network)
                .withNetworkAliases(GATEWAY_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SERVER_PORT", String.valueOf(SERVICE_PORT))
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("COMMUNITY_SERVICE_URI", "http://" + COMMUNITY_ALIAS + ":" + SERVICE_PORT)
                .withEnv("ARTIST_SERVICE_URI", "http://" + ARTIST_ALIAS + ":" + SERVICE_PORT)
                // TASK-FAN-INT-006 — routes /api/v1/memberships/** → the service's
                // /api/fan/memberships/**. The e2e subscribes through this route rather
                // than calling membership-service directly, so the ACTIVE row that opens
                // the gate is created by the same path a real fan uses.
                .withEnv("MEMBERSHIP_SERVICE_URI",
                        "http://" + MEMBERSHIP_ALIAS + ":" + SERVICE_PORT)
                .withEnv("OIDC_ISSUER_URL", JwtTestHelper.SAS_ISSUER)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("OIDC_REQUIRED_TENANT_ID", JwtTestHelper.DEFAULT_TENANT_ID)
                .withEnv("CORS_ALLOWED_ORIGINS", "http://fan-platform.local,http://localhost:3000")
                // Disable the JWKS startup probe — the JWKS server starts
                // before the gateway, but the probe's 30 s timeout would
                // still slow the boot signal on cold runners.
                .withEnv("GATEWAY_JWKS_STARTUP_PROBE_ENABLED", "false")
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        gateway.start();

        log.info("fan-platform e2e infrastructure ready: gateway={} community={} artist={} iam={} kafka={}",
                gatewayBaseUri(), community.getContainerId(), artist.getContainerId(),
                iam.getContainerId(), kafka.getBootstrapServers());
    }

    @AfterAll
    void stopInfrastructure() throws IOException {
        if (jwks != null) jwks.close();
        if (gateway != null) gateway.stop();
        if (community != null) community.stop();
        if (artist != null) artist.stop();
        if (membership != null) membership.stop();
        if (iam != null) iam.stop();
        if (kafka != null) kafka.stop();
        if (redis != null) redis.stop();
        if (mysql != null) mysql.stop();
        if (postgres != null) postgres.stop();
        if (network != null) network.close();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Gateway URL reachable from the host JVM (HTTP client targets this). */
    protected URI gatewayBaseUri() {
        return URI.create("http://" + gateway.getHost() + ":" + gateway.getMappedPort(SERVICE_PORT));
    }

    /** Kafka bootstrap address reachable from the host JVM. Containers use the network alias instead. */
    protected String kafkaBootstrapForHost() {
        return kafka.getBootstrapServers();
    }

    /**
     * Builds the container for a service.
     *
     * <p>When {@code prebuiltImageProp} is set as a system property (CI path),
     * skips {@link ImageFromDockerfile} entirely and uses the pre-built image
     * name directly. This avoids the Docker 28 BuildKit gRPC hang documented
     * in {@code projects/wms-platform/apps/gateway-service/.../E2EBase.java}.
     *
     * <p>When the property is absent (local dev path), falls back to
     * {@link ImageFromDockerfile} so developers without a pre-built image can
     * still run the suite with a plain {@code ./gradlew :...:e2eTest}.
     */
    private static GenericContainer<?> buildServiceContainer(
            String prebuiltImageProp, Path jar, Path dockerfile) {
        return buildServiceContainer(prebuiltImageProp, jar, dockerfile, SERVICE_PORT, "build/libs/");
    }

    /**
     * TASK-FAN-INT-005 overload — a service whose image is not shaped like fan's.
     *
     * @param port           the port the service listens on. iam's auth-service uses
     *                       8081; the three fan services use {@link #SERVICE_PORT}.
     * @param jarDirInContext where the Dockerfile expects to find the boot jar,
     *                       relative to the build context. fan's Dockerfiles do
     *                       {@code COPY build/libs/<svc>.jar} (context = the service
     *                       dir); iam's does {@code COPY apps/auth-service/build/libs/}
     *                       (context = the PROJECT root). Getting this wrong fails in
     *                       the image build with a bare "file not found", which reads
     *                       like a missing jar rather than a wrong context.
     */
    private static GenericContainer<?> buildServiceContainer(
            String prebuiltImageProp, Path jar, Path dockerfile, int port, String jarDirInContext) {
        String prebuiltImage = System.getProperty(prebuiltImageProp);
        if (prebuiltImage != null && !prebuiltImage.isBlank()) {
            return new GenericContainer<>(DockerImageName.parse(prebuiltImage))
                    .withExposedPorts(port);
        }
        if (jar == null) {
            throw new IllegalStateException(
                    "No pre-built image system property (" + prebuiltImageProp + ") and no boot jar"
                            + " on disk for fallback ImageFromDockerfile path. Either set the system"
                            + " property to a pre-built image (CI path) or run the corresponding"
                            + " bootJar task (local dev path).");
        }
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withDockerfile(dockerfile)
                .withFileFromPath(jarDirInContext + jar.getFileName().toString(), jar)
                .withFileFromPath("Dockerfile", dockerfile);
        return new GenericContainer<>(image).withExposedPorts(port);
    }

    private static Path locateOptionalJar(String relative) {
        Path p = locateFile(relative);
        return java.nio.file.Files.exists(p) ? p : null;
    }

    /**
     * Walks up from the working dir to find the fan-platform project root
     * containing the relative path. Works in both monorepo layout
     * (cwd deep under {@code projects/fan-platform/...}) and in the future
     * extracted-standalone layout (cwd deep under the extracted repo root).
     */
    private static Path locateFile(String relative) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path cur = cwd;
        // Try a couple of project-relative roots: the fan-platform project
        // root and any of its ancestors. Eight levels is enough for both the
        // monorepo (5-6 deep) and standalone (1-2 deep) layouts.
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(relative);
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.normalize();
            }
            // Try the fan-platform project sub-path explicitly so resolution
            // works when Gradle invokes the e2e task from the monorepo root.
            Path projectScoped = cur.resolve("projects/fan-platform").resolve(relative);
            if (java.nio.file.Files.exists(projectScoped)) {
                return projectScoped.normalize();
            }
            cur = cur.getParent();
        }
        // Fall back to the naive resolve — the subsequent existence check or
        // ImageFromDockerfile call will report a clear error.
        return cwd.resolve(relative).normalize();
    }

    /**
     * Dumps each service container's stdout+stderr to {@code System.err} when an
     * e2e test fails, so the CI log carries the actual stack trace that produced
     * a 4xx/5xx response. Without this, GitHub Actions only shows the JUnit
     * assertion ("expected: 201 / but was: 500") with no service-side context.
     *
     * <p>Triggered automatically by JUnit 5 because every concrete e2e test class
     * extends this base class and inherits the {@code @ExtendWith} declaration.
     */
    public static class ServiceContainerLogDumper implements TestWatcher {

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            Object instance = context.getTestInstance().orElse(null);
            if (!(instance instanceof FanPlatformE2ETestBase suite)) {
                return;
            }
            System.err.println("================================================================");
            System.err.println("[e2e-fail] " + context.getDisplayName());
            System.err.println("[e2e-fail] dumping service container logs for diagnosis");
            System.err.println("================================================================");
            dumpContainerLogs("gateway", suite.gateway);
            dumpContainerLogs("community", suite.community);
            dumpContainerLogs("artist", suite.artist);
            dumpContainerLogs("membership", suite.membership);
            // TASK-FAN-INT-005 — iam last but not least: a follow that 403s now has
            // three candidate causes (token not minted / token minted without
            // artist.read / artist-service rejected it), and only this log separates
            // the first from the other two.
            dumpContainerLogs("iam", suite.iam);
        }

        private static void dumpContainerLogs(String label, GenericContainer<?> container) {
            if (container == null || !container.isRunning()) {
                System.err.println("[e2e-fail] " + label + " container: <not running>");
                return;
            }
            try {
                String logs = container.getLogs();
                System.err.println("---- " + label + " container logs (" + container.getContainerId() + ") ----");
                System.err.println(logs);
                System.err.println("---- end " + label + " logs ----");
            } catch (Exception e) {
                System.err.println("[e2e-fail] " + label + " getLogs() failed: " + e.getMessage());
            }
        }
    }
}
