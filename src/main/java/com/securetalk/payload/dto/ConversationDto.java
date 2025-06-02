package com.securetalk.payload.dto;

import java.time.LocalDateTime;

public class ConversationDto {
    private Long id;
    private UserInfoDto participant;
    private MessageDto lastMessage;
    private LocalDateTime lastActivity;
    private int unreadCount;

    public ConversationDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserInfoDto getParticipant() {
        return participant;
    }

    public void setParticipant(UserInfoDto participant) {
        this.participant = participant;
    }

    public MessageDto getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(MessageDto lastMessage) {
        this.lastMessage = lastMessage;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }
}
