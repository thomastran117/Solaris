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
import java.util.UUID;

@Document(indexName = "bundles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BundleDocument {

    @Id
    private UUID id;

    @Field(type = FieldType.Keyword)
    private UUID companyId;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String status;

    private boolean listed;

    @Field(type = FieldType.Double)
    private BigDecimal price;
}
