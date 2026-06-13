package backend.services.impl.savedlist;

import java.util.UUID;
import backend.dtos.requests.savedlist.AddSavedListItemRequest;
import backend.dtos.requests.savedlist.CreateSavedListRequest;
import backend.dtos.requests.savedlist.UpdateSavedListItemRequest;
import backend.dtos.requests.savedlist.UpdateSavedListRequest;
import backend.dtos.responses.savedlist.PublicSavedListResponse;
import backend.dtos.responses.savedlist.SavedListItemResponse;
import backend.dtos.responses.savedlist.SavedListResponse;
import backend.dtos.responses.savedlist.SavedListSummaryResponse;
import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.PremiumRequiredException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.UserTier;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.SavedList;
import backend.models.core.SavedListItem;
import backend.models.core.SavedListType;
import backend.models.core.User;
import backend.repositories.ProductRepository;
import backend.repositories.SavedListItemRepository;
import backend.repositories.SavedListRepository;
import backend.repositories.UserRepository;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.savedlist.SavedListService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Service
public class SavedListServiceImpl implements SavedListService {

    private static final String SLUG_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SLUG_LENGTH = 12;
    private static final int SLUG_MAX_ATTEMPTS = 5;

    private final SavedListRepository savedListRepository;
    private final SavedListItemRepository savedListItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ActivityEventPublisher activityEventPublisher;
    private final SecureRandom random = new SecureRandom();

