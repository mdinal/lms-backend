package com.lms.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ZoomMeetingResponse {

    private String id;
    
    @JsonProperty("join_url")
    private String joinUrl;
    
    @JsonProperty("start_url")
    private String startUrl;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getJoinUrl() { return joinUrl; }
    public void setJoinUrl(String joinUrl) { this.joinUrl = joinUrl; }
    
    public String getStartUrl() { return startUrl; }
    public void setStartUrl(String startUrl) { this.startUrl = startUrl; }
}
