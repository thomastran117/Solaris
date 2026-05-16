package backend.services.impl.customers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.credit.IssueCreditRequest;
import backend.dtos.responses.credit.CreditBalanceResponse;
import backend.dtos.responses.credit.CreditEntryResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CustomerCredit;
import backend.models.core.User;
import backend.models.enums.CreditEntryType;
import backend.repositories.CustomerCreditRepository;
import backend.repositories.UserRepository;
import backend.services.intf.customers.CustomerCreditService;
import backend.utilities.SecurityUtils;

import java.time.Instant;
import java.util.List;

@Service
public class CustomerCreditServiceImpl implements CustomerCreditService {

    private static final Logger log = LoggerFactory.getLogger(CustomerCreditServiceImpl.class);

    private final CustomerCreditRepository creditRepository;
    private final UserRepository userRepository;

    public CustomerCreditServiceImpl(CustomerCreditRepository creditRepository, UserRepository userRepository) {
        this.creditRepository = creditRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public CreditEntryResponse issueCredit(long userId, IssueCreditRequest request,
                                           long issuedByUserId, Long sourceTicketId, Long sourceOrderIssueId) {
        if (sourceOrderIssueId != null) {
            java.util.Optional<CustomerCredit> existing = creditRepository.findFirstBySourceOrderIssueId(sourceOrderIssueId);
            if (existing.isPresent()) {
                log.info("Idempotent credit re-request for orderIssue={} — returning existing entry {}",
                        sourceOrderIssueId, existing.get().getId());
                return toResponse(existing.get());
            }
        }

        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        User issuedBy = userRepository.findById(issuedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + issuedByUserId));
        SecurityUtils.requireStaff(issuedBy);

        CustomerCredit entry = new CustomerCredit();
        entry.setUser(customer);
        entry.setAmountCents(request.amountCents());
        entry.setType(request.type());
        entry.setReason(request.reason());
        entry.setIssuedBy(issuedBy);
        entry.setSourceTicketId(sourceTicketId);
        entry.setSourceOrderIssueId(sourceOrderIssueId);
        entry.setExpiresAt(request.expiresAt());

        creditRepository.save(entry);
        log.info("Issued {} cents credit to user {} by staff {}", request.amountCents(), userId, issuedByUserId);
        return toResponse(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditBalanceResponse getBalance(long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        long balance = creditRepository.sumBalanceByUserId(userId, Instant.now());
        List<CreditEntryResponse> entries = creditRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();

        return new CreditBalanceResponse(userId, balance, "USD", entries);
    }

    @Override
    @Transactional
    public CreditEntryResponse redeemCredit(long userId, long orderId, long amountCents) {
        if (amountCents <= 0) {
            throw new BadRequestException("Redemption amount must be greater than zero");
        }

        // Pessimistic lock prevents concurrent double-spend
        creditRepository.findAllByUserIdForUpdate(userId);
        long balance = creditRepository.sumBalanceByUserId(userId, Instant.now());
        if (balance < amountCents) {
            throw new BadRequestException(
                    "Insufficient credit balance. Available: " + balance + " cents, requested: " + amountCents + " cents");
        }

        User customer = userRepository.getReferenceById(userId);
        CustomerCredit entry = new CustomerCredit();
        entry.setUser(customer);
        entry.setAmountCents(-amountCents);
        entry.setType(CreditEntryType.REDEEMED);
        entry.setRedeemedOnOrderId(orderId);

        creditRepository.save(entry);
        log.info("User {} redeemed {} cents credit on order {}", userId, amountCents, orderId);
        return toResponse(entry);
    }

    @Override
    @Transactional
    public CreditEntryResponse reverseCredit(long creditEntryId, long actorUserId) {
        CustomerCredit original = creditRepository.findById(creditEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit entry not found: " + creditEntryId));

        if (creditRepository.claimForReversal(original.getId()) == 0) {
            throw new BadRequestException("Cannot reverse an already reversed or expired credit entry");
        }

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        CustomerCredit reversal = new CustomerCredit();
        reversal.setUser(original.getUser());
        reversal.setAmountCents(-original.getAmountCents());
        reversal.setType(CreditEntryType.REVERSED);
        reversal.setReason("Reversal of credit entry #" + creditEntryId);
        reversal.setIssuedBy(actor);

        creditRepository.save(reversal);

        log.info("Staff {} reversed credit entry {} for user {}", actorUserId, creditEntryId, original.getUser().getId());
        return toResponse(reversal);
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 2 * * *")
    public void expireCredits() {
        List<CustomerCredit> expired = creditRepository.findExpiredCredits(Instant.now());
        int count = 0;
        for (CustomerCredit credit : expired) {
            if (creditRepository.claimForExpiry(credit.getId()) == 0) {
                continue; // already claimed by a concurrent scheduler run
            }
            CustomerCredit expiry = new CustomerCredit();
            expiry.setUser(credit.getUser());
            expiry.setAmountCents(-credit.getAmountCents());
            expiry.setType(CreditEntryType.EXPIRED);
            expiry.setReason("Credit expired — original entry #" + credit.getId());
            creditRepository.save(expiry);
            count++;
        }
        if (count > 0) {
            log.info("Expired {} customer credit entries", count);
        }
    }

    private CreditEntryResponse toResponse(CustomerCredit c) {
        return new CreditEntryResponse(
                c.getId(),
                c.getAmountCents(),
                c.getCurrency(),
                c.getType().name(),
                c.getReason(),
                c.getIssuedBy() != null ? c.getIssuedBy().getId() : null,
                c.getSourceTicketId(),
                c.getSourceOrderIssueId(),
                c.getRedeemedOnOrderId(),
                c.getExpiresAt(),
                c.getCreatedAt()
        );
    }
}
