package backend.dtos.responses.mfa;

import java.util.List;

public record TotpVerifyResponse(
        List<String> backupCodes
) {}
