package com.bervan.streamingapp.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> audioLang;

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

    @JsonProperty("videoFormat")
    @JsonSetter(nulls = Nulls.SKIP)
    private VideoFormat videoFormat = VideoFormat.MP4;

    public enum VideoFormat {
        @JsonProperty("mp4")
        MP4("mp4"),

        @JsonProperty("hls")
        HLS("hls");

        private final String value;

        VideoFormat(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

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
