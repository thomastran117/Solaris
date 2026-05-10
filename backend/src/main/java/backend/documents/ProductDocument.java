package backend.documents;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document(indexName = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    @Id
    private Long id;

    private Long companyId;

    /** Non-null when the product is listed on a marketplace. */
    private Long marketplaceId;

    /** The owning vendor's company ID — populated when marketplaceId is set. */
    private Long vendorId;

    @Field(type = FieldType.Keyword)
    private String vendorName;

    /** True when the vendor has enabled this product for marketplace display. */
    private boolean marketplaceListed;

    @Field(type = FieldType.Text, searchAnalyzer = "product_search")
    private String name;

    @Field(type = FieldType.Text, searchAnalyzer = "product_search")
    private String description;

    @Field(type = FieldType.Keyword)
    private String sku;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Text, searchAnalyzer = "product_search")
    private String tags;

    @Field(type = FieldType.Text, analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String nameCompletion;

    @Field(type = FieldType.Keyword)
    private String status;

    /** Set on products with status SCHEDULED. Used by vendor admin filtering. */
    @Field(type = FieldType.Date)
    private Instant scheduledPublishAt;

    /** Set the first time the product transitioned to ACTIVE. */
    @Field(type = FieldType.Date)
    private Instant publishedAt;

    private boolean featured;

    private boolean listed;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    /**
     * Thematic discount categories currently active for this product
     * (e.g. "summer", "school", "weekly"). Populated from the discountCategory
     * field of all ACTIVE, in-window discounts that include this product.
     * Supports exact-match keyword filtering in product search.
     */
    @Field(type = FieldType.Keyword)
    private List<String> discountCategories;

    /** True when at least one ACTIVE, in-window discount covers this product. */
    private boolean hasActiveDiscount;

    /**
     * The product's effective price after the largest active discount.
     * Null when hasActiveDiscount is false (base price is authoritative).
     */
    @Field(type = FieldType.Double)
    private BigDecimal discountedPrice;
}
