package me.f0reach.vshop.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public final class VaultEconomyAdapter {
    private Economy economy;

    public void init() {
        RegisteredServiceProvider<Economy> rsp =
            Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }
        economy = rsp.getProvider();
    }

    public boolean has(UUID playerUuid, double amount) {
        return economy.has(Bukkit.getOfflinePlayer(playerUuid), amount);
    }

    public boolean withdraw(UUID playerUuid, double amount) {
        EconomyResponse resp = economy.withdrawPlayer(Bukkit.getOfflinePlayer(playerUuid), amount);
        return resp.transactionSuccess();
    }

    public boolean deposit(UUID playerUuid, double amount) {
        EconomyResponse resp = economy.depositPlayer(Bukkit.getOfflinePlayer(playerUuid), amount);
        return resp.transactionSuccess();
    }

    public double getBalance(UUID playerUuid) {
        return economy.getBalance(Bukkit.getOfflinePlayer(playerUuid));
    }

    public String format(double amount) {
        return economy.format(amount);
    }
}
