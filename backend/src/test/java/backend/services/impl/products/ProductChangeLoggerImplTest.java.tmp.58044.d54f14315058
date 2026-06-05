package backend.services.impl.products;

import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductChangeLog;
import backend.models.core.ProductVariant;
import backend.models.core.User;
import backend.models.enums.ChangeSource;
import backend.models.enums.ProductStatus;
import backend.repositories.ProductChangeLogRepository;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.AuditorAware;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductChangeLoggerImplTest {

    private static final UUID PRODUCT_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID ACTOR_ID   = TestIds.uuid(3);

    private ProductChangeLogRepository changeLogRepository;
    private UserRepository             userRepository;

    @SuppressWarnings("unchecked")
    private AuditorAware<UUID> auditorAware;

    private ProductChangeLoggerImpl logger;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        changeLogRepository = mock(ProductChangeLogRepository.class);
        userRepository      = mock(UserRepository.class);
        auditorAware        = mock(AuditorAware.class);

        logger = new ProductChangeLoggerImpl(changeLogRepository, userRepository, auditorAware);
    }

    // ─── logCreate ────────────────────────────────────────────────────────────

    @Test
    void logCreate_allFieldsSet_savesRowsForEachNonNullField() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        Product p = product("Widget", "SKU-1", new BigDecimal("19.99"));

        ArgumentCaptor<List<ProductChangeLog>> captor = ArgumentCaptor.forClass(List.class);
        logger.logCreate(p, ChangeSource.USER);

        verify(changeLogRepository).saveAll(captor.capture());
        assertFalse(captor.getValue().isEmpty());
    }

    @Test
    void logCreate_nullFieldValue_excludedFromRows() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        Product p = product("Widget", null, new BigDecimal("19.99")); // sku is null

        ArgumentCaptor<List<ProductChangeLog>> captor = ArgumentCaptor.forClass(List.class);
        logger.logCreate(p, ChangeSource.USER);

        verify(changeLogRepository).saveAll(captor.capture());
        boolean hasSku = captor.getValue().stream()
                .anyMatch(row -> "sku".equals(row.getFieldName()));
        assertFalse(hasSku, "null sku field should be excluded");
    }

    // ─── logUpdate ────────────────────────────────────────────────────────────

    @Test
    void logUpdate_noChanges_saveAllNeverCalled() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        Product before = product("Widget", "SKU-1", new BigDecimal("19.99"));
        Product after  = product("Widget", "SKU-1", new BigDecimal("19.99"));

        logger.logUpdate(before, after, ChangeSource.USER, null);

        verify(changeLogRepository, never()).saveAll(anyList());
    }

    @Test
    void logUpdate_oneFieldChanged_savesOneRow() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        Product before = product("Widget", "SKU-1", new BigDecimal("19.99"));
        Product after  = product("Widget Updated", "SKU-1", new BigDecimal("19.99"));

        ArgumentCaptor<List<ProductChangeLog>> captor = ArgumentCaptor.forClass(List.class);
        logger.logUpdate(before, after, ChangeSource.USER, null);

        verify(changeLogRepository).saveAll(captor.capture());
        List<ProductChangeLog> rows = captor.getValue();
        assertEquals(1, rows.size());
        assertEquals("name", rows.get(0).getFieldName());
        assertEquals("Widget", rows.get(0).getOldValue());
        assertEquals("Widget Updated", rows.get(0).getNewValue());
    }

    // ─── logDelete ────────────────────────────────────────────────────────────

    @Test
    void logDelete_savesEntityRow() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        Product p = product("Widget", "SKU-1", new BigDecimal("19.99"));

        ArgumentCaptor<ProductChangeLog> captor = ArgumentCaptor.forClass(ProductChangeLog.class);
        logger.logDelete(p);

        verify(changeLogRepository).save(captor.capture());
        assertEquals("__entity__", captor.getValue().getFieldName());
        assertEquals("EXISTS", captor.getValue().getOldValue());
        assertEquals("DELETED", captor.getValue().getNewValue());
    }

    // ─── logVariantCreate ─────────────────────────────────────────────────────

    @Test
    void logVariantCreate_allFieldsSet_savesRows() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        ProductVariant variant = variant(new BigDecimal("9.99"), "OPT-A");

        ArgumentCaptor<List<ProductChangeLog>> captor = ArgumentCaptor.forClass(List.class);
        logger.logVariantCreate(variant, ChangeSource.USER);

        verify(changeLogRepository).saveAll(captor.capture());
        assertFalse(captor.getValue().isEmpty());
    }

    @Test
    void logVariantUpdate_noChanges_saveAllNeverCalled() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        ProductVariant before = variant(new BigDecimal("9.99"), "OPT-A");
        ProductVariant after  = variant(new BigDecimal("9.99"), "OPT-A");

        logger.logVariantUpdate(before, after, ChangeSource.USER, null);

        verify(changeLogRepository, never()).saveAll(anyList());
    }

    @Test
    void logVariantDelete_savesEntityRow() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        ProductVariant v = variant(new BigDecimal("9.99"), "OPT-A");

        ArgumentCaptor<ProductChangeLog> captor = ArgumentCaptor.forClass(ProductChangeLog.class);
        logger.logVariantDelete(v);

        verify(changeLogRepository).save(captor.capture());
        assertEquals("DELETED", captor.getValue().getNewValue());
    }

    // ─── currentActor ─────────────────────────────────────────────────────────

    @Test
    void logCreate_auditorAwareEmpty_changedByIsNull() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        Product p = product("Widget", "SKU-1", new BigDecimal("19.99"));

        ArgumentCaptor<List<ProductChangeLog>> captor = ArgumentCaptor.forClass(List.class);
        logger.logCreate(p, ChangeSource.USER);

        verify(changeLogRepository).saveAll(captor.capture());
        ProductChangeLog row = captor.getValue().get(0);
        assertNull(row.getChangedBy());
    }

    // ─── snapshot ─────────────────────────────────────────────────────────────

    @Test
    void snapshot_product_copiesAllTrackableFields() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        Product p = product("Widget", "SKU-1", new BigDecimal("19.99"));
        p.setDescription("A widget");
        p.setCompareAtPrice(new BigDecimal("24.99"));
        p.setCurrency("USD");
        p.setCategory("Tools");
        p.setBrand("Acme");
        p.setTags("tag1,tag2");
        p.setThumbnailUrl("http://img.test/1.jpg");
        p.setWeight(new BigDecimal("0.5"));
        p.setWeightUnit("kg");
        p.setStatus(ProductStatus.ACTIVE);

        Product copy = logger.snapshot(p);

        assertEquals(p.getId(), copy.getId());
        assertEquals("Widget", copy.getName());
        assertEquals("A widget", copy.getDescription());
        assertEquals("SKU-1", copy.getSku());
        assertEquals(new BigDecimal("19.99"), copy.getPrice());
        assertEquals(new BigDecimal("24.99"), copy.getCompareAtPrice());
        assertEquals("USD", copy.getCurrency());
        assertEquals("Tools", copy.getCategory());
        assertEquals("Acme", copy.getBrand());
        assertEquals("tag1,tag2", copy.getTags());
        assertEquals("http://img.test/1.jpg", copy.getThumbnailUrl());
        assertEquals(new BigDecimal("0.5"), copy.getWeight());
        assertEquals("kg", copy.getWeightUnit());
        assertEquals(ProductStatus.ACTIVE, copy.getStatus());
        assertTrue(copy.isPurchasable());
        assertFalse(copy.isBackorderEnabled());
        assertFalse(copy.isSubscribable());
        assertFalse(copy.isMarketplaceListed());
        assertFalse(copy.isAutoRestockEnabled());
    }

    @Test
    void snapshot_variant_copiesAllTrackableFields() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        ProductVariant v = variant(new BigDecimal("9.99"), "Red");
        v.setOption2("Large");
        v.setOption3("Matte");
        v.setCompareAtPrice(new BigDecimal("12.99"));
        v.setLowStockThreshold(5);
        v.setDisplayOrder(2);

        ProductVariant copy = logger.snapshot(v);

        assertEquals(v.getId(), copy.getId());
        assertEquals(new BigDecimal("9.99"), copy.getPrice());
        assertEquals(new BigDecimal("12.99"), copy.getCompareAtPrice());
        assertEquals(5, copy.getLowStockThreshold());
        assertTrue(copy.isPurchasable());
        assertFalse(copy.isBackorderEnabled());
        assertFalse(copy.isAutoRestockEnabled());
        assertEquals("Red", copy.getOption1());
        assertEquals("Large", copy.getOption2());
        assertEquals("Matte", copy.getOption3());
        assertEquals(2, copy.getDisplayOrder());
    }

    @Test
    void logCreate_auditorAwareHasActor_changedByIsSet() {
        User user = new User();
        user.setId(ACTOR_ID);
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(ACTOR_ID));
        when(userRepository.getReferenceById(ACTOR_ID)).thenReturn(user);

        Product p = product("Widget", "SKU-1", new BigDecimal("19.99"));

        ArgumentCaptor<List<ProductChangeLog>> captor = ArgumentCaptor.forClass(List.class);
        logger.logCreate(p, ChangeSource.USER);

        verify(changeLogRepository).saveAll(captor.capture());
        ProductChangeLog row = captor.getValue().get(0);
        assertEquals(ACTOR_ID, row.getChangedBy().getId());
    }

    @Test
    void logVariantUpdate_oneFieldChanged_savesOneRow() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        ProductVariant before = variant(new BigDecimal("9.99"), "Red");
        ProductVariant after  = variant(new BigDecimal("14.99"), "Red"); // price changed

        ArgumentCaptor<List<ProductChangeLog>> captor = ArgumentCaptor.forClass(List.class);
        logger.logVariantUpdate(before, after, ChangeSource.USER, null);

        verify(changeLogRepository).saveAll(captor.capture());
        List<ProductChangeLog> rows = captor.getValue();
        assertEquals(1, rows.size());
        assertEquals("price", rows.get(0).getFieldName());
        assertEquals("9.99", rows.get(0).getOldValue());
        assertEquals("14.99", rows.get(0).getNewValue());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Product product(String name, String sku, BigDecimal price) {
        Company company = new Company();
        company.setId(COMPANY_ID);

        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setCompany(company);
        p.setName(name);
        p.setSku(sku);
        p.setPrice(price);
        p.setFeatured(false);
        p.setPurchasable(true);
        p.setListed(true);
        p.setBackorderEnabled(false);
        p.setSubscribable(false);
        p.setMarketplaceListed(false);
        p.setAutoRestockEnabled(false);
        return p;
    }

    private ProductVariant variant(BigDecimal price, String option1) {
        Product p = product("Widget", "SKU-1", new BigDecimal("19.99"));
        ProductVariant v = new ProductVariant();
        v.setId(TestIds.uuid(20));
        v.setProduct(p);
        v.setPrice(price);
        v.setOption1(option1);
        v.setPurchasable(true);
        v.setBackorderEnabled(false);
        v.setAutoRestockEnabled(false);
        v.setDisplayOrder(0);
        return v;
    }
}
