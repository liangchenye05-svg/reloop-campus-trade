package com.campustrade;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CAMPUS_INTEGRATION", matches = "true")
class IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void realMysqlPersistsAccountAndProductAndProtectsEditing() throws Exception {
        String username = "it_" + UUID.randomUUID().toString().substring(0, 8);
        String password = UUID.randomUUID().toString();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("username", username, "displayName", "测试卖家", "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("USER"));
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse();
        String token = json.readTree(response.getContentAsByteArray()).at("/data/token").asText();

        String payload = json.writeValueAsString(Map.of("title", username, "description", "二手书", "category", "BOOKS",
                "price", 9.90, "stock", 3));
        var product = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stock").value(3)).andReturn().getResponse();
        long id = json.readTree(product.getContentAsByteArray()).at("/data/id").asLong();
        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.seller.displayName").value("测试卖家"));
        mvc.perform(get("/api/products").param("q", username))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)));
        mvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
    }
}
