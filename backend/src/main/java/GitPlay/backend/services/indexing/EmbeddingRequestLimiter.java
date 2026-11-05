package GitPlay.backend.services.indexing;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Spaces batches by chunk count across all workers sharing the Gemini project. */
@Component
public class EmbeddingRequestLimiter {
    private final long perChunkNanos;
    private long lastRequestNanos;
    private int lastChunkCount;
    private boolean started;

    public EmbeddingRequestLimiter(@Value("${app.indexing.embedding-delay-ms:750}") long delayMs) {
        perChunkNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, delayMs));
    }

    public synchronized void awaitSlot(int chunkCount) {
        if (started) {
            long remaining = perChunkNanos * lastChunkCount - (System.nanoTime() - lastRequestNanos);
            if (remaining > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(remaining);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Indexing interrupted while waiting for embedding quota", ex);
                }
            }
        }
        lastRequestNanos = System.nanoTime();
        lastChunkCount = Math.max(1, chunkCount);
        started = true;
    }
}
