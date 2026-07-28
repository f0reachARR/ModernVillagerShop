package me.f0reach.vshop.ui.text;

import me.f0reach.vshop.testsupport.BukkitTestSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DisplaysTest {

    @BeforeAll
    static void boot() {
        BukkitTestSupport.ensureBukkit();
    }

    @Test
    void rendersPlainItemAsTranslatableName() {
        ItemStack stack = BukkitTestSupport.item(Material.DIAMOND);

        Component rendered = Displays.itemName(stack);

        TranslatableComponent translatable = assertInstanceOf(TranslatableComponent.class, rendered);
        assertEquals("item.minecraft.diamond", translatable.key());
        // Clients that don't know the key (and plain-text consumers such as the
        // Bedrock bridge) fall back to the material name.
        assertEquals(Material.DIAMOND.name(), translatable.fallback());
    }

    @Test
    void keepsCustomDisplayNameWhenPresent() {
        ItemStack stack = BukkitTestSupport.item(Material.DIAMOND);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Shiny"));
        stack.setItemMeta(meta);

        Component rendered = Displays.itemName(stack);

        assertEquals("Shiny", PlainTextComponentSerializer.plainText().serialize(rendered));
    }

    @Test
    void nullStackRendersPlaceholder() {
        assertEquals("?", PlainTextComponentSerializer.plainText()
                .serialize(Displays.itemName(null)));
        assertNotNull(Displays.item(null));
    }
}
