package com.ecommerce.portal;

import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(PortalStore store) {
        return username -> {
            var rows = store.user(username);
            if (rows.isEmpty()) throw new UsernameNotFoundException("User not found");
            var row = rows.get(0);
            return User.withUsername(username).password((String)row.get("PASSWORD_HASH")).roles((String)row.get("ROLE")).build();
        };
    }
    @Bean SecurityFilterChain security(HttpSecurity http, PortalStore store) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg", "/api/csrf", "/api/login", "/error").permitAll()
                .requestMatchers("/api/**").hasRole("ADMIN").anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req,res,e) -> { res.setStatus(401); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"message\":\"请先登录\"}"); })
                .accessDeniedHandler((req,res,e) -> { res.setStatus(403); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"message\":\"权限不足或安全令牌失效，请刷新页面\"}"); }))
            .formLogin(login -> login.loginProcessingUrl("/api/login")
                .successHandler((req,res,auth) -> { store.audit(auth.getName(),"LOGIN","Administrator signed in"); res.setContentType("application/json"); res.getWriter().write("{\"ok\":true}"); })
                .failureHandler((req,res,e) -> { store.audit("anonymous","LOGIN_FAILED","Invalid credentials"); res.setStatus(401); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"message\":\"用户名或密码不正确\"}"); }))
            .logout(logout -> logout.logoutUrl("/api/logout").logoutSuccessHandler((req,res,auth) -> {
                if (auth != null) store.audit(auth.getName(),"LOGOUT","Administrator signed out");
                res.setStatus(204);
            })).build();
    }
}
