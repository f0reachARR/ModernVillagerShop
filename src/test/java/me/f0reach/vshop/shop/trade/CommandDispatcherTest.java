package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.model.LimitScope;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopLocation;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDispatcherTest {

    @BeforeAll
    static void boot() {
        BukkitTestSupport.ensureStarted();
    }

    @Test
    void splitDropsBlankSegments() {
        List<String> parts = CommandDispatcher.split("give <player> a\ngive <player> b;;give <player> c");
        assertEquals(List.of("give <player> a", "give <player> b", "give <player> c"), parts);
    }

    @Test
    void splitReturnsEmptyForBlankOrSeparatorOnly() {
        assertTrue(CommandDispatcher.split("   ").isEmpty());
        assertTrue(CommandDispatcher.split(";;\n\n").isEmpty());
    }

    @Test
    void resolveSubstitutesAllPlaceholders() {
        Player player = MockBukkit.getMock().addPlayer("Alice");
        Shop shop = shop(true);
        ShopSlot slot = slot(shop.id(), "give <player> cmd");

        CommandDispatcher dispatcher = new CommandDispatcher(
                MockBukkit.createMockPlugin(),
                null, null, (s, c) -> {});
        String resolved = dispatcher.resolve(
                "run <player> <player_uuid> <packs> <amount> <price> <shop_id> <slot_id>",
                player, shop, slot, 3, 30, new BigDecimal("120.00"));

        String expected = "run " + player.getName()
                + " " + player.getUniqueId()
                + " 3 30 120.00 "
                + shop.id() + " " + slot.id();
        assertEquals(expected, resolved);
    }

    @Test
    void dispatchExecutesEachCommandAsConsoleSender() {
        Player player = MockBukkit.getMock().addPlayer("Bob");
        Shop shop = shop(true);
        ShopSlot slot = slot(shop.id(), "say hi <player>\nsay bye <player>");

        List<String> executed = new ArrayList<>();
        List<CommandSender> senders = new ArrayList<>();
        CommandDispatcher dispatcher = new CommandDispatcher(
                MockBukkit.createMockPlugin(),
                null, null, (sender, cmd) -> { senders.add(sender); executed.add(cmd); });

        dispatcher.dispatch(player, shop, slot, 1, 1, BigDecimal.ONE);

        assertEquals(List.of("say hi " + player.getName(), "say bye " + player.getName()), executed);
        assertTrue(senders.stream().allMatch(s -> s == Bukkit.getConsoleSender()));
    }

    @Test
    void dispatchIsNoopWhenSlotHasNoCommand() {
        Player player = MockBukkit.getMock().addPlayer("Cara");
        Shop shop = shop(true);
        ShopSlot slot = slot(shop.id(), null);

        List<String> executed = new ArrayList<>();
        CommandDispatcher dispatcher = new CommandDispatcher(
                MockBukkit.createMockPlugin(),
                null, null, (sender, cmd) -> executed.add(cmd));

        dispatcher.dispatch(player, shop, slot, 1, 1, BigDecimal.ONE);

        assertFalse(slot.hasCommand());
        assertTrue(executed.isEmpty());
    }

    private static Shop shop(boolean admin) {
        return new Shop(UUID.randomUUID(), admin ? ShopType.ADMIN : ShopType.PLAYER,
                admin ? null : UUID.randomUUID(),
                new ShopLocation(UUID.randomUUID(), 0, 64, 0, 0f, 0f),
                null, null, "Test", false, 1,
                Instant.now(), Instant.now());
    }

    private static ShopSlot slot(UUID shopId, String command) {
        ItemStack item = new ItemStack(Material.DIAMOND);
        return new ShopSlot(UUID.randomUUID(), shopId, 0, TradeSide.SELL, item,
                new BigDecimal("10"), null, 1, 0, null,
                LimitScope.PER_PLAYER, null, command);
    }
}
