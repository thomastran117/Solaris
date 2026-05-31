package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "report_screenshots",
        indexes = @Index(name = "idx_screenshot_report", columnList = "report_id")
)
public class ReportScreenshot {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(nullable = false)
    private int position;

    public ReportScreenshot(Report report, String url, int position) {
        this.report = report;
        this.url = url;
        this.position = position;
    }
}
