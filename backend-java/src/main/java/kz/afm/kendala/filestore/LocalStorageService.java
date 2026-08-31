package kz.afm.kendala.filestore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.common.exception.StorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local")
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(@Value("${app.storage.local.path}") String path) throws IOException {
        this.root = Paths.get(path).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public StoredFile store(String originalFileName, String contentType, byte[] content) {
        String key = UUID.randomUUID().toString();
        try {
            Files.write(resolveSafe(key), content);
        } catch (IOException e) {
            throw new StorageException("Ошибка сохранения файла");
        }
        return new StoredFile(key, content.length);
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            Path file = resolveSafe(storageKey);
            if (!Files.exists(file)) {
                throw new NotFoundException("Файл не найден в хранилище");
            }
            return Files.readAllBytes(file);
        } catch (NotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new StorageException("Ошибка чтения файла");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveSafe(storageKey));
        } catch (IOException e) {
            throw new StorageException("Ошибка удаления файла");
        }
    }

    @Override
    public void checkHealth() {
        if (!Files.isDirectory(root) || !Files.isReadable(root) || !Files.isWritable(root)) {
            throw new StorageException("Локальное хранилище недоступно");
        }
    }

    private Path resolveSafe(String key) {
        if (key == null || key.isBlank()) {
            throw new StorageException("Пустой ключ хранилища");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("Недопустимый ключ хранилища");
        }
        return resolved;
    }
}
