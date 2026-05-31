package backend.services.intf.products;

import backend.dtos.requests.qa.ModerateQARequest;
import backend.models.enums.QAReportType;

import java.util.UUID;

public interface ProductQAModerationService {
    void moderateContent(QAReportType type, UUID targetId, ModerateQARequest request);
}
