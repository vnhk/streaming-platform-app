package com.bervan.streamingapp;


import com.bervan.common.model.BervanBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class WatchDetails extends BervanBaseEntity<UUID> {
    @Id
    @GeneratedValue
    private UUID id;
    private UUID videoId;
    private UUID userId;
    private double currentVideoTime;
    private LocalDateTime modificationDate;
    private LocalDateTime creationDate;

    private boolean deleted = false;

    public UUID getVideoId() {
        return videoId;
    }

    public void setVideoId(UUID videoId) {
        this.videoId = videoId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }


    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public LocalDateTime getModificationDate() {
        return modificationDate;
    }

    @Override
    public void setModificationDate(LocalDateTime modificationDate) {
        this.modificationDate = modificationDate;
    }


    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public double getCurrentVideoTime() {
        return currentVideoTime;
    }

    public void setCurrentVideoTime(double currentVideoTime) {
        this.currentVideoTime = currentVideoTime;
    }
}
