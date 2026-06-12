package backend.integration.customers;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerFeedIT extends AbstractIntegrationIT {

    // ── GET /me/announcement-feed ─────────────────────────────────────────────

    @Test
    void getAnnouncementFeed_returns200WithEmptyPage() throws Exception {
        User user = createActiveUser("feed-user@example.com", "Password1!");

        mockMvc.perform(get("/me/announcement-feed")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void getAnnouncementFeed_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/me/announcement-feed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAnnouncementFeed_negativePageParam_returns400() throws Exception {
        User user = createActiveUser("feed-negpage@example.com", "Password1!");

        mockMvc.perform(get("/me/announcement-feed")
                        .param("page", "-1")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAnnouncementFeed_zeroSize_returns400() throws Exception {
        User user = createActiveUser("feed-zerosize@example.com", "Password1!");

        mockMvc.perform(get("/me/announcement-feed")
                        .param("size", "0")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAnnouncementFeed_oversizedPage_returns400() throws Exception {
        User user = createActiveUser("feed-bigsize@example.com", "Password1!");

        mockMvc.perform(get("/me/announcement-feed")
                        .param("size", "51")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isBadRequest());
    }
}
