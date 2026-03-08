package me.f0reach.vshop.storage;

public enum StorageType {
    SQLITE,
    MYSQL;

    public static StorageType fromConfig(String value) {
        return "mysql".equalsIgnoreCase(value) ? MYSQL : SQLITE;
    }
}
