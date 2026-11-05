package GitPlay.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionProperties;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Exercises the real provider auto-configuration without contacting Gemini or
 * requiring PostgreSQL. Provider models and their options come from the same
 * application.properties used by the running application.
 */
@SpringBootTest(properties = {
        "GEMINI_API_KEY=test-only-gemini-key",
        "GOOGLE_API_KEY=",
        "spring.datasource.url=jdbc:h2:mem:gitplay-gemini-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
        "spring.security.oauth2.client.registration.github.client-id=test-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-secret",
        "app.token-encryptor-password=test-only-password",
        "app.token-encryptor-salt=0123456789abcdef"
})
class GeminiConfigurationTests {
    @Autowired ChatModel chatModel;
    @Autowired EmbeddingModel embeddingModel;
    @Autowired GoogleGenAiConnectionProperties chatConnection;
    @Autowired GoogleGenAiEmbeddingConnectionProperties embeddingConnection;
    @Autowired Environment environment;

    @MockitoBean VectorStore vectorStore;

    @Test
    void applicationCreatesGeminiModelsForChatAndRepositoryEmbeddings() {
        assertThat(chatModel).isInstanceOf(GoogleGenAiChatModel.class);
        assertThat(chatModel.getOptions().getModel()).isEqualTo("gemini-3.1-flash-lite");
        assertThat(embeddingModel).isInstanceOf(GoogleGenAiTextEmbeddingModel.class);
        assertThat(((GoogleGenAiTextEmbeddingModel) embeddingModel).options.getModel())
                .isEqualTo("gemini-embedding-2");
    }

    @Test
    void oneGeminiKeyConfiguresBothProviderConnections() {
        assertThat(chatConnection.getApiKey()).isEqualTo("test-only-gemini-key");
        assertThat(embeddingConnection.getApiKey()).isEqualTo(chatConnection.getApiKey());
    }

    @Test
    void embeddingsMatchPgVectorDimensionsAndUseTheirOwnTable() {
        Integer embeddingDimensions = ((GoogleGenAiTextEmbeddingModel) embeddingModel).options.getDimensions();
        assertThat(embeddingDimensions).isEqualTo(1536);
        assertThat(environment.getProperty("spring.ai.vectorstore.pgvector.dimensions", Integer.class))
                .isEqualTo(embeddingDimensions);
        assertThat(environment.getProperty("spring.ai.vectorstore.pgvector.table-name"))
                .isEqualTo("vector_store_gemini");
        assertThat(environment.getProperty("spring.ai.vectorstore.pgvector.remove-existing-vector-store-table", Boolean.class, false))
                .isFalse();
    }
}
