package learn.gjx.tuku1.infrastructure.collaboration;

import learn.gjx.tuku1.application.collaboration.CollaborationAccess;
import learn.gjx.tuku1.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import javax.servlet.http.*;
import java.util.*;

@Configuration
@EnableWebSocket
public class CollaborationWebSocketConfig implements WebSocketConfigurer {
    private final PictureWebSocket handler;
    private final UserService users;
    private final CollaborationAccess access;
    @Value("${picture.collaboration.allowed-origins:http://localhost:5173,http://localhost:4173}")
    private String[] origins;
    public CollaborationWebSocketConfig(PictureWebSocket handler, UserService users, CollaborationAccess access) {
        this.handler = handler; this.users = users; this.access = access;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/picture/edit").setAllowedOrigins(origins).addInterceptors(new HandshakeInterceptor() {
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler ws, Map<String, Object> attrs) {
                try {
                    // Browsers must send an explicitly allowed Origin; same-host alone is insufficient.
                    if (!Arrays.asList(origins).contains(request.getHeaders().getOrigin())) throw new IllegalArgumentException();
                    HttpServletRequest servlet = ((ServletServerHttpRequest) request).getServletRequest();
                    HttpSession session = servlet.getSession(false);
                    if (session == null) throw new IllegalArgumentException();
                    Long userId = users.getLoginUser(servlet).getId();
                    String id = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("pictureId");
                    Long pictureId = Long.valueOf(id);
                    if (pictureId <= 0) throw new IllegalArgumentException();
                    access.authorize(session.getId(), userId, pictureId);
                    attrs.put("sessionId", session.getId()); attrs.put("userId", userId); attrs.put("pictureId", pictureId);
                    return true;
                } catch (RuntimeException e) { response.setStatusCode(HttpStatus.FORBIDDEN); return false; }
            }
            public void afterHandshake(ServerHttpRequest r, ServerHttpResponse s, WebSocketHandler w, Exception e) { }
        });
    }
}
