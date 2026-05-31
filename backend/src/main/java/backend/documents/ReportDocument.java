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

@Document(indexName = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportDocument {

    @Id
    private UUID id;

    @Field(type = FieldType.Keyword)
    private String targetType;

    @Field(type = FieldType.Keyword)
    private String targetId;

    @Field(type = FieldType.Keyword)
    private String reporterId;

    @Field(type = FieldType.Keyword)
    private String reason;

    @Field(type = FieldType.Text, searchAnalyzer = "product_search")
    private String title;

    @Field(type = FieldType.Text, searchAnalyzer = "product_search")
    private String description;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Text, searchAnalyzer = "product_search")
    private String targetName;

    @Field(type = FieldType.Text, searchAnalyzer = "product_search")
    private String reporterName;

    @Field(type = FieldType.Integer)
    private int screenshotCount;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant resolvedAt;
}
