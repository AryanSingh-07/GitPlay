package GitPlay.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import GitPlay.backend.entity.ChatSession;
import GitPlay.backend.entity.ChatMessage;
import GitPlay.backend.entity.MessageRole;
import GitPlay.backend.entity.IndexStatus;
import GitPlay.backend.entity.Repository;
import GitPlay.backend.entity.User;
import GitPlay.backend.repository.ChatSessionRepository;
import GitPlay.backend.repository.ChatMessageRepository;
import GitPlay.backend.repository.RepositoryRepository;
import GitPlay.backend.repository.UserRepository;
import GitPlay.backend.security.AppUserPrincipal;
import GitPlay.backend.services.UserService;
import GitPlay.backend.services.github.GithubApiClient;
import reactor.core.publisher.Flux;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gitplay-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
        "spring.ai.google.genai.api-key=test-only-key",
        "spring.ai.google.genai.embedding.api-key=test-only-key",
        "spring.security.oauth2.client.registration.github.client-id=test-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-secret",
        "app.token-encryptor-password=test-only-password",
        "app.token-encryptor-salt=0123456789abcdef"
})
@AutoConfigureMockMvc
class BackendApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired RepositoryRepository repositories;
    @Autowired ChatSessionRepository sessions;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired ChatMessageRepository messages;

    @MockitoBean VectorStore vectorStore;
    @MockitoBean ChatModel chatModel;
    @MockitoBean EmbeddingModel embeddingModel;
    @MockitoBean GithubApiClient github;

	@Test
	void contextLoads() {
	}

    @Test
    void loginUrlIsPublicAndApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/auth/login-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/oauth2/authorization/github"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void githubUserTokensAreEncryptedAndRecoverable() {
        var user = userService.upsertFromGitHub(
                Map.of("id", 12345L, "login", "test-user"), "test-access-token", "read:user,repo");
        assertThat(user.getAccessToken()).isNotEqualTo("test-access-token");
        assertThat(userService.decryptAccessToken(user)).isEqualTo("test-access-token");
        assertThat(user.getId()).isNotNull();
    }

    @Test
    void chatRoutesEnforceOwnershipAndValidateRequests() throws Exception {
        UUID userId = UUID.randomUUID();
        Repository repo = readyRepository(userId);
        var auth = authentication(authenticatedUser(userId));

        String body = "{\"repositoryId\":\"" + repo.getId() + "\"}";
        mvc.perform(post("/api/chat/sessions").with(auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repositoryId").value(repo.getId().toString()));
        mvc.perform(get("/api/chat/sessions").with(auth).param("repositoryId", repo.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/chat/sessions").with(authentication(authenticatedUser(UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/chat/sessions").with(auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/chat/sessions/invalid-id").with(auth))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/chat/sessions").with(auth)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat/sessions").with(auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat/sessions/" + UUID.randomUUID() + "/messages").with(auth)
                        .accept("text/event-stream, application/json")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat/sessions/" + UUID.randomUUID() + "/messages").with(auth)
                        .accept("text/event-stream, application/json")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"explain this\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void modelFailureIsReportedAsAnSseError() throws Exception {
        UUID userId = UUID.randomUUID();
        Repository repo = readyRepository(userId);
        ChatSession session = sessions.save(ChatSession.builder()
                .userId(userId).repositoryId(repo.getId()).title("error chat").build());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new IllegalStateException("model unavailable")));

        var result = mvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                        .with(authentication(authenticatedUser(userId)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"explain this\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:error")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("event:done"))));
        mvc.perform(get("/api/chat/sessions/" + session.getId())
                        .with(authentication(authenticatedUser(userId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void chatTokensUseJsonEncodingAndReplyIsPersisted() throws Exception {
        UUID userId = UUID.randomUUID();
        Repository repo = readyRepository(userId);
        ChatSession session = sessions.save(ChatSession.builder()
                .userId(userId).repositoryId(repo.getId()).title("test chat").build());
        String token = "hello \"code\"\n";
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage(token))))));

        var result = mvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                        .with(authentication(authenticatedUser(userId)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"explain this\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data:\"hello \\\"code\\\"\\n\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
        mvc.perform(get("/api/chat/sessions/" + session.getId())
                        .with(authentication(authenticatedUser(userId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].content").value(token));
    }

    @Test
    void automaticSyncHidesMissingRepositoriesAndRestoresTheSameRecordWithItsHistory() throws Exception {
        User user = syncUser("reconcile");
        Repository kept = syncRepository(user.getId(), 1101L, "kept");
        Repository removed = syncRepository(user.getId(), 1102L, "removed");
        ChatSession session = sessions.save(ChatSession.builder().userId(user.getId())
                .repositoryId(removed.getId()).title("saved history").build());
        messages.save(ChatMessage.builder().sessionId(session.getId()).role(MessageRole.USER)
                .content("Keep this conversation").build());
        var auth = authentication(authenticatedUser(user.getId()));
        when(github.listUserRepos("sync-token-reconcile")).thenReturn(List.of(remoteRepo(1101L, "kept")));

        mvc.perform(get("/api/repos").param("refresh", "false").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(kept.getId().toString()));
        mvc.perform(get("/api/repos").param("refresh", "false").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        verify(github, times(1)).listUserRepos("sync-token-reconcile");
        assertThat(repositories.findById(removed.getId()).orElseThrow().getGithubUnavailableAt()).isNotNull();
        assertThat(sessions.findById(session.getId())).isPresent();
        assertThat(messages.findBySessionIdOrderByCreatedAtAsc(session.getId())).hasSize(1);
        mvc.perform(get("/api/repos/" + removed.getId()).with(auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/repos/" + removed.getId() + "/status").with(auth)).andExpect(status().isNotFound());
        mvc.perform(post("/api/repos/" + removed.getId() + "/index").with(auth)).andExpect(status().isNotFound());
        mvc.perform(post("/api/chat/sessions").with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryId\":\"" + removed.getId() + "\"}"))
                .andExpect(status().isNotFound());

        when(github.listUserRepos("sync-token-reconcile"))
                .thenReturn(List.of(remoteRepo(1101L, "kept"), remoteRepo(1102L, "restored")));
        mvc.perform(get("/api/repos").param("refresh", "true").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        Repository restored = repositories.findById(removed.getId()).orElseThrow();
        assertThat(restored.getGithubUnavailableAt()).isNull();
        assertThat(restored.getName()).isEqualTo("restored");
        assertThat(restored.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(messages.findBySessionIdOrderByCreatedAtAsc(session.getId())).hasSize(1);
        mvc.perform(get("/api/repos/" + removed.getId()).with(auth)).andExpect(status().isOk());
    }

    @Test
    void failedAutomaticSyncKeepsSavedRepositoriesAndThrottlesRetries() throws Exception {
        User user = syncUser("failed");
        Repository kept = syncRepository(user.getId(), 1201L, "keep-on-failure");
        var auth = authentication(authenticatedUser(user.getId()));
        when(github.listUserRepos("sync-token-failed")).thenThrow(new RestClientException("GitHub unavailable"));

        mvc.perform(get("/api/repos").param("refresh", "false").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/repos").param("refresh", "false").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        verify(github, times(1)).listUserRepos("sync-token-failed");
        assertThat(repositories.findById(kept.getId()).orElseThrow().getGithubUnavailableAt()).isNull();
        assertThat(users.findById(user.getId()).orElseThrow().getRepositoriesCheckedAt()).isNotNull();

        mvc.perform(get("/api/repos").param("refresh", "true").with(auth))
                .andExpect(status().isInternalServerError());
        assertThat(repositories.findById(kept.getId()).orElseThrow().getGithubUnavailableAt()).isNull();
    }

    @Test
    void successfulEmptySyncHidesOnlyTheCurrentUsersRepositories() throws Exception {
        User user = syncUser("empty");
        Repository removed = syncRepository(user.getId(), 1301L, "removed");
        Repository otherUsersRepo = syncRepository(UUID.randomUUID(), 1302L, "another-user");
        var auth = authentication(authenticatedUser(user.getId()));
        when(github.listUserRepos("sync-token-empty")).thenReturn(List.of());

        mvc.perform(get("/api/repos").param("refresh", "false").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/repos").param("refresh", "false").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        verify(github, times(1)).listUserRepos("sync-token-empty");
        assertThat(repositories.findById(removed.getId()).orElseThrow().getGithubUnavailableAt()).isNotNull();
        assertThat(repositories.findById(otherUsersRepo.getId()).orElseThrow().getGithubUnavailableAt()).isNull();
    }

    @Test
    void storedListAutomaticallyRechecksGithubAfterTheRefreshInterval() throws Exception {
        User user = syncUser("periodic");
        Repository removed = syncRepository(user.getId(), 1401L, "deleted-since-last-sync");
        user.setRepositoriesCheckedAt(Instant.EPOCH);
        users.save(user);
        when(github.listUserRepos("sync-token-periodic")).thenReturn(List.of());

        mvc.perform(get("/api/repos").param("refresh", "false")
                        .with(authentication(authenticatedUser(user.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        verify(github).listUserRepos("sync-token-periodic");
        assertThat(repositories.findById(removed.getId()).orElseThrow().getGithubUnavailableAt()).isNotNull();
    }

    private User syncUser(String suffix) {
        return userService.upsertFromGitHub(Map.of("id", System.nanoTime(), "login", "sync-" + suffix),
                "sync-token-" + suffix, "read:user,repo");
    }

    private Repository syncRepository(UUID userId, long githubId, String name) {
        return repositories.save(Repository.builder().userId(userId).githubRepoId(githubId)
                .owner("test-owner").name(name).fullName("test-owner/" + name)
                .defaultBranch("main").indexStatus(IndexStatus.READY).build());
    }

    private Map<String, Object> remoteRepo(long githubId, String name) {
        return Map.of("id", githubId, "full_name", "test-owner/" + name, "default_branch", "main");
    }

    private Repository readyRepository(UUID userId) {
        return repositories.save(Repository.builder()
                .userId(userId).githubRepoId(System.nanoTime()).owner("test-owner").name("test-repo")
                .fullName("test-owner/test-repo").defaultBranch("main").indexStatus(IndexStatus.READY).build());
    }

    private UsernamePasswordAuthenticationToken authenticatedUser(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new AppUserPrincipal(User.builder().id(userId).build(), Map.of()),
                null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
