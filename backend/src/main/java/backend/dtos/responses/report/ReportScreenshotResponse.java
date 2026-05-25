package backend.dtos.responses.report;

import backend.models.core.ReportScreenshot;
import lombok.Getter;

@Getter
public class ReportScreenshotResponse {

    private final String id;
    private final String url;
    private final int position;

    public ReportScreenshotResponse(ReportScreenshot screenshot) {
        this.id = screenshot.getId().toString();
        this.url = screenshot.getUrl();
        this.position = screenshot.getPosition();
    }
}
