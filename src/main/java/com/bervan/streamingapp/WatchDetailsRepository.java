package com.bervan.streamingapp;

import com.bervan.history.model.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WatchDetailsRepository extends BaseRepository<WatchDetails, UUID> {
}
