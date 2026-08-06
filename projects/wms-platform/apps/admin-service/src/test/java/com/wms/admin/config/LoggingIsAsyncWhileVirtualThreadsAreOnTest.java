package com.wms.admin.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Regression guard for {@code TASK-BE-579}.
 *
 * <p>This service runs with {@code spring.threads.virtual.enabled: true}. On JDK
 * 21 a virtual thread that blocks inside a {@code synchronized} region cannot
 * unmount, so it parks <em>on its carrier</em>. The Kafka client logs from
 * inside its own synchronized sections, and a synchronous {@code
 * ConsoleAppender} makes that pinned thread contend for the appender's
 * {@code ReentrantLock} while stdout is a pipe to the container runtime. The
 * virtual-thread scheduler's parallelism is {@code availableProcessors} (4 on
 * the demo host), so four such threads consume every carrier — and the thread
 * that actually holds the appender lock can no longer be mounted to release it.
 * The service then accepts connections (Tomcat's Acceptor/Poller are platform
 * threads) and serves nothing at all, while Kafka's platform threads keep
 * heart-beating so the logs and {@code docker ps} both look healthy.
 *
 * <p>Measured, not theorised: a JSON thread dump of a wedged {@code
 * outbound-service} showed 4 {@code kafka-N} virtual threads at {@code
 * VirtualThread.parkOnCarrierThread} under {@code
 * OutputStreamAppender.writeBytes}, and 18 {@code tomcat-handler-N} virtual
 * threads with <em>empty stacks</em> — created, queued, never mounted.
 *
 * <p>So: while this service enables virtual threads, every {@code <root>}
 * appender must be an {@code AsyncAppender}. The appending thread then only
 * offers to an in-memory queue and never blocks on the pipe write.
 * {@code neverBlock=true} is part of the contract — without it a full queue
 * reintroduces exactly the block this guards against.
 */
class LoggingIsAsyncWhileVirtualThreadsAreOnTest {

    @Test
    @DisplayName("every root appender is a non-blocking AsyncAppender (while virtual threads are enabled)")
    void rootAppendersAreAsyncAndNeverBlock() throws Exception {
        assertThat(virtualThreadsEnabled())
                .as("if virtual threads are ever turned off for this service, this guard's premise "
                        + "is gone and the test should be deleted rather than weakened")
                .isTrue();

        Document doc = parse("logback-spring.xml");
        Map<String, Element> appenders = new HashMap<>();
        NodeList all = doc.getElementsByTagName("appender");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            appenders.put(e.getAttribute("name"), e);
        }

        List<String> rootRefs = new ArrayList<>();
        NodeList roots = doc.getElementsByTagName("root");
        for (int i = 0; i < roots.getLength(); i++) {
            NodeList refs = ((Element) roots.item(i)).getElementsByTagName("appender-ref");
            for (int j = 0; j < refs.getLength(); j++) {
                rootRefs.add(((Element) refs.item(j)).getAttribute("ref"));
            }
        }

        assertThat(rootRefs)
                .as("logback-spring.xml declares no <root> appender-ref — the guard would "
                        + "vacuously pass, which is the failure mode it exists to avoid")
                .isNotEmpty();

        for (String ref : rootRefs) {
            Element appender = appenders.get(ref);
            assertThat(appender).as("root references unknown appender '%s'", ref).isNotNull();
            assertThat(appender.getAttribute("class"))
                    .as("root appender '%s' must be async — a synchronous appender can wedge "
                            + "the whole virtual-thread scheduler (TASK-BE-579)", ref)
                    .isEqualTo("ch.qos.logback.classic.AsyncAppender");
            assertThat(childText(appender, "neverBlock"))
                    .as("async appender '%s' must set neverBlock=true", ref)
                    .isEqualTo("true");
        }
    }

    private static boolean virtualThreadsEnabled() throws Exception {
        try (InputStream in = LoggingIsAsyncWhileVirtualThreadsAreOnTest.class.getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(in).as("application.yml must be on the test classpath").isNotNull();
            String yml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            // `threads: virtual: enabled: true` — matched loosely on purpose; the
            // exact indentation is not the property under test.
            return yml.replaceAll("\\s+", " ").contains("threads: virtual: enabled: true");
        }
    }

    private static String childText(Element parent, String tag) {
        NodeList n = parent.getElementsByTagName(tag);
        return n.getLength() == 0 ? null : n.item(0).getTextContent().trim();
    }

    private Document parse(String resource) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("%s must be on the test classpath", resource).isNotNull();
            Document d = f.newDocumentBuilder().parse(in);
            d.getDocumentElement().normalize();
            return d;
        }
    }
}
