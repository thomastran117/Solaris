package backend.documents;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.UUID;

@Document(indexName = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDocument {

    @Id
    private UUID id;

    @Field(type = FieldType.Keyword)
    private UUID productId;

    @Field(type = FieldType.Keyword)
    private UUID companyId;

    @Field(type = FieldType.Keyword)
    private UUID marketplaceId;

    @Field(type = FieldType.Keyword)
    private UUID reviewerId;

    @Field(type = FieldType.Keyword)
    private String reviewerName;

    @Field(type = FieldType.Text, searchAnalyzer = "product_search")
    private String title;

    @Field(type = FieldType.Text, searchAnalyzer = "product_search")
    private String body;

    @Field(type = FieldType.Integer)
    private int rating;

    private boolean verifiedPurchase;

    private boolean hasMedia;

    @Field(type = FieldType.Integer)
    private int helpfulCount;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant updatedAt;
}
