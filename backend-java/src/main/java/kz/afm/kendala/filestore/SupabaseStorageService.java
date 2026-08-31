package kz.afm.kendala.filestore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.common.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase")
public class SupabaseStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final HttpClient httpClient;
    private final String objectsBaseUrl;
    private final String bucket;
    private final String serviceKey;

    public SupabaseStorageService(
            @Value("${SUPABASE_URL}") String supabaseUrl,
            @Value("${SUPABASE_STORAGE_BUCKET}") String bucket,
            @Value("${SUPABASE_SERVICE_KEY}") String serviceKey
    ) {
        String base = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        this.objectsBaseUrl = base + "/storage/v1/object";
        this.bucket = bucket;
        this.serviceKey = serviceKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public StoredFile store(String originalFileName, String contentType, byte[] content) {
        String key = UUID.randomUUID().toString();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(objectsBaseUrl + "/" + bucket + "/" + key))
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Supabase upload failed, status={}", response.statusCode());
                throw new StorageException("Ошибка загрузки файла в хранилище");
            }
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Supabase upload error", e);
            throw new StorageException("Ошибка загрузки файла в хранилище");
        }
        return new StoredFile(key, content.length);
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(objectsBaseUrl + "/authenticated/" + bucket + "/" + storageKey))
                    .header("Authorization", "Bearer " + serviceKey)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new NotFoundException("Файл не найден в хранилище");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Supabase download failed, status={}", response.statusCode());
                throw new StorageException("Ошибка скачивания файла из хранилища");
            }
            return response.body();
        } catch (StorageException | NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Supabase load error", e);
            throw new StorageException("Ошибка скачивания файла из хранилища");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            String body = "{\"prefixes\":[\"" + storageKey + "\"]}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(objectsBaseUrl + "/" + bucket))
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Supabase delete failed, status={}", response.statusCode());
                throw new StorageException("Ошибка удаления файла из хранилища");
            }
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Supabase delete error", e);
            throw new StorageException("Ошибка удаления файла из хранилища");
        }
    }

    @Override
    public void checkHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(objectsBaseUrl + "/list/" + bucket))
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"limit\":1,\"offset\":0}"))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new StorageException("Хранилище Supabase недоступно");
            }
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Хранилище Supabase недоступно");
        }
    }
}
