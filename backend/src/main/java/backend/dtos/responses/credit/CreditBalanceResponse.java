package backend.dtos.responses.credit;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CreditBalanceResponse {
    private UUID userId;
    private long balanceCents;
    private String currency;
    private List<CreditEntryResponse> entries;
}
