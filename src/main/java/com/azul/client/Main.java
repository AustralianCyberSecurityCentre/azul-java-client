package com.azul.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: azul-client <sha256>");
            System.exit(1);
        }

        // Load configuration from ~/.azul.ini (creates a default if absent)
        String sha256 = args[0];
        AzulConfig cfg = AzulConfig.load();
        AzulClient client = new AzulClient(cfg);

        JsonNode meta = client.getBinaryMeta(sha256);
        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(meta));
    }
}
