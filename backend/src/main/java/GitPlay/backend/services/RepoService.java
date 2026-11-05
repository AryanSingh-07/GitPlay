package GitPlay.backend.services;


import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import GitPlay.backend.dto.IndexStatusResponse;
import GitPlay.backend.dto.RepositoryResponse;
import GitPlay.backend.entity.Repository;
import GitPlay.backend.entity.User;
import GitPlay.backend.exceptions.NotFoundException;
import GitPlay.backend.repository.RepositoryRepository;
import GitPlay.backend.services.github.GithubApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoService {
    private final RepositoryRepository repositoryRepository;
    private final UserService userService;
    private final GithubApiClient gitHubApiClient;

    @Value("${app.github.repository-sync-interval-ms:60000}")
    private long repositorySyncIntervalMs = 60_000;

    @Transactional
    public List<RepositoryResponse> syncAndListRepos(UUID userId) {
        User user = userService.requireById(userId);
        String token = userService.decryptAccessToken(user);
        List<Map<String, Object>> remoteRepos = gitHubApiClient.listUserRepos(token);

        List<Repository> saved = new ArrayList<>();
        Set<Long> remoteIds = new HashSet<>();
        Instant checkedAt = Instant.now();

          for (Map<String, Object> remote : remoteRepos) {
            Long githubRepoId = toLong(remote.get("id"));
            remoteIds.add(githubRepoId);
            Repository repo = repositoryRepository
                    .findByUserIdAndGithubRepoId(userId, githubRepoId)
                    .orElseGet(Repository::new);

            String fullName = String.valueOf(remote.get("full_name"));
            String[] parts = fullName.split("/", 2);

            repo.setUserId(userId);
            repo.setGithubRepoId(githubRepoId);
            repo.setOwner(parts.length > 0 ? parts[0] : String.valueOf(remote.get("owner")));
            repo.setName(parts.length > 1 ? parts[1] : String.valueOf(remote.get("name")));
            repo.setFullName(fullName);
            repo.setPrivate(Boolean.TRUE.equals(remote.get("private")));
            repo.setDefaultBranch(remote.get("default_branch") != null
                    ? String.valueOf(remote.get("default_branch"))
                    : "main");
            repo.setLanguage(remote.get("language") != null ? String.valueOf(remote.get("language")) : null);
            repo.setHtmlUrl(remote.get("html_url") != null ? String.valueOf(remote.get("html_url")) : null);
            repo.setDescription(remote.get("description") != null ? String.valueOf(remote.get("description")) : null);
            repo.setGithubUnavailableAt(null);
            repo.setUpdatedAt(Instant.now());
            if (repo.getOwner() == null || repo.getOwner().isBlank()) {
                Object ownerObj = remote.get("owner");
                if (ownerObj instanceof Map<?, ?> ownerMap && ownerMap.get("login") != null) {
                    repo.setOwner(String.valueOf(ownerMap.get("login")));
                }
            }
            saved.add(repositoryRepository.save(repo));
        }

        // Only reconcile after every GitHub page has been fetched successfully.
        // Keep rows and their chat history so restored access can reuse the same IDs.
        for (Repository stored : repositoryRepository.findByUserIdOrderByFullNameAsc(userId)) {
            if (!remoteIds.contains(stored.getGithubRepoId()) && stored.getGithubUnavailableAt() == null) {
                stored.setGithubUnavailableAt(checkedAt);
                repositoryRepository.save(stored);
            }
        }
        user.setRepositoriesCheckedAt(checkedAt);
    
         return saved.stream()
                .sorted((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()))
                .map(this::toResponse)
                .toList();
    }

    
    @Transactional
    public List<RepositoryResponse> listStored(UUID userId) {
        User user = userService.requireById(userId);
        Instant now = Instant.now();
        if (user.getRepositoriesCheckedAt() == null ||
                !user.getRepositoriesCheckedAt().plusMillis(Math.max(1000, repositorySyncIntervalMs)).isAfter(now)) {
            // Remember attempts as well as successes to avoid retrying failed GitHub
            // requests on every short indexing-progress poll.
            user.setRepositoriesCheckedAt(now);
            try {
                return syncAndListRepos(userId);
            } catch (RestClientException ex) {
                log.warn("Could not refresh repositories for user {}; keeping the saved list", userId);
            }
        }
        return repositoryRepository.findByUserIdAndGithubUnavailableAtIsNullOrderByFullNameAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public Repository requireOwned(UUID repoId, UUID userId) {
        return repositoryRepository.findByIdAndUserIdAndGithubUnavailableAtIsNull(repoId, userId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));
    }

    
    @Transactional(readOnly = true)
    public IndexStatusResponse status(UUID repoId, UUID userId) {
        Repository repo = requireOwned(repoId, userId);
        return new IndexStatusResponse(
                repo.getId(),
                repo.getIndexStatus(),
                repo.getFilesTotal(),
                repo.getFilesProcessed(),
                repo.getChunkCount(),
                repo.getIndexedAt(),
                repo.getErrorMessage());
    }

     public RepositoryResponse toResponse(Repository repo) {
        return new RepositoryResponse(
                repo.getId(),
                repo.getGithubRepoId(),
                repo.getOwner(),
                repo.getName(),
                repo.getFullName(),
                repo.isPrivate(),
                repo.getDefaultBranch(),
                repo.getLanguage(),
                repo.getHtmlUrl(),
                repo.getDescription(),
                repo.getIndexStatus(),
                repo.getIndexedAt(),
                repo.getChunkCount(),
                repo.getFilesTotal(),
                repo.getFilesProcessed(),
                repo.getErrorMessage());
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
