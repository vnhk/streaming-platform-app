package com.bervan.streamingapp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductionDetails {
    
    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;
    
    @JsonProperty("type")
    private VideoType type;
    
    @JsonProperty("audioLang")
    private String audioLang;
    
    @JsonProperty("releaseYearStart")
    private Integer releaseYearStart;
    
    @JsonProperty("releaseYearEnd")
    private Integer releaseYearEnd;
    
    @JsonProperty("categories")
    private List<String> categories;
    
    @JsonProperty("tags")
    private List<String> tags;
    
    @JsonProperty("country")
    private String country;
    
    @JsonProperty("rating")
    private Double rating;
    
    public enum VideoType {
        @JsonProperty("tv_series")
        TV_SERIES("tv_series"),
        
        @JsonProperty("movie")
        MOVIE("movie"),
        
        @JsonProperty("other")
        OTHER("other");
        
        private final String value;
        
        VideoType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
}
