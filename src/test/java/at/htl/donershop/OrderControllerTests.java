package at.htl.donershop;

import at.htl.donershop.Controllers.OrderController;
import at.htl.donershop.Entities.Order;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class OrderControllerTests {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void unknownCustomerReturns404FromOrderController() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":9223372036854775807,\"status\":\"OPEN\"}"))
                .andExpect(handler().handlerType(OrderController.class))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Kunde nicht gefunden"));
    }

    @Test
    void missingCustomerIdReturns400() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCreatedViaApiCanBeUsedForPersistedOrder() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        String customerJson = mvc.perform(post("/api/kunden").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Max\",\"lastName\":\"Mustermann\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long customerId = mapper.readTree(customerJson).get("id").asLong();
        String orderJson = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + customerId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kunde.id").value(customerId))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        JsonNode result = mapper.readTree(orderJson);
        long orderId = result.get("id").asLong();
        em.flush();
        em.clear();
        Order persisted = em.find(Order.class, orderId);
        assertThat(persisted.getKunde().getId()).isEqualTo(customerId);
        assertThat(persisted.getOrderDate()).isNotNull();
        mvc.perform(get("/api/orders/" + orderId)).andExpect(status().isOk());
    }
}
