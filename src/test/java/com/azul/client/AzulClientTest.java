package com.azul.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

// WireMockTest Starts a real local HTTP server before each test and tears it down after. 
@WireMockTest
class AzulClientTest {

    // Create a client with auth disabled, pointing both API and OIDC at WireMock
    private AzulClient makeClient(WireMockRuntimeInfo wm) {
        AzulConfig cfg = new AzulConfig() {
            @Override
            public void save() throws IOException {
                /* Make the function do nothing during testing */ }
        };
        cfg.azulUrl = wm.getHttpBaseUrl();
        cfg.oidcUrl = wm.getHttpBaseUrl();
        cfg.authType = "none";
        return new AzulClient(cfg);
    }

    // -------------------------------------------------------------------------
    // checkMeta — mirrors Python BinariesMeta.check_meta()
    // -------------------------------------------------------------------------

    @Test
    void testCheckMeta_found(WireMockRuntimeInfo wm) throws Exception {
        stubFor(head(urlEqualTo("/api/v0/binaries/abc123"))
                .willReturn(aResponse().withStatus(200)));

        assertTrue(makeClient(wm).checkMeta("abc123"));
    }

    @Test
    void testCheckMeta_notFound(WireMockRuntimeInfo wm) throws Exception {
        stubFor(head(urlEqualTo("/api/v0/binaries/abc123"))
                .willReturn(aResponse().withStatus(404)));

        assertFalse(makeClient(wm).checkMeta("abc123"));
    }

    @Test
    void testCheckMeta_partialContent(WireMockRuntimeInfo wm) throws Exception {
        stubFor(head(urlEqualTo("/api/v0/binaries/abc123"))
                .willReturn(aResponse().withStatus(206)));

        assertTrue(makeClient(wm).checkMeta("abc123"));

    }

    @Test
    void testCheckMeta_unexpectedStatus(WireMockRuntimeInfo wm) throws Exception {
        stubFor(head(urlEqualTo("/api/v0/binaries/abc123"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(Exception.class, () -> makeClient(wm).checkMeta("abc123"));
    }

    // -------------------------------------------------------------------------
    // getBinaryMeta — mirrors Python BinariesMeta.get_meta() behaviour
    // -------------------------------------------------------------------------

    @Test
    void testGetBinaryMeta(WireMockRuntimeInfo wm) throws Exception {
        stubFor(get(urlEqualTo("/api/v0/binaries/abc123"))
                .willReturn(okJson("{\"data\":{\"sha256\":\"abc123\",\"security\":\"OFFICIAL\"}}")));

        JsonNode data = makeClient(wm).getBinaryMeta("abc123");

        assertEquals("abc123", data.get("sha256").asText());
        assertEquals("OFFICIAL", data.get("security").asText());
    }

    @Test
    void testGetBinaryMeta_noDataKey(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/api/v0/binaries/abc123"))
                .willReturn(okJson("{\"result\":\"empty\"}")));

        Exception ex = assertThrows(Exception.class,
                () -> makeClient(wm).getBinaryMeta("abc123"));
        assertEquals(ex.getMessage(), new String("Response has no 'data' key"));
    }

    @Test
    void testGetBinaryMeta_errorStatus(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/api/v0/binaries/abc123"))
                .willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThrows(Exception.class, () -> makeClient(wm).getBinaryMeta("abc123"));
    }

    // -------------------------------------------------------------------------
    // find — mirrors Python BinariesMeta.find()
    // -------------------------------------------------------------------------

    @Test
    void testFind_returnsData(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlPathEqualTo("/api/v0/binaries"))
                .willReturn(okJson("{\"data\":{\"entities\":[],\"total\":0}}")));

        JsonNode data = makeClient(wm).find("file_format:\"image/png\"");

        assertEquals(0, data.get("total").asInt());
        assertNotNull(data.get("entities"));
    }

    @Test
    void testFind_sendsTermAsQueryParam(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlPathEqualTo("/api/v0/binaries"))
                .withQueryParam("term", equalTo("size:>1000"))
                .willReturn(okJson("{\"data\":{\"entities\":[],\"total\":0}}")));

        JsonNode data = makeClient(wm).find("size:>1000");
        assertNotNull(data);
    }

}
