package backend.services.impl;

import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.BulkAdjustItem;
import backend.dtos.requests.inventory.BulkAdjustRequest;
import backend.dtos.requests.inventory.CreateLocationRequest;
import backend.dtos.requests.inventory.CreateRestockRequest;
import backend.dtos.requests.inventory.UpdateLocationRequest;
import backend.dtos.requests.inventory.UpdateRestockRequest;
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
import backend.dtos.requests.product.UpdateProductOptionRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;
import backend.services.intf.SanitizationService;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SanitizationServiceImpl implements SanitizationService, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SanitizationServiceImpl.class);

    private static final String PROFANITY_RESOURCE = "profanity.txt";

    private static final Pattern PLAIN_TEXT_HTML = Pattern.compile("[<>]");
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9_\\-.]+$");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private static final PolicyFactory RICH_TEXT_POLICY = new HtmlPolicyBuilder()
            .allowCommonInlineFormattingElements()
            .allowElements("p", "br", "ul", "ol", "li")
            .toFactory();

    private Set<String> profanityTerms = Collections.emptySet();
    private Pattern profanityPattern;

    @Override
    public void afterPropertiesSet() {
        this.profanityTerms = loadProfanityList();
        this.profanityPattern = buildProfanityPattern(this.profanityTerms);
    }

    // ---- Primitives ----

    @Override
    public String normalizeText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return trimmed;
        return WHITESPACE_RUN.matcher(trimmed).replaceAll(" ");
    }

    @Override
    public String normalizeRichText(String value) {
        if (value == null) return null;
        return value.trim();
    }

    @Override
    public String normalizeCode(String value) {
        if (value == null) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String normalizeCategory(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean isSafePlainText(String value) {
        if (value == null || value.isBlank()) return true;
        if (PLAIN_TEXT_HTML.matcher(value).find()) return false;
        if (hasDisallowedControlChars(value, false)) return false;
        return !containsProfanity(value);
    }

    @Override
    public boolean isSafeRichText(String value) {
        if (value == null || value.isBlank()) return true;
        if (hasDisallowedControlChars(value, true)) return false;
        String sanitized = RICH_TEXT_POLICY.sanitize(value);
        if (!stripTags(sanitized).equals(stripTags(value))) return false;
        if (containsDisallowedHtmlConstructs(value)) return false;
        return !containsProfanity(stripTags(value));
    }

    @Override
    public boolean isSafeIdentifier(String value) {
        if (value == null || value.isBlank()) return true;
        if (!IDENTIFIER.matcher(value).matches()) return false;
        return !containsProfanity(value);
    }

    @Override
    public boolean containsProfanity(String value) {
        if (value == null || value.isBlank() || profanityPattern == null) return false;
        return profanityPattern.matcher(value).find();
    }

    // ---- Product DTOs ----

    @Override
    public void normalize(CreateProductRequest r) {
        if (r == null) return;
        r.setName(normalizeText(r.getName()));
        r.setDescription(normalizeRichText(r.getDescription()));
        r.setSku(normalizeCode(r.getSku()));
        r.setCurrency(normalizeCode(r.getCurrency()));
        r.setCategory(normalizeCategory(r.getCategory()));
        r.setBrand(normalizeText(r.getBrand()));
        r.setTags(normalizeRichText(r.getTags()));
        r.setThumbnailUrl(normalizeRichText(r.getThumbnailUrl()));
        r.setWeightUnit(normalizeText(r.getWeightUnit()));
    }

    @Override
    public void normalize(UpdateProductRequest r) {
        if (r == null) return;
        r.setName(normalizeText(r.getName()));
        r.setDescription(normalizeRichText(r.getDescription()));
        r.setSku(normalizeCode(r.getSku()));
        r.setCurrency(normalizeCode(r.getCurrency()));
        r.setCategory(normalizeCategory(r.getCategory()));
        r.setBrand(normalizeText(r.getBrand()));
        r.setTags(normalizeRichText(r.getTags()));
        r.setThumbnailUrl(normalizeRichText(r.getThumbnailUrl()));
        r.setWeightUnit(normalizeText(r.getWeightUnit()));
    }

    @Override
    public void normalize(BatchCreateProductsRequest r) {
        if (r == null || r.getProducts() == null) return;
        r.getProducts().forEach(this::normalize);
    }

    @Override
    public void normalize(CreateProductVariantRequest r) {
        if (r == null) return;
        r.setSku(normalizeCode(r.getSku()));
        r.setOption1(normalizeText(r.getOption1()));
        r.setOption2(normalizeText(r.getOption2()));
        r.setOption3(normalizeText(r.getOption3()));
    }

    @Override
    public void normalize(UpdateProductVariantRequest r) {
        if (r == null) return;
        r.setSku(normalizeCode(r.getSku()));
        r.setOption1(normalizeText(r.getOption1()));
        r.setOption2(normalizeText(r.getOption2()));
        r.setOption3(normalizeText(r.getOption3()));
    }

    @Override
    public void normalize(CreateProductOptionRequest r) {
        if (r == null) return;
        r.setName(normalizeText(r.getName()));
    }

    @Override
    public void normalize(UpdateProductOptionRequest r) {
        if (r == null) return;
        r.setName(normalizeText(r.getName()));
    }

    @Override
    public void normalize(AddProductImageRequest r) {
        if (r == null) return;
        r.setImageUrl(normalizeRichText(r.getImageUrl()));
    }

    @Override
    public void normalize(SetProductAttributesRequest r) {
        if (r == null || r.getAttributes() == null) return;
        for (SetProductAttributesRequest.AttributeItem item : r.getAttributes()) {
            if (item == null) continue;
            item.setName(normalizeText(item.getName()));
            item.setValue(normalizeText(item.getValue()));
        }
    }

    // ---- Restock / Inventory DTOs ----

    @Override
    public void normalize(CreateRestockRequest r) {
        if (r == null) return;
        r.setSupplierNote(normalizeRichText(r.getSupplierNote()));
    }

    @Override
    public void normalize(UpdateRestockRequest r) {
        if (r == null) return;
        r.setSupplierNote(normalizeRichText(r.getSupplierNote()));
    }

    @Override
    public void normalize(AdjustStockRequest r) {
        if (r == null) return;
        r.setNote(normalizeRichText(r.getNote()));
    }

    @Override
    public void normalize(BulkAdjustRequest r) {
        if (r == null || r.getItems() == null) return;
        for (BulkAdjustItem item : r.getItems()) {
            if (item == null) continue;
            item.setNote(normalizeRichText(item.getNote()));
        }
    }

    @Override
    public void normalize(CreateLocationRequest r) {
        if (r == null) return;
        r.setName(normalizeText(r.getName()));
        r.setCode(normalizeCode(r.getCode()));
        r.setAddress(normalizeRichText(r.getAddress()));
        r.setCity(normalizeText(r.getCity()));
        r.setCountry(normalizeText(r.getCountry()));
    }

    @Override
    public void normalize(UpdateLocationRequest r) {
        if (r == null) return;
        r.setName(normalizeText(r.getName()));
        r.setCode(normalizeCode(r.getCode()));
        r.setAddress(normalizeRichText(r.getAddress()));
        r.setCity(normalizeText(r.getCity()));
        r.setCountry(normalizeText(r.getCountry()));
    }

    // ---- Internals ----

    private static boolean hasDisallowedControlChars(String value, boolean allowLineBreaks) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20) {
                if (allowLineBreaks && (c == '\n' || c == '\r' || c == '\t')) continue;
                if (!allowLineBreaks && c == '\t') continue;
                return true;
            }
            if (c == 0x7F) return true;
        }
        return false;
    }

    private static boolean containsDisallowedHtmlConstructs(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        // Fast-fail on common XSS vectors even if the OWASP pass would also strip them.
        return lower.contains("<script") || lower.contains("</script")
                || lower.contains("javascript:") || lower.contains("data:text/html")
                || lower.contains("<iframe") || lower.contains("<object")
                || lower.contains("<embed") || lower.contains("<style")
                || lower.matches("(?s).*\\bon[a-z]+\\s*=.*");
    }

    private static String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
    }

    private Set<String> loadProfanityList() {
        Set<String> terms = new LinkedHashSet<>();
        ClassPathResource resource = new ClassPathResource(PROFANITY_RESOURCE);
        if (!resource.exists()) {
            log.warn("Profanity list resource not found at classpath:{} — profanity check disabled.",
                    PROFANITY_RESOURCE);
            return terms;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                terms.add(trimmed.toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            log.error("Failed to load profanity list; check disabled.", e);
            return Collections.emptySet();
        }
        log.info("Loaded {} profanity terms.", terms.size());
        return Collections.unmodifiableSet(terms);
    }

    private static Pattern buildProfanityPattern(Set<String> terms) {
        if (terms.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("(?i)\\b(?:");
        boolean first = true;
        for (String term : terms) {
            if (!first) sb.append('|');
            sb.append(Pattern.quote(term));
            first = false;
        }
        sb.append(")\\b");
        return Pattern.compile(sb.toString());
    }

    // Exposed for tests / diagnostics.
    List<String> profanityTermsSnapshot() {
        return List.copyOf(profanityTerms);
    }
}
