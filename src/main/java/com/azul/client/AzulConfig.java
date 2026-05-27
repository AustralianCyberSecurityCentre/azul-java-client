package com.azul.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class AzulConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String azulUrl = "http://localhost";
    public String oidcUrl = "http://keycloak/.well-known/openid-configuration";
    public String authType = "callback";
    public String authScopes = "";
    public String authClientId = "azul-web";
    public String authClientSecret = "";
    public boolean azulVerifySsl = true;
    public Map<String, Object> authToken = new HashMap<>();
    public long authTokenTime = 0;
    public double maxTimeout = 300.0;
    public double oidcTimeout = 10.0;

    private Path configPath;
    private String section;

    public static AzulConfig load() throws IOException {
        return load("default");
    }

    @SuppressWarnings("unchecked")
    public static AzulConfig load(String section) throws IOException {
        // Get the config file path (~/.azul.ini)
        Path configPath = Path.of(System.getProperty("user.home"), ".azul.ini");

        AzulConfig cfg = new AzulConfig();
        cfg.configPath = configPath;
        cfg.section = section;

        // If no config exists yet, write a default file and return early
        if (!Files.exists(configPath)) {
            System.err.println("No config found, generating default at " + configPath);
            System.err.println("You will likely need to edit this config.");
            cfg.save();
            return cfg;
        }

        // Parse the requested INI section into a key-value map
        System.err.println("Loading config [" + section + "] from " + configPath);
        Map<String, String> values = parseIniCommons(configPath, section);

        // Build the cfg object, using defaults for any keys that are absent
        if (values.containsKey("azul_url"))
            // Replace any trailing slashes
            cfg.azulUrl = values.get("azul_url").replaceAll("/+$", "");
        if (values.containsKey("oidc_url"))
            cfg.oidcUrl = values.get("oidc_url");
        if (values.containsKey("auth_type"))
            cfg.authType = values.get("auth_type");
        if (values.containsKey("auth_scopes"))
            cfg.authScopes = values.get("auth_scopes");
        if (values.containsKey("auth_client_id"))
            cfg.authClientId = values.get("auth_client_id");
        if (values.containsKey("auth_client_secret"))
            cfg.authClientSecret = values.get("auth_client_secret");
        if (values.containsKey("azul_verify_ssl"))
            cfg.azulVerifySsl = Boolean.parseBoolean(values.get("azul_verify_ssl"));
        if (values.containsKey("max_timeout"))
            cfg.maxTimeout = Double.parseDouble(values.get("max_timeout"));
        if (values.containsKey("oidc_timeout"))
            cfg.oidcTimeout = Double.parseDouble(values.get("oidc_timeout"));
        if (values.containsKey("auth_token_time"))
            cfg.authTokenTime = Long.parseLong(values.get("auth_token_time"));

        // Deserialize the cached token JSON (empty/missing means no saved token)
        String tokenJson = values.get("auth_token");
        if (tokenJson != null && !tokenJson.isBlank() && !tokenJson.equals("{}")) {
            cfg.authToken = MAPPER.readValue(tokenJson, Map.class);
        }

        System.err.println("Using Azul API at " + cfg.azulUrl + "\n");
        return cfg;
    }

    public void save() throws IOException {
        INIConfiguration ini = new INIConfiguration();
        // Read existing config if present
        if (Files.exists(configPath)) {
            try (FileReader reader = new FileReader(configPath.toFile())) {
                ini.read(reader);
            } catch (ConfigurationException e) {
                throw new IOException("Failed to parse INI file: " + configPath, e);
            }
        }

        // Get a section from the ini file or return an empyty section
        SubnodeConfiguration sec = ini.getSection(section);
        sec.clear();
        sec.addProperty("azul_url", azulUrl);
        sec.addProperty("oidc_url", oidcUrl);
        sec.addProperty("auth_type", authType);
        sec.addProperty("auth_scopes", authScopes);
        sec.addProperty("auth_client_id", authClientId);
        sec.addProperty("auth_client_secret", authClientSecret);
        sec.addProperty("azul_verify_ssl", String.valueOf(azulVerifySsl));
        sec.addProperty("auth_token", MAPPER.writeValueAsString(authToken));
        sec.addProperty("auth_token_time", String.valueOf(authTokenTime));
        sec.addProperty("max_timeout", String.valueOf(maxTimeout));
        sec.addProperty("oidc_timeout", String.valueOf(oidcTimeout));

        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            ini.write(writer);
        } catch (ConfigurationException e) {
            throw new IOException("Failed to write INI file: " + configPath, e);
        }
    }

    /**
     * Parse a section of an INI file and returns its key-value pairs.
     * Returns an empty map if the section does not exist.
     *
     */
    private static Map<String, String> parseIniCommons(Path path, String targetSection) throws IOException {
        INIConfiguration ini = new INIConfiguration();
        try (FileReader reader = new FileReader(path.toFile())) {
            ini.read(reader);
        } catch (ConfigurationException e) {
            throw new IOException("Failed to parse INI file: " + path, e);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Iterator<String> it = ini.getSection(targetSection).getKeys(); it.hasNext();) {
            String key = it.next();
            result.put(key, ini.getSection(targetSection).getString(key));
        }
        return result;
    }

}
