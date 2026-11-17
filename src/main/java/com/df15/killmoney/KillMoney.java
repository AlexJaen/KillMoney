package com.df15.killmoney;

import java.util.logging.Level;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * KillMoney (sesion 2026-08-17, plugin propio a peticion del usuario:
 * "Con respecto al plugin de robo de % de dinero, vamos a hacerlo
 * nosotros. Quiero que se llame KillMoney") -- roba un porcentaje
 * configurable del dinero TOTAL de un jugador a quien lo mata en PVP, via
 * la API de Vault (funciona con cualquier plugin de economia compatible,
 * en este server EssentialsX -- ver server/plugins/Vault.jar, mismo
 * patron de RegisteredServiceProvider&lt;Economy&gt; que ya usa
 * RareItemHunter).
 *
 * Antes de escribir esto se investigaron los plugins clasicos para este
 * mismo proposito (KillerMoney, MoneyThief, PlayerKillMoney, PvPReward):
 * todos llevan entre 5 y 10 años sin actualizarse y ninguno confirma
 * soporte para 1.21, asi que se descarto instalarlos y se opto por un
 * plugin propio pequeño, siguiendo el mismo patron que LobbyBridge/
 * ServerRules de este proyecto (un solo fichero, sin dependencias raras).
 *
 * Solo se activa con un kill DIRECTO: Player#getKiller() (usado dentro de
 * PlayerDeathEvent) solo devuelve un jugador si el ULTIMO golpe antes de
 * morir vino de otro jugador -- es comportamiento nativo de Bukkit, no
 * configurable aqui. Muertes indirectas (caida al huir, ahogarse,
 * lava...) NUNCA activan el robo, aunque hayan sido provocadas por otro
 * jugador.
 *
 * Sesion 2026-08-17 (a peticion del usuario, patio de practica "The
 * Yard" sin perdidas de ningun tipo): integracion opcional con
 * WorldGuard via una flag custom "killmoney" -- ver WorldGuardHook.java
 * para el detalle completo. softdepend, no depend: si WorldGuard no esta
 * instalado, el robo simplemente funciona en todo el mapa como hasta
 * ahora (fail-open).
 */
public class KillMoney extends JavaPlugin implements Listener
{
    private Economy economy;

    private double percentage;
    private double minimumVictimBalance;
    private double maximumStealAmount;
    private boolean broadcast;
    private boolean blockSameIp;

    private String prefix;
    private String killerMessage;
    private String victimMessage;
    private String broadcastMessage;
    private String victimTooPoorMessage;

    @Override
    public void onLoad()
    {
        // Registrar la flag custom de WorldGuard tiene que pasar aqui,
        // en onLoad(), NO en onEnable() -- ver el comentario largo en
        // WorldGuardHook.registerFlag() para el motivo exacto.
        if(getServer().getPluginManager().getPlugin("WorldGuard") != null)
        {
            WorldGuardHook.registerFlag();
        }
    }

    @Override
    public void onEnable()
    {
        saveDefaultConfig();

        if(!setupEconomy())
        {
            getLogger().log(Level.SEVERE, "No se encontro Vault (o ningun plugin de economia registrado en el, p.ej. EssentialsX) -- KillMoney no puede funcionar sin economia real detras. Desactivando.");

            getPluginLoader().disablePlugin(this);

            return;
        }

        loadSettings();

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("KillMoney habilitado (" + this.percentage + "% del dinero de la victima por kill directo).");
    }

    private boolean setupEconomy()
    {
        if(getServer().getPluginManager().getPlugin("Vault") == null)
        {
            return false;
        }

        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);

        if(economyProvider == null)
        {
            return false;
        }

        this.economy = economyProvider.getProvider();

        return this.economy != null;
    }

    private void loadSettings()
    {
        reloadConfig();

        FileConfiguration cfg = getConfig();

        this.percentage = cfg.getDouble("settings.percentage", 10.0);
        this.minimumVictimBalance = cfg.getDouble("settings.minimum-victim-balance", 0.0);
        this.maximumStealAmount = cfg.getDouble("settings.maximum-steal-amount", -1.0);
        this.broadcast = cfg.getBoolean("settings.broadcast", false);
        this.blockSameIp = cfg.getBoolean("settings.block-same-ip", true);

        this.prefix = colorize(cfg.getString("messages.prefix", ""));
        this.killerMessage = colorize(cfg.getString("messages.killer-message", ""));
        this.victimMessage = colorize(cfg.getString("messages.victim-message", ""));
        this.broadcastMessage = colorize(cfg.getString("messages.broadcast-message", ""));
        this.victimTooPoorMessage = colorize(cfg.getString("messages.victim-too-poor", ""));
    }

    /**
     * true si ambos jugadores tienen la misma IP real. Si la IP es de loopback
     * (el proxy no reenvia la IP del cliente) no se puede saber: se devuelve false
     * para no desactivar el robo para todo el mundo.
     */
    private boolean sameIp(Player a, Player b)
    {
        java.net.InetSocketAddress sa = a.getAddress();
        java.net.InetSocketAddress sb = b.getAddress();

        if(sa == null || sb == null || sa.getAddress() == null || sb.getAddress() == null)
        {
            return false;
        }

        if(sa.getAddress().isLoopbackAddress() || sb.getAddress().isLoopbackAddress())
        {
            return false;
        }

        return sa.getAddress().getHostAddress().equals(sb.getAddress().getHostAddress());
    }

    private String colorize(String s)
    {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event)
    {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if(killer == null || killer.getUniqueId().equals(victim.getUniqueId()))
        {
            return;
        }

        // Sesion 2026-11-08 (a peticion del usuario: "misma IP = sin robo"): si asesino y
        // victima se conectan desde la misma IP no se roba nada (evita pasarse dinero
        // entre cuentas propias matandose). Configurable: settings.block-same-ip.
        if(this.blockSameIp && sameIp(killer, victim))
        {
            return;
        }

        // Permiso pensado para staff/pruebas -- si la VICTIMA lo tiene,
        // no se le puede robar nada al matarla (evita que un admin
        // pierda dinero de verdad haciendo pruebas de PVP, o que se use
        // para "granjear" dinero matando repetidamente a un staff).
        if(victim.hasPermission("killmoney.bypass"))
        {
            return;
        }

        // Zona sin robo (p.ej. "The Yard"): "/rg flag <region> killmoney
        // deny". Fail-open si WorldGuard no esta instalado -- ver
        // WorldGuardHook.
        if(!WorldGuardHook.isMoneyTheftAllowed(victim.getLocation()))
        {
            return;
        }

        double victimBalance = this.economy.getBalance(victim);

        if(victimBalance < this.minimumVictimBalance)
        {
            if(!this.victimTooPoorMessage.isEmpty())
            {
                killer.sendMessage(this.prefix + this.victimTooPoorMessage.replace("%victim%", victim.getName()));
            }

            return;
        }

        double amount = victimBalance * (this.percentage / 100.0);

        if(this.maximumStealAmount >= 0 && amount > this.maximumStealAmount)
        {
            amount = this.maximumStealAmount;
        }

        if(amount <= 0)
        {
            return;
        }

        EconomyResponse withdrawResponse = this.economy.withdrawPlayer(victim, amount);

        if(!withdrawResponse.transactionSuccess())
        {
            getLogger().warning("No se pudo retirar $" + amount + " de " + victim.getName() + " para el robo de KillMoney: " + withdrawResponse.errorMessage);

            return;
        }

        this.economy.depositPlayer(killer, amount);

        String amountFormatted = this.economy.format(amount);

        killer.sendMessage(this.prefix + this.killerMessage
            .replace("%amount%", amountFormatted)
            .replace("%victim%", victim.getName())
            .replace("%balance%", this.economy.format(this.economy.getBalance(killer))));

        victim.sendMessage(this.prefix + this.victimMessage
            .replace("%amount%", amountFormatted)
            .replace("%killer%", killer.getName())
            .replace("%balance%", this.economy.format(this.economy.getBalance(victim))));

        if(this.broadcast && !this.broadcastMessage.isEmpty())
        {
            Bukkit.broadcastMessage(this.prefix + this.broadcastMessage
                .replace("%amount%", amountFormatted)
                .replace("%killer%", killer.getName())
                .replace("%victim%", victim.getName()));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if(!command.getName().equalsIgnoreCase("killmoney"))
        {
            return false;
        }

        if(args.length == 1 && args[0].equalsIgnoreCase("reload"))
        {
            if(!sender.hasPermission("killmoney.admin"))
            {
                sender.sendMessage(this.prefix + ChatColor.RED + "No tienes permiso para usar este comando.");

                return true;
            }

            loadSettings();

            sender.sendMessage(this.prefix + ChatColor.GREEN + "Configuracion recargada.");

            return true;
        }

        sender.sendMessage(ChatColor.RED + "Uso: /killmoney reload");

        return true;
    }
}
