package com.azul.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class OidcAuth {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AzulConfig cfg;
    private final HttpClient http;
    private Map<String, Object> oidcInfo;

    public OidcAuth(AzulConfig cfg) {
        this.cfg = cfg;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds((long) cfg.oidcTimeout))
                .build();
    }

    public String getAccessToken() throws Exception {
        Map<String, Object> token = getToken();
        Object accessToken = token.get("access_token");
        if (accessToken == null && !"none".equals(cfg.authType)) {
            throw new Exception("Token required but was not found");
        }
        return accessToken != null ? accessToken.toString() : "none";
    }

    /** Returns an HttpClient pre-configured with a current Bearer token. */
    public HttpClient getAuthorizedClient() throws Exception {
        // Java's built-in HttpClient doesn't support mutable default headers,
        // so callers add the Authorization header per-request via getAccessToken().
        return http;
    }

    private Map<String, Object> getToken() throws Exception {
        // If auth type is none, return empty map
        if ("none".equals(cfg.authType))
            return new HashMap<>();

        long now = System.currentTimeMillis() / 1000;

        if (cfg.authToken != null && !cfg.authToken.isEmpty()) {
            // Saved token exists — attempt a refresh if it is older than 60 seconds
            if (now > cfg.authTokenTime + 60) {
                Map<String, Object> refreshed = viaRefresh(cfg.authToken);
                if (refreshed != null) {
                    persistToken(refreshed);
                    return refreshed;
                }
                System.err.println("Warning - Refresh token likely has expired.");
            } else {
                // Token is still fresh — return it directly without a network call
                return cfg.authToken;
            }
        }

        // No usable saved token — perform a full login and persist the result
        Map<String, Object> token = getTokenNonRefresh();
        persistToken(token);
        return token;
    }

    private void persistToken(Map<String, Object> token) throws Exception {
        cfg.authToken = token;
        cfg.authTokenTime = System.currentTimeMillis() / 1000;
        cfg.save();
    }

    private Map<String, Object> getTokenNonRefresh() throws Exception {
        if ("callback".equals(cfg.authType)) {
            return viaCodeCallback();
        } else if ("service".equals(cfg.authType)) {
            return viaServiceToken();
        } else {
            throw new UnsupportedOperationException("Unknown auth_type: " + cfg.authType);
        }
    }

    private Map<String, Object> viaRefresh(Map<String, Object> token) throws Exception {
        Object refreshToken = token.get("refresh_token");
        if (refreshToken == null)
            return null;

        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("client_id", cfg.authClientId);
        params.put("refresh_token", refreshToken.toString());
        params.put("scope", cfg.authScopes);

        HttpResponse<String> resp = postForm(getTokenEndpoint(), params);
        if (resp.statusCode() >= 400 && resp.statusCode() < 500)
            return null;
        return parseJsonResponse(resp, "via refresh");
    }

    /**
     * Retrieves a token via the PKCE Authorization Code flow.
     *
     * @return the token response map (access_token, refresh_token, etc.)
     * @throws Exception if the callback returns no code or the token exchange fails
     */
    private Map<String, Object> viaCodeCallback() throws Exception {
        // Prove the requestor of token is same as receiver with code challenge and verify
        byte[] randomBytes = new byte[40];
        new SecureRandom().nextBytes(randomBytes);
        String codeVerifier = Base64.getUrlEncoder().encodeToString(randomBytes)
                .replaceAll("[^a-zA-Z0-9]", "");

        // Generate code_challenge as BASE64URL(SHA256(codeVerifier))
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        // Build the authorization URL and prompt the user to open it
        int port = 8080;
        String callbackUrl = "http://localhost:" + port + "/client/callback";
        String state = String.valueOf(ThreadLocalRandom.current().nextInt(1000000, 9999999));

        Map<String, String> authParams = new LinkedHashMap<>();
        authParams.put("response_type", "code");
        authParams.put("client_id", cfg.authClientId);
        authParams.put("redirect_uri", callbackUrl);
        authParams.put("state", state);
        authParams.put("scope", cfg.authScopes);
        authParams.put("code_challenge", codeChallenge);
        authParams.put("code_challenge_method", "S256");

        String authUrl = getAuthorizationEndpoint() + "?" + buildQueryString(authParams);
        System.err.println("Please navigate to the following URL to continue authentication:\n" + authUrl);

        // Block until the browser redirects to the local callback server with the code
        String code = CallbackServer.receiveCode(state, "/client/callback", "localhost", port);
        if (code == null)
            throw new Exception("No token retrieval code was returned");

        // Exchange the authorization code + PKCE verifier for tokens
        Map<String, String> tokenParams = new LinkedHashMap<>();
        tokenParams.put("grant_type", "authorization_code");
        tokenParams.put("code", code);
        tokenParams.put("client_id", cfg.authClientId);
        tokenParams.put("state", state);
        tokenParams.put("scope", cfg.authScopes);
        tokenParams.put("redirect_uri", callbackUrl);
        tokenParams.put("code_verifier", codeVerifier);

        return parseJsonResponse(postForm(getTokenEndpoint(), tokenParams), "via code callback");
    }

    /**
     * Retrieves a token via the client_credentials grant (service account flow).
     *
     * @return the token response map (access_token, etc.)
     * @throws Exception if the token endpoint returns a non-200 response
     */
    private Map<String, Object> viaServiceToken() throws Exception {
        // Prefer the secret from the environment; fall back to the config file
        String secret = System.getenv("AZUL_CLIENT_SECRET");
        if (secret == null)
            secret = cfg.authClientSecret;

        // POST a client_credentials grant to the token endpoint
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "token");
        params.put("client_id", cfg.authClientId);
        params.put("client_secret", secret);
        params.put("grant_type", "client_credentials");
        params.put("scope", cfg.authScopes);

        return parseJsonResponse(postForm(getTokenEndpoint(), params), "via service token");
    }

    private String getAuthorizationEndpoint() throws Exception {
        return (String) getOidcInfo().get("authorization_endpoint");
    }

    private String getTokenEndpoint() throws Exception {
        return (String) getOidcInfo().get("token_endpoint");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOidcInfo() throws Exception {
        // Return cached discovery document if already fetched this session
        if (oidcInfo != null)
            return oidcInfo;

        // Fetch the OIDC well-known discovery document and cache it
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg.oidcUrl))
                .timeout(Duration.ofSeconds((long) cfg.oidcTimeout))
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new Exception("Failed to fetch OIDC well-known: " + resp.statusCode() + " - " + resp.body());
        }
        oidcInfo = MAPPER.readValue(resp.body(), Map.class);
        return oidcInfo;
    }

    private HttpResponse<String> postForm(String url, Map<String, String> params) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds((long) cfg.oidcTimeout))
                .POST(HttpRequest.BodyPublishers.ofString(buildFormBody(params)))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(HttpResponse<String> resp, String context) throws Exception {
        if (resp.statusCode() != 200) {
            throw new Exception("Failed " + context + " - Code: " + resp.statusCode() + " - " + resp.body());
        }
        return MAPPER.readValue(resp.body(), Map.class);
    }

    private static String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0)
                sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String buildQueryString(Map<String, String> params) {
        return buildFormBody(params); // same encoding
    }
}
