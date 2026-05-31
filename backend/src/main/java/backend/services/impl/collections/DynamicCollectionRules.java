package backend.services.impl.collections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import backend.exceptions.http.BadRequestException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parses and represents the rule body stored on a DYNAMIC {@code Collection}.
 *
 * <p>Schema v1 (JSON):
 * <pre>
 * {
 *   "tagsAnyOf":       ["summer", "sale"],
 *   "categoriesAnyOf": ["Apparel"],
 *   "brandsAnyOf":     ["Acme", "Globex"]
 * }
 * </pre>
 *
 * <p>Each list is evaluated as <em>any-of</em> (OR within the list). Multiple lists combine with
 * AND (all populated lists must match). A rule body with all-empty lists matches nothing and is
 * rejected so admins can't accidentally hoover every product into a collection.
 */
public final class DynamicCollectionRules {

    private static final Logger log = LoggerFactory.getLogger(DynamicCollectionRules.class);

    private final List<String> tagsAnyOf;
    private final List<String> categoriesAnyOf;
    private final List<String> brandsAnyOf;

    private DynamicCollectionRules(List<String> tagsAnyOf, List<String> categoriesAnyOf, List<String> brandsAnyOf) {
        this.tagsAnyOf = tagsAnyOf;
        this.categoriesAnyOf = categoriesAnyOf;
        this.brandsAnyOf = brandsAnyOf;
    }

    public List<String> tagsAnyOf() { return tagsAnyOf; }
    public List<String> categoriesAnyOf() { return categoriesAnyOf; }
    public List<String> brandsAnyOf() { return brandsAnyOf; }

    public boolean isEmpty() {
        return tagsAnyOf.isEmpty() && categoriesAnyOf.isEmpty() && brandsAnyOf.isEmpty();
    }

    public static DynamicCollectionRules parse(String rulesJson, ObjectMapper mapper) {
        if (rulesJson == null || rulesJson.isBlank()) {
            throw new BadRequestException("Dynamic collections require a rules body");
        }
        Map<String, Object> raw;
        try {
            raw = mapper.readValue(rulesJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[COLLECTION RULES] Failed to parse rules JSON: {}", e.getMessage());
            throw new BadRequestException("rulesJson is not valid JSON");
        }
        DynamicCollectionRules rules = new DynamicCollectionRules(
                readStringList(raw, "tagsAnyOf"),
                readStringList(raw, "categoriesAnyOf"),
                readStringList(raw, "brandsAnyOf"));
        if (rules.isEmpty()) {
            throw new BadRequestException("Dynamic collection rules must include at least one tag, category, or brand");
        }
        return rules;
    }

    private static List<String> readStringList(Map<String, Object> raw, String key) {
        Object v = raw.get(key);
        if (v == null) return Collections.emptyList();
        if (!(v instanceof List<?> list)) {
            throw new BadRequestException(key + " must be an array of strings");
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null) continue;
            String s = item.toString().trim();
            if (!s.isEmpty()) out.add(s.toLowerCase());
        }
        return out;
    }
}
