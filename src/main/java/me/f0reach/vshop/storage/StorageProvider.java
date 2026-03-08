package me.f0reach.vshop.storage;

public interface StorageProvider {
    void init();

    ShopRepository shopRepository();

    ListingRepository listingRepository();

    ShopInventoryRepository shopInventoryRepository();

    TransactionRepository transactionRepository();

    void shutdown();
}
