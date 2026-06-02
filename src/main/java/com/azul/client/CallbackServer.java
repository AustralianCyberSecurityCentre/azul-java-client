package com.azul.client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Single-request HTTP server that captures the OIDC authorization code
 * from the browser redirect after the user completes login.
 */
class CallbackServer {

    static String receiveCode(String expectedState, String path, String hostname, int port) throws Exception {
        System.err.printf("Waiting for OAuth callback at http://%s:%d%s%n", hostname, port, path);

        // Open a server socket and block until the browser connects
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            try (Socket socket = serverSocket.accept()) {
                // Read the first line — that's all we need from the HTTP request
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String requestLine = reader.readLine(); // "GET /client/callback?code=X&state=Y HTTP/1.1"

                String code = null;
                String errorMsg = null;

                try {
                    if (requestLine == null)
                        throw new Exception("Empty HTTP request");
                    String[] parts = requestLine.split(" ");
                    if (parts.length < 2)
                        throw new Exception("Malformed HTTP request line");

                    // Extract and parse the query string from the request path
                    String fullPath = parts[1];
                    // Extract everything after '?' as the query string, or empty string if no '?' present
                    String query = fullPath.contains("?") ? fullPath.substring(fullPath.indexOf('?') + 1) : "";
                    Map<String, String> params = parseQueryString(query);

                    // Verify the state parameter to prevent CSRF, then extract the code
                    if (!expectedState.equals(params.get("state"))) {
                        throw new Exception("Returned state does not match");
                    }
                    code = params.get("code");
                    if (code == null)
                        throw new Exception("No code in callback");
                } catch (Exception e) {
                    errorMsg = e.getMessage();
                }

                // Send a success or failure page so the user knows they can close the tab
                sendHtmlResponse(socket, code != null ? 200 : 400,
                        code != null
                                ? "<p>Success</p><p>You may now close this tab.</p>"
                                : "<p>Failure</p><p>" + errorMsg + "</p>");

                return code;
            }
        }
    }

    private static void sendHtmlResponse(Socket socket, int status, String bodyContent) throws IOException {
        String body = "<html><head><title>Azul Client Auth</title></head><body>" + bodyContent + "</body></html>";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String statusText = status == 200 ? "OK" : "Bad Request";

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        writer.print("HTTP/1.1 " + status + " " + statusText + "\r\n");
        writer.print("Content-Type: text/html; charset=utf-8\r\n");
        writer.print("Content-Length: " + bodyBytes.length + "\r\n");
        writer.print("Connection: close\r\n");
        writer.print("\r\n");
        writer.print(body);
        writer.flush();
    }

    private static Map<String, String> parseQueryString(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isBlank())
            return result;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                result.put(
                        URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return result;
    }
}
