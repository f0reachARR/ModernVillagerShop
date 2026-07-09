package me.f0reach.vshop.economy;

import me.f0reach.vshop.config.PluginConfig;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Vault adapter that keeps the rest of the plugin in {@link BigDecimal} space —
 * conversion to {@code double} only happens at the Vault boundary, using the
 * configured scale + rounding mode so the deposited amount is exactly the
 * rounded value we computed.
 */
public final class EconomyService {

    private final Plugin plugin;
    private final PluginConfig config;
    private Economy economy;

    public EconomyService(Plugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Resolve Vault at enable time. Returns false if Vault/Economy is missing. */
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean isAvailable() { return economy != null; }

    public BigDecimal balance(OfflinePlayer player) {
        if (economy == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(economy.getBalance(player));
    }

    public boolean has(OfflinePlayer player, BigDecimal amount) {
        if (economy == null) return false;
        return economy.has(player, toDouble(amount));
    }

    public EconomyResponse withdraw(OfflinePlayer player, BigDecimal amount) {
        return economy.withdrawPlayer(player, toDouble(amount));
    }

    public EconomyResponse deposit(OfflinePlayer player, BigDecimal amount) {
        return economy.depositPlayer(player, toDouble(amount));
    }

    public String format(BigDecimal amount) {
        if (economy == null) return amount.toPlainString();
        try {
            return economy.format(toDouble(amount));
        } catch (Throwable t) {
            return amount.setScale(config.economy().fractionDigits(), config.economy().roundingMode())
                    .toPlainString();
        }
    }

    public BigDecimal round(BigDecimal amount) {
        return amount.setScale(config.economy().fractionDigits(), config.economy().roundingMode());
    }

    public BigDecimal computeFee(BigDecimal gross, boolean adminShop) {
        BigDecimal rate = adminShop ? config.economy().feeRateAdmin() : config.economy().feeRate();
        BigDecimal raw = gross.multiply(rate);
        return raw.setScale(config.economy().fractionDigits(), config.economy().roundingMode());
    }

    private double toDouble(BigDecimal amount) {
        return amount.setScale(config.economy().fractionDigits(), config.economy().roundingMode()).doubleValue();
    }

    Plugin plugin() { return plugin; }
    RoundingMode roundingMode() { return config.economy().roundingMode(); }
    int scale() { return config.economy().fractionDigits(); }
}
