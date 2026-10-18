package com.izhar.splitledger.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:itest;DB_CLOSE_DELAY=-1", "app.seed-demo=false"})
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    private JsonNode createGroup() throws Exception {
        String body = mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Trip\",\"members\":[\"Ali\",\"Sara\",\"Hamza\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private static String expenseJson(String amount, long paidBy, String splitType, String splits) {
        return String.format("{\"description\":\"Dinner\",\"amount\":%s,\"paidBy\":%d,\"splitType\":\"%s\",\"splits\":%s}",
                amount, paidBy, splitType, splits);
    }

    private static String equalSplits(JsonNode group) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < group.get("members").size(); i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"memberId\":").append(group.get("members").get(i).get("id").asLong()).append('}');
        }
        return sb.append(']').toString();
    }

    @Test
    void invalidBodyReturns400WithFieldDetails() throws Exception {
        mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownGroupReturns404() throws Exception {
        mvc.perform(get("/groups/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void splitThatDoesNotAddUpReturns422() throws Exception {
        JsonNode g = createGroup();
        long ali = g.get("members").get(0).get("id").asLong();
        long sara = g.get("members").get(1).get("id").asLong();
        String splits = "[{\"memberId\":" + ali + ",\"value\":10},{\"memberId\":" + sara + ",\"value\":10}]";
        mvc.perform(post("/groups/" + g.get("id").asLong() + "/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("100.00", ali, "EXACT", splits)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sameIdempotencyKeyReplaysInsteadOfDuplicating() throws Exception {
        JsonNode g = createGroup();
        long gid = g.get("id").asLong();
        long ali = g.get("members").get(0).get("id").asLong();
        String body = expenseJson("300.00", ali, "EQUAL", equalSplits(g));

        String first = mvc.perform(post("/groups/" + gid + "/expenses").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/groups/" + gid + "/expenses").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replay", "true"))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(mapper.readTree(first).get("id"), mapper.readTree(second).get("id"));
        mvc.perform(get("/groups/" + gid + "/expenses")).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void reusingAKeyWithDifferentPayloadReturns409() throws Exception {
        JsonNode g = createGroup();
        long gid = g.get("id").asLong();
        long ali = g.get("members").get(0).get("id").asLong();
        mvc.perform(post("/groups/" + gid + "/expenses").header("Idempotency-Key", "k-2")
                        .contentType(MediaType.APPLICATION_JSON).content(expenseJson("300.00", ali, "EQUAL", equalSplits(g))))
                .andExpect(status().isCreated());
        mvc.perform(post("/groups/" + gid + "/expenses").header("Idempotency-Key", "k-2")
                        .contentType(MediaType.APPLICATION_JSON).content(expenseJson("999.00", ali, "EQUAL", equalSplits(g))))
                .andExpect(status().isConflict());
    }

    @Test
    void balancesPlanAndSettlementFlow() throws Exception {
        JsonNode g = createGroup();
        long gid = g.get("id").asLong();
        long ali = g.get("members").get(0).get("id").asLong();
        long sara = g.get("members").get(1).get("id").asLong();
        long hamza = g.get("members").get(2).get("id").asLong();

        mvc.perform(post("/groups/" + gid + "/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("300.00", ali, "EQUAL", equalSplits(g))))
                .andExpect(status().isCreated());

        mvc.perform(get("/groups/" + gid + "/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].balance").value(200.00))
                .andExpect(jsonPath("$.balances[0].status").value("OWED"));

        mvc.perform(get("/groups/" + gid + "/settlements/suggested"))
                .andExpect(jsonPath("$.transferCount").value(2));

        mvc.perform(post("/groups/" + gid + "/settlements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromMemberId\":" + sara + ",\"toMemberId\":" + ali + ",\"amount\":100.00}"))
                .andExpect(status().isCreated());

        // Hamza only owes 100.00, so paying 150.00 is a semantic error
        mvc.perform(post("/groups/" + gid + "/settlements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromMemberId\":" + hamza + ",\"toMemberId\":" + ali + ",\"amount\":150.00}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/groups/" + gid + "/ledger")).andExpect(jsonPath("$.length()").value(2));
    }
}
