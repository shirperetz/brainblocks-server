package com.example.brainblocks.dto.response;

import com.example.brainblocks.model.GameEvent;
import com.example.brainblocks.model.GameEventType;

import java.time.LocalDateTime;

public class GameEventResponse {

    private final Long id;
    private final Long participantId;
    private final String participantName;
    private final String participantColor;
    private final GameEventType type;
    private final String message;
    private final int pointsDelta;
    private final LocalDateTime createdAt;

    public GameEventResponse(Long id, Long participantId, String participantName, String participantColor,
                              GameEventType type, String message, int pointsDelta, LocalDateTime createdAt) {
        this.id = id;
        this.participantId = participantId;
        this.participantName = participantName;
        this.participantColor = participantColor;
        this.type = type;
        this.message = message;
        this.pointsDelta = pointsDelta;
        this.createdAt = createdAt;
    }

    public static GameEventResponse fromEntity(GameEvent event) {
        return new GameEventResponse(
                event.getId(),
                event.getParticipant() != null ? event.getParticipant().getId() : null,
                event.getParticipant() != null ? event.getParticipant().getDisplayName() : "System",
                event.getParticipant() != null ? event.getParticipant().getColor() : "#ffffff",
                event.getType(),
                event.getMessage(),
                event.getPointsDelta(),
                event.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public Long getParticipantId() { return participantId; }
    public String getParticipantName() { return participantName; }
    public String getParticipantColor() { return participantColor; }
    public GameEventType getType() { return type; }
    public String getMessage() { return message; }
    public int getPointsDelta() { return pointsDelta; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
