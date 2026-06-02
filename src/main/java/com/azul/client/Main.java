package com.azul.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.Callable;

public class Main {

    public static void main(String[] args) {
        System.exit(new CommandLine(new Cli()).execute(args));
    }

    @Command(
        name = "azul-client",
        subcommands = {BinariesCmd.class, ConfigCmd.class, CommandLine.HelpCommand.class},
        mixinStandardHelpOptions = true,
        description = "Interact with the Azul API via CLI"
    )
    static class Cli implements Callable<Integer> {

        @Option(names = "-c", defaultValue = "default",
                description = "Switch to a different configured Azul instance.")
        String configSection;

        private AzulConfig cfg;
        private AzulClient client;

        // Shared with subcommands via @ParentCommand
        AzulConfig getConfig() throws IOException {
            if (cfg == null) cfg = AzulConfig.load(configSection);
            return cfg;
        }

        AzulClient getClient() throws IOException {
            if (client == null) client = new AzulClient(getConfig());
            return client;
        }

        @Override
        public Integer call() {
            // Print help text by default
            CommandLine.usage(this, System.out);
            return 0;
        }
    }

    // binaries
    @Command(
        name = "binaries", aliases = {"b"},
        subcommands = {CheckCmd.class, GetMetaCmd.class, GetCmd.class, CommandLine.HelpCommand.class},
        mixinStandardHelpOptions = true,
        description = "Upload, download and get metadata associated with binaries."
    )
    static class BinariesCmd implements Callable<Integer> {

        @ParentCommand Cli parent;

        @Override
        public Integer call() {
            CommandLine.usage(this, System.out);
            return 0;
        }
    }

    @Command(name = "check", mixinStandardHelpOptions = true,
             description = "Check if binary metadata for the provided SHA256 is in Azul.")
    static class CheckCmd implements Callable<Integer> {

        @ParentCommand BinariesCmd binaries;

        @Parameters(index = "0", paramLabel = "sha256", description = "SHA256 hash of the binary.")
        String sha256;

        @Override
        public Integer call() throws Exception {
            if (binaries.parent.getClient().checkMeta(sha256)) {
                System.out.println("Binary metadata available");
                return 0;
            } else {
                System.out.println("Binary metadata NOT available");
                return 1;
            }
        }
    }

    @Command(name = "get-meta", mixinStandardHelpOptions = true,
             description = "Get a binary's metadata from Azul by SHA256.")
    static class GetMetaCmd implements Callable<Integer> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @ParentCommand BinariesCmd binaries;

        @Parameters(index = "0", paramLabel = "sha256", description = "SHA256 hash of the binary.")
        String sha256;

        @Option(names = {"-o", "--output"}, defaultValue = "-",
                description = "Output to a file — use '-' for stdout (default: ${DEFAULT-VALUE}).")
        String output;

        @Override
        public Integer call() throws Exception {
            JsonNode meta;
            try {
                meta = binaries.parent.getClient().getBinaryMeta(sha256);
            } catch (BinaryNotFoundException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(meta);
            if (output.equals("-")) {
                System.out.println(json);
            } else {
                System.err.println("saving output to path " + output);
                Files.writeString(Path.of(output), json);
            }
            return 0;
        }
    }

    @Command(name = "get", mixinStandardHelpOptions = true,
             description = {"Find and download samples from Azul.",
                            "Combining multiple filters may lead to unexpected results."})
    static class GetCmd implements Callable<Integer> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @ParentCommand BinariesCmd binaries;

        @Option(names = "--term", defaultValue = "",
                description = "Search term (refer to UI Explore for suggested search terms).")
        String term;

        @Option(names = "--max", defaultValue = "100",
                description = "Max number of entities to retrieve (default: ${DEFAULT-VALUE}).")
        int max;

        @Option(names = "--count-entities",
                description = "Include total count in the response.")
        Boolean countEntities;

        @Override
        public Integer call() throws Exception {
            JsonNode result = binaries.parent.getClient().find(term, max, countEntities);
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            return 0;
        }
    }

    // config
    @Command(
        name = "config",
        subcommands = {ClearAuthCmd.class, CommandLine.HelpCommand.class},
        mixinStandardHelpOptions = true,
        description = "Change azul-client configuration."
    )
    static class ConfigCmd implements Callable<Integer> {

        @ParentCommand Cli parent;

        @Override
        public Integer call() {
            CommandLine.usage(this, System.out);
            return 0;
        }
    }

    @Command(name = "clear-auth", mixinStandardHelpOptions = true,
             description = "Reset current auth information.")
    static class ClearAuthCmd implements Callable<Integer> {

        @ParentCommand ConfigCmd config;

        @Override
        public Integer call() throws Exception {
            AzulConfig cfg = config.parent.getConfig();
            cfg.authToken = new HashMap<>();
            cfg.authTokenTime = 0;
            cfg.save();
            System.out.println("Auth cleared.");
            return 0;
        }
    }
}
