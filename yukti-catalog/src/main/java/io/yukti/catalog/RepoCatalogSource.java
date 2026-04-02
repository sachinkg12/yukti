package io.yukti.catalog;

import io.yukti.catalog.dsl.CardDefinition;
import io.yukti.catalog.dsl.CatalogIndex;
import io.yukti.catalog.validation.ValidationError;
import io.yukti.catalog.validation.ValidationErrorItem;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.CatalogSource;
import io.yukti.core.api.ValuationPolicy;
import io.yukti.catalog.impl.ImmutableCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads catalog from index.json + per-card JSON files (DSL v0.1).
 * Verifies sha256 over exact file bytes. Checksum mismatch or validation failure fails hard.
 * Deterministic: process cards in index order; throw first error encountered.
 */
public final class RepoCatalogSource implements CatalogSource {
    private static final ObjectMapper OM = new ObjectMapper();
    private static final String SUPPORTED_DSL = "carddsl.v0.1";

    private final String basePath;
    private final List<ValuationPolicy> valuationPolicies;

    public RepoCatalogSource(String basePath, List<ValuationPolicy> valuationPolicies) {
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
        this.valuationPolicies = valuationPolicies != null ? List.copyOf(valuationPolicies) : List.of();
    }

    @Override
    public String id() {
        return "repo:" + basePath;
    }

    @Override
    public Catalog load(String catalogVersion) throws Exception {
        String pathPrefix = basePath.startsWith("/") ? basePath.substring(1) : basePath;
        String indexPath = pathPrefix + "index.json";
        CatalogIndex index;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(indexPath)) {
            if (in == null) throw new CatalogException(ValidationError.INDEX_ENTRY_NOT_FOUND, "Index not found: " + indexPath);
            index = OM.readValue(in, CatalogIndex.class);
        }

        if (!catalogVersion.equals(index.catalogVersion())) {
            throw new CatalogException(ValidationError.INDEX_ENTRY_NOT_FOUND,
                "Catalog version mismatch: requested " + catalogVersion + ", index has " + index.catalogVersion());
        }
        if (!SUPPORTED_DSL.equals(index.dslVersion())) {
            throw new CatalogException(ValidationError.UNSUPPORTED_DSL_VERSION,
                "Unsupported index dslVersion: " + index.dslVersion());
        }

        List<CatalogIndex.IndexEntry> entries = index.entries();
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i - 1).cardId().compareTo(entries.get(i).cardId()) > 0) {
                throw new CatalogException(ValidationError.INDEX_NOT_SORTED,
                    "Index cards must be sorted by cardId; out of order: " + entries.get(i - 1).cardId() + " > " + entries.get(i).cardId());
            }
        }

        CardDefinitionParser parser = new CardDefinitionParser();
        CardDefinitionMapper mapper = new CardDefinitionMapper();
        CatalogValidator validator = new CatalogValidator();
        List<io.yukti.core.api.Card> cards = new ArrayList<>();

        for (CatalogIndex.IndexEntry entry : entries) {
            String relPath = entry.path() != null && !entry.path().isBlank() ? entry.path() : "cards/" + entry.cardId() + ".json";
            String resourcePath = pathPrefix + relPath;
            byte[] bytes;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new CatalogException(ValidationError.CARD_NOT_LISTED_IN_INDEX, "Card file not found: " + resourcePath);
                }
                bytes = in.readAllBytes();
            }

            String computed = sha256Hex(bytes);
            String expected = entry.sha256() != null ? entry.sha256().toLowerCase() : "";
            if (!computed.equals(expected)) {
                throw new CatalogException(ValidationError.CHECKSUM_MISMATCH,
                    "Checksum mismatch for " + entry.cardId() + ": expected " + entry.sha256() + ", got " + computed);
            }

            CardDefinition def;
            try {
                def = parser.parse(new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new CatalogException(ValidationError.MISSING_REQUIRED_FIELD, "Malformed JSON: " + resourcePath + " - " + e.getMessage(), e);
            }

            if (!entry.cardId().equals(def.cardId())) {
                throw new CatalogException(ValidationError.DUPLICATE_CARD_ID,
                    "CardId mismatch: index " + entry.cardId() + " vs file " + def.cardId());
            }

            List<ValidationErrorItem> errs = validator.validateCard(def);
            if (!errs.isEmpty()) {
                ValidationErrorItem first = errs.get(0);
                throw new CatalogException(first.code(), first.message() + " (" + first.location() + ")");
            }

            cards.add(mapper.toCard(def));
        }

        return new ImmutableCatalog(index.catalogVersion(), cards, valuationPolicies);
    }

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
