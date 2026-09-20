package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * T07 acceptance: the real OkHttp client speaks to a local synthetic HTTP service and maps the wire
 * result exactly (status, Retry-After, body). The product itself refuses plain HTTP; the test injects
 * a local address through the test-only factory.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleOkHttpWireTest {
    private static final class Wire {
        int status;
        long retryAfterMs = -2;
        String body = "";
        boolean transportFailure;
    }

    /** Serves exactly one canned response and records the request line. */
    private static ServerSocket startServer(String statusLine, String headers, String body, String[] requestLine) throws Exception {
        ServerSocket server = new ServerSocket(0);

        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                requestLine[0] = reader.readLine();

                String line;

                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    // Drain headers; the test only needs the request line.
                }

                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                String response = statusLine + "\r\n" + headers
                        + "Content-Length: " + payload.length + "\r\nConnection: close\r\n\r\n";
                OutputStream output = socket.getOutputStream();
                output.write(response.getBytes(StandardCharsets.UTF_8));
                output.write(payload);
                output.flush();
            } catch (Exception ignored) {
                // The test asserts on what the client reports.
            }
        });
        thread.setDaemon(true);
        thread.start();

        return server;
    }

    private static Wire send(ServerSocket server, String path) throws Exception {
        BlockingQueue<Wire> result = new ArrayBlockingQueue<>(1);
        SubtitleTranslationRequest request = SubtitleTranslationRequest.unsafeCreateForTest(
                "http://127.0.0.1:" + server.getLocalPort() + path, "Bearer sk-test", "{\"items\":[]}");

        new SubtitleOkHttpTranslationClient().send(request, "instruction", new SubtitleTranslationClient.ResponseHandler() {
            @Override
            public void onResponse(int status, long retryAfterMs, boolean truncated, String body) {
                Wire wire = new Wire();
                wire.status = status;
                wire.retryAfterMs = retryAfterMs;
                wire.body = body;
                result.offer(wire);
            }

            @Override
            public void onTransportFailure() {
                Wire wire = new Wire();
                wire.transportFailure = true;
                result.offer(wire);
            }
        });

        Wire wire = result.poll(20, TimeUnit.SECONDS);
        assertNotNull("the client must report an outcome", wire);

        return wire;
    }

    @Test
    public void aRateLimitedResponseCarriesItsStatusAndRetryAfter() throws Exception {
        String[] requestLine = new String[1];
        ServerSocket server = startServer("HTTP/1.1 429 Too Many Requests",
                "Retry-After: 5\r\nContent-Type: application/json\r\n", "", requestLine);

        try {
            Wire wire = send(server, "/chat/completions");

            assertEquals(429, wire.status);
            assertEquals(5_000, wire.retryAfterMs);
            assertTrue("the request is a POST to the prepared path", requestLine[0].startsWith("POST /chat/completions"));
            assertEquals("", wire.body);
        } finally {
            server.close();
        }
    }

    @Test
    public void aSuccessfulJsonResponseIsDeliveredVerbatim() throws Exception {
        String[] requestLine = new String[1];
        String body = "{\"items\":[{\"id\":\"a\",\"translation\":\"1\"}]}";
        ServerSocket server = startServer("HTTP/1.1 200 OK", "Content-Type: application/json\r\n", body, requestLine);

        try {
            Wire wire = send(server, "/chat/completions");

            assertEquals(200, wire.status);
            assertEquals(body, wire.body);
        } finally {
            server.close();
        }
    }

    @Test
    public void anUnreachableServiceIsReportedAsATransportFailure() throws Exception {
        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();
        server.close(); // nothing listens any more

        BlockingQueue<Wire> result = new ArrayBlockingQueue<>(1);
        SubtitleTranslationRequest request = SubtitleTranslationRequest.unsafeCreateForTest(
                "http://127.0.0.1:" + port + "/chat/completions", "Bearer sk-test", "{}");

        new SubtitleOkHttpTranslationClient().send(request, "instruction", new SubtitleTranslationClient.ResponseHandler() {
            @Override
            public void onResponse(int status, long retryAfterMs, boolean truncated, String body) {
                result.offer(new Wire());
            }

            @Override
            public void onTransportFailure() {
                Wire wire = new Wire();
                wire.transportFailure = true;
                result.offer(wire);
            }
        });

        Wire wire = result.poll(20, TimeUnit.SECONDS);
        assertNotNull(wire);
        assertTrue("a refused connection must be a transport failure", wire.transportFailure);
    }
}
