package GitPlay.backend.repository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import GitPlay.backend.entity.Repository;

public interface RepositoryRepository extends JpaRepository<Repository, UUID> {
    List<Repository> findByUserIdOrderByFullNameAsc(UUID userId);

    List<Repository> findByUserIdAndGithubUnavailableAtIsNullOrderByFullNameAsc(UUID userId);

    Optional<Repository> findByIdAndUserIdAndGithubUnavailableAtIsNull(UUID id, UUID userId);

    Optional<Repository> findByUserIdAndGithubRepoId(UUID userId, Long githubRepoId);
}
