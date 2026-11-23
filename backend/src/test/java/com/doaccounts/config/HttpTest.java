package com.doaccounts.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 對方收到請求卻一直不回應時，要在讀取時限內放棄，而不是永遠卡住。 */
class HttpTest {

    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer server;

    @AfterEach
    void stop() {
        release.countDown();
        if (server != null) server.stop(0);
    }

    @Test
    void givesUpWhenServerNeverResponds() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            try {
                release.await(30, TimeUnit.SECONDS);   // 模擬對方不回應
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";

        long start = System.nanoTime();
        var client = Http.client(Duration.ofSeconds(2), Duration.ofMillis(500));
        assertThrows(RuntimeException.class, () -> client.get().uri(url).retrieve().body(String.class));
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 5_000, "應在時限後放棄，實際等了 " + ms + " ms");
    }

    @Test
    void defaultClientHasTimeouts() {
        assertTrue(Http.READ_TIMEOUT.compareTo(Duration.ofSeconds(30)) < 0, "讀取時限要小於前端等待時間");
        assertTrue(Http.CONNECT_TIMEOUT.compareTo(Duration.ofSeconds(10)) <= 0);
    }
}
