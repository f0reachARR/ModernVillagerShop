package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the {@link ShopSlot#command()} string on SELL settlement as the console
 * sender, substituting a fixed placeholder set. Splits on newline / semicolon
 * so operators can chain multiple commands per slot.
 *
 * <p>Only invoked by {@link TradeService} for admin shops; the caller has
 * already committed the DB and Vault legs, so a command failure here does NOT
 * roll the trade back — we just log a warning and notify the buyer.</p>
 */
public final class CommandDispatcher {

    private static final Logger LOG = Logger.getLogger(CommandDispatcher.class.getName());

    private final Plugin plugin;
    private final MessageManager messages;
    private final EconomyService economy;
    // Test seam: TradeService and the unit tests can swap in a synchronous
    // dispatcher so we don't have to spin up a Bukkit scheduler in-process.
    private final BiConsumer<CommandSender, String> executor;

    public CommandDispatcher(Plugin plugin, MessageManager messages, EconomyService economy) {
        this(plugin, messages, economy, (sender, cmd) -> Bukkit.dispatchCommand(sender, cmd));
    }

    CommandDispatcher(Plugin plugin, MessageManager messages, EconomyService economy,
                      BiConsumer<CommandSender, String> executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.economy = economy;
        this.executor = executor;
    }

    /**
     * Resolve placeholders, split, and execute each line as the console sender.
     * Any failure notifies the buyer via {@code trade.command.failed} but does
     * not throw (the trade is already committed).
     */
    public void dispatch(Player buyer, Shop shop, ShopSlot slot, int packs, int totalItems, BigDecimal gross) {
        if (!slot.hasCommand()) return;
        List<String> commands = split(slot.command());
        Runnable runAll = () -> executeAll(buyer, shop, slot, packs, totalItems, gross, commands);
        if (Bukkit.isPrimaryThread()) {
            runAll.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runAll);
        }
    }

    private void executeAll(Player buyer, Shop shop, ShopSlot slot, int packs, int totalItems,
                            BigDecimal gross, List<String> commands) {
        CommandSender console = Bukkit.getConsoleSender();
        for (String line : commands) {
            String resolved = resolve(line, buyer, shop, slot, packs, totalItems, gross);
            try {
                executor.accept(console, resolved);
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Command dispatch failed for shop " + shop.id()
                        + " slot " + slot.id() + ": " + resolved, t);
                buyer.sendMessage(messages.get("trade.command.failed",
                        Placeholder.parsed("command", resolved)));
            }
        }
    }

    /**
     * Split the raw slot string on newlines and semicolons. Blank segments are
     * dropped so trailing separators don't dispatch empty commands.
     */
    static List<String> split(String raw) {
        return List.of(raw.split("[\\r\\n;]+")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Substitute the supported placeholder set. Placeholders are plain text
     * (not MiniMessage tags) because commands are dispatched to Bukkit as raw
     * strings.
     */
    String resolve(String line, Player buyer, Shop shop, ShopSlot slot, int packs, int totalItems, BigDecimal gross) {
        String price = economy != null ? economy.format(gross) : gross.toPlainString();
        return line
                .replace("<player>", buyer.getName())
                .replace("<player_uuid>", buyer.getUniqueId().toString())
                .replace("<packs>", Integer.toString(packs))
                .replace("<amount>", Integer.toString(totalItems))
                .replace("<price>", price)
                .replace("<shop_id>", shop.id().toString())
                .replace("<slot_id>", slot.id().toString());
    }
}
