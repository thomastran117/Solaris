package backend.dtos.requests.dispute;

import backend.annotations.safeRichText.SafeRichText;
import backend.models.enums.DisputeEvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddEvidenceRequest {

    @NotNull(message = "Evidence type is required")
    private DisputeEvidenceType evidenceType;

    /**
     * Multi-line by nature — staff paste order notes, call transcripts and email threads here,
     * so this uses {@code @SafeRichText} (line breaks allowed, XSS vectors rejected) rather than
     * {@code @SafeText}, which treats a newline as a disallowed control character.
     */
    @NotBlank(message = "Evidence content is required")
    @Size(max = 8000, message = "Evidence content must not exceed 8000 characters")
    @SafeRichText
    private String content;

    /**
     * Rendered back as an {@code <a href>} on the admin page, so the scheme is constrained
     * server-side rather than trusting the client: HTTPS only, which also rules out a
     * {@code javascript:} URL being persisted by anything calling the API directly.
     * Optional — null and empty both pass.
     */
    @Size(max = 500, message = "Attachment URL must not exceed 500 characters")
    @Pattern(regexp = "^(https://\\S+)?$", message = "Attachment URL must be an HTTPS URL")
    private String attachmentUrl;
}
