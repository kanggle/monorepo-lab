package com.example.search;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton nori-enabled Elasticsearch container for search-service integration tests
 * (TASK-MONO-319).
 *
 * <p>The stock {@code docker.elastic.co/elasticsearch/elasticsearch} image lacks the Korean
 * {@code analysis-nori} plugin, so {@code IndexInitializer}'s nori-analyzer index creation
 * fails on startup with {@code "Unknown tokenizer type [nori_tokenizer]"} → every
 * {@code @SpringBootTest} aborts at context load. This builds a nori image (mirroring
 * {@code infra/elasticsearch/Dockerfile}: {@code FROM elasticsearch:8.15.0} +
 * {@code elasticsearch-plugin install --batch analysis-nori}) and exposes ONE shared,
 * started container for all four search ITs.
 *
 * <p><b>Singleton pattern.</b> Several IT classes use this container; starting it once here
 * (never via {@code @Container}/{@code @Testcontainers} managed lifecycle) means the four ITs
 * share a single Elasticsearch instead of each booting its own (faster CI, and no
 * managed-teardown-vs-cached-context hazard). Ryuk reaps it at JVM exit.
 */
public final class NoriElasticsearchContainer {

    /** Fixed tag so the (slow) image build is cached + reused across runs and ITs. */
    private static final String NORI_IMAGE_TAG = "ecommerce-search-it-es-nori:8.15.0";

    public static final ElasticsearchContainer INSTANCE = build();

    static {
        INSTANCE.start();
    }

    private static ElasticsearchContainer build() {
        // ImageFromDockerfile (a LazyFuture) overrides Future#get() to throw no checked
        // exceptions — it builds the image on first get() and returns the resolved tag.
        String builtImage = new ImageFromDockerfile(NORI_IMAGE_TAG, false)
                .withDockerfileFromBuilder(b -> b
                        .from("elasticsearch:8.15.0")
                        .run("elasticsearch-plugin install --batch analysis-nori")
                        .build())
                .get();
        return new ElasticsearchContainer(
                DockerImageName.parse(builtImage)
                        .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
    }

    public static String httpUri() {
        return "http://" + INSTANCE.getHttpHostAddress();
    }

    private NoriElasticsearchContainer() {
    }
}
