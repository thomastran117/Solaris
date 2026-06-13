package backend.services.impl.products;

import backend.dtos.requests.product.CreateKitRequest;
import backend.dtos.requests.product.ImportFromCollectionRequest;
import backend.dtos.requests.product.KitSlotChoiceRequest;
import backend.dtos.requests.product.KitSlotRequest;
import backend.dtos.requests.product.UpdateKitRequest;
import backend.dtos.responses.product.KitResponse;
import backend.dtos.responses.product.KitSlotChoiceResponse;
import backend.dtos.responses.product.KitSlotResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CollectionProduct;
import backend.models.core.Company;
import backend.models.core.KitSlot;
import backend.models.core.KitSlotChoice;
import backend.models.core.Product;
import backend.models.core.ProductKit;
import backend.models.core.ProductVariant;
import backend.models.enums.CompanyCapability;
import backend.models.enums.ProductStatus;
import backend.repositories.CollectionProductRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductKitRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KitServiceImplTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(1);
    private static final UUID OWNER_ID    = TestIds.uuid(2);
    private static final UUID KIT_ID      = TestIds.uuid(3);
    private static final UUID PRODUCT_A   = TestIds.uuid(4);
    private static final UUID PRODUCT_B   = TestIds.uuid(5);
    private static final UUID VARIANT_ID  = TestIds.uuid(6);
    private static final UUID SLOT_ID     = TestIds.uuid(7);
    private static final UUID COLLECTION_ID = TestIds.uuid(8);

    private ProductKitRepository kitRepository;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private CollectionProductRepository collectionProductRepository;
    private OrderRepository orderRepository;
    private CompanyAccessService companyAccessService;
    private SingleFlightCache singleFlightCache;

    private KitServiceImpl service;

    @BeforeEach
    void setUp() {
        kitRepository                = mock(ProductKitRepository.class);
        productRepository            = mock(ProductRepository.class);
        variantRepository            = mock(ProductVariantRepository.class);
        collectionProductRepository  = mock(CollectionProductRepository.class);
        orderRepository              = mock(OrderRepository.class);
        companyAccessService         = mock(CompanyAccessService.class);
        singleFlightCache            = mock(SingleFlightCache.class);

        service = new KitServiceImpl(
                kitRepository, productRepository, variantRepository,
                collectionProductRepository, orderRepository, companyAccessService, singleFlightCache, 300L);

        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), any(CompanyCapability.class)))
                .thenReturn(makeCompany());
        when(kitRepository.save(any(ProductKit.class))).thenAnswer(inv -> {
            ProductKit k = inv.getArgument(0);
            if (k.getId() == null) k.setId(KIT_ID);
            return k;
        });

        // Bypass cache
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(Class.class));
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(TypeReference.class));
    }

    // ─── createKit ────────────────────────────────────────────────────────────

    @Test
    void createKit_singleSlotSingleChoice_succeeds() {
        Product product = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(product));

        KitResponse result = service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(
                List.of(slotReq("CPU", true, 1, 1, List.of(choiceReq(PRODUCT_A, null, null, true))))));

        assertNotNull(result);
        assertEquals("Test Kit", result.getName());
        assertEquals(1, result.getSlots().size());
        assertEquals(1, result.getSlots().get(0).getChoices().size());
        assertEquals(PRODUCT_A, result.getSlots().get(0).getChoices().get(0).getProductId());
    }

    @Test
    void createKit_priceDeltaSurcharge_reflectedInEffectivePrice() {
        Product product = makeProduct(PRODUCT_A, new BigDecimal("100.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(product));

        KitResponse result = service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(
                List.of(slotReq("CPU", true, 1, 1,
                        List.of(choiceReq(PRODUCT_A, null, new BigDecimal("20.00"), false))))));

        KitSlotChoiceResponse choice = result.getSlots().get(0).getChoices().get(0);
        assertEquals(new BigDecimal("100.00"), choice.getBasePrice());
        assertEquals(new BigDecimal("20.00"), choice.getPriceDelta());
        assertEquals(new BigDecimal("120.00"), choice.getEffectivePrice());
    }

    @Test
    void createKit_variantChoice_usesVariantPrice() {
        Product product = makeProduct(PRODUCT_A, new BigDecimal("200.00"));
        ProductVariant variant = makeVariant(VARIANT_ID, new BigDecimal("150.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_A)).thenReturn(Optional.of(variant));

        KitResponse result = service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(
                List.of(slotReq("GPU", true, 1, 1,
                        List.of(choiceReq(PRODUCT_A, VARIANT_ID, null, false))))));

        KitSlotChoiceResponse choice = result.getSlots().get(0).getChoices().get(0);
        assertEquals(new BigDecimal("150.00"), choice.getBasePrice());
    }

    @Test
    void createKit_minQtyExceedsMaxQty_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(
                        List.of(slotReq("CPU", true, 5, 2, List.of())))));
    }

    @Test
    void createKit_multipleDuplicateDefaultChoices_throwsBadRequest() {
        Product product = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class, () ->
                service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(List.of(
                        slotReq("CPU", true, 1, 1, List.of(
                                choiceReq(PRODUCT_A, null, null, true),
                                choiceReq(PRODUCT_A, VARIANT_ID, null, true)))))));
    }

    @Test
    void createKit_productNotFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(
                        List.of(slotReq("CPU", true, 1, 1,
                                List.of(choiceReq(PRODUCT_A, null, null, false)))))));
    }

    @Test
    void createKit_variantNotFound_throwsResourceNotFound() {
        Product product = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_A)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(
                        List.of(slotReq("GPU", true, 1, 1,
                                List.of(choiceReq(PRODUCT_A, VARIANT_ID, null, false)))))));
    }

    @Test
    void createKit_nullCurrency_defaultsToUsd() {
        Product product = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(product));

        CreateKitRequest req = makeCreateRequest(
                List.of(slotReq("CPU", true, 1, 1, List.of(choiceReq(PRODUCT_A, null, null, false)))));
        req.setCurrency(null);

        KitResponse result = service.createKit(COMPANY_ID, OWNER_ID, req);
        assertEquals("USD", result.getCurrency());
    }

    // ─── computedMinPrice / computedMaxPrice ──────────────────────────────────

    @Test
    void createKit_twoChoicesInSlot_computedPriceRange() {
        Product cheap     = makeProduct(PRODUCT_A, new BigDecimal("50.00"));
        Product expensive = makeProduct(PRODUCT_B, new BigDecimal("150.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(cheap));
        when(productRepository.findByIdAndCompanyId(PRODUCT_B, COMPANY_ID)).thenReturn(Optional.of(expensive));

        KitResponse result = service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(List.of(
                slotReq("CPU", true, 1, 2, List.of(
                        choiceReq(PRODUCT_A, null, null, false),
                        choiceReq(PRODUCT_B, null, null, false))))));

        // min: cheapest ($50) × minQty (1) = $50; max: expensive ($150) × maxQty (2) = $300
        assertEquals(new BigDecimal("50.00"), result.getComputedMinPrice());
        assertEquals(new BigDecimal("300.00"), result.getComputedMaxPrice());
    }

    // ─── kitPurchasable ───────────────────────────────────────────────────────

    @Test
    void createKit_requiredSlotAllChoicesOos_purchasableFalse() {
        Product oos = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        oos.setStock(0);
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(oos));

        KitResponse result = service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(
                List.of(slotReq("CPU", true, 1, 1, List.of(choiceReq(PRODUCT_A, null, null, false))))));

        assertFalse(result.isPurchasable());
    }

    @Test
    void createKit_optionalSlotAllChoicesOos_purchasableStillTrue() {
        Product oos = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        oos.setStock(0);
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(oos));

        KitResponse result = service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(
                List.of(slotReq("Extras", false, 1, 1, List.of(choiceReq(PRODUCT_A, null, null, false))))));

        assertTrue(result.isPurchasable());
    }

    @Test
    void createKit_requiredSlotOneInStockChoice_purchasableTrue() {
        Product inStock = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        inStock.setStock(5);
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(inStock));

        KitResponse result = service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(
                List.of(slotReq("CPU", true, 1, 1, List.of(choiceReq(PRODUCT_A, null, null, false))))));

        assertTrue(result.isPurchasable());
    }

    // ─── updateKit ────────────────────────────────────────────────────────────

    @Test
    void updateKit_nameOnly_updatesName() {
        ProductKit kit = makeKit(KIT_ID);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        UpdateKitRequest req = new UpdateKitRequest();
        req.setName("New Name");

        KitResponse result = service.updateKit(COMPANY_ID, KIT_ID, OWNER_ID, req);
        assertEquals("New Name", result.getName());
    }

    @Test
    void updateKit_emptySlotsInRequest_throwsBadRequest() {
        ProductKit kit = makeKit(KIT_ID);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        UpdateKitRequest req = new UpdateKitRequest();
        req.setSlots(List.of());

        assertThrows(BadRequestException.class,
                () -> service.updateKit(COMPANY_ID, KIT_ID, OWNER_ID, req));
    }

    @Test
    void updateKit_nullSlots_leavesExistingSlots() {
        ProductKit kit = makeKit(KIT_ID);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        UpdateKitRequest req = new UpdateKitRequest();
        req.setName("Renamed");
        // slots == null → do not touch existing slots

        service.updateKit(COMPANY_ID, KIT_ID, OWNER_ID, req);
        verify(kitRepository).save(argThat(k -> !k.getSlots().isEmpty()));
    }

    @Test
    void updateKit_notFound_throwsResourceNotFound() {
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateKit(COMPANY_ID, KIT_ID, OWNER_ID, new UpdateKitRequest()));
    }

    // ─── deleteKit ────────────────────────────────────────────────────────────

    @Test
    void deleteKit_happyPath_delegatesToRepository() {
        ProductKit kit = makeKit(KIT_ID);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        service.deleteKit(COMPANY_ID, KIT_ID, OWNER_ID);

        verify(kitRepository).delete(kit);
    }

    @Test
    void deleteKit_notFound_throwsResourceNotFound() {
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteKit(COMPANY_ID, KIT_ID, OWNER_ID));
    }

    @Test
    void deleteKit_referencedByAnyOrder_throwsConflict() {
        // Deletion is blocked while ANY order (including delivered/cancelled history) references it.
        ProductKit kit = makeKit(KIT_ID);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));
        when(orderRepository.existsAnyOrderWithKit(KIT_ID)).thenReturn(true);

        assertThrows(backend.exceptions.http.ConflictException.class,
                () -> service.deleteKit(COMPANY_ID, KIT_ID, OWNER_ID));
        verify(kitRepository, never()).delete(any());
    }

    @Test
    void deleteKit_noOrderReferences_proceeds() {
        ProductKit kit = makeKit(KIT_ID);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));
        when(orderRepository.existsAnyOrderWithKit(KIT_ID)).thenReturn(false);

        service.deleteKit(COMPANY_ID, KIT_ID, OWNER_ID);

        verify(kitRepository).delete(kit);
    }

    // ─── getKit (public) ──────────────────────────────────────────────────────

    @Test
    void getKit_activeKit_returnsResponse() {
        ProductKit kit = makeKit(KIT_ID);
        kit.setStatus(ProductStatus.ACTIVE);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        KitResponse result = service.getKit(COMPANY_ID, KIT_ID);
        assertEquals("Test Kit", result.getName());
    }

    @Test
    void getKit_draftKit_throwsResourceNotFound() {
        ProductKit kit = makeKit(KIT_ID);
        kit.setStatus(ProductStatus.DRAFT);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getKit(COMPANY_ID, KIT_ID));
    }

    @Test
    void getKit_notFound_throwsResourceNotFound() {
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.getKit(COMPANY_ID, KIT_ID));
    }

    // ─── listKits (public) ────────────────────────────────────────────────────

    @Test
    void listKits_alwaysRestrictsToActive() {
        when(kitRepository.findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listKits(COMPANY_ID, ProductStatus.DRAFT, 0, 20);

        verify(kitRepository).findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE), any(Pageable.class));
    }

    @Test
    void listKits_clampsPageSizeTo50() {
        when(kitRepository.findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listKits(COMPANY_ID, null, 0, 200);

        verify(kitRepository).findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE),
                argThat(p -> p.getPageSize() == 50));
    }

    // ─── importChoicesFromCollection ──────────────────────────────────────────

    @Test
    void importChoicesFromCollection_addsNewChoicesSkipsDuplicates() {
        Product existing = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        Product newProd  = makeProduct(PRODUCT_B, new BigDecimal("20.00"));

        // Kit with one slot that already has PRODUCT_A as a choice
        ProductKit kit  = makeKit(KIT_ID);
        KitSlot slot    = kit.getSlots().get(0);
        slot.setId(SLOT_ID);
        KitSlotChoice existingChoice = new KitSlotChoice();
        existingChoice.setProduct(existing);
        existingChoice.setVariant(null);
        existingChoice.setDisplayOrder(0);
        slot.getChoices().add(existingChoice);

        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        // Collection contains PRODUCT_A (already a choice) and PRODUCT_B (new)
        CollectionProduct cpA = new CollectionProduct();
        cpA.setProduct(existing);
        CollectionProduct cpB = new CollectionProduct();
        cpB.setProduct(newProd);
        when(collectionProductRepository.findAllByCollectionId(COLLECTION_ID)).thenReturn(List.of(cpA, cpB));

        ImportFromCollectionRequest req = new ImportFromCollectionRequest();
        req.setCollectionId(COLLECTION_ID);
        req.setSlotId(SLOT_ID);

        KitResponse result = service.importChoicesFromCollection(COMPANY_ID, KIT_ID, OWNER_ID, req);

        // slot should now have 2 choices: the original + the newly imported one
        assertEquals(2, result.getSlots().get(0).getChoices().size());
    }

    @Test
    void importChoicesFromCollection_emptyCollection_throwsBadRequest() {
        ProductKit kit = makeKit(KIT_ID);
        kit.getSlots().get(0).setId(SLOT_ID);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));
        when(collectionProductRepository.findAllByCollectionId(COLLECTION_ID)).thenReturn(List.of());

        ImportFromCollectionRequest req = new ImportFromCollectionRequest();
        req.setCollectionId(COLLECTION_ID);
        req.setSlotId(SLOT_ID);

        assertThrows(BadRequestException.class,
                () -> service.importChoicesFromCollection(COMPANY_ID, KIT_ID, OWNER_ID, req));
    }

    @Test
    void importChoicesFromCollection_unknownSlot_throwsResourceNotFound() {
        ProductKit kit = makeKit(KIT_ID);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        ImportFromCollectionRequest req = new ImportFromCollectionRequest();
        req.setCollectionId(COLLECTION_ID);
        req.setSlotId(TestIds.uuid(99)); // does not exist on the kit

        assertThrows(ResourceNotFoundException.class,
                () -> service.importChoicesFromCollection(COMPANY_ID, KIT_ID, OWNER_ID, req));
    }

    // ─── listKits / getKit (authenticated owner API) ─────────────────────────

    @Test
    void listKits_authenticatedOwner_delegatesToPublicListing() {
        when(kitRepository.findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listKits(COMPANY_ID, OWNER_ID, null, 0, 10);

        verify(companyAccessService).require(eq(COMPANY_ID), eq(OWNER_ID), any());
        verify(kitRepository).findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE), any(Pageable.class));
    }

    @Test
    void getKit_authenticatedOwner_returnsKitResponse() {
        ProductKit kit = makeKit(KIT_ID);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        KitResponse result = service.getKit(COMPANY_ID, KIT_ID, OWNER_ID);

        verify(companyAccessService).require(eq(COMPANY_ID), eq(OWNER_ID), any());
        assertNotNull(result);
        assertEquals("Test Kit", result.getName());
    }

    @Test
    void getKit_authenticatedOwner_notFound_throwsResourceNotFound() {
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getKit(COMPANY_ID, KIT_ID, OWNER_ID));
    }

    // ─── updateKit with valid new slots (success path) ───────────────────────

    @Test
    void updateKit_withValidNewSlots_replacesSlots() {
        ProductKit kit = makeKit(KIT_ID);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        Product product = makeProduct(PRODUCT_A, new BigDecimal("50.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(product));

        UpdateKitRequest req = new UpdateKitRequest();
        req.setSlots(List.of(slotReq("New Slot", true, 1, 2,
                List.of(choiceReq(PRODUCT_A, null, null, true)))));

        KitResponse result = service.updateKit(COMPANY_ID, KIT_ID, OWNER_ID, req);

        assertEquals(1, result.getSlots().size());
        assertEquals("New Slot", result.getSlots().get(0).getName());
    }

    @Test
    void updateKit_withStatusChange_updatesStatus() {
        ProductKit kit = makeKit(KIT_ID);
        kit.setStatus(ProductStatus.DRAFT);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        UpdateKitRequest req = new UpdateKitRequest();
        req.setStatus(ProductStatus.ACTIVE);

        KitResponse result = service.updateKit(COMPANY_ID, KIT_ID, OWNER_ID, req);

        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void updateKit_withCurrencyAndListed_updatesBothFields() {
        ProductKit kit = makeKit(KIT_ID);
        kit.setListed(false);
        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));

        UpdateKitRequest req = new UpdateKitRequest();
        req.setCurrency("EUR");
        req.setListed(true);

        KitResponse result = service.updateKit(COMPANY_ID, KIT_ID, OWNER_ID, req);

        assertEquals("EUR", result.getCurrency());
        assertTrue(result.isListed());
    }

    @Test
    void createKit_withCompareAtPriceAndStatus_setsFields() {
        Product product = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(product));

        CreateKitRequest req = makeCreateRequest(
                List.of(slotReq("CPU", true, 1, 1, List.of(choiceReq(PRODUCT_A, null, null, false)))));
        req.setStatus(ProductStatus.ACTIVE);
        req.setCompareAtPrice(new BigDecimal("99.00"));

        KitResponse result = service.createKit(COMPANY_ID, OWNER_ID, req);

        assertEquals("ACTIVE", result.getStatus());
        assertEquals(new BigDecimal("99.00"), result.getCompareAtPrice());
    }

    @Test
    void importChoicesFromCollection_emptySlotChoices_usesZeroDisplayOrder() {
        ProductKit kit = makeKit(KIT_ID);
        KitSlot slot = kit.getSlots().get(0);
        slot.setId(SLOT_ID);
        // slot starts with no choices
        assertTrue(slot.getChoices().isEmpty());

        Product newProd = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        CollectionProduct cp = new CollectionProduct();
        cp.setProduct(newProd);

        when(kitRepository.findByIdAndCompanyId(KIT_ID, COMPANY_ID)).thenReturn(Optional.of(kit));
        when(collectionProductRepository.findAllByCollectionId(COLLECTION_ID)).thenReturn(List.of(cp));

        ImportFromCollectionRequest req = new ImportFromCollectionRequest();
        req.setCollectionId(COLLECTION_ID);
        req.setSlotId(SLOT_ID);

        KitResponse result = service.importChoicesFromCollection(COMPANY_ID, KIT_ID, OWNER_ID, req);

        assertEquals(1, result.getSlots().get(0).getChoices().size());
        assertEquals(0, result.getSlots().get(0).getChoices().get(0).getDisplayOrder());
    }

    @Test
    void createKit_variantNotPurchasable_purchasableFalse() {
        Product product = makeProduct(PRODUCT_A, new BigDecimal("10.00"));
        product.setStatus(ProductStatus.ACTIVE);
        product.setPurchasable(true);

        ProductVariant variant = makeVariant(VARIANT_ID, new BigDecimal("10.00"));
        variant.setPurchasable(false); // not purchasable → choiceInStock returns false

        when(productRepository.findByIdAndCompanyId(PRODUCT_A, COMPANY_ID)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_A)).thenReturn(Optional.of(variant));

        KitResponse result = service.createKit(COMPANY_ID, OWNER_ID, makeCreateRequest(
                List.of(slotReq("CPU", true, 1, 1,
                        List.of(choiceReq(PRODUCT_A, VARIANT_ID, null, false))))));

        assertFalse(result.isPurchasable());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Company makeCompany() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        c.setName("Test Company");
        return c;
    }

    private Product makeProduct(UUID id, BigDecimal price) {
        Product p = new Product();
        p.setId(id);
        p.setCompany(makeCompany());
        p.setName("Product " + id);
        p.setPrice(price);
        p.setCurrency("USD");
        p.setStatus(ProductStatus.ACTIVE);
        p.setPurchasable(true);
        p.setImages(new ArrayList<>());
        p.setOptions(new ArrayList<>());
        p.setVariants(new ArrayList<>());
        p.setAttributes(new ArrayList<>());
        return p;
    }

    private ProductVariant makeVariant(UUID id, BigDecimal price) {
        ProductVariant v = new ProductVariant();
        v.setId(id);
        v.setPrice(price);
        v.setPurchasable(true);
        return v;
    }

    /** Kit with one placeholder slot (no choices). */
    private ProductKit makeKit(UUID id) {
        ProductKit k = new ProductKit();
        k.setId(id);
        k.setCompany(makeCompany());
        k.setName("Test Kit");
        k.setCurrency("USD");
        k.setStatus(ProductStatus.ACTIVE);
        k.setListed(true);

        KitSlot slot = new KitSlot();
        slot.setId(SLOT_ID);
        slot.setKit(k);
        slot.setName("Slot 1");
        slot.setRequired(true);
        slot.setMinQty(1);
        slot.setMaxQty(1);
        slot.setDisplayOrder(0);
        slot.setChoices(new ArrayList<>());
        k.setSlots(new ArrayList<>(List.of(slot)));
        return k;
    }

    private CreateKitRequest makeCreateRequest(List<KitSlotRequest> slots) {
        CreateKitRequest req = new CreateKitRequest();
        req.setName("Test Kit");
        req.setCurrency("USD");
        req.setSlots(slots);
        return req;
    }

    private KitSlotRequest slotReq(String name, boolean required, int minQty, int maxQty,
                                    List<KitSlotChoiceRequest> choices) {
        KitSlotRequest r = new KitSlotRequest();
        r.setName(name);
        r.setRequired(required);
        r.setMinQty(minQty);
        r.setMaxQty(maxQty);
        r.setChoices(choices);
        return r;
    }

    private KitSlotChoiceRequest choiceReq(UUID productId, UUID variantId,
                                            BigDecimal priceDelta, boolean defaultChoice) {
        KitSlotChoiceRequest r = new KitSlotChoiceRequest();
        r.setProductId(productId);
        r.setVariantId(variantId);
        r.setPriceDelta(priceDelta);
        r.setDefaultChoice(defaultChoice);
        return r;
    }
}
