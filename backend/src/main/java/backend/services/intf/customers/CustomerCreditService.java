package backend.services.intf.customers;

import java.util.UUID;
import backend.dtos.requests.credit.IssueCreditRequest;
import backend.dtos.responses.credit.CreditBalanceResponse;
import backend.dtos.responses.credit.CreditEntryResponse;

public interface CustomerCreditService {

    /**
     * Issues store credit to a customer. Creates a positive ledger entry.
     *
     * @param userId            the customer receiving the credit
     * @param request           amount, type, reason, and optional expiry
     * @param issuedByUserId    staff member authorising the issuance
     * @param sourceTicketId    optional linked ticket ID
     * @param sourceOrderIssueId optional linked order issue ID
     * @return the new ledger entry
     */
    CreditEntryResponse issueCredit(UUID userId, IssueCreditRequest request,
                                    UUID issuedByUserId, UUID sourceTicketId, UUID sourceOrderIssueId);

    /** Returns the customer's current balance and full ledger history. */
    CreditBalanceResponse getBalance(UUID userId);

    /**
     * Redeems credit against an order. Creates a negative REDEEMED entry.
     * Throws if the customer's balance is insufficient.
     *
     * @param userId      the customer redeeming credit
     * @param orderId     the order the credit is applied to
     * @param amountCents amount to redeem in cents
     * @return the new ledger entry
     */
    CreditEntryResponse redeemCredit(UUID userId, UUID orderId, long amountCents);

    /**
     * Reverses a credit entry by appending an offsetting REVERSED entry.
     * Staff-only; caller must validate the actor role before calling.
     */
    CreditEntryResponse reverseCredit(UUID creditEntryId, UUID actorUserId);

    /** Scheduled job: expires credits past their expiry date. */
    void expireCredits();
}
