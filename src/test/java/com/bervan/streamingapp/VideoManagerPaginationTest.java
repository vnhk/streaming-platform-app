package com.bervan.streamingapp;

import com.bervan.common.search.SearchQueryOption;
import com.bervan.common.search.SearchRequest;
import com.bervan.common.search.SearchService;
import com.bervan.common.search.model.SearchResponse;
import com.bervan.filestorage.model.Metadata;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class VideoManagerPaginationTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 49, 50, 51, 120})
    void loadsEveryProductionIncludingThoseBeyondTheFirstPage(int count) {
        List<Metadata> folders = IntStream.range(0, count)
                .mapToObj(i -> new Metadata(UUID.randomUUID(), "/Videos/", "Production %03d".formatted(i), null, true))
                .toList();
        SearchService search = mock(SearchService.class);
        when(search.search(any(SearchRequest.class), any(SearchQueryOption.class))).thenAnswer(invocation -> {
            SearchQueryOption options = invocation.getArgument(1);
            assertEquals("filename", options.getSortField());
            assertEquals(Integer.MAX_VALUE, options.getPageSize());
            assertEquals(0, options.getPage());
            int from = Math.min(options.getPage() * options.getPageSize(), count);
            int to = Math.min(from + options.getPageSize(), count);
            List<Metadata> page = folders.subList(from, to);
            return new SearchResponse<>(page, page.size(), options.getPage(), (long) count);
        });
        VideoManager manager = new VideoManager(null, search, null);
        ReflectionTestUtils.setField(manager, "appFolder", "/Videos");

        assertEquals(folders, manager.loadVideosMainDirectories());
        verify(search).search(any(SearchRequest.class), any(SearchQueryOption.class));
    }
}
