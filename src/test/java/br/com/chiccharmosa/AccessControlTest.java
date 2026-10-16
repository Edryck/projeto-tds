package br.com.chiccharmosa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chiccharmosa.entity.User;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AccessControlTest extends IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestData testData;

    @Test
    void userWithoutPermissionGets403() throws Exception {
        User user = testData.userWith("product:view");
        String token = testData.tokenFor(testData.loginAs(user));

        mockMvc.perform(get("/api/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissionChangeTakesEffectOnNextRequest() throws Exception {
        User user = testData.userWith("product:view");
        String userToken = testData.tokenFor(testData.loginAs(user));
        String adminToken = testData.tokenFor(testData.loginAs(testData.admin()));
        String roleId = user.getRole().getId().toString();

        mockMvc.perform(get("/api/roles").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(updateRole(roleId, adminToken, "product:view", "role:manage"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/roles").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(updateRole(roleId, adminToken, "product:view"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/roles").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminHasAccess() throws Exception {
        String adminToken = testData.tokenFor(testData.loginAs(testData.admin()));

        mockMvc.perform(get("/api/roles").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void disablingUserRevokesRefreshToken() throws Exception {
        User user = testData.userWith("product:view");
        String adminToken = testData.tokenFor(testData.loginAs(testData.admin()));

        String loginBody = "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + TestData.PASSWORD
                + "\",\"deviceId\":\"" + UUID.randomUUID() + "\",\"deviceName\":\"Teste\"}";
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody).with(request -> {
                            request.setRemoteAddr("10.50.0.1");
                            return request;
                        }))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(response, "$.refreshToken");

        String updateBody = "{\"email\":\"" + user.getEmail() + "\",\"name\":\"Usuário de teste\",\"roleId\":\""
                + user.getRole().getId() + "\",\"active\":false}";
        mockMvc.perform(put("/api/users/" + user.getId()).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateActiveEmailReturns409() throws Exception {
        User existing = testData.userWith("product:view");
        String adminToken = testData.tokenFor(testData.loginAs(testData.admin()));

        mockMvc.perform(createUser(adminToken, existing.getEmail().toUpperCase(), "Senha@123", existing.getRole().getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordReturns400() throws Exception {
        User existing = testData.userWith("product:view");
        String adminToken = testData.tokenFor(testData.loginAs(testData.admin()));

        mockMvc.perform(createUser(adminToken, UUID.randomUUID() + "@test.com", "curta", existing.getRole().getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingRoleWithUsersReturns409() throws Exception {
        User existing = testData.userWith("product:view");
        String adminToken = testData.tokenFor(testData.loginAs(testData.admin()));

        mockMvc.perform(delete("/api/roles/" + existing.getRole().getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void userWithoutManagePermissionCannotListUsers() throws Exception {
        User user = testData.userWith("product:view");
        String token = testData.tokenFor(testData.loginAs(user));

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder createUser(String token, String email, String password, UUID roleId) {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"name\":\"Novo\",\"roleId\":\"" + roleId + "\",\"active\":true}";
        return post("/api/users").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder updateRole(String roleId, String token, String... codes) {
        String quoted = "\"" + String.join("\",\"", codes) + "\"";
        String body = "{\"name\":\"Perfil de teste\",\"permissionCodes\":[" + quoted + "]}";
        return put("/api/roles/" + roleId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
