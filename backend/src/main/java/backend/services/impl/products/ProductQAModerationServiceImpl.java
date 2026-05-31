package backend.services.impl.products;

import backend.dtos.requests.qa.ModerateQARequest;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.QAReportType;
import backend.models.enums.QAStatus;
import backend.repositories.ProductAnswerRepository;
import backend.repositories.ProductQuestionRepository;
import backend.services.intf.products.ProductQAModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductQAModerationServiceImpl implements ProductQAModerationService {

    private final ProductQuestionRepository questionRepository;
    private final ProductAnswerRepository answerRepository;

    public ProductQAModerationServiceImpl(
            ProductQuestionRepository questionRepository,
            ProductAnswerRepository answerRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
    }

    @Override
    @Transactional
    public void moderateContent(QAReportType type, UUID targetId, ModerateQARequest request) {
        QAStatus newStatus = switch (request.getAction()) {
            case "APPROVE" -> QAStatus.VISIBLE;
            case "REJECT"  -> QAStatus.HIDDEN;
            default -> throw new BadRequestException("Invalid moderation action: " + request.getAction());
        };

        if (type == QAReportType.QUESTION) {
            questionRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + targetId));
            questionRepository.updateStatusIfDifferent(targetId, newStatus.name());
        } else {
            answerRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Answer not found with id: " + targetId));
            answerRepository.updateStatusIfDifferent(targetId, newStatus.name());
        }
    }
}
