package GitPlay.backend.services;

import java.util.UUID;
import java.util.Map;

import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import GitPlay.backend.entity.User;
import GitPlay.backend.exceptions.NotFoundException;
import GitPlay.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    public final UserRepository userRepository;
    public final TextEncryptor textEncryptor;

    @Transactional
public User upsertFromGitHub(Map<String, Object> attributes, String accessToken, String scopes) {
    Long githubId = toLong(attributes.get("id"));
    String login = String.valueOf(attributes.get("login"));
    String name = attributes.get("name") != null
            ? String.valueOf(attributes.get("name"))
            : login;
    String avatarUrl = attributes.get("avatar_url") != null
            ? String.valueOf(attributes.get("avatar_url"))
            : null;

    String encryptedToken = textEncryptor.encrypt(accessToken);

    User user = userRepository.findByGithubId(githubId).orElseGet(User::new);
    user.setGithubId(githubId);
    user.setGithubUsername(login);
    user.setDisplayName(name);
    user.setAvatarUrl(avatarUrl);
    user.setAccessToken(encryptedToken);
    user.setTokenScopes(scopes);
    return userRepository.save(user);
}

    @Transactional(readOnly = true)

    public User requireById(UUID id) {
    return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));
}

public String decryptAccessToken(User user) {
    return textEncryptor.decrypt(user.getAccessToken());
}
private static Long toLong(Object value) {
    if (value instanceof Number number) {
        return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
}
}
