package com.azul.client;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builds a term query string for the /api/v0/binaries find endpoints.
 * Mirrors Python azul-client's FindOptions dataclass and the to_query() method.
 *
 * Note list values assume an OR relationship when looking for matches.
 * Note list values assume an AND relationship when excluding matches.
 * Note dict values always assume an AND relationship between members of dict.
 * Wildcard '*' is supported at the end of string values only.
 */
public class FindOptions {

    // Source options
    public List<String> sources;
    public List<String> sourceExcludes;

    public List<Integer> sourceDepth;
    public List<Integer> sourceDepthExclude;
    public Integer sourceDepthGreater;
    public Integer sourceDepthLess;

    public String sourceUsername;
    public Map<String, String> sourceReference;

    // Time options
    public Instant sourceTimestampNewerOrEqual;
    public Instant sourceTimestampOlderOrEqual;
    public Instant sourceTimestampNewer;
    public Instant sourceTimestampOlder;

    // Plugin / Author filtering
    public String pluginName;
    public String pluginVersion;

    // Feature filtering
    public List<String> hasFeatureKeys;
    public List<String> hasFeatureValues;

    // Binary info
    public Integer greaterThanSizeBytes;
    public Integer lessThanSizeBytes;

    public List<String> fileFormatsLegacy;
    public List<String> fileFormatsLegacyExclude;
    public List<String> fileFormats;
    public List<String> fileFormatsExclude;

    // Tags
    public List<String> binaryTags;
    public List<String> featureTags;

    // Internal query builder
    private StringBuilder query;

    /**
     * Append a new value to the query.
     */
    private void add(String value) {
        if (query.length() == 0) {
            query.append(value);
        } else {
            query.append(" ").append(value);
        }
    }

    /**
     * Add a date as an integer to the query.
     */
    private void addDate(String searchKey, Instant value) {
        if (value == null)
            return;
        add(String.format(searchKey, value.toEpochMilli()));
    }

    /**
     * Add a value to the internal query if the provided value isn't none.
     */
    private void addIfNotNull(String searchKey, Object value) {
        if (value == null)
            return;
        add(String.format(searchKey, value));
    }

    /**
     * Add a list of values to the internal query if the provided value isn't none.
     * Negation is used to switch between 'AND'ing and 'OR'ing members of the list.
     */
    private void addList(String searchKey, List<?> values, boolean negation) {
        if (values == null || values.isEmpty())
            return;
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                add((negation ? "AND " : "OR ") + String.format(searchKey, values.get(i)));
            } else {
                add(String.format(searchKey, values.get(i)));
            }
        }
    }

    /**
     * Add a list of values to the internal query if the provided value isn't none.
     */
    private void addList(String searchKey, List<?> values) {
        addList(searchKey, values, false);
    }

    /**
     * Add a number key value pair queries.
     */
    private void addKeyValue(String searchKey, Map<String, String> value) {
        if (value == null || value.isEmpty())
            return;
        for (Map.Entry<String, String> entry : value.entrySet()) {
            add(String.format(searchKey, entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Converts the configured options into a term query string ready to send to the
     * API.
     * Each call resets and rebuilds the query from the current field values.
     */
    public String toQuery() {
        query = new StringBuilder();

        // Source options
        addList("source.name:\"%s\"", sources);
        addList("!source.name:\"%s\"", sourceExcludes, true);

        addList("depth:%s", sourceDepth);
        addList("!depth:%s", sourceDepthExclude, true);
        addIfNotNull("depth:>%s", sourceDepthGreater);
        addIfNotNull("depth:<%s", sourceDepthLess);

        addIfNotNull("source.encoded_references.key_value:\"user.%s\"", sourceUsername);
        addKeyValue("source.encoded_references.key_value:\"%s.%s\"", sourceReference);

        // Time options
        addDate("source.timestamp:>=%s", sourceTimestampNewerOrEqual);
        addDate("source.timestamp:<=%s", sourceTimestampOlderOrEqual);
        addDate("source.timestamp:>%s", sourceTimestampNewer);
        addDate("source.timestamp:<%s", sourceTimestampOlder);

        // Author options
        addIfNotNull("author.name:\"%s\"", pluginName);
        addIfNotNull("author.version:\"%s\"", pluginVersion);

        // Features
        addList("features.name:\"%s\"", hasFeatureKeys);
        addList("features.value:\"%s\"", hasFeatureValues);

        // Binary info
        addIfNotNull("size:>%s", greaterThanSizeBytes);
        addIfNotNull("size:<%s", lessThanSizeBytes);

        addList("file_format_legacy:\"%s\"", fileFormatsLegacy);
        addList("!file_format_legacy:\"%s\"", fileFormatsLegacyExclude, true);
        addList("file_format:\"%s\"", fileFormats);
        addList("!file_format:\"%s\"", fileFormatsExclude, true);

        // Tags
        addList("binary.tag:\"%s\"", binaryTags);
        addList("feature.tag:\"%s\"", featureTags);

        return query.toString();
    }
}
