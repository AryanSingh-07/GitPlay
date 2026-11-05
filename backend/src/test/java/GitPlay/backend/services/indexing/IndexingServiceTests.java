package GitPlay.backend.services.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.mockito.ArgumentCaptor;

import GitPlay.backend.entity.IndexStatus;
import GitPlay.backend.entity.Repository;
import GitPlay.backend.entity.User;
import GitPlay.backend.repository.RepositoryRepository;
import GitPlay.backend.services.UserService;
import GitPlay.backend.services.github.GitHubRateLimiter;
import GitPlay.backend.services.github.GithubApiClient;

class IndexingServiceTests {
    private RepositoryRepository repositories;
    private GithubApiClient github;
    private CodeChunker chunker;
    private VectorStore vectors;
    private EmbeddingRequestLimiter embeddingLimiter;
    private IndexingService service;
    private Repository repo;
    private UUID userId;

    @BeforeEach
    void setUp() {
        repositories = mock(RepositoryRepository.class);
        github = mock(GithubApiClient.class);
        chunker = mock(CodeChunker.class);
        vectors = mock(VectorStore.class);
        embeddingLimiter = mock(EmbeddingRequestLimiter.class);
        UserService users = mock(UserService.class);
        userId = UUID.randomUUID();
        repo = Repository.builder().id(UUID.randomUUID()).userId(userId).owner("owner")
                .name("repo").fullName("owner/repo").defaultBranch("release")
                .indexStatus(IndexStatus.INDEXING).build();
        when(repositories.findById(repo.getId())).thenReturn(Optional.of(repo));
        User user = User.builder().id(userId).build();
        when(users.requireById(userId)).thenReturn(user);
        when(users.decryptAccessToken(user)).thenReturn("test-token");
        service = new IndexingService(repositories, users, github, new CodeFileFilter(),
                chunker, mock(GitHubRateLimiter.class), vectors, embeddingLimiter);
    }

    @Test
    void vectorDeletionFailureStopsIndexing() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(vectors).delete(any(Filter.Expression.class));

        service.indexAsync(repo.getId(), userId);

        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.FAILED);
        assertThat(repo.getErrorMessage()).isEqualTo("database unavailable");
        verifyNoInteractions(github);
    }

    @Test
    void embeddingFailureCannotMarkRepositoryReady() {
        when(github.getRepoTree("test-token", "owner", "repo", "release"))
                .thenReturn(Map.of("tree", List.of(Map.of("type", "blob", "path", "Main.java"))));
        when(github.getFileContent("test-token", "owner", "repo", "Main.java", "release"))
                .thenReturn("class Main {}");
        when(chunker.chunkFile(repo.getId().toString(), "Main.java", "class Main {}"))
                .thenReturn(IntStream.range(0, 32).mapToObj(i -> new Document("chunk " + i)).toList());
        doThrow(new IllegalStateException("embedding request failed")).when(vectors).add(any());

        service.indexAsync(repo.getId(), userId);

        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.FAILED);
        assertThat(repo.getErrorMessage()).isEqualTo("embedding request failed");
        verify(github).getFileContent("test-token", "owner", "repo", "Main.java", "release");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void largeFileIsIndexedInBoundedBatchesWithoutLosingChunks() {
        when(github.getRepoTree("test-token", "owner", "repo", "release"))
                .thenReturn(Map.of("tree", List.of(Map.of("type", "blob", "path", "Main.java"))));
        when(github.getFileContent("test-token", "owner", "repo", "Main.java", "release"))
                .thenReturn("class Main {}");
        List<Document> chunks = IntStream.range(0, 35)
                .mapToObj(i -> new Document("chunk " + i)).toList();
        when(chunker.chunkFile(repo.getId().toString(), "Main.java", "class Main {}"))
                .thenReturn(chunks);

        service.indexAsync(repo.getId(), userId);

        ArgumentCaptor<List<Document>> batches = ArgumentCaptor.forClass((Class) List.class);
        verify(vectors, times(3)).add(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(16, 16, 3);
        assertThat(batches.getAllValues().stream().flatMap(List::stream).toList())
                .containsExactlyElementsOf(chunks);
        ArgumentCaptor<Integer> quotaUnits = ArgumentCaptor.forClass(Integer.class);
        verify(embeddingLimiter, times(3)).awaitSlot(quotaUnits.capture());
        assertThat(quotaUnits.getAllValues()).containsExactly(16, 16, 3);
        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(repo.getChunkCount()).isEqualTo(35);
    }
}
