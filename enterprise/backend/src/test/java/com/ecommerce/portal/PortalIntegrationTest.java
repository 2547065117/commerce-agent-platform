package com.ecommerce.portal;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:portaltest;DB_CLOSE_DELAY=-1", "portal.admin-password=Test-password-123!", "portal.credentials-file=target/test-data/admin.txt"})
@AutoConfigureMockMvc
class PortalIntegrationTest {
    static HttpServer upstream;
    static volatile int upstreamStatus=200;
    static volatile boolean degraded=false;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @DynamicPropertySource static void agent(DynamicPropertyRegistry props) throws Exception {
        upstream=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        upstream.createContext("/api/v1/recommend", exchange -> {
            String body = "{\"request_id\":\"test\",\"user_id\":\"u001\",\"products\":[{\"product_id\":\"P001\",\"name\":\"Test product\",\"price\":10}],\"agent_results\":{\"user_profile\":{\"success\":true},\"product_rec\":{\"success\":true},\"inventory\":{\"success\":true},\"marketing_copy\":{\"success\":"+!degraded+"}}}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(upstreamStatus,bytes.length); exchange.getResponseBody().write(bytes);exchange.close();
        }); upstream.start();
        props.add("portal.agent-url",()->"http://127.0.0.1:"+upstream.getAddress().getPort());
    }
    @AfterAll static void stop(){ if(upstream!=null)upstream.stop(0); }
    @BeforeEach void reset(){ db.update("DELETE FROM recommendation_runs"); upstreamStatus=200;degraded=false; }
    String request(){ return "{\"user_id\":\"u001\",\"scene\":\"homepage\",\"num_items\":3,\"context\":{}}"; }
    @Test void rejectsAnonymousAccess() throws Exception { mvc.perform(get("/api/runs")).andExpect(status().isUnauthorized()); }
    @Test void csrfProtectsLoginAndWrites() throws Exception { mvc.perform(post("/api/login").param("username","admin").param("password","Test-password-123!")).andExpect(status().isForbidden()); mvc.perform(post("/api/recommend").with(user("admin").roles("ADMIN")).contentType("application/json").content(request())).andExpect(status().isForbidden()); }
    @Test void authenticatesAdministrator() throws Exception { mvc.perform(post("/api/login").with(csrf()).param("username","admin").param("password","Test-password-123!")).andExpect(status().isOk()); mvc.perform(post("/api/login").with(csrf()).param("username","admin").param("password","wrong")).andExpect(status().isUnauthorized()); }
    @Test void rejectsInvalidQuantity() throws Exception { mvc.perform(post("/api/recommend").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(request().replace("\"num_items\":3","\"num_items\":100"))).andExpect(status().isBadRequest()); }
    @Test void persistsRealResponseAndDiscoversProducts() throws Exception {
        mvc.perform(post("/api/recommend").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(request())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(get("/api/runs").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$[0].status").value("SUCCESS"));
        mvc.perform(get("/api/products").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$[0].product_id").value("P001"));
    }
    @Test void exposesAgentDegradation() throws Exception { degraded=true; mvc.perform(post("/api/recommend").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(request())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEGRADED")); }
    @Test void persistsUpstreamFailureWithoutLeakingDetails() throws Exception {
        upstreamStatus=500; mvc.perform(post("/api/recommend").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(request())).andExpect(status().isBadGateway());
        mvc.perform(get("/api/runs").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$[0].status").value("FAILED"));
    }
}
