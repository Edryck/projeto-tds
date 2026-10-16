package br.com.chiccharmosa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chiccharmosa.entity.Role;
import br.com.chiccharmosa.entity.User;
import br.com.chiccharmosa.repository.PermissionRepository;
import br.com.chiccharmosa.repository.RoleRepository;
import br.com.chiccharmosa.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class AuthFlowTest extends IntegrationTest {

    private static final String PASSWORD = "Senha@123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PermissionRepository permissionRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void loginReturnsTokens() throws Exception {
        mockMvc.perform(login("admin@chiccharmosa.com.br", "Admin@123", UUID.randomUUID(), "10.1.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        mockMvc.perform(login("admin@chiccharmosa.com.br", "errada", UUID.randomUUID(), "10.1.0.2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noTokenReturns401OnMe() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesAndOldTokenStopsWorking() throws Exception {
        User user = createUser();
        String body = mockMvc.perform(login(user.getEmail(), PASSWORD, UUID.randomUUID(), "10.2.0.1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String oldRefresh = JsonPath.read(body, "$.refreshToken");

        String refreshed = mockMvc.perform(refresh(oldRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = JsonPath.read(refreshed, "$.refreshToken");

        mockMvc.perform(refresh(oldRefresh)).andExpect(status().isUnauthorized());
        mockMvc.perform(refresh(newRefresh)).andExpect(status().isOk());
    }

    @Test
    void disabledUserTokenStopsWorking() throws Exception {
        User user = createUser();
        String body = mockMvc.perform(login(user.getEmail(), PASSWORD, UUID.randomUUID(), "10.3.0.1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$._links.self.href").isNotEmpty());

        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sixthWrongLoginFromSameIpReturns429() throws Exception {
        User user = createUser();
        UUID deviceId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(user.getEmail(), "errada", deviceId, "10.4.0.1"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(user.getEmail(), "errada", deviceId, "10.4.0.1"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(login(user.getEmail(), PASSWORD, deviceId, "10.4.0.1"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void sameEmailFromOtherIpStillLogsIn() throws Exception {
        User user = createUser();
        UUID deviceId = UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(login(user.getEmail(), "errada", deviceId, "10.5.0.1"));
        }
        mockMvc.perform(login(user.getEmail(), PASSWORD, deviceId, "10.5.0.1"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(login(user.getEmail(), PASSWORD, deviceId, "10.5.0.2"))
                .andExpect(status().isOk());
    }

    @Test
    void forwardedForHeaderIsUsedAsClientIp() throws Exception {
        User user = createUser();
        UUID deviceId = UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(login(user.getEmail(), "errada", deviceId, "10.6.0.1").header("X-Forwarded-For", "203.0.113.1"));
        }
        mockMvc.perform(login(user.getEmail(), PASSWORD, deviceId, "10.6.0.1").header("X-Forwarded-For", "203.0.113.1"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(login(user.getEmail(), PASSWORD, deviceId, "10.6.0.1").header("X-Forwarded-For", "203.0.113.2"))
                .andExpect(status().isOk());
    }

    private User createUser() {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("Role " + UUID.randomUUID());
        Set<br.com.chiccharmosa.entity.Permission> permissions = new HashSet<>();
        permissions.add(permissionRepository.findAll().get(0));
        role.setPermissions(permissions);
        roleRepository.save(role);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user-" + UUID.randomUUID() + "@teste.com");
        user.setName("Usuário de teste");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        return userRepository.save(user);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email,
            String password, UUID deviceId, String ip) {
        String json = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"deviceId\":\"" + deviceId
                + "\",\"deviceName\":\"Teste\"}";
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json).with(fromIp(ip));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(String token) {
        return post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}");
    }

    private RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
