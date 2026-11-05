package GitPlay.backend.services.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class GithubApiClientTests {
    private MockRestServiceServer server;
    private GithubApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GithubApiClient(builder);
    }

    @Test
    void listsAllRepositoriesBeyondTenPages() {
        for (int page = 1; page <= 11; page++) {
            server.expect(requestTo(containsString("&page=" + page)))
                    .andRespond(withSuccess(fullPage(), MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo(containsString("&page=12")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.listUserRepos("test-token")).hasSize(1100);
        server.verify();
    }

    @Test
    void failedLaterPageDoesNotReturnAnIncompleteList() {
        server.expect(requestTo(containsString("&page=1")))
                .andRespond(withSuccess(fullPage(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("&page=2")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.listUserRepos("test-token")).isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void missingResponseBodyDoesNotBecomeASuccessfulEmptyList() {
        server.expect(requestTo(containsString("&page=1")))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.listUserRepos("test-token"))
                .isInstanceOf(RestClientException.class).hasMessageContaining("not completed");
        server.verify();
    }

    private String fullPage() {
        return "[" + String.join(",", Collections.nCopies(100, "{}")) + "]";
    }
}