    public SavedListServiceImpl(SavedListRepository savedListRepository,
                                SavedListItemRepository savedListItemRepository,
                                UserRepository userRepository,
                                ProductRepository productRepository,
                                ActivityEventPublisher activityEventPublisher) {
        this.savedListRepository = savedListRepository;
        this.savedListItemRepository = savedListItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.activityEventPublisher = activityEventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedListSummaryResponse> listSavedLists(UUID userId, SavedListType typeFilter) {
        List<SavedList> lists = (typeFilter == null)
                ? savedListRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                : savedListRepository.findAllByUserIdAndTypeOrderByCreatedAtDesc(userId, typeFilter);
        return lists.stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SavedListResponse getSavedList(UUID userId, UUID listId) {
        return toResponse(findOwned(userId, listId));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicSavedListResponse getPublicSavedList(String shareSlug) {
        SavedList list = savedListRepository.findByShareSlug(shareSlug)
                .filter(SavedList::isPublic)
                .orElseThrow(() -> new ResourceNotFoundException("Public list not found"));
        return toPublicResponse(list);
    }

    @Override
    @Transactional
    public SavedListResponse createSavedList(UUID userId, CreateSavedListRequest req) {
        SavedListType type = req.getType();
        if (savedListRepository.existsByNameAndUserIdAndType(req.getName(), userId, type)) {
            throw new ConflictException("A " + type.name().toLowerCase()
                    + " list named '" + req.getName() + "' already exists");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getTier() == UserTier.FREE && savedListRepository.countByUserId(userId) >= 5) {
            throw new PremiumRequiredException(
                    "Free accounts are limited to 5 saved lists. Upgrade to Premium for unlimited lists.");
        }

        SavedList list = new SavedList();
        list.setUser(user);
        list.setName(req.getName());
        list.setType(type);
        list.setDescription(req.getDescription());
        list.setPublic(req.isPublic());
        if (req.isPublic()) {
            list.setShareSlug(generateUniqueSlug());
        }

        return toResponse(savedListRepository.save(list));
    }

    @Override
    @Transactional
    public SavedListResponse updateSavedList(UUID userId, UUID listId, UpdateSavedListRequest req) {
        SavedList list = findOwned(userId, listId);

        SavedListType targetType = req.getType() != null ? req.getType() : list.getType();
        String targetName = req.getName() != null ? req.getName() : list.getName();

        boolean nameChanged = req.getName() != null && !req.getName().equals(list.getName());
        boolean typeChanged = req.getType() != null && req.getType() != list.getType();
        if ((nameChanged || typeChanged)
                && savedListRepository.existsByNameAndUserIdAndType(targetName, userId, targetType)) {
            throw new ConflictException("A " + targetType.name().toLowerCase()
                    + " list named '" + targetName + "' already exists");
        }

        if (nameChanged) list.setName(req.getName());
        if (typeChanged) list.setType(req.getType());
        if (req.getDescription() != null) list.setDescription(req.getDescription());

        if (req.getIsPublic() != null && req.getIsPublic() != list.isPublic()) {
            list.setPublic(req.getIsPublic());
            if (req.getIsPublic()) {
                list.setShareSlug(generateUniqueSlug());
            } else {
                list.setShareSlug(null);
            }
        }

        return toResponse(savedListRepository.save(list));
    }

    @Override
    @Transactional
    public void deleteSavedList(UUID userId, UUID listId) {
        savedListRepository.delete(findOwned(userId, listId));
    }

    @Override
    @Transactional
    public SavedListItemResponse addItem(UUID userId, UUID listId, AddSavedListItemRequest req) {
        SavedList list = findOwned(userId, listId);

        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + req.getProductId()));

        ProductVariant variant = null;
        if (req.getVariantId() != null) {
            variant = product.getVariants().stream()
                    .filter(v -> v.getId().equals(req.getVariantId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "Variant " + req.getVariantId() + " does not belong to product " + req.getProductId()));
        }

        boolean duplicate = (variant != null)
                ? savedListItemRepository.existsBySavedListIdAndProductIdAndVariantId(
                        listId, req.getProductId(), variant.getId())
                : savedListItemRepository.existsBySavedListIdAndProductIdAndVariantIsNull(
                        listId, req.getProductId());

        if (duplicate) {
            throw new ConflictException("This item is already in the list");
        }

        SavedListItem item = new SavedListItem();
        item.setSavedList(list);
        item.setProduct(product);
        item.setVariant(variant);
        item.setQuantity(req.getQuantity() != null ? req.getQuantity() : 1);
        item.setNote(req.getNote());

        SavedListItemResponse response = toItemResponse(savedListItemRepository.save(item));

        UUID marketplaceId = product.getMarketplaceId();
        if (marketplaceId != null) {
            activityEventPublisher.publish(new UserActivityEvent(
                    userId, null, product.getId(), marketplaceId, ActivityType.SAVED_LIST_ADD, Instant.now()));
        }

        return response;
    }

    @Override
    @Transactional
    public SavedListItemResponse updateItem(UUID userId, UUID listId, UUID itemId,
                                            UpdateSavedListItemRequest req) {
        findOwned(userId, listId); // ownership check
        SavedListItem item = savedListItemRepository.findByIdAndSavedListId(itemId, listId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item not found with id: " + itemId));

        if (req.getQuantity() != null) item.setQuantity(req.getQuantity());
        if (req.getNote() != null) item.setNote(req.getNote());
        if (req.getPurchased() != null && req.getPurchased() != item.isPurchased()) {
            item.setPurchased(req.getPurchased());
            item.setPurchasedAt(req.getPurchased() ? Instant.now() : null);
        }

        return toItemResponse(savedListItemRepository.save(item));
    }

    @Override
    @Transactional
    public void removeItem(UUID userId, UUID listId, UUID itemId) {
        findOwned(userId, listId); // ownership check
        SavedListItem item = savedListItemRepository.findByIdAndSavedListId(itemId, listId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item not found with id: " + itemId));

        // Capture before delete to avoid lazy-loading a detached entity.
        UUID productId = item.getProduct() != null ? item.getProduct().getId() : null;
        UUID marketplaceId = item.getProduct() != null ? item.getProduct().getMarketplaceId() : null;

        savedListItemRepository.delete(item);

        if (productId != null && marketplaceId != null) {
            activityEventPublisher.publish(new UserActivityEvent(
                    userId, null, productId, marketplaceId, ActivityType.SAVED_LIST_REMOVE, Instant.now()));
        }
    }

    // -------------------------------------------------------------------------

    private SavedList findOwned(UUID userId, UUID listId) {
        return savedListRepository.findByIdAndUserId(listId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Saved list not found with id: " + listId));
    }

    private String generateUniqueSlug() {
        for (int attempt = 0; attempt < SLUG_MAX_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder(SLUG_LENGTH);
            for (int i = 0; i < SLUG_LENGTH; i++) {
                sb.append(SLUG_ALPHABET.charAt(random.nextInt(SLUG_ALPHABET.length())));
            }
            String slug = sb.toString();
            if (savedListRepository.findByShareSlug(slug).isEmpty()) return slug;
        }
        throw new IllegalStateException("Could not generate unique share slug");
    }

    private SavedListSummaryResponse toSummary(SavedList l) {
        int total = l.getItems().size();
        int purchased = (int) l.getItems().stream().filter(SavedListItem::isPurchased).count();
        return new SavedListSummaryResponse(
                l.getId(),
                l.getUser().getId(),
                l.getName(),
                l.getType(),
                l.getDescription(),
                l.isPublic(),
                l.getShareSlug(),
                total,
                purchased,
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }

    private SavedListResponse toResponse(SavedList l) {
        List<SavedListItemResponse> items = l.getItems().stream()
                .map(this::toItemResponse).toList();
        return new SavedListResponse(
                l.getId(),
                l.getUser().getId(),
                l.getName(),
                l.getType(),
                l.getDescription(),
                l.isPublic(),
                l.getShareSlug(),
                items,
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }

    private PublicSavedListResponse toPublicResponse(SavedList l) {
        // Public rendering must not leak products that aren't publicly visible: a product saved while
        // ACTIVE+listed may since have been unlisted/archived. Only expose ACTIVE + listed items.
        List<SavedListItemResponse> items = l.getItems().stream()
                .filter(i -> i.getProduct() != null
                        && i.getProduct().getStatus() == backend.models.enums.ProductStatus.ACTIVE
                        && i.getProduct().isListed())
                .map(this::toItemResponse).toList();
        User owner = l.getUser();
        String displayName = buildDisplayName(owner);
        return new PublicSavedListResponse(
                l.getId(),
                displayName,
                l.getName(),
                l.getType(),
                l.getDescription(),
                l.getShareSlug(),
                items,
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }

    private String buildDisplayName(User user) {
        if (user == null) return "Anonymous";
        String first = user.getFirstName();
        String last = user.getLastName();
        if (first != null && last != null) return first + " " + last.charAt(0) + ".";
        if (first != null) return first;
        if (last != null) return last;
        return "Anonymous";
    }

    private SavedListItemResponse toItemResponse(SavedListItem i) {
        Product p = i.getProduct();
        return new SavedListItemResponse(
                i.getId(),
                p.getId(),
                p.getName(),
                p.getThumbnailUrl(),
                i.getVariant() != null ? i.getVariant().getId() : null,
                i.getVariant() != null ? i.getVariant().getSku() : null,
                i.getQuantity(),
                i.getNote(),
                i.isPurchased(),
                i.getPurchasedAt(),
                i.getAddedAt()
        );
    }
}
