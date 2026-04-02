package io.yukti.catalog.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DSL v0.1 catalog index: catalogVersion, dslVersion, generatedAtIso, cards with path and sha256.
 */
public record CatalogIndex(
    @JsonProperty("catalogVersion") String catalogVersion,
    @JsonProperty("dslVersion") String dslVersion,
    @JsonProperty("generatedAtIso") String generatedAtIso,
    @JsonProperty("cards") List<IndexEntry> entries
) {
    @JsonCreator
    public CatalogIndex(
        @JsonProperty("catalogVersion") String catalogVersion,
        @JsonProperty("dslVersion") String dslVersion,
        @JsonProperty("generatedAtIso") String generatedAtIso,
        @JsonProperty("cards") List<IndexEntry> entries
    ) {
        this.catalogVersion = catalogVersion != null ? catalogVersion : "v1";
        this.dslVersion = dslVersion != null ? dslVersion : "carddsl.v0.1";
        this.generatedAtIso = generatedAtIso != null ? generatedAtIso : "";
        this.entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public record IndexEntry(
        @JsonProperty("cardId") String cardId,
        @JsonProperty("path") String path,
        @JsonProperty("sha256") String sha256
    ) {}
}
