package br.com.chiccharmosa;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.Link;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Import(StackSmokeTest.SmokeController.class)
class StackSmokeTest extends IntegrationTest {

    @RestController
    static class SmokeController {

        @GetMapping("/smoke/hal")
        EntityModel<Map<String, Boolean>> hal() {
            return EntityModel.of(Map.of("ok", true), Link.of("/smoke/hal", "self"));
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void jjwtSignsAndParsesToken() {
        SecretKey key = Keys.hmacShaKeyFor("0123456789-0123456789-0123456789".getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder().subject("smoke-user").signWith(key).compact();
        String subject = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();

        assertThat(subject).isEqualTo("smoke-user");
    }

    @Test
    void hateoasSerializesHalWithLinks() throws Exception {
        mockMvc.perform(get("/smoke/hal").accept("application/hal+json").with(user("smoke")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    void springdocServesApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("smoke")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/smoke/hal']").exists());
    }
}
