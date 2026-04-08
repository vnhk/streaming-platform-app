package com.bervan.streamingapp;

import com.bervan.common.service.AuthService;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.ProductionDetails;
import com.bervan.streamingapp.config.structure.BaseRootProductionStructure;
import com.bervan.streamingapp.config.structure.MovieBaseRootProductionStructure;
import com.bervan.streamingapp.config.structure.TvSeriesBaseRootProductionStructure;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/streaming/productions")
public class ProductionsApiController {

    private final Map<String, ProductionData> streamingProductionData;

    public ProductionsApiController(Map<String, ProductionData> streamingProductionData) {
        this.streamingProductionData = streamingProductionData;
    }

    // ---- DTOs ----

    public record EpisodeDto(String id, String name) {}

    public record SeasonDto(String name, List<EpisodeDto> episodes) {}

    public record ProductionSummaryDto(
            String productionName,
            String title,
            String type,
            String description,
            Double rating,
            Integer releaseYearStart,
            Integer releaseYearEnd,
            List<String> categories,
            List<String> tags,
            List<String> audioLang,
            String country,
            String posterUrl,
            String videoFormat
    ) {}

    public record ProductionDetailsDto(
            ProductionSummaryDto summary,
            List<SeasonDto> seasons,    // TV_SERIES
            List<EpisodeDto> episodes   // MOVIE / OTHER
    ) {}

    // ---- Endpoints ----

    @GetMapping
    public ResponseEntity<List<ProductionSummaryDto>> listProductions() {
        if (AuthService.getLoggedUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<ProductionSummaryDto> result = streamingProductionData.values().stream()
                .map(this::toSummaryDto)
                .sorted(Comparator.comparing(
                        p -> p.title() != null ? p.title().toLowerCase() : "",
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{name}")
    public ResponseEntity<ProductionDetailsDto> getProduction(@PathVariable String name) {
        if (AuthService.getLoggedUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProductionData pd = streamingProductionData.get(name);
        if (pd == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toDetailsDto(pd));
    }

    @GetMapping("/{name}/poster")
    public ResponseEntity<byte[]> getPoster(@PathVariable String name) {
        ProductionData pd = streamingProductionData.get(name);
        if (pd == null || pd.getBase64PosterSrc() == null || pd.getBase64PosterSrc().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            String b64 = pd.getBase64PosterSrc().trim();
            String contentType = "image/jpeg";
            String data;

            if (b64.startsWith("data:")) {
                int comma = b64.indexOf(',');
                String header = b64.substring(5, comma); // e.g. "image/png;base64"
                contentType = header.split(";")[0];
                data = b64.substring(comma + 1);
            } else {
                data = b64;
            }

            byte[] bytes = Base64.getDecoder().decode(data);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("Cache-Control", "max-age=3600")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ---- Mapping helpers ----

    private ProductionSummaryDto toSummaryDto(ProductionData pd) {
        ProductionDetails d = pd.getProductionDetails();
        if (d == null) {
            return new ProductionSummaryDto(
                    pd.getProductionName(), pd.getProductionName(),
                    null, null, null, null, null,
                    List.of(), List.of(), List.of(), null,
                    "/api/streaming/productions/" + pd.getProductionName() + "/poster",
                    "MP4"
            );
        }
        return new ProductionSummaryDto(
                pd.getProductionName(),
                d.getName(),
                d.getType() != null ? d.getType().name() : null,
                d.getDescription(),
                d.getRating(),
                d.getReleaseYearStart(),
                d.getReleaseYearEnd(),
                d.getCategories() != null ? d.getCategories() : List.of(),
                d.getTags() != null ? d.getTags() : List.of(),
                d.getAudioLang() != null ? d.getAudioLang() : List.of(),
                d.getCountry(),
                "/api/streaming/productions/" + pd.getProductionName() + "/poster",
                d.getVideoFormat() != null ? d.getVideoFormat().name() : "MP4"
        );
    }

    private ProductionDetailsDto toDetailsDto(ProductionData pd) {
        ProductionSummaryDto summary = toSummaryDto(pd);
        BaseRootProductionStructure structure = pd.getProductionStructure();
        if (structure == null) {
            return new ProductionDetailsDto(summary, null, null);
        }

        if (structure instanceof TvSeriesBaseRootProductionStructure tvSeries) {
            List<SeasonDto> seasons = tvSeries.getSeasons().stream()
                    .map(season -> new SeasonDto(
                            season.getMetadataName(),
                            season.getEpisodes().stream()
                                    .map(ep -> new EpisodeDto(
                                            ep.getEpisodeFolder().getId().toString(),
                                            ep.getEpisodeFolder().getFilename()
                                    ))
                                    .collect(Collectors.toList())
                    ))
                    .collect(Collectors.toList());
            return new ProductionDetailsDto(summary, seasons, null);

        } else if (structure instanceof MovieBaseRootProductionStructure movie) {
            List<EpisodeDto> episodes = movie.getVideosFolders().stream()
                    .map(m -> new EpisodeDto(m.getId().toString(), m.getFilename()))
                    .collect(Collectors.toList());
            return new ProductionDetailsDto(summary, null, episodes);
        }

        return new ProductionDetailsDto(summary, null, null);
    }
}
