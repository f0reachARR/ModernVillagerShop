package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.admin.AdminShopSlotIO;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.NoSuchFileException;
import java.util.Optional;

@SuppressWarnings("UnstableApiUsage")
public final class AdminImportSlotsCommand {

    private static final int TARGET_RANGE = 8;

    private final CommandSupport support;

    public AdminImportSlotsCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("import")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.admin.import"))
                .then(Commands.argument("file", StringArgumentType.word())
                        .executes(ctx -> execute(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "file"))));
    }

    private int execute(CommandSender sender, String fileName) {
        if (!(sender instanceof Player player)) {
            support.sendPlayerOnly(sender);
            return 0;
        }
        Optional<Shop> looked = support.findShopFromLineOfSight(player, TARGET_RANGE);
        if (looked.isEmpty()) {
            sender.sendMessage(support.messages().get("command.admin.import.no-target-villager"));
            return 0;
        }
        Shop shop = looked.get();
        if (!shop.isAdminShop()) {
            sender.sendMessage(support.messages().get("command.admin.import.not-admin-shop"));
            return 0;
        }

        AdminShopSlotIO io = support.plugin().adminShopSlotIO();
        Bukkit.getScheduler().runTaskAsynchronously(support.plugin(), () -> {
            try {
                AdminShopSlotIO.ImportResult result = io.importSlots(shop, fileName);
                Bukkit.getScheduler().runTask(support.plugin(), () ->
                        sender.sendMessage(support.messages().get("command.admin.import.done",
                                Placeholder.parsed("deleted", String.valueOf(result.deleted())),
                                Placeholder.parsed("inserted", String.valueOf(result.inserted())),
                                Placeholder.parsed("backup", result.backupPath().getFileName().toString()))));
            } catch (NoSuchFileException ex) {
                Bukkit.getScheduler().runTask(support.plugin(), () ->
                        sender.sendMessage(support.messages().get("command.admin.import.file-not-found",
                                Placeholder.parsed("file", String.valueOf(ex.getFile())))));
            } catch (AdminShopSlotIO.InvalidExportFormatException ex) {
                Bukkit.getScheduler().runTask(support.plugin(), () ->
                        sender.sendMessage(support.messages().get("command.admin.import.parse-failed",
                                Placeholder.parsed("reason", String.valueOf(ex.getMessage())))));
            } catch (IllegalArgumentException ex) {
                Bukkit.getScheduler().runTask(support.plugin(), () ->
                        sender.sendMessage(support.messages().get("command.admin.import.failed",
                                Placeholder.parsed("reason", String.valueOf(ex.getMessage())))));
            } catch (Exception ex) {
                support.plugin().getLogger().warning("Import failed: " + ex.getMessage());
                Bukkit.getScheduler().runTask(support.plugin(), () ->
                        sender.sendMessage(support.messages().get("command.admin.import.failed",
                                Placeholder.parsed("reason", String.valueOf(ex.getMessage())))));
            }
        });
        return Command.SINGLE_SUCCESS;
    }
}
