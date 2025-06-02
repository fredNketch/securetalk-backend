package com.securetalk.payload.request;

public class SendMessageRequest {
    private Long recipientId;
    private String content;
    private String messageType;
    private Long replyTo;
    
    public Long getRecipientId() { 
        return recipientId; 
    }
    
    public void setRecipientId(Long recipientId) { 
        this.recipientId = recipientId; 
    }
    
    public String getContent() { 
        return content; 
    }
    
    public void setContent(String content) { 
        this.content = content; 
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public Long getReplyTo() {
        return replyTo;
    }
    
    public void setReplyTo(Long replyTo) {
        this.replyTo = replyTo;
    }
}
