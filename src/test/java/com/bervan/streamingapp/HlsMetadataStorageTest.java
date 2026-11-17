package com.bervan.streamingapp;

import com.bervan.common.search.SearchService;
import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.policy.FileStoragePolicies;
import com.bervan.filestorage.policy.FileStoragePolicy;
import com.bervan.filestorage.repository.MetadataRepository;
import com.bervan.filestorage.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HlsMetadataStorageTest {
    @TempDir
    Path root;
    private final Map<UUID, Metadata> records = new LinkedHashMap<>();
    private FileDiskStorageService disk;
    private FileDBStorageService database;
    private FileServiceManager manager;
    private LoadStorageAndIntegrateWithDB sync;
    private HlsFileStoragePolicy policy;
    private FileStoragePolicy hooks;

    @BeforeEach
    void setUp() {
        disk = spy(new FileDiskStorageService());
        ReflectionTestUtils.setField(disk, "FOLDERS", List.of("main"));
        ReflectionTestUtils.setField(disk, "folderMapping", Map.of("main", root.toString()));
        ReflectionTestUtils.setField(disk, "autoMapping", Map.of());
        ReflectionTestUtils.setField(disk, "GLOBAL_TMP_DIR", "/tmp");
        MetadataRepository repository = mock(MetadataRepository.class);
        when(repository.save(any(Metadata.class))).thenAnswer(invocation -> {
            Metadata file = invocation.getArgument(0);
            if (file.getId() == null) file.setId(UUID.randomUUID());
            records.put(file.getId(), file);
            return file;
        });
        doAnswer(invocation -> {
            records.remove(((Metadata) invocation.getArgument(0)).getId());
            return null;
        }).when(repository).delete(any(Metadata.class));
        when(repository.findById(any())).thenAnswer(i -> Optional.ofNullable(records.get(i.getArgument(0))));
        when(repository.findByPathStartingWith(anyString())).thenAnswer(i -> matching(m -> m.getPath().startsWith(i.getArgument(0))));
        when(repository.findByPath(anyString())).thenAnswer(i -> matching(m -> m.getPath().equals(i.getArgument(0))));
        when(repository.findByPathAndInTrashFalse(anyString())).thenAnswer(i -> matching(m -> m.getPath().equals(i.getArgument(0)) && !m.isInTrash()));
        when(repository.findByPathAndFilenameAndInTrashTrue(anyString(), anyString())).thenAnswer(i ->
                matching(m -> m.getPath().equals(i.getArgument(0)) && m.getFilename().equals(i.getArgument(1)) && m.isInTrash()).stream().toList());
        database = spy(new FileDBStorageService(repository));
        policy = new HlsFileStoragePolicy("/Configured Videos", disk);
        hooks = mock(FileStoragePolicy.class, CALLS_REAL_METHODS);
        FileStoragePolicies policies = new FileStoragePolicies(List.of(policy, hooks));
        sync = new LoadStorageAndIntegrateWithDB(database, disk, policies);
        manager = new FileServiceManager(database, mock(SearchService.class), disk, repository, sync, policies,
                new FileZipImportService(disk, database, policies), new FileDirectoryContents(database, disk, policies));
    }

    private Set<Metadata> matching(Predicate<Metadata> condition) {
        return records.values().stream().filter(condition).collect(Collectors.toSet());
    }

    private void write(String relative, String content) throws Exception {
        Path target = root.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private void createProduction() throws Exception {
        write("Configured Videos/Film/master.m3u8", "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=5000000\nvideo.m3u8\n");
        write("Configured Videos/Film/video.m3u8", "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nv_001.ts\n#EXT-X-ENDLIST\n");
        for (String name : List.of("v_001.ts", "a_001.ts", "chunk.m4s", "source.mp4", "audio.mp3", "sub.srt", "sub.vtt", "poster.jpg", "details.json")) {
            write("Configured Videos/Film/" + name, name);
        }
        write("Configured Videos/Film/segments/nested.m4s", "nested");
        write("Configured Videos/Other/source.ts", "standalone");
        write("Configured Videos Backup/Film/master.m3u8", "#EXTM3U");
        write("Configured Videos Backup/Film/keep.ts", "outside");
        // Simulate metadata created by the previous application version.
        database.insertBatch(disk.getAllFilesInFolder("/"));
    }

    private Metadata record(String path, String name) {
        return records.values().stream().filter(m -> m.getPath().equals(path) && m.getFilename().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void repeatedSynchronizationRemovesOnlySegmentRecordsAndNeverDeletesDiskFiles() throws Exception {
        createProduction();
        record("/Configured Videos/Film/", "a_001.ts").setInTrash(true);
        Set<Path> before;
        try (var files = Files.walk(root)) { before = files.collect(Collectors.toSet()); }

        sync.synchronizeStorageWithDB("/Configured Videos/");
        sync.synchronizeStorageWithDB("/Configured Videos/");

        for (String name : List.of("master.m3u8", "video.m3u8", "source.mp4", "audio.mp3", "sub.srt", "sub.vtt", "poster.jpg", "details.json")) {
            assertNotNull(record("/Configured Videos/Film/", name));
        }
        assertNotNull(record("/Configured Videos/", "Film"));
        assertNotNull(record("/Configured Videos/Film/", "segments"));
        assertNotNull(record("/Configured Videos/Other/", "source.ts"));
        assertNotNull(record("/Configured Videos Backup/Film/", "keep.ts"));
        assertFalse(records.values().stream().anyMatch(m -> !m.isDirectory() && m.getPath().startsWith("/Configured Videos/Film/")
                && Set.of("ts", "m4s").contains(m.getExtension())));
        try (var files = Files.walk(root)) { assertEquals(before, files.collect(Collectors.toSet())); }
        assertEquals("v_001.ts", Files.readString(root.resolve("Configured Videos/Film/v_001.ts")));
        verify(disk, never()).delete(anyString(), anyString());
        verify(hooks, never()).beforeFileDelete(any());
        verify(hooks, never()).beforeDirectoryDelete(any());
        verify(database, times(1)).removeFileMetadataOnly(anyList());
    }

    @Test
    void initialRootSynchronizationTracksDirectoriesAndEditableFilesButNotSegments() throws Exception {
        createProduction();
        records.clear();
        sync.synchronizeStorageWithDB("/");
        sync.synchronizeStorageWithDB("/");
        assertNotNull(record("/Configured Videos/", "Film"));
        assertNotNull(record("/Configured Videos/Film/", "master.m3u8"));
        assertNotNull(record("/Configured Videos/Film/", "source.mp4"));
        assertNotNull(record("/Configured Videos/Film/", "audio.mp3"));
        assertTrue(matching(m -> m.getPath().equals("/Configured Videos/Film/segments/")).isEmpty());
        assertFalse(records.values().stream().anyMatch(m -> m.getFilename().equals("v_001.ts")));
        assertNotNull(record("/Configured Videos Backup/Film/", "keep.ts"));
        verify(disk, never()).delete(anyString(), anyString());
    }

    @Test
    void scopedSynchronizationRecognizesPlaylistInAnAncestor() throws Exception {
        createProduction();
        sync.synchronizeStorageWithDB("/Configured Videos/Film/segments/");
        assertTrue(matching(m -> m.getPath().equals("/Configured Videos/Film/segments/")).isEmpty());
        assertNotNull(record("/Configured Videos/Film/", "v_001.ts"));
        assertTrue(Files.exists(root.resolve("Configured Videos/Film/segments/nested.m4s")));
    }

    @Test
    void onlyConfiguredSubdirectoriesWithPlaylistsExcludeSegments() throws Exception {
        createProduction();
        assertFalse(policy.shouldTrackMetadata(new Metadata("/Configured Videos/Film/", "CHUNK.TS", null, LocalDateTime.now(), false)));
        assertTrue(policy.shouldTrackMetadata(new Metadata("/Configured Videos/Film/", "folder.ts", null, LocalDateTime.now(), true)));
        assertTrue(policy.shouldTrackMetadata(new Metadata("/Configured Videos/", "source.ts", null, LocalDateTime.now(), false)));
        assertEquals(List.of("ts", "m4s"), policy.untrackedExtensions("/Configured Videos"));
        assertEquals(List.of("ts", "m4s"), policy.untrackedExtensions("/Configured Videos/Other/"));
        assertTrue(policy.untrackedExtensions("/Configured Videos Backup/").isEmpty());
        assertTrue(policy.untrackedExtensions("/").isEmpty());
        assertTrue(new FileStoragePolicies(List.of()).trackingFilter().test(
                new Metadata("/Configured Videos/Film/", "v_001.ts", null, LocalDateTime.now(), false)));
    }

    @Test
    void singleUploadWritesUntrackedSegmentToDiskAndKeepsMp4AndMp3Metadata() throws Exception {
        createProduction();
        for (String name : List.of("new.ts", "new.m4s", "new.mp4", "new.mp3")) {
            var response = manager.save(new MockMultipartFile("file", name, "application/octet-stream", name.getBytes()), "", "/Configured Videos/Film/");
            assertTrue(Files.exists(root.resolve("Configured Videos/Film/" + name)));
            assertEquals(name.endsWith("mp4") || name.endsWith("mp3") ? 1 : 0, response.getMetadata().size());
        }
    }

    @Test
    void zipImportExcludesSegmentsEvenWhenTheyPrecedeThePlaylist() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : List.of("Film/video.ts", "Film/nested/chunk.m4s", "Film/master.m3u8", "Film/source.mp4", "Film/audio.mp3")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(name.endsWith("m3u8") ? "#EXTM3U".getBytes() : name.getBytes());
                zip.closeEntry();
            }
        }
        manager.saveAndExtractZip(new MockMultipartFile("file", "film.zip", "application/zip", bytes.toByteArray()), "", "/Configured Videos");
        assertTrue(Files.exists(root.resolve("Configured Videos/Film/video.ts")));
        assertTrue(Files.exists(root.resolve("Configured Videos/Film/nested/chunk.m4s")));
        assertFalse(records.values().stream().anyMatch(m -> !m.isDirectory() && Set.of("ts", "m4s").contains(m.getExtension())));
        assertNotNull(record("/Configured Videos/Film/", "master.m3u8"));
        assertNotNull(record("/Configured Videos/Film/", "source.mp4"));
        assertNotNull(record("/Configured Videos/Film/", "audio.mp3"));
    }

    @Test
    void folderZipAndSizeIncludeUntrackedSegmentsButExcludeTrash() throws Exception {
        createProduction();
        sync.synchronizeStorageWithDB("/Configured Videos/");
        Metadata folder = record("/Configured Videos/", "Film");
        record("/Configured Videos/Film/", "poster.jpg").setInTrash(true);
        write("Configured Videos/Film/not-synchronized.txt", "not indexed");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            manager.addFilesToZip(List.of(folder.getId()), zip);
        }
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertEquals("v_001.ts", new String(entries.get("Film/v_001.ts"), StandardCharsets.UTF_8));
        assertEquals("nested", new String(entries.get("Film/segments/nested.m4s"), StandardCharsets.UTF_8));
        assertTrue(entries.containsKey("Film/master.m3u8"));
        assertTrue(entries.containsKey("Film/source.mp4"));
        assertFalse(entries.containsKey("Film/poster.jpg"));
        assertFalse(entries.containsKey("Film/not-synchronized.txt"));
        assertEquals(entries.values().stream().mapToLong(value -> value.length).sum(), manager.calculateAndUpdateFolderSize(folder.getId()));
    }

    @Test
    void permanentFolderDeletionRemovesUntrackedFilesOnlyAfterExplicitTrashAndDelete() throws Exception {
        createProduction();
        sync.synchronizeStorageWithDB("/Configured Videos/");
        Metadata folder = record("/Configured Videos/", "Film");
        assertThrows(IllegalStateException.class, () -> manager.deletePermanently(folder));
        manager.delete(folder);
        assertTrue(Files.exists(root.resolve("Configured Videos/Film/v_001.ts")));
        manager.deletePermanently(folder);
        assertFalse(Files.exists(root.resolve("Configured Videos/Film")));
        assertTrue(Files.exists(root.resolve("Configured Videos/Other/source.ts")));
        assertTrue(matching(m -> m.getPath().startsWith("/Configured Videos/Film/")).isEmpty());
        verify(hooks).beforeDirectoryDelete(folder);
    }

    @Test
    void conversionPassesDiskPlaylistToFfmpegWithoutSegmentMetadata() throws Exception {
        createProduction();
        sync.synchronizeStorageWithDB("/Configured Videos/");
        Metadata folder = record("/Configured Videos/", "Film");
        VideoManager videoManager = mock(VideoManager.class);
        when(videoManager.loadById(folder.getId().toString())).thenReturn(List.of(folder));
        String folderPath = manager.getFile(folder).toString();
        when(videoManager.getSrc(folder)).thenReturn(folderPath);
        VideoController controller = new VideoController(videoManager, Map.of());

        var response = controller.downloadAndConvert(folder.getId().toString());

        assertEquals(200, response.getStatusCode().value());
        assertEquals("video/mp4", response.getHeaders().getFirst("Content-Type"));
        assertNotNull(response.getBody());
        verify(videoManager).loadById(folder.getId().toString());
        verify(videoManager).getSrc(folder);
        byte[] convertedBytes = "converted-mp4-output".getBytes(StandardCharsets.UTF_8);
        Process process = mock(Process.class);
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor()).thenReturn(0);
        var command = new java.util.concurrent.atomic.AtomicReference<List<String>>();
        try (var builders = mockConstruction(ProcessBuilder.class, (builder, context) -> {
            when(builder.command(anyList())).thenAnswer(invocation -> {
                command.set(invocation.getArgument(0));
                return builder;
            });
            when(builder.start()).thenAnswer(invocation -> {
                List<String> args = command.get();
                Files.write(Path.of(args.get(args.size() - 1)), convertedBytes);
                return process;
            });
        })) {
            ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
            response.getBody().writeTo(downloaded);
            assertArrayEquals(convertedBytes, downloaded.toByteArray());
            assertEquals(1, builders.constructed().size());
        }
        List<String> args = command.get();
        assertEquals("ffmpeg", args.get(0));
        assertEquals(root.resolve("Configured Videos/Film/master.m3u8").toString(), args.get(args.indexOf("-i") + 1));
        assertFalse(Files.exists(Path.of(args.get(args.size() - 1))));
        assertTrue(Files.exists(root.resolve("Configured Videos/Film/v_001.ts")));
    }
}
