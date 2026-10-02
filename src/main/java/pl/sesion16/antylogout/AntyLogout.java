package pl.sesion16.antylogout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class AntyLogout extends JavaPlugin implements Listener {

    private final Map<UUID, Long> combatMap = new HashMap<>();
    private final Set<String> blockedCommands = new HashSet<>();
    private int combatTimeSeconds;
    private BukkitTask actionbarTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();

        getServer().getPluginManager().registerEvents(this, this);
        startActionbarTask();
        getLogger().info("Plugin AntyLogout został pomyślnie załadowany z obsługą config.yml!");
    }

    @Override
    public void onDisable() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
        }
        combatMap.clear();
    }

    private void loadConfiguration() {
        reloadConfig();
        combatTimeSeconds = getConfig().getInt("combat-time", 25);
        blockedCommands.clear();
        for (String cmd : getConfig().getStringList("blocked-commands")) {
            blockedCommands.add(cmd.toLowerCase());
        }
    }

    private Component colorMessage(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;

        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
            tagPlayer(victim);
            tagPlayer(attacker);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isInCombat(player)) {
            player.setHealth(0.0);
            combatMap.remove(player.getUniqueId());

            String msg = getConfig().getString("messages.quit-broadcast", "&cGracz &e&l{PLAYER} &cwylogował się podczas walki i zginął!")
                    .replace("{PLAYER}", player.getName());
            Bukkit.broadcast(colorMessage(msg));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (combatMap.containsKey(player.getUniqueId())) {
            combatMap.remove(player.getUniqueId());
            String msg = getConfig().getString("messages.combat-end-actionbar", "&aMożesz się bezpiecznie wylogować.");
            player.sendActionBar(colorMessage(msg));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isInCombat(player)) return;

        String message = event.getMessage().toLowerCase();
        String command = message.split(" ")[0];

        if (blockedCommands.contains(command)) {
            event.setCancelled(true);
            String msg = getConfig().getString("messages.command-blocked", "&cNie możesz używać komendy {COMMAND} podczas walki!")
                    .replace("{COMMAND}", command);
            player.sendMessage(colorMessage(msg));
        }
    }

    private void tagPlayer(Player player) {
        boolean wasInCombat = isInCombat(player);
        combatMap.put(player.getUniqueId(), System.currentTimeMillis() + (combatTimeSeconds * 1000L));

        if (!wasInCombat) {
            String msg = getConfig().getString("messages.combat-start", "&cJesteś w trakcie walki! Nie wylogowuj się przez {TIME} sekund.")
                    .replace("{TIME}", String.valueOf(combatTimeSeconds));
            player.sendMessage(colorMessage(msg));
        }
    }

    private boolean isInCombat(Player player) {
        if (!combatMap.containsKey(player.getUniqueId())) return false;
        long expireTime = combatMap.get(player.getUniqueId());
        return System.currentTimeMillis() < expireTime;
    }

    private void startActionbarTask() {
        actionbarTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Long>> iterator = combatMap.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = iterator.next();
                    Player player = Bukkit.getPlayer(entry.getKey());

                    if (player == null || !player.isOnline()) {
                        iterator.remove();
                        continue;
                    }

                    long remainingMs = entry.getValue() - now;

                    if (remainingMs <= 0) {
                        iterator.remove();
                        String endActionbar = getConfig().getString("messages.combat-end-actionbar", "&aMożesz się bezpiecznie wylogować.");
                        String endChat = getConfig().getString("messages.combat-end-chat", "&aKoniec walki. Możesz ponownie używać komend.");
                        
                        player.sendActionBar(colorMessage(endActionbar));
                        player.sendMessage(colorMessage(endChat));
                    } else {
                        int secondsLeft = (int) Math.ceil(remainingMs / 1000.0);
                        String timerMsg = getConfig().getString("messages.actionbar-timer", "&cJesteś w walce jeszcze przez: &e&l{TIME}s")
                                .replace("{TIME}", String.valueOf(secondsLeft));
                        
                        player.sendActionBar(colorMessage(timerMsg));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }
}
