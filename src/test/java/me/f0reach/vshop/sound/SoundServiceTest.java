package me.f0reach.vshop.sound;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.sound.AudioExperience;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundServiceTest {

    private static final String LEVELUP_KEY = "minecraft:entity.player.levelup";
    private static final String VILLAGER_NO_KEY = "minecraft:entity.villager.no";

    @BeforeAll
    static void bootBukkit() {
        BukkitTestSupport.ensureStarted();
    }

    private static PluginConfig config(String yaml) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(yaml);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return new PluginConfig(y);
    }

    private static PlayerMock freshPlayer() {
        ServerMock server = MockBukkit.getMock();
        World world = server.addSimpleWorld("sound-test-" + System.nanoTime());
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0, 64, 0));
        return player;
    }

    @Test
    void playsConfiguredSoundToTheGivenPlayer() {
        SoundService sounds = new SoundService(config("""
                sounds:
                  ui:
                    open:
                      key: "%s"
                      volume: 0.5
                      pitch: 1.25
                      category: PLAYERS
                """.formatted(LEVELUP_KEY)), Logger.getLogger("test"));

        PlayerMock player = freshPlayer();
        sounds.play(player, SoundEvents.UI_OPEN);

        List<AudioExperience> heard = player.getHeardSounds();
        assertEquals(1, heard.size());
        AudioExperience only = heard.get(0);
        assertEquals(LEVELUP_KEY, only.getSound());
        assertEquals(SoundCategory.PLAYERS, only.getCategory());
        assertEquals(0.5f, only.getVolume());
        assertEquals(1.25f, only.getPitch());
    }

    @Test
    void unknownEventKeyIsSilentNoOp() {
        SoundService sounds = new SoundService(config("""
                sounds:
                  ui:
                    open:
                      key: "%s"
                """.formatted(LEVELUP_KEY)), Logger.getLogger("test"));

        PlayerMock player = freshPlayer();
        sounds.play(player, "not.configured");

        assertTrue(player.getHeardSounds().isEmpty());
    }

    @Test
    void emptyKeySkipsWithoutError() {
        SoundService sounds = new SoundService(config("""
                sounds:
                  ui:
                    open:
                      key: ""
                """), Logger.getLogger("test"));

        PlayerMock player = freshPlayer();
        sounds.play(player, SoundEvents.UI_OPEN);

        assertTrue(player.getHeardSounds().isEmpty());
    }

    @Test
    void malformedNamespacedKeyIsSkipped() {
        SoundService sounds = new SoundService(config("""
                sounds:
                  ui:
                    open:
                      key: "NOT A VALID KEY!!"
                """), Logger.getLogger("test"));

        PlayerMock player = freshPlayer();
        sounds.play(player, SoundEvents.UI_OPEN);

        assertTrue(player.getHeardSounds().isEmpty());
    }

    @Test
    void invalidCategoryFallsBackToMaster() {
        SoundService sounds = new SoundService(config("""
                sounds:
                  ui:
                    open:
                      key: "%s"
                      category: NOT_A_CATEGORY
                """.formatted(LEVELUP_KEY)), Logger.getLogger("test"));

        PlayerMock player = freshPlayer();
        sounds.play(player, SoundEvents.UI_OPEN);

        assertEquals(SoundCategory.MASTER, player.getHeardSounds().get(0).getCategory());
    }

    @Test
    void reloadPicksUpChangesInPlace() {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString("""
                    sounds:
                      ui:
                        open:
                          key: "%s"
                    """.formatted(LEVELUP_KEY));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        PluginConfig config = new PluginConfig(y);
        SoundService sounds = new SoundService(config, Logger.getLogger("test"));

        // Swap the config in place — mirrors what PluginConfig.reload does at runtime.
        YamlConfiguration y2 = new YamlConfiguration();
        try {
            y2.loadFromString("""
                    sounds:
                      ui:
                        open:
                          key: "%s"
                    """.formatted(VILLAGER_NO_KEY));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        config.reload(y2);
        sounds.reload();

        PlayerMock player = freshPlayer();
        sounds.play(player, SoundEvents.UI_OPEN);
        assertEquals(VILLAGER_NO_KEY, player.getHeardSounds().get(0).getSound());
    }
}
