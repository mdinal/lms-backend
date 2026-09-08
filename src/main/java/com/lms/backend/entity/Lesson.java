package com.lms.backend.entity;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // S3 keys for videos or materials
    private String videoS3Key;
    private String documentS3Key;

    // Zoom link if it's a live class
    private String zoomJoinUrl;
    private String zoomMeetingId;
    
    private LocalDateTime scheduledAt;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public String getVideoS3Key() { return videoS3Key; }
    public void setVideoS3Key(String videoS3Key) { this.videoS3Key = videoS3Key; }
    public String getDocumentS3Key() { return documentS3Key; }
    public void setDocumentS3Key(String documentS3Key) { this.documentS3Key = documentS3Key; }
    public String getZoomJoinUrl() { return zoomJoinUrl; }
    public void setZoomJoinUrl(String zoomJoinUrl) { this.zoomJoinUrl = zoomJoinUrl; }
    public String getZoomMeetingId() { return zoomMeetingId; }
    public void setZoomMeetingId(String zoomMeetingId) { this.zoomMeetingId = zoomMeetingId; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
}
