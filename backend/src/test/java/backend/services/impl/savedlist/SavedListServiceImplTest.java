package backend.services.impl.savedlist;

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
import backend.exceptions.http.ResourceNotFoundException;
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
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SavedListServiceImplTest {

    private static final UUID USER_ID       = TestIds.uuid(1);
    private static final UUID LIST_ID       = TestIds.uuid(2);
    private static final UUID ITEM_ID       = TestIds.uuid(3);
    private static final UUID PRODUCT_ID    = TestIds.uuid(4);
    private static final UUID VARIANT_ID    = TestIds.uuid(5);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(6);

    private SavedListRepository savedListRepository;
    private SavedListItemRepository savedListItemRepository;
    private UserRepository userRepository;
    private ProductRepository productRepository;
    private ActivityEventPublisher activityEventPublisher;

    private SavedListServiceImpl service;

    @BeforeEach
    void setUp() {
        savedListRepository     = mock(SavedListRepository.class);
        savedListItemRepository = mock(SavedListItemRepository.class);
        userRepository          = mock(UserRepository.class);
        productRepository       = mock(ProductRepository.class);
        activityEventPublisher  = mock(ActivityEventPublisher.class);

        service = new SavedListServiceImpl(
                savedListRepository, savedListItemRepository,
                userRepository, productRepository, activityEventPublisher);

        // Common stubs
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(makeUser(USER_ID, "John", "Doe")));
        when(savedListRepository.save(any(SavedList.class))).thenAnswer(inv -> {
            SavedList l = inv.getArgument(0);
            if (l.getId() == null) l.setId(LIST_ID);
            return l;
        });
        when(savedListItemRepository.save(any(SavedListItem.class))).thenAnswer(inv -> {
            SavedListItem i = inv.getArgument(0);
            if (i.getId() == null) i.setId(ITEM_ID);
            return i;
        });
        // Default: slug uniqueness succeeds immediately
        when(savedListRepository.findByShareSlug(anyString())).thenReturn(Optional.empty());
    }

    // ─── listSavedLists ───────────────────────────────────────────────────────

    @Test
    void listSavedLists_noFilter_returnsAllLists() {
        when(savedListRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(makeSavedList(LIST_ID), makeSavedList(TestIds.uuid(9))));

        List<SavedListSummaryResponse> result = service.listSavedLists(USER_ID, null);

        assertEquals(2, result.size());
        verify(savedListRepository).findAllByUserIdOrderByCreatedAtDesc(USER_ID);
        verify(savedListRepository, never()).findAllByUserIdAndTypeOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listSavedLists_withTypeFilter_delegatesToFilteredQuery() {
        when(savedListRepository.findAllByUserIdAndTypeOrderByCreatedAtDesc(USER_ID, SavedListType.WISHLIST))
                .thenReturn(List.of(makeSavedList(LIST_ID)));

        List<SavedListSummaryResponse> result = service.listSavedLists(USER_ID, SavedListType.WISHLIST);

        assertEquals(1, result.size());
        verify(savedListRepository).findAllByUserIdAndTypeOrderByCreatedAtDesc(USER_ID, SavedListType.WISHLIST);
    }

    @Test
    void listSavedLists_itemCountAndPurchasedCountCorrect() {
        SavedList list = makeSavedList(LIST_ID);
        SavedListItem bought = makeItem(TestIds.uuid(10));
        bought.setPurchased(true);
        SavedListItem notBought = makeItem(TestIds.uuid(11));
        notBought.setPurchased(false);
        list.getItems().addAll(List.of(bought, notBought));
        when(savedListRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(list));

        List<SavedListSummaryResponse> result = service.listSavedLists(USER_ID, null);

        assertEquals(2, result.get(0).getItemCount());
        assertEquals(1, result.get(0).getPurchasedCount());
    }

    // ─── getSavedList ─────────────────────────────────────────────────────────

    @Test
    void getSavedList_happyPath_returnsResponse() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));

        SavedListResponse result = service.getSavedList(USER_ID, LIST_ID);

        assertNotNull(result);
        assertEquals("My List", result.getName());
    }

    @Test
    void getSavedList_notFound_throwsResourceNotFound() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getSavedList(USER_ID, LIST_ID));
    }

    // ─── getPublicSavedList ───────────────────────────────────────────────────

    @Test
    void getPublicSavedList_foundAndPublic_returnsResponse() {
        SavedList list = makeSavedList(LIST_ID);
        list.setPublic(true);
        list.setShareSlug("abc123");
        when(savedListRepository.findByShareSlug("abc123")).thenReturn(Optional.of(list));

        PublicSavedListResponse result = service.getPublicSavedList("abc123");

        assertNotNull(result);
        assertEquals("My List", result.getName());
    }

    @Test
    void getPublicSavedList_foundButPrivate_throwsResourceNotFound() {
        SavedList list = makeSavedList(LIST_ID);
        list.setPublic(false);
        list.setShareSlug("abc123");
        when(savedListRepository.findByShareSlug("abc123")).thenReturn(Optional.of(list));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getPublicSavedList("abc123"));
    }

    @Test
    void getPublicSavedList_slugNotFound_throwsResourceNotFound() {
        when(savedListRepository.findByShareSlug("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getPublicSavedList("unknown"));
    }

    @Test
    void getPublicSavedList_displayName_bothNames_formatsAsFirstLastInitial() {
        SavedList list = makeSavedList(LIST_ID);
        list.setPublic(true);
        list.setShareSlug("slug1");
        when(savedListRepository.findByShareSlug("slug1")).thenReturn(Optional.of(list));

        PublicSavedListResponse result = service.getPublicSavedList("slug1");

        assertEquals("John D.", result.getOwnerDisplayName());
    }

    @Test
    void getPublicSavedList_displayName_firstNameOnly() {
        SavedList list = makeSavedList(LIST_ID);
        list.getUser().setLastName(null);
        list.setPublic(true);
        list.setShareSlug("slug2");
        when(savedListRepository.findByShareSlug("slug2")).thenReturn(Optional.of(list));

        PublicSavedListResponse result = service.getPublicSavedList("slug2");

        assertEquals("John", result.getOwnerDisplayName());
    }

    @Test
    void getPublicSavedList_displayName_nullBothNames_returnsAnonymous() {
        SavedList list = makeSavedList(LIST_ID);
        list.getUser().setFirstName(null);
        list.getUser().setLastName(null);
        list.setPublic(true);
        list.setShareSlug("slug3");
        when(savedListRepository.findByShareSlug("slug3")).thenReturn(Optional.of(list));

        PublicSavedListResponse result = service.getPublicSavedList("slug3");

        assertEquals("Anonymous", result.getOwnerDisplayName());
    }

    // ─── createSavedList ──────────────────────────────────────────────────────

    @Test
    void createSavedList_happyPath_savesAndReturnsResponse() {
        when(savedListRepository.existsByNameAndUserIdAndType("My List", USER_ID, SavedListType.WISHLIST))
                .thenReturn(false);

        SavedListResponse result = service.createSavedList(USER_ID, makeCreateRequest("My List", SavedListType.WISHLIST, false));

        verify(savedListRepository).save(any(SavedList.class));
        assertEquals("My List", result.getName());
    }

    @Test
    void createSavedList_duplicateName_throwsConflict() {
        when(savedListRepository.existsByNameAndUserIdAndType("My List", USER_ID, SavedListType.WISHLIST))
                .thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.createSavedList(USER_ID, makeCreateRequest("My List", SavedListType.WISHLIST, false)));
    }

    @Test
    void createSavedList_userNotFound_throwsResourceNotFound() {
        when(savedListRepository.existsByNameAndUserIdAndType(any(), any(), any())).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createSavedList(USER_ID, makeCreateRequest("My List", SavedListType.WISHLIST, false)));
    }

    @Test
    void createSavedList_publicList_generatesAndAssignsSlug() {
        when(savedListRepository.existsByNameAndUserIdAndType(any(), any(), any())).thenReturn(false);

        SavedListResponse result = service.createSavedList(USER_ID, makeCreateRequest("My List", SavedListType.WISHLIST, true));

        assertNotNull(result.getShareSlug());
        assertFalse(result.getShareSlug().isBlank());
    }

    @Test
    void createSavedList_privateList_noSlugAssigned() {
        when(savedListRepository.existsByNameAndUserIdAndType(any(), any(), any())).thenReturn(false);

        SavedListResponse result = service.createSavedList(USER_ID, makeCreateRequest("My List", SavedListType.WISHLIST, false));

        assertNull(result.getShareSlug());
    }

    @Test
    void createSavedList_publicList_slugAlwaysTaken_throwsIllegalState() {
        when(savedListRepository.existsByNameAndUserIdAndType(any(), any(), any())).thenReturn(false);
        // Every slug attempt is already taken
        when(savedListRepository.findByShareSlug(anyString()))
                .thenReturn(Optional.of(makeSavedList(TestIds.uuid(99))));

        assertThrows(IllegalStateException.class,
                () -> service.createSavedList(USER_ID, makeCreateRequest("My List", SavedListType.WISHLIST, true)));
    }

    // ─── updateSavedList ──────────────────────────────────────────────────────

    @Test
    void updateSavedList_nameOnly_updatesName() {
        SavedList list = makeSavedList(LIST_ID);
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.of(list));
        when(savedListRepository.existsByNameAndUserIdAndType("New Name", USER_ID, SavedListType.WISHLIST))
                .thenReturn(false);

        UpdateSavedListRequest req = new UpdateSavedListRequest();
        req.setName("New Name");

        SavedListResponse result = service.updateSavedList(USER_ID, LIST_ID, req);

        assertEquals("New Name", result.getName());
    }

    @Test
    void updateSavedList_nameCollision_throwsConflict() {
        SavedList list = makeSavedList(LIST_ID);
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.of(list));
        when(savedListRepository.existsByNameAndUserIdAndType("Taken Name", USER_ID, SavedListType.WISHLIST))
                .thenReturn(true);

        UpdateSavedListRequest req = new UpdateSavedListRequest();
        req.setName("Taken Name");

        assertThrows(ConflictException.class,
                () -> service.updateSavedList(USER_ID, LIST_ID, req));
    }

    @Test
    void updateSavedList_sameNameNoTypeChange_skipsConflictCheck() {
        SavedList list = makeSavedList(LIST_ID); // name="My List", type=WISHLIST
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.of(list));

        UpdateSavedListRequest req = new UpdateSavedListRequest();
        req.setName("My List"); // same as current → nameChanged=false
        // type not set → typeChanged=false

        service.updateSavedList(USER_ID, LIST_ID, req);

        verify(savedListRepository, never()).existsByNameAndUserIdAndType(any(), any(), any());
    }

    @Test
    void updateSavedList_typeChange_triggersConflictCheck() {
        SavedList list = makeSavedList(LIST_ID); // type=WISHLIST
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.of(list));
        when(savedListRepository.existsByNameAndUserIdAndType("My List", USER_ID, SavedListType.GIFT))
                .thenReturn(false);

        UpdateSavedListRequest req = new UpdateSavedListRequest();
        req.setType(SavedListType.GIFT); // type changes → conflict check runs

        service.updateSavedList(USER_ID, LIST_ID, req);

        verify(savedListRepository).existsByNameAndUserIdAndType("My List", USER_ID, SavedListType.GIFT);
    }

    @Test
    void updateSavedList_privateToPublic_generatesSlug() {
        SavedList list = makeSavedList(LIST_ID);
        list.setPublic(false);
        list.setShareSlug(null);
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.of(list));

        UpdateSavedListRequest req = new UpdateSavedListRequest();
        req.setIsPublic(true);

        SavedListResponse result = service.updateSavedList(USER_ID, LIST_ID, req);

        assertTrue(result.isPublic());
        assertNotNull(result.getShareSlug());
    }

    @Test
    void updateSavedList_publicToPrivate_clearsSlug() {
        SavedList list = makeSavedList(LIST_ID);
        list.setPublic(true);
        list.setShareSlug("existing-slug");
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.of(list));

        UpdateSavedListRequest req = new UpdateSavedListRequest();
        req.setIsPublic(false);

        SavedListResponse result = service.updateSavedList(USER_ID, LIST_ID, req);

        assertFalse(result.isPublic());
        assertNull(result.getShareSlug());
    }

    @Test
    void updateSavedList_publicRemainsPublic_noSlugRegeneration() {
        SavedList list = makeSavedList(LIST_ID);
        list.setPublic(true);
        list.setShareSlug("existing-slug");
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.of(list));

        UpdateSavedListRequest req = new UpdateSavedListRequest();
        req.setIsPublic(true); // no change in value → no new slug generated

        SavedListResponse result = service.updateSavedList(USER_ID, LIST_ID, req);

        assertEquals("existing-slug", result.getShareSlug());
    }

    @Test
    void updateSavedList_notFound_throwsResourceNotFound() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateSavedList(USER_ID, LIST_ID, new UpdateSavedListRequest()));
    }

    // ─── deleteSavedList ──────────────────────────────────────────────────────

    @Test
    void deleteSavedList_happyPath_deletes() {
        SavedList list = makeSavedList(LIST_ID);
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.of(list));

        service.deleteSavedList(USER_ID, LIST_ID);

        verify(savedListRepository).delete(list);
    }

    @Test
    void deleteSavedList_notFound_throwsResourceNotFound() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteSavedList(USER_ID, LIST_ID));
    }

    // ─── addItem ──────────────────────────────────────────────────────────────

    @Test
    void addItem_noVariant_savesAndReturnsResponse() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(savedListItemRepository.existsBySavedListIdAndProductIdAndVariantIsNull(LIST_ID, PRODUCT_ID))
                .thenReturn(false);

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);
        req.setQuantity(2);

        SavedListItemResponse result = service.addItem(USER_ID, LIST_ID, req);

        assertNotNull(result);
        assertEquals(2, result.getQuantity());
        verify(savedListItemRepository).save(any(SavedListItem.class));
    }

    @Test
    void addItem_nullQuantity_defaultsToOne() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(savedListItemRepository.existsBySavedListIdAndProductIdAndVariantIsNull(LIST_ID, PRODUCT_ID))
                .thenReturn(false);

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);
        req.setQuantity(null);

        SavedListItemResponse result = service.addItem(USER_ID, LIST_ID, req);

        assertEquals(1, result.getQuantity());
    }

    @Test
    void addItem_withVariant_validVariant_savesCorrectly() {
        Product product = makeProduct(PRODUCT_ID);
        ProductVariant variant = makeVariant(VARIANT_ID, "SKU-1");
        product.setVariants(new ArrayList<>(List.of(variant)));
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(savedListItemRepository.existsBySavedListIdAndProductIdAndVariantId(LIST_ID, PRODUCT_ID, VARIANT_ID))
                .thenReturn(false);

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);
        req.setVariantId(VARIANT_ID);

        SavedListItemResponse result = service.addItem(USER_ID, LIST_ID, req);

        assertEquals(VARIANT_ID, result.getVariantId());
        assertEquals("SKU-1", result.getVariantSku());
    }

    @Test
    void addItem_variantNotInProduct_throwsBadRequest() {
        Product product = makeProduct(PRODUCT_ID);
        product.setVariants(new ArrayList<>()); // no variants
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);
        req.setVariantId(VARIANT_ID); // not in product's variants

        assertThrows(BadRequestException.class,
                () -> service.addItem(USER_ID, LIST_ID, req));
    }

    @Test
    void addItem_productNotFound_throwsResourceNotFound() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);

        assertThrows(ResourceNotFoundException.class,
                () -> service.addItem(USER_ID, LIST_ID, req));
    }

    @Test
    void addItem_duplicateNoVariant_throwsConflict() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(savedListItemRepository.existsBySavedListIdAndProductIdAndVariantIsNull(LIST_ID, PRODUCT_ID))
                .thenReturn(true); // already in list

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);

        assertThrows(ConflictException.class,
                () -> service.addItem(USER_ID, LIST_ID, req));
    }

    @Test
    void addItem_duplicateWithVariant_throwsConflict() {
        Product product = makeProduct(PRODUCT_ID);
        ProductVariant variant = makeVariant(VARIANT_ID, "SKU-1");
        product.setVariants(new ArrayList<>(List.of(variant)));
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(savedListItemRepository.existsBySavedListIdAndProductIdAndVariantId(LIST_ID, PRODUCT_ID, VARIANT_ID))
                .thenReturn(true); // already in list with same variant

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);
        req.setVariantId(VARIANT_ID);

        assertThrows(ConflictException.class,
                () -> service.addItem(USER_ID, LIST_ID, req));
    }

    @Test
    void addItem_marketplaceProduct_publishesAddActivity() {
        Product product = makeProduct(PRODUCT_ID);
        product.setMarketplaceId(MARKETPLACE_ID);
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(savedListItemRepository.existsBySavedListIdAndProductIdAndVariantIsNull(LIST_ID, PRODUCT_ID))
                .thenReturn(false);

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);

        service.addItem(USER_ID, LIST_ID, req);

        ArgumentCaptor<UserActivityEvent> captor = ArgumentCaptor.forClass(UserActivityEvent.class);
        verify(activityEventPublisher).publish(captor.capture());
        assertEquals(ActivityType.SAVED_LIST_ADD, captor.getValue().activityType());
        assertEquals(MARKETPLACE_ID, captor.getValue().marketplaceId());
    }

    @Test
    void addItem_noMarketplaceId_noActivityPublished() {
        Product product = makeProduct(PRODUCT_ID); // marketplaceId = null by default
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(savedListItemRepository.existsBySavedListIdAndProductIdAndVariantIsNull(LIST_ID, PRODUCT_ID))
                .thenReturn(false);

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);

        service.addItem(USER_ID, LIST_ID, req);

        verify(activityEventPublisher, never()).publish(any());
    }

    @Test
    void addItem_listNotFound_throwsResourceNotFound() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.empty());

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);

        assertThrows(ResourceNotFoundException.class,
                () -> service.addItem(USER_ID, LIST_ID, req));
    }

    // ─── updateItem ───────────────────────────────────────────────────────────

    @Test
    void updateItem_quantity_updatesQuantity() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        SavedListItem item = makeItem(ITEM_ID);
        when(savedListItemRepository.findByIdAndSavedListId(ITEM_ID, LIST_ID))
                .thenReturn(Optional.of(item));

        UpdateSavedListItemRequest req = new UpdateSavedListItemRequest();
        req.setQuantity(5);

        SavedListItemResponse result = service.updateItem(USER_ID, LIST_ID, ITEM_ID, req);

        assertEquals(5, result.getQuantity());
    }

    @Test
    void updateItem_purchasedTrue_setsPurchasedAt() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        SavedListItem item = makeItem(ITEM_ID);
        item.setPurchased(false);
        item.setPurchasedAt(null);
        when(savedListItemRepository.findByIdAndSavedListId(ITEM_ID, LIST_ID))
                .thenReturn(Optional.of(item));

        UpdateSavedListItemRequest req = new UpdateSavedListItemRequest();
        req.setPurchased(true);

        service.updateItem(USER_ID, LIST_ID, ITEM_ID, req);

        assertTrue(item.isPurchased());
        assertNotNull(item.getPurchasedAt());
    }

    @Test
    void updateItem_purchasedFalse_clearsPurchasedAt() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        SavedListItem item = makeItem(ITEM_ID);
        item.setPurchased(true);
        item.setPurchasedAt(java.time.Instant.now());
        when(savedListItemRepository.findByIdAndSavedListId(ITEM_ID, LIST_ID))
                .thenReturn(Optional.of(item));

        UpdateSavedListItemRequest req = new UpdateSavedListItemRequest();
        req.setPurchased(false);

        service.updateItem(USER_ID, LIST_ID, ITEM_ID, req);

        assertFalse(item.isPurchased());
        assertNull(item.getPurchasedAt());
    }

    @Test
    void updateItem_purchasedSameValue_noChangeToTimestamp() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        SavedListItem item = makeItem(ITEM_ID);
        item.setPurchased(true);
        java.time.Instant original = java.time.Instant.now().minusSeconds(3600);
        item.setPurchasedAt(original);
        when(savedListItemRepository.findByIdAndSavedListId(ITEM_ID, LIST_ID))
                .thenReturn(Optional.of(item));

        UpdateSavedListItemRequest req = new UpdateSavedListItemRequest();
        req.setPurchased(true); // same value → no change

        service.updateItem(USER_ID, LIST_ID, ITEM_ID, req);

        assertEquals(original, item.getPurchasedAt()); // timestamp unchanged
    }

    @Test
    void updateItem_itemNotFound_throwsResourceNotFound() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(savedListItemRepository.findByIdAndSavedListId(ITEM_ID, LIST_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateItem(USER_ID, LIST_ID, ITEM_ID, new UpdateSavedListItemRequest()));
    }

    @Test
    void updateItem_listNotFound_throwsResourceNotFound() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateItem(USER_ID, LIST_ID, ITEM_ID, new UpdateSavedListItemRequest()));
    }

    // ─── removeItem ───────────────────────────────────────────────────────────

    @Test
    void removeItem_happyPath_deletesItem() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        SavedListItem item = makeItem(ITEM_ID);
        when(savedListItemRepository.findByIdAndSavedListId(ITEM_ID, LIST_ID))
                .thenReturn(Optional.of(item));

        service.removeItem(USER_ID, LIST_ID, ITEM_ID);

        verify(savedListItemRepository).delete(item);
    }

    @Test
    void removeItem_marketplaceProduct_publishesRemoveActivity() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        Product product = makeProduct(PRODUCT_ID);
        product.setMarketplaceId(MARKETPLACE_ID);
        SavedListItem item = makeItem(ITEM_ID);
        item.setProduct(product);
        when(savedListItemRepository.findByIdAndSavedListId(ITEM_ID, LIST_ID))
                .thenReturn(Optional.of(item));

        service.removeItem(USER_ID, LIST_ID, ITEM_ID);

        ArgumentCaptor<UserActivityEvent> captor = ArgumentCaptor.forClass(UserActivityEvent.class);
        verify(activityEventPublisher).publish(captor.capture());
        assertEquals(ActivityType.SAVED_LIST_REMOVE, captor.getValue().activityType());
    }

    @Test
    void removeItem_noMarketplaceId_noActivityPublished() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        SavedListItem item = makeItem(ITEM_ID); // product has no marketplaceId
        when(savedListItemRepository.findByIdAndSavedListId(ITEM_ID, LIST_ID))
                .thenReturn(Optional.of(item));

        service.removeItem(USER_ID, LIST_ID, ITEM_ID);

        verify(activityEventPublisher, never()).publish(any());
    }

    @Test
    void removeItem_itemNotFound_throwsResourceNotFound() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID))
                .thenReturn(Optional.of(makeSavedList(LIST_ID)));
        when(savedListItemRepository.findByIdAndSavedListId(ITEM_ID, LIST_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.removeItem(USER_ID, LIST_ID, ITEM_ID));
    }

    @Test
    void removeItem_listNotFound_throwsResourceNotFound() {
        when(savedListRepository.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.removeItem(USER_ID, LIST_ID, ITEM_ID));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private User makeUser(UUID id, String firstName, String lastName) {
        User u = new User();
        u.setId(id);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(firstName.toLowerCase() + "@test.com");
        return u;
    }

    private SavedList makeSavedList(UUID id) {
        SavedList l = new SavedList();
        l.setId(id);
        l.setUser(makeUser(USER_ID, "John", "Doe"));
        l.setName("My List");
        l.setType(SavedListType.WISHLIST);
        l.setItems(new ArrayList<>());
        return l;
    }

    private Product makeProduct(UUID id) {
        Product p = new Product();
        p.setId(id);
        p.setName("Test Product");
        p.setPrice(new BigDecimal("19.99"));
        p.setCurrency("USD");
        p.setVariants(new ArrayList<>());
        p.setImages(new ArrayList<>());
        p.setOptions(new ArrayList<>());
        p.setAttributes(new ArrayList<>());
        // marketplaceId is null by default
        return p;
    }

    private ProductVariant makeVariant(UUID id, String sku) {
        ProductVariant v = new ProductVariant();
        v.setId(id);
        v.setSku(sku);
        v.setPrice(new BigDecimal("19.99"));
        return v;
    }

    private SavedListItem makeItem(UUID id) {
        SavedListItem item = new SavedListItem();
        item.setId(id);
        item.setProduct(makeProduct(PRODUCT_ID));
        item.setQuantity(1);
        return item;
    }

    private CreateSavedListRequest makeCreateRequest(String name, SavedListType type, boolean isPublic) {
        CreateSavedListRequest req = new CreateSavedListRequest();
        req.setName(name);
        req.setType(type);
        req.setPublic(isPublic);
        return req;
    }

    // ─── Premium tier: saved-list cap ────────────────────────────────────────

    @Test
    void createSavedList_freeUserAtCap_throwsPremiumRequired() {
        User freeUser = makeUser(USER_ID, "John", "Doe");
        // tier defaults to FREE; 5 lists already exist
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(freeUser));
        when(savedListRepository.existsByNameAndUserIdAndType(anyString(), eq(USER_ID), any())).thenReturn(false);
        when(savedListRepository.countByUserId(USER_ID)).thenReturn(5L);

        assertThrows(backend.exceptions.http.PremiumRequiredException.class,
                () -> service.createSavedList(USER_ID, makeCreateRequest("New List", SavedListType.WISHLIST, false)));
    }

    @Test
    void createSavedList_freeUserBelowCap_succeeds() {
        User freeUser = makeUser(USER_ID, "John", "Doe");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(freeUser));
        when(savedListRepository.existsByNameAndUserIdAndType(anyString(), eq(USER_ID), any())).thenReturn(false);
        when(savedListRepository.countByUserId(USER_ID)).thenReturn(4L);

        assertDoesNotThrow(
                () -> service.createSavedList(USER_ID, makeCreateRequest("New List", SavedListType.WISHLIST, false)));
    }

    @Test
    void createSavedList_premiumUserAboveCap_succeeds() {
        User premiumUser = makeUser(USER_ID, "John", "Doe");
        premiumUser.setTier(backend.models.enums.UserTier.PREMIUM);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser));
        when(savedListRepository.existsByNameAndUserIdAndType(anyString(), eq(USER_ID), any())).thenReturn(false);

        assertDoesNotThrow(
                () -> service.createSavedList(USER_ID, makeCreateRequest("List 6", SavedListType.WISHLIST, false)));

        // countByUserId must NOT be called for premium users
        verify(savedListRepository, never()).countByUserId(any());
    }
}
