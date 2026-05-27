package com.azul.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AzulClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AzulConfig cfg;
    private final OidcAuth auth;
    private final HttpClient http;

    public AzulClient(AzulConfig cfg) {
        this.cfg = cfg;
        // Create the OIDC auth helper (token fetching is deferred until first request)
        this.auth = new OidcAuth(cfg);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Returns true if metadata exists for the given sha256.
     * Mirrors azul-client's BinariesMeta.check_meta().
     */
    public boolean checkMeta(String sha256) throws Exception {
        // Send a HEAD request — avoids downloading the body when we only need to check
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg.azulUrl + "/api/v0/binaries/" + sha256))
                .header("Authorization", "Bearer " + auth.getAccessToken())
                .timeout(Duration.ofSeconds((long) cfg.maxTimeout))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        // Treat 404 as "not found" and 200/206 as "exists"; anything else is error
        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        if (resp.statusCode() == 404)
            return false;
        if (resp.statusCode() != 200 && resp.statusCode() != 206) {
            throw new Exception("Unexpected response checking meta: " + resp.statusCode());
        }
        return true;
    }

    /**
     * Returns the metadata JSON for a sha256 hash.
     * Mirrors azul-client's BinariesMeta.get_meta().
     */
    public JsonNode getBinaryMeta(String sha256) throws Exception {
        // GET to the binaries endpoint with a Bearer token
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg.azulUrl + "/api/v0/binaries/" + sha256))
                .header("Authorization", "Bearer " + auth.getAccessToken())
                .timeout(Duration.ofSeconds((long) cfg.maxTimeout))
                .GET()
                .build();

        // Check for a successful response
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) {
            throw new BinaryNotFoundException(sha256);
        }
        if (resp.statusCode() != 200 && resp.statusCode() != 206) {
            throw new Exception("Bad response from Azul: " + resp.statusCode() + " - " + resp.body());
        }

        // Parse the JSON and return the nested "data" object
        JsonNode root = MAPPER.readTree(resp.body());
        JsonNode data = root.get("data");
        if (data == null)
            throw new Exception("Response has no 'data' key");
        return data;
    }

    /**
     * Find binaries matching a raw term query string.
     * Mirrors azul-client's BinariesMeta.find().
     */
    public JsonNode find(String term) throws Exception {
        return find(term, null, null);
    }

    /**
     * Find binaries matching a raw term query string, with optional result limits.
     * Mirrors azul-client's BinariesMeta.find() with max_entities / count_entities.
     */
    public JsonNode find(String term, Integer maxEntities, Boolean countEntities) throws Exception {
        return baseFindRequest(term, maxEntities, countEntities, null);
    }

    /**
     * Find binaries using a structured FindOptions query builder.
     * Mirrors azul-client's BinariesMeta.find_simple().
     */
    public JsonNode findSimple(FindOptions options) throws Exception {
        return findSimple(options, null, null);
    }

    /**
     * Find binaries using a structured FindOptions query builder, with optional
     * result limits.
     * Mirrors azul-client's BinariesMeta.find_simple() with max_entities /
     * count_entities.
     */
    public JsonNode findSimple(FindOptions options, Integer maxEntities, Boolean countEntities) throws Exception {
        return baseFindRequest(options.toQuery(), maxEntities, countEntities, null);
    }

    /**
     * Check which hashes from the provided list exist in Azul and return their
     * summary.
     * Mirrors azul-client's BinariesMeta.find_hashes().
     */
    public JsonNode findHashes(List<String> hashes) throws Exception {
        return baseFindRequest(null, null, null, hashes);
    }

    private JsonNode baseFindRequest(String term, Integer maxEntities, Boolean countEntities, List<String> hashes)
            throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        if (term != null && !term.isEmpty())
            params.put("term", term);
        if (maxEntities != null)
            params.put("max_entities", maxEntities);
        if (countEntities != null)
            params.put("count_entities", countEntities);

        String body = MAPPER.writeValueAsString(
                Map.of("hashes", hashes != null ? hashes : List.of()));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg.azulUrl + "/api/v0/binaries" + buildQueryString(params)))
                .header("Authorization", "Bearer " + auth.getAccessToken())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds((long) cfg.maxTimeout))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200 && resp.statusCode() != 206) {
            throw new Exception("Bad response from Azul: " + resp.statusCode() + " - " + resp.body());
        }

        JsonNode root = MAPPER.readTree(resp.body());
        JsonNode data = root.get("data");
        if (data == null)
            throw new Exception("Response has no 'data' key");
        return data;
    }

    private static String buildQueryString(Map<String, Object> params) {
        if (params.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder("?");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (sb.length() > 1)
                sb.append("&");
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
