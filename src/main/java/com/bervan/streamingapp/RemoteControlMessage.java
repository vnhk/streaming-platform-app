package com.bervan.streamingapp;

import lombok.Data;

@Data
public class RemoteControlMessage {
    private String action; // PLAY, PAUSE, SEEK, VOLUME, FULLSCREEN, NAVIGATE, etc.
    private String target; // video player, navigation, etc.
    private Object data; // additional data like seek position, volume level, etc.
    private String roomId;
    
    public RemoteControlMessage() {}
    
    public RemoteControlMessage(String action, String target, Object data) {
        this.action = action;
        this.target = target;
        this.data = data;
    }
}
