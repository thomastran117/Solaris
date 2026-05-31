package backend.models.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "index_versions")
public class IndexVersion {

    @Id
    @Column(nullable = false, length = 100)
    private String alias;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 120)
    private String indexName;

    @Column(nullable = false)
    private Instant updatedAt;
}
