package com.leadrush.config;

import com.leadrush.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.UUID;

// STOMP-over-WebSocket config. JWT on CONNECT attaches a Principal for /user/queue/* routing.
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;
    private final LeadRushProperties properties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(properties.getFrontendUrl())
                .withSockJS();

        // Public endpoint for widget embeds on arbitrary origins. visitor_token
        // in the topic destination is the security boundary.
        registry.addEndpoint("/ws-public")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker; switch to broker relay for multi-node.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    // Anonymous connect is allowed for the public widget only; SUBSCRIBE
                    // filter below restricts them to /topic/chat/visitor/**.
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        accessor.getSessionAttributes().put("anonymous", true);
                        return message;
                    }

                    String token = authHeader.substring(7);
                    if (!jwtTokenProvider.isTokenValid(token)) {
                        log.warn("STOMP CONNECT rejected: invalid token");
                        return null;
                    }

                    Claims claims = jwtTokenProvider.parseToken(token);
                    UUID userId = UUID.fromString(claims.getSubject());
                    String workspaceId = claims.get("workspaceId", String.class);
                    String role = claims.get("role", String.class);

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId.toString(), null, authorities);
                    accessor.setUser(auth);

                    accessor.getSessionAttributes().put("workspaceId", workspaceId);
                    accessor.getSessionAttributes().put("userId", userId.toString());

                    log.debug("STOMP connected: user={} workspace={}", userId, workspaceId);
                    return message;
                }

                // Anonymous sessions: only allow SUBSCRIBE to /topic/chat/visitor/**;
                // block SEND entirely.
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && Boolean.TRUE.equals(accessor.getSessionAttributes().get("anonymous"))) {
                    String dest = accessor.getDestination();
                    if (dest == null || !dest.startsWith("/topic/chat/visitor/")) {
                        log.debug("Blocking anonymous SUBSCRIBE to {}", dest);
                        return null;
                    }
                }
                if (StompCommand.SEND.equals(accessor.getCommand())
                        && Boolean.TRUE.equals(accessor.getSessionAttributes().get("anonymous"))) {
                    return null;
                }

                return message;
            }
        });
    }
}
