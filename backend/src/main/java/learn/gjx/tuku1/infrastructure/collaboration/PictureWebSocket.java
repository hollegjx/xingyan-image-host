package learn.gjx.tuku1.infrastructure.collaboration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import learn.gjx.tuku1.application.collaboration.CollaborationService;
import learn.gjx.tuku1.domain.collaboration.EditRoom;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PictureWebSocket extends TextWebSocketHandler {
    private final CollaborationService service;
    private final CollaborationQueue queue;
    private final ObjectMapper mapper;
    private final Map<String, Socket> sockets = new ConcurrentHashMap<>();
    public PictureWebSocket(CollaborationService service, CollaborationQueue queue, ObjectMapper mapper) {
        this.service = service; this.queue = queue; this.mapper = mapper;
    }
    @Override public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(2048);
        if (sockets.size() >= 128) { try { session.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) { } return; }
        Socket socket = new Socket(session); sockets.put(session.getId(), socket);
        if (!queue.offer(() -> { if (socket.session.isOpen()) service.connect(socket); })) socket.close();
    }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Socket socket = sockets.get(session.getId());
        if (socket == null) return;
        if (message.getPayloadLength() > 2048 || !socket.allow()) { socket.close(); return; }
        try {
            JsonNode json = mapper.readTree(message.getPayload());
            String type = json.path("type").asText(); String action = json.path("action").asText();
            Long revision = json.hasNonNull("revision") ? Long.valueOf(json.get("revision").asText()) : null;
            if (!queue.offer(() -> service.message(session.getId(), type, action, revision))) socket.close();
        } catch (Exception e) { socket.close(); }
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sockets.remove(session.getId());
        // Never drop cleanup when the event queue is full.
        if (!queue.offer(() -> service.disconnect(session.getId()))) service.disconnect(session.getId());
    }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) throws Exception { session.close(); }
    @Scheduled(fixedDelay = 5000) public void sweep() { queue.offer(service::tick); }
    private class Socket implements CollaborationService.Connection {
        final WebSocketSession session;
        long window; int count;
        Socket(WebSocketSession original) { session = new ConcurrentWebSocketSessionDecorator(original, 3000, 16384); }
        synchronized boolean allow() {
            long now = System.nanoTime() / 1_000_000_000;
            if (now != window) { window = now; count = 0; }
            return ++count <= 20;
        }
        public String id() { return session.getId(); }
        public String sessionId() { return (String) session.getAttributes().get("sessionId"); }
        public Long userId() { return (Long) session.getAttributes().get("userId"); }
        public Long pictureId() { return (Long) session.getAttributes().get("pictureId"); }
        public void send(String type, EditRoom.Snapshot state, String message) throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", type); result.put("state", state); result.put("message", message);
            session.sendMessage(new TextMessage(mapper.writeValueAsString(result)));
        }
        public void close() { try { session.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) { } }
    }
}
