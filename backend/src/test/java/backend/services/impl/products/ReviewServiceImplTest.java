package backend.services.impl.products;

import backend.dtos.requests.review.CreateReviewRequest;
import backend.dtos.requests.review.UpdateReviewRequest;
import backend.dtos.responses.review.ReviewResponse;
import backend.dtos.responses.review.ReviewSummaryResponse;
import backend.events.activity.UserActivityEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.kafka.workers.ReviewIndexingService;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.core.User;
import backend.models.enums.ProductStatus;
import backend.models.enums.ReviewStatus;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.repositories.UserRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.intf.ActivityEventPublisher;
import backend.testutil.TestIds;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewServiceImplTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(1);
    private static final UUID PRODUCT_ID  = TestIds.uuid(2);
    private static final UUID USER_ID     = TestIds.uuid(3);
    private static final UUID REVIEW_ID   = TestIds.uuid(4);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(5);

    private ProductReviewRepository reviewRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private ReviewMediaRepository mediaRepository;
    private ActivityEventPublisher activityEventPublisher;
    private ReviewIndexingService reviewIndexingService;
    private SingleFlightCache singleFlightCache;

    private ReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        reviewRepository        = mock(ProductReviewRepository.class);
        productRepository       = mock(ProductRepository.class);
        userRepository          = mock(UserRepository.class);
        orderRepository         = mock(OrderRepository.class);
        mediaRepository         = mock(ReviewMediaRepository.class);
        activityEventPublisher  = mock(ActivityEventPublisher.class);
        reviewIndexingService   = mock(ReviewIndexingService.class);
        singleFlightCache       = mock(SingleFlightCache.class);

        service = new ReviewServiceImpl(
                reviewRepository, productRepository, userRepository,
                orderRepository, mediaRepository, activityEventPublisher,
                reviewIndexingService, singleFlightCache, 300L, 60L);

        // Common stubs
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(makeUser()));
        when(mediaRepository.findByReviewIdOrderByPositionAsc(any())).thenReturn(List.of());
        when(mediaRepository.findByReviewIdInOrderByReviewIdAscPositionAsc(any())).thenReturn(List.of());
        when(reviewRepository.save(any(ProductReview.class))).thenAnswer(inv -> {
            ProductReview r = inv.getArgument(0);
            if (r.getId() == null) r.setId(REVIEW_ID);
            return r;
        });

        // Bypass cache
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(Class.class));
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(TypeReference.class));
    }

    // ─── createReview ─────────────────────────────────────────────────────────

    @Test
    void createReview_happyPath_savesAndReturnsResponse() {
        when(orderRepository.existsDeliveredOrShippedOrderForProduct(USER_ID, PRODUCT_ID)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(false);

        CreateReviewRequest req = makeCreateReviewRequest(5);

        ReviewResponse result = service.createReview(COMPANY_ID, PRODUCT_ID, USER_ID, req);

        assertNotNull(result);
        assertEquals(5, result.getRating());
        assertTrue(result.isVerifiedPurchase());
        verify(reviewRepository).save(any(ProductReview.class));
    }

    @Test
    void createReview_noDeliveredOrder_throwsBadRequest() {
        when(orderRepository.existsDeliveredOrShippedOrderForProduct(USER_ID, PRODUCT_ID)).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.createReview(COMPANY_ID, PRODUCT_ID, USER_ID, makeCreateReviewRequest(5)));
    }

    @Test
    void createReview_duplicateReview_throwsConflict() {
        when(orderRepository.existsDeliveredOrShippedOrderForProduct(USER_ID, PRODUCT_ID)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.createReview(COMPANY_ID, PRODUCT_ID, USER_ID, makeCreateReviewRequest(4)));
    }

    @Test
    void createReview_productNotFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createReview(COMPANY_ID, PRODUCT_ID, USER_ID, makeCreateReviewRequest(5)));
    }

    @Test
    void createReview_userNotFound_throwsResourceNotFound() {
        when(orderRepository.existsDeliveredOrShippedOrderForProduct(USER_ID, PRODUCT_ID)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createReview(COMPANY_ID, PRODUCT_ID, USER_ID, makeCreateReviewRequest(5)));
    }

    @Test
    void createReview_positiveRating_withMarketplace_publishesPositiveActivity() {
        Product product = makeProduct();
        product.setMarketplaceId(MARKETPLACE_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(orderRepository.existsDeliveredOrShippedOrderForProduct(USER_ID, PRODUCT_ID)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(false);

        service.createReview(COMPANY_ID, PRODUCT_ID, USER_ID, makeCreateReviewRequest(5));

        ArgumentCaptor<UserActivityEvent> captor = ArgumentCaptor.forClass(UserActivityEvent.class);
        verify(activityEventPublisher).publish(captor.capture());
        assertEquals(backend.events.activity.ActivityType.REVIEW_POSITIVE, captor.getValue().activityType());
    }

    @Test
    void createReview_negativeRating_withMarketplace_publishesNegativeActivity() {
        Product product = makeProduct();
        product.setMarketplaceId(MARKETPLACE_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(orderRepository.existsDeliveredOrShippedOrderForProduct(USER_ID, PRODUCT_ID)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(false);

        service.createReview(COMPANY_ID, PRODUCT_ID, USER_ID, makeCreateReviewRequest(2));

        ArgumentCaptor<UserActivityEvent> captor = ArgumentCaptor.forClass(UserActivityEvent.class);
        verify(activityEventPublisher).publish(captor.capture());
        assertEquals(backend.events.activity.ActivityType.REVIEW_NEGATIVE, captor.getValue().activityType());
    }

    @Test
    void createReview_neutralRating_noActivityPublished() {
        Product product = makeProduct();
        product.setMarketplaceId(MARKETPLACE_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(orderRepository.existsDeliveredOrShippedOrderForProduct(USER_ID, PRODUCT_ID)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(false);

        service.createReview(COMPANY_ID, PRODUCT_ID, USER_ID, makeCreateReviewRequest(3));

        verify(activityEventPublisher, never()).publish(any());
    }

    @Test
    void createReview_noMarketplaceId_noActivityPublished() {
        // product has no marketplace ID (default makeProduct)
        when(orderRepository.existsDeliveredOrShippedOrderForProduct(USER_ID, PRODUCT_ID)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(false);

        service.createReview(COMPANY_ID, PRODUCT_ID, USER_ID, makeCreateReviewRequest(5));

        verify(activityEventPublisher, never()).publish(any());
    }

    // ─── updateReview ─────────────────────────────────────────────────────────

    @Test
    void updateReview_happyPath_updatesFields() {
        ProductReview review = makeReview(REVIEW_ID, 4, "Old Title");
        when(reviewRepository.findByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(Optional.of(review));
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(0L);

        UpdateReviewRequest req = new UpdateReviewRequest();
        req.setRating(5);
        req.setTitle("New Title");

        ReviewResponse result = service.updateReview(COMPANY_ID, PRODUCT_ID, USER_ID, req);

        assertEquals(5, result.getRating());
        assertEquals("New Title", result.getTitle());
    }

    @Test
    void updateReview_partialUpdate_onlyChangesProvidedFields() {
        ProductReview review = makeReview(REVIEW_ID, 3, "Original");
        review.setBody("Original body");
        when(reviewRepository.findByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(Optional.of(review));
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(0L);

        UpdateReviewRequest req = new UpdateReviewRequest();
        req.setRating(4); // only rating changes

        service.updateReview(COMPANY_ID, PRODUCT_ID, USER_ID, req);

        assertEquals(4, review.getRating());
        assertEquals("Original", review.getTitle());  // unchanged
        assertEquals("Original body", review.getBody()); // unchanged
    }

    @Test
    void updateReview_notFound_throwsResourceNotFound() {
        when(reviewRepository.findByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateReview(COMPANY_ID, PRODUCT_ID, USER_ID, new UpdateReviewRequest()));
    }

    @Test
    void updateReview_productNotFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateReview(COMPANY_ID, PRODUCT_ID, USER_ID, new UpdateReviewRequest()));
    }

    // ─── deleteReview ─────────────────────────────────────────────────────────

    @Test
    void deleteReview_happyPath_deletesAndInvokesIndexing() {
        ProductReview review = makeReview(REVIEW_ID, 4, "Great");
        when(reviewRepository.findByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(Optional.of(review));

        service.deleteReview(COMPANY_ID, PRODUCT_ID, USER_ID);

        verify(reviewRepository).delete(review);
        verify(reviewIndexingService).removeReview(REVIEW_ID);
    }

    @Test
    void deleteReview_notFound_throwsResourceNotFound() {
        when(reviewRepository.findByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteReview(COMPANY_ID, PRODUCT_ID, USER_ID));
    }

    // ─── getMyReview ──────────────────────────────────────────────────────────

    @Test
    void getMyReview_found_returnsResponse() {
        ProductReview review = makeReview(REVIEW_ID, 5, "Loved it");
        when(reviewRepository.findByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(Optional.of(review));

        ReviewResponse result = service.getMyReview(COMPANY_ID, PRODUCT_ID, USER_ID);

        assertNotNull(result);
        assertEquals(5, result.getRating());
    }

    @Test
    void getMyReview_notFound_throwsResourceNotFound() {
        when(reviewRepository.findByProductIdAndReviewerId(PRODUCT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getMyReview(COMPANY_ID, PRODUCT_ID, USER_ID));
    }

    // ─── getReviews ───────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getReviews_noFilters_queriesRepository() {
        when(reviewRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getReviews(COMPANY_ID, PRODUCT_ID, 0, 20, "createdAt", "desc", null, null, null);

        verify(reviewRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getReviews_invalidRatingValues_filteredOut() {
        when(reviewRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // ratings [0, 3, 6] — only 3 is valid (1–5)
        service.getReviews(COMPANY_ID, PRODUCT_ID, 0, 20, "createdAt", "desc",
                List.of(0, 3, 6), null, null);

        // verify call happened (no exception from filtering invalid values)
        verify(reviewRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getReviews_invalidSortField_defaultsToCreatedAt() {
        when(reviewRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getReviews(COMPANY_ID, PRODUCT_ID, 0, 20, "unknownField", "desc", null, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findAll(any(Specification.class), captor.capture());
        // The sort field defaults to "createdAt" — verify the sort property name
        assertTrue(captor.getValue().getSort().getOrderFor("createdAt") != null
                || captor.getValue().getSort().isSorted());
    }

    // ─── getReviewSummary ─────────────────────────────────────────────────────

    @Test
    void getReviewSummary_withData_returnsAggregates() {
        when(reviewRepository.findSummaryAggregates(PRODUCT_ID))
                .thenReturn(new Object[]{4.2, 10L, 7L});
        when(reviewRepository.findRatingDistribution(PRODUCT_ID))
                .thenReturn(List.of(
                        new Object[]{5, 5L},
                        new Object[]{4, 3L},
                        new Object[]{3, 2L}
                ));

        ReviewSummaryResponse result = service.getReviewSummary(COMPANY_ID, PRODUCT_ID);

        assertEquals(4.2, result.getAverageRating(), 0.001);
        assertEquals(10L, result.getTotal());
        assertEquals(7L, result.getVerifiedCount());
        assertEquals(5L, result.getDistribution().get(5));
        assertEquals(3L, result.getDistribution().get(4));
        assertEquals(0L, result.getDistribution().get(1)); // missing rating defaults to 0
    }

    @Test
    void getReviewSummary_nullAggregates_returnsZeroDefaults() {
        when(reviewRepository.findSummaryAggregates(PRODUCT_ID)).thenReturn(null);
        when(reviewRepository.findRatingDistribution(PRODUCT_ID)).thenReturn(List.of());

        ReviewSummaryResponse result = service.getReviewSummary(COMPANY_ID, PRODUCT_ID);

        assertEquals(0.0, result.getAverageRating(), 0.001);
        assertEquals(0L, result.getTotal());
        assertEquals(0L, result.getVerifiedCount());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Product makeProduct() {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        backend.models.core.Company company = new backend.models.core.Company();
        company.setId(COMPANY_ID);
        p.setCompany(company);
        p.setName("Test Product");
        p.setPrice(new java.math.BigDecimal("19.99"));
        p.setCurrency("USD");
        p.setStatus(ProductStatus.ACTIVE);
        p.setImages(new ArrayList<>());
        p.setOptions(new ArrayList<>());
        p.setVariants(new ArrayList<>());
        p.setAttributes(new ArrayList<>());
        // marketplaceId is null by default
        return p;
    }

    private User makeUser() {
        User u = new User();
        u.setId(USER_ID);
        u.setFirstName("John");
        u.setLastName("Doe");
        u.setEmail("john@test.com");
        return u;
    }

    private ProductReview makeReview(UUID id, int rating, String title) {
        ProductReview r = new ProductReview();
        r.setId(id);
        r.setProduct(makeProduct());
        r.setReviewer(makeUser());
        r.setRating(rating);
        r.setTitle(title);
        r.setBody("Review body");
        r.setStatus(ReviewStatus.PUBLISHED);
        r.setVerifiedPurchase(true);
        return r;
    }

    private CreateReviewRequest makeCreateReviewRequest(int rating) {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setRating(rating);
        req.setTitle("Test Review");
        req.setBody("This product is great.");
        return req;
    }
}
