package com.bervan.streamingapp.audiostreaming.controller;

import com.bervan.streamingapp.audiostreaming.service.AudioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AudioStreamingApiControllerTest {
    @TempDir Path root;
    private MockMvc mvc;
    private byte[] audio;

    @BeforeEach
    void setup() throws Exception {
        audio = new byte[2 * 1024 * 1024];
        Arrays.fill(audio, (byte) 42);
        Path file = root.resolve("track.mp3");
        Files.write(file, audio);
        AudioStorageService storage = mock(AudioStorageService.class);
        when(storage.getTrackFile("track")).thenReturn(file.toFile());
        mvc = MockMvcBuilders.standaloneSetup(new AudioStreamingApiController(storage)).build();
    }

    @Test
    void servesEntireFileWhenTvDoesNotSendRange() throws Exception {
        mvc.perform(get("/api/audio/tracks/track/stream"))
                .andExpect(status().isOk()).andExpect(content().bytes(audio))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().contentType("audio/mpeg"));
    }

    @Test
    void servesRequestedByteRange() throws Exception {
        mvc.perform(get("/api/audio/tracks/track/stream").header("Range", "bytes=100-199"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 100-199/" + audio.length))
                .andExpect(content().bytes(Arrays.copyOfRange(audio, 100, 200)));
    }

    @Test
    void rejectsOutOfBoundsRange() throws Exception {
        mvc.perform(get("/api/audio/tracks/track/stream").header("Range", "bytes=9999999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }
}
