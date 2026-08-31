package kz.afm.kendala.filestore;

public interface StorageService {
    StoredFile store(String originalFileName, String contentType, byte[] content);
    byte[] load(String storageKey);
    void delete(String storageKey);
    void checkHealth();
}
