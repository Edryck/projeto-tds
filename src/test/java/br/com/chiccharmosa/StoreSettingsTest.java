package br.com.chiccharmosa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chiccharmosa.config.SeedIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

class StoreSettingsTest extends IntegrationTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestData testData;

    private String adminToken() {
        return "Bearer " + testData.tokenFor(testData.loginAs(testData.admin()));
    }

    private String cashierToken() {
        return "Bearer " + testData.tokenFor(testData.loginAs(testData.userWithRole(SeedIds.CASHIER_ROLE)));
    }

    @Test
    void logoIsPublicAndKeepsContentType() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/store-settings/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG_BYTES))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasLogo").value(true));
        mockMvc.perform(get("/api/store-settings/logo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void svgDisguisedAsPngIsRejected() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/store-settings/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", "<svg/>".getBytes()))
                        .header("Authorization", adminToken()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail").value("Logo deve ser PNG, JPG ou WebP de até 1 MB"));
    }

    @Test
    void disallowedContentTypeIsRejected() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/store-settings/logo")
                        .file(new MockMultipartFile("file", "logo.svg", "image/svg+xml", PNG_BYTES))
                        .header("Authorization", adminToken()))
                .andExpect(status().is(422));
    }

    @Test
    void cashierCannotUpdateSettings() throws Exception {
        mockMvc.perform(put("/api/store-settings").header("Authorization", cashierToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cashierCanReadSettings() throws Exception {
        mockMvc.perform(get("/api/store-settings").header("Authorization", cashierToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    void adminUpdatesSettings() throws Exception {
        mockMvc.perform(put("/api/store-settings").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chic Charmosa\",\"phone\":\"11999990000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chic Charmosa"));
    }
}
