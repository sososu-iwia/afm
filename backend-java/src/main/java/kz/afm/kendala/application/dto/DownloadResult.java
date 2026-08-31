package kz.afm.kendala.application.dto;

public record DownloadResult(byte[] content, String contentType, String originalFileName, long size) {}
