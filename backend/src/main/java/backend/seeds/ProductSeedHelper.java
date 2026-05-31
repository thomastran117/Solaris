package backend.seeds;

import backend.models.core.*;
import backend.models.enums.ProductStatus;
import backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared builder utilities for product seeders. Owns the product-family
 * repositories so individual seeders stay focused on their catalog data.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class ProductSeedHelper {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductAttributeRepository productAttributeRepository;

    // -------------------------------------------------------------------------
    // Top-level product builders
    // -------------------------------------------------------------------------

    /** Product with one or more option groups and a variant builder callback. */
    public Product product(Company co, String name, String description, String sku,
                           String price, String compareAt, String category, String brand,
                           String thumbnail, int stock, int lowStock,
                           boolean featured, boolean subscribable, boolean backorder,
                           String subIntervals, BigDecimal subDiscount,
                           List<String> imageUrls, List<String[]> attrPairs,
                           List<String[]> optDefs, Consumer<Product> variantBuilder) {
        if (sku != null) {
            var existing = productRepository.findBySkuAndCompanyId(sku, co.getId());
            if (existing.isPresent()) return existing.get();
        }
        Product p = base(co, name, description, sku, price, compareAt, category, brand,
                thumbnail, stock, lowStock, featured, subscribable, backorder, subIntervals, subDiscount);
        attachImages(p, imageUrls);
        attachAttrs(p, attrPairs);
        attachOptions(p, optDefs);
        variantBuilder.accept(p);
        return p;
    }

    /** Single-SKU product — no options or variants. */
    public Product productSingle(Company co, String name, String description, String sku,
                                  String price, String compareAt, String category, String brand,
                                  String thumbnail, int stock, int lowStock,
                                  boolean featured, boolean subscribable, boolean backorder,
                                  String subIntervals, BigDecimal subDiscount,
                                  List<String> imageUrls, List<String[]> attrPairs) {
        if (sku != null) {
            var existing = productRepository.findBySkuAndCompanyId(sku, co.getId());
            if (existing.isPresent()) return existing.get();
        }
        Product p = base(co, name, description, sku, price, compareAt, category, brand,
                thumbnail, stock, lowStock, featured, subscribable, backorder, subIntervals, subDiscount);
        attachImages(p, imageUrls);
        attachAttrs(p, attrPairs);
        return p;
    }

    private Product base(Company co, String name, String description, String sku,
                         String price, String compareAt, String category, String brand,
                         String thumbnail, int stock, int lowStock,
                         boolean featured, boolean subscribable, boolean backorder,
                         String subIntervals, BigDecimal subDiscount) {
        Product p = new Product();
        p.setCompany(co);
        p.setName(name);
        p.setDescription(description);
        p.setSku(sku);
        p.setPrice(new BigDecimal(price));
        p.setCompareAtPrice(compareAt != null ? new BigDecimal(compareAt) : null);
        p.setCurrency("USD");
        p.setCategory(category);
        p.setBrand(brand);
        p.setThumbnailUrl(thumbnail);
        p.setStock(stock);
        p.setLowStockThreshold(lowStock);
        p.setStatus(ProductStatus.ACTIVE);
        p.setFeatured(featured);
        p.setPurchasable(true);
        p.setListed(true);
        p.setBackorderEnabled(backorder);
        p.setSubscribable(subscribable);
        p.setSubscriptionIntervals(subIntervals);
        p.setSubscriptionDiscountPercent(subDiscount);
        return productRepository.save(p);
    }

    // -------------------------------------------------------------------------
    // Attachment helpers
    // -------------------------------------------------------------------------

    private void attachImages(Product p, List<String> urls) {
        for (int i = 0; i < urls.size(); i++) {
            ProductImage img = new ProductImage();
            img.setProduct(p);
            img.setImageUrl(urls.get(i));
            img.setDisplayOrder(i);
            productImageRepository.save(img);
        }
    }

    private void attachAttrs(Product p, List<String[]> pairs) {
        for (int i = 0; i < pairs.size(); i++) {
            ProductAttribute attr = new ProductAttribute();
            attr.setProduct(p);
            attr.setName(pairs.get(i)[0]);
            attr.setValue(pairs.get(i)[1]);
            attr.setDisplayOrder(i);
            productAttributeRepository.save(attr);
        }
    }

    private void attachOptions(Product p, List<String[]> optDefs) {
        for (int pos = 0; pos < optDefs.size(); pos++) {
            ProductOption opt = new ProductOption();
            opt.setProduct(p);
            opt.setName(optDefs.get(pos)[0]);
            opt.setPosition(pos);
            productOptionRepository.save(opt);
        }
    }

    // -------------------------------------------------------------------------
    // Variant persistence
    // -------------------------------------------------------------------------

    /** Single-option variant. */
    public void pv(Product p, String opt1, String sku, BigDecimal price, int stock) {
        ProductVariant v = new ProductVariant();
        v.setProduct(p);
        v.setOption1(opt1);
        v.setSku(sku);
        v.setPrice(price);
        v.setStock(stock);
        v.setLowStockThreshold(5);
        v.setPurchasable(true);
        productVariantRepository.save(v);
    }

    /** Two-option variant. */
    public void pv2(Product p, String opt1, String opt2, String sku, BigDecimal price, int stock) {
        ProductVariant v = new ProductVariant();
        v.setProduct(p);
        v.setOption1(opt1);
        v.setOption2(opt2);
        v.setSku(sku);
        v.setPrice(price);
        v.setStock(stock);
        v.setLowStockThreshold(5);
        v.setPurchasable(true);
        productVariantRepository.save(v);
    }

    /**
     * Cross-product size × color variants (or any two string dimensions).
     * SKU is auto-generated as {@code skuPrefix-SIZE-COL} (first 3 chars of color).
     */
    public void addSizeColorVariants(Product p, String skuPrefix,
                                      String[] sizes, String[] colors,
                                      String price, int stockPerVariant) {
        int order = 0;
        for (String size : sizes) {
            for (String color : colors) {
                String shortSize  = size.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                String shortColor = color.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                if (shortColor.length() > 3) shortColor = shortColor.substring(0, 3);
                ProductVariant v = new ProductVariant();
                v.setProduct(p);
                v.setOption1(size);
                v.setOption2(color);
                v.setSku(skuPrefix + "-" + shortSize + "-" + shortColor);
                v.setPrice(new BigDecimal(price));
                v.setStock(stockPerVariant);
                v.setLowStockThreshold(3);
                v.setPurchasable(true);
                v.setDisplayOrder(order++);
                productVariantRepository.save(v);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Data declaration helpers — keep seeders readable
    // -------------------------------------------------------------------------

    public List<String> images(String... urls) {
        return List.of(urls);
    }

    public List<String[]> attrs(String... pairs) {
        List<String[]> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2)
            list.add(new String[]{pairs[i], pairs[i + 1]});
        return list;
    }

    /** Single option group: first element is the name, rest are values. */
    public List<String[]> options1(String name, String... values) {
        String[] entry = new String[values.length + 1];
        entry[0] = name;
        System.arraycopy(values, 0, entry, 1, values.length);
        List<String[]> result = new ArrayList<>();
        result.add(entry);
        return result;
    }

    /** Two option groups. */
    public List<String[]> options2(String n1, String v1a, String v1b, String v1c,
                                    String n2, String v2a, String v2b, String v2c) {
        return List.of(new String[]{n1, v1a, v1b, v1c}, new String[]{n2, v2a, v2b, v2c});
    }

    /** Two option groups with two values each. */
    public List<String[]> options2(String n1, String v1a, String v1b,
                                    String n2, String v2a, String v2b) {
        return List.of(new String[]{n1, v1a, v1b}, new String[]{n2, v2a, v2b});
    }
}
