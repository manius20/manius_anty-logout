package pl.sesion16.antylogout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    private final Set<String> blockedCommands = new HashSet<>(Arrays.asList(
            "/spawn", "/tp", "/tpa", "/tpaccept", "/home", "/sethome", "/warp", "/hub", "/lobby"
    ));
    private BukkitTask actionbarTask;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        startActionbarTask();
        getLogger().info("Plugin AntyLogout został pomyślnie załadowany!");
    }

    @Override
    public void onDisable() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
        }
        combatMap.clear();
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

            Bukkit.broadcast(Component.text("Gracz ", NamedTextColor.RED)
                    .append(Component.text(player.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" wylogował się podczas walki i zginął!", NamedTextColor.RED)));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (combatMap.containsKey(player.getUniqueId())) {
            combatMap.remove(player.getUniqueId());
            player.sendActionBar(Component.text("Pojedynek zakończony.", NamedTextColor.GREEN));
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
            player.sendMessage(Component.text("Nie możesz używać komendy " + command + " podczas walki!", NamedTextColor.RED));
        }
    }

    private void tagPlayer(Player player) {
        boolean wasInCombat = isInCombat(player);
        combatMap.put(player.getUniqueId(), System.currentTimeMillis() + 25000L);

        if (!wasInCombat) {
            player.sendMessage(Component.text("Jesteś w trakcie walki! Nie wylogowuj się przez 25 sekund.", NamedTextColor.RED, TextDecoration.BOLD));
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
                        player.sendActionBar(Component.text("Możesz się bezpiecznie wylogować.", NamedTextColor.GREEN));
                        player.sendMessage(Component.text("Koniec walki. Możesz ponownie używać komend.", NamedTextColor.GREEN));
                    } else {
                        int secondsLeft = (int) Math.ceil(remainingMs / 1000.0);
                        player.sendActionBar(Component.text("Jesteś w walce jeszcze przez: ", NamedTextColor.RED)
                                .append(Component.text(secondsLeft + "s", NamedTextColor.YELLOW, TextDecoration.BOLD)));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }
}
