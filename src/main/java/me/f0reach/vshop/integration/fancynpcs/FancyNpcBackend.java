package me.f0reach.vshop.integration.fancynpcs;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.skins.SkinData;
import de.oliver.fancynpcs.api.skins.SkinLoadException;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopAppearance;
import me.f0reach.vshop.shop.entity.ShopAppearanceRegistry;
import me.f0reach.vshop.shop.entity.ShopDisplayName;
import me.f0reach.vshop.shop.entity.ShopEntityBackend;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Backs a shop with a FancyNpcs NPC.
 *
 * <p>NPCs are packet-based, so unlike {@link me.f0reach.vshop.shop.entity.VillagerBackend}
 * there is no Bukkit entity: {@link #spawn} returns null, chunk loading is
 * irrelevant, and nothing here is visible to {@code World#getEntities}.
 *
 * <p>Our database is authoritative and NPCs are pure derived state — they are
 * created with {@code saveToFile(false)} so FancyNpcs never writes them to its
 * own {@code npcs.yml}, and rebuilt from {@code shop_appearance} on every boot.
 * That keeps a shop's cosmetics inside {@code /vshop migrate} and means a
 * crashed server cannot leave orphaned NPCs behind.
 *
 * <p>Every FancyNpcs type referenced here is confined to this package, which is
 * only loaded once the plugin has been confirmed present.
 */
public final class FancyNpcBackend implements ShopEntityBackend {

    /** Prefix identifying our NPCs inside FancyNpcs' global name space. */
    static final String NAME_PREFIX = "vshop-";

    /** Stand-in creator for admin shops, which have no owner. */
    private static final UUID NO_CREATOR = new UUID(0L, 0L);

    private final Plugin plugin;
    private final ShopDisplayName displayName;
    private final ShopAppearanceRegistry appearances;
    private final PluginConfig config;

    public FancyNpcBackend(Plugin plugin, ShopDisplayName displayName,
                           ShopAppearanceRegistry appearances, PluginConfig config) {
        this.plugin = plugin;
        this.displayName = displayName;
        this.appearances = appearances;
        this.config = config;
    }

    static String npcName(UUID shopId) {
        return NAME_PREFIX + shopId;
    }

    /** Inverse of {@link #npcName}; null when the NPC is not one of ours. */
    static UUID shopIdOf(String npcName) {
        if (npcName == null || !npcName.startsWith(NAME_PREFIX)) return null;
        try {
            return UUID.fromString(npcName.substring(NAME_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public UUID spawn(Shop shop, Location at) {
        remove(shop); // idempotent: never leave a stale NPC under the same name

        UUID creator = shop.ownerUuid() == null ? NO_CREATOR : shop.ownerUuid();
        NpcData data = new NpcData(npcName(shop.id()), creator, at);
        apply(data, shop);

        Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
        npc.setSaveToFile(false);
        FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
        npc.create();
        npc.spawnForAll();
        // Packet entity — there is no Bukkit entity id to persist on the shop.
        return null;
    }

    @Override
    public void refresh(Shop shop) {
        Npc npc = find(shop.id());
        if (npc == null) return;
        apply(npc.getData(), shop);
        // Skin, entity type and equipment are baked into the spawn packets, so a
        // plain update() would not show them. Re-send the whole NPC instead.
        npc.removeForAll();
        npc.create();
        npc.spawnForAll();
    }

    @Override
    public void refreshDisplayName(Shop shop) {
        Npc npc = find(shop.id());
        if (npc == null) return;
        npc.getData().setDisplayName(displayName.miniMessage(shop));
        npc.updateForAll();
    }

    @Override
    public void remove(Shop shop) {
        removeById(shop.id());
    }

    void removeById(UUID shopId) {
        Npc npc = find(shopId);
        if (npc == null) return;
        FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
        npc.removeForAll();
    }

    Npc find(UUID shopId) {
        return FancyNpcsPlugin.get().getNpcManager().getNpc(npcName(shopId));
    }

    private void apply(NpcData data, Shop shop) {
        ShopAppearance a = appearances.getOrDefault(shop.id());

        data.setDisplayName(displayName.miniMessage(shop));
        data.setType(a.entityType() == null ? EntityType.PLAYER : a.entityType());
        data.setCollidable(false);
        data.setShowInTab(false);
        data.setTurnToPlayer(a.turnToPlayer() == null
                ? config.fancyNpcs().turnToPlayer()
                : a.turnToPlayer());
        data.setInteractionCooldown(config.fancyNpcs().interactionCooldown());
        data.setGlowing(a.glowing());
        if (a.glowColor() != null) data.setGlowingColor(a.glowColor());
        if (a.scale() != null) data.setScale(a.scale());

        applySkin(data, a, shop);
        applyEquipment(data, a, shop);
        applyAttributes(data, a, shop);
    }

    /**
     * Skins only exist for PLAYER NPCs. Resolution can hit the network on a cold
     * cache, so callers must not reach this from the main thread with an unseen
     * skin — see {@code FancyNpcsIntegration}. A resolved SkinData may still
     * report {@code hasTexture() == false}; FancyNpcs fills the texture in later
     * from its own async queue.
     */
    private void applySkin(NpcData data, ShopAppearance a, Shop shop) {
        if (a.skin() == null || data.getType() != EntityType.PLAYER) {
            data.setSkinData(null);
            return;
        }
        SkinData.SkinVariant variant = a.skinVariant() == me.f0reach.vshop.model.SkinVariant.SLIM
                ? SkinData.SkinVariant.SLIM
                : SkinData.SkinVariant.AUTO;
        try {
            data.setSkin(a.skin(), variant);
        } catch (SkinLoadException ex) {
            plugin.getLogger().warning("Shop " + shop.id() + ": could not load skin '"
                    + a.skin() + "' (" + ex.getReason() + "); rendering without one");
            data.setSkinData(null);
        }
    }

    private void applyEquipment(NpcData data, ShopAppearance a, Shop shop) {
        data.setEquipment(new java.util.HashMap<>());
        for (Map.Entry<String, ItemStack> entry : a.equipment().entrySet()) {
            NpcEquipmentSlot slot = NpcEquipmentSlot.parse(entry.getKey());
            if (slot == null) {
                plugin.getLogger().warning("Shop " + shop.id() + ": unknown equipment slot '"
                        + entry.getKey() + "'; skipped");
                continue;
            }
            data.addEquipment(slot, entry.getValue());
        }
    }

    private void applyAttributes(NpcData data, ShopAppearance a, Shop shop) {
        if (a.attributes().isEmpty()) return;
        var manager = FancyNpcsPlugin.get().getAttributeManager();
        for (Map.Entry<String, String> entry : a.attributes().entrySet()) {
            NpcAttribute attribute = manager.getAttributeByName(data.getType(), entry.getKey());
            if (attribute == null || !attribute.isValidValue(entry.getValue())) {
                plugin.getLogger().log(Level.WARNING, "Shop {0}: attribute {1}={2} is not valid for {3}; skipped",
                        new Object[]{shop.id(), entry.getKey(), entry.getValue(), data.getType()});
                continue;
            }
            data.addAttribute(attribute, entry.getValue());
        }
    }
}
