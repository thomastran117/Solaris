package backend.controllers.impl.admin;

import backend.configurations.application.GlobalExceptionHandler;
import backend.kafka.workers.IndexVersionManager;
import backend.kafka.workers.ProductIndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SearchAdminControllerTest {

    private IndexVersionManager indexVersionManager;
    private ProductIndexingService productIndexingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        indexVersionManager   = mock(IndexVersionManager.class);
        productIndexingService = mock(ProductIndexingService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SearchAdminController(indexVersionManager, productIndexingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── POST /admin/search/reindex ───────────────────────────────────────────

    @Test
    void reindex_returns200WithMessage() throws Exception {
        mockMvc.perform(post("/admin/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void reindex_callsRolloverBeforeReindexAll() throws Exception {
        mockMvc.perform(post("/admin/search/reindex"));

        verify(indexVersionManager).rolloverIndex("products");
        verify(productIndexingService).reindexAll();
    }

    @Test
    void reindex_rolloverCalledWithProductsIndex() throws Exception {
        mockMvc.perform(post("/admin/search/reindex"));

        verify(indexVersionManager, times(1)).rolloverIndex("products");
        verifyNoMoreInteractions(indexVersionManager);
    }

    @Test
    void reindex_rolloverFails_returns500() throws Exception {
        doThrow(new RuntimeException("ES unreachable"))
                .when(indexVersionManager).rolloverIndex(anyString());

        mockMvc.perform(post("/admin/search/reindex"))
                .andExpect(status().isInternalServerError());

        verifyNoInteractions(productIndexingService);
    }

    @Test
    void reindex_reindexAllFails_returns500() throws Exception {
        doThrow(new RuntimeException("reindex failed"))
                .when(productIndexingService).reindexAll();

        mockMvc.perform(post("/admin/search/reindex"))
                .andExpect(status().isInternalServerError());
    }
}
