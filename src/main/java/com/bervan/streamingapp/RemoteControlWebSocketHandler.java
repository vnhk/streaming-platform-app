package com.bervan.streamingapp;


import com.bervan.logging.JsonLogger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RemoteControlWebSocketHandler implements WebSocketHandler {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");

    private final Map<String, WebSocketSession> tvSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> remoteSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceType = (String) session.getAttributes().get("deviceType");
        String roomId = (String) session.getAttributes().get("roomId");
        String sessionKey = roomId != null ? roomId : "default";

        if ("TV".equals(deviceType)) {
            tvSessions.put(sessionKey, session);
            log.info("TV connected for room: " + sessionKey);
        } else if ("REMOTE".equals(deviceType)) {
            remoteSessions.put(sessionKey, session);
            log.info("Remote control connected for room: " + sessionKey);
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String payload = message.getPayload().toString();

        String roomId = (String) session.getAttributes().get("roomId");
        String sessionKey = roomId != null ? roomId : "default";

        // Forward message from remote to TV
        if (remoteSessions.containsValue(session)) {
            WebSocketSession tvSession = tvSessions.get(sessionKey);
            if (tvSession != null && tvSession.isOpen()) {
                tvSession.sendMessage(new TextMessage(payload));
            }
        }
        // Forward message from TV to remote (for status updates)
        else if (tvSessions.containsValue(session)) {
            WebSocketSession remoteSession = remoteSessions.get(sessionKey);
            if (remoteSession != null && remoteSession.isOpen()) {
                remoteSession.sendMessage(new TextMessage(payload));
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: " + exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String deviceType = (String) session.getAttributes().get("deviceType");
        String roomId = (String) session.getAttributes().get("roomId");
        String sessionKey = roomId != null ? roomId : "default";

        if ("TV".equals(deviceType)) {
            tvSessions.remove(sessionKey);
        } else if ("REMOTE".equals(deviceType)) {
            remoteSessions.remove(sessionKey);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}