package com.ecommerce.portal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.http.HttpClient;
import java.security.Principal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PortalController {
    private final PortalStore store;
    private final RestClient agents;
    private final Semaphore slots = new Semaphore(3);
    public PortalController(PortalStore store, @Value("${portal.agent-url}") String base) {
        this.store = store;
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(client); factory.setReadTimeout(Duration.ofSeconds(90));
        this.agents = RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }
    public record RecommendInput(@JsonProperty("user_id") @NotBlank @Size(max=100) String userId,
        @Pattern(regexp="homepage|detail|cart|campaign") String scene,
        @JsonProperty("num_items") @Min(1) @Max(15) int numItems,
        @NotNull @Size(max=12) Map<String,Object> context) {}

    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/me") Map<String,String> me(Principal principal) { return Map.of("username",principal.getName(),"role","ADMIN"); }
    @GetMapping("/overview") Map<String,Object> overview() {
        Map<String,Object> result = new LinkedHashMap<>();
        store.stats().forEach((key,value) -> result.put(key.toLowerCase(Locale.ROOT),value));
        result.put("product_count",store.products().size());
        try { result.put("agent_service", agents.get().uri("/health").retrieve().body(Map.class)); }
        catch (Exception e) { result.put("agent_service",Map.of("status","offline")); }
        return result;
    }
    @GetMapping("/products") Object products() { return store.products(); }
    @GetMapping("/runs") Object runs() { return store.runs(); }
    @GetMapping("/runs/{id}") Object run(@PathVariable String id) {
        var result=store.run(id); if(result==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在"); return result;
    }
    @GetMapping("/audit") Object audit() { return store.auditEvents(); }
    @PostMapping("/recommend") Map<String,Object> recommend(@Valid @RequestBody RecommendInput input, Principal principal) {
        if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"任务繁忙，请稍后重试");
        String id=UUID.randomUUID().toString(); long started=System.nanoTime();
        Map<String,Object> request = Map.of("user_id", input.userId(), "scene", Objects.requireNonNullElse(input.scene(),"homepage"), "num_items", input.numItems(), "context", input.context());
        try {
            Map<String,Object> response = agents.post().uri("/api/v1/recommend").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(Map.class);
            if (response==null || !(response.get("products") instanceof List<?>)) throw new IllegalStateException("Invalid agent response");
            Object results=response.get("agent_results");
            boolean healthy = results instanceof Map<?,?> map && map.size()>=4 && map.values().stream().allMatch(v -> v instanceof Map<?,?> agent && Boolean.TRUE.equals(agent.get("success")));
            String status=healthy?"SUCCESS":"DEGRADED";
            store.save(id,principal.getName(),input.userId(),input.scene(),status,elapsed(started),request,response,null);
            store.audit(principal.getName(),"RECOMMEND_"+status,"run="+id);
            return Map.of("id",id,"status",status,"response",response);
        } catch (Exception e) {
            store.save(id,principal.getName(),input.userId(),input.scene(),"FAILED",elapsed(started),request,null,"Agent service failed or timed out");
            store.audit(principal.getName(),"RECOMMEND_FAILED","run="+id);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"推荐服务异常或超时，请检查服务状态与调用记录");
        } finally { slots.release(); }
    }
    private long elapsed(long start) { return (System.nanoTime()-start)/1_000_000; }
}
