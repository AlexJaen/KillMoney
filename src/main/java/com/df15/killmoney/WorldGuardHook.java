package com.df15.killmoney;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/**
 * Sesion 2026-08-17 (a peticion del usuario, a raiz de "The Yard" -- un
 * patio de practica PVP sin perder items/XP/durabilidad/dinero: "killmoney
 * es compatible con esto de alguna forma? Si no, hay que hacer algo para
 * que lo sea").
 *
 * WorldGuard/WorldGuardExtraFlags (ya instalados, ver
 * server/plugins/WorldGuardExtraFlags.jar) no saben nada de Vault ni de
 * dinero -- ninguna flag existente cubre "no robar dinero al morir aqui".
 * Este fichero registra una flag NUEVA y propia, "killmoney" (state,
 * allow/deny, ALLOW por defecto en todo el mapa para no cambiar el
 * comportamiento actual en ningun sitio), para que el robo de KillMoney
 * se pueda desactivar por region igual que ya se hace con pvp/
 * keep-inventory/keep-exp/item-durability. Para "The Yard":
 *
 *   /rg flag TheYard killmoney deny
 *
 * Registro de flags custom en WorldGuard 7: SOLO funciona si se hace
 * dentro de JavaPlugin#onLoad() (ver KillMoney.onLoad()) -- Bukkit llama
 * a onLoad() de TODOS los plugins (en orden de dependencias) ANTES de
 * llamar a onEnable() de ninguno, y WorldGuard registra sus propias flags
 * dentro de su propio onLoad(). Con "softdepend: [WorldGuard]" en
 * plugin.yml, el onLoad() de KillMoney se garantiza que corre DESPUES del
 * de WorldGuard si esta instalado, dejando su FlagRegistry ya listo para
 * aceptar registros de otros plugins. Registrar la flag mas tarde (en
 * onEnable(), por ejemplo) simplemente falla en silencio o lanza errores,
 * segun la version.
 *
 * Todo lo demas es fail-open, mismo patron que
 * RareItemHunter/util/PvpZoneUtil.java y
 * RareItemHunter/util/FactionsZoneUtil.java (si WorldGuard no esta
 * instalado, se desinstala, o falla la consulta, se permite el robo por
 * defecto -- sin WorldGuard este servidor no tiene ningun concepto de
 * "region sin robo" que respetar).
 */
public class WorldGuardHook
{
    private static final String FLAG_NAME = "killmoney";

    private static StateFlag killMoneyFlag;

    /**
     * Llamar SOLO desde KillMoney#onLoad(), y solo si el plugin
     * "WorldGuard" esta presente en el servidor.
     */
    public static void registerFlag()
    {
        try
        {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

            StateFlag flag = new StateFlag(FLAG_NAME, true);

            registry.register(flag);

            killMoneyFlag = flag;

            Bukkit.getLogger().info("[KillMoney] Flag de WorldGuard 'killmoney' registrada (allow por defecto -- usa '/rg flag <region> killmoney deny' para desactivar el robo en una region concreta).");
        }
        catch(FlagConflictException e)
        {
            // Otro plugin ya registro una flag "killmoney" antes que
            // nosotros (muy improbable, pero mejor reusarla que reventar
            // el arranque de WorldGuard).
            var existing = WorldGuard.getInstance().getFlagRegistry().get(FLAG_NAME);

            if(existing instanceof StateFlag)
            {
                killMoneyFlag = (StateFlag) existing;
            }
            else
            {
                Bukkit.getLogger().log(Level.WARNING, "[KillMoney] Ya existe una flag 'killmoney' de otro plugin que no es del tipo esperado -- el robo de dinero no respetara regiones de WorldGuard.", e);
            }
        }
        catch(Throwable t)
        {
            Bukkit.getLogger().log(Level.WARNING, "[KillMoney] No se pudo registrar la flag 'killmoney' de WorldGuard -- el robo de dinero no respetara regiones.", t);
        }
    }

    /**
     * @return true si se permite robar dinero en esa ubicacion (por
     *         defecto en todo el mapa, o si WorldGuard no esta
     *         instalado/falla la consulta -- fail-open). false solo si
     *         una region que cubre esa ubicacion tiene la flag
     *         "killmoney" explicitamente puesta a "deny".
     */
    public static boolean isMoneyTheftAllowed(Location location)
    {
        try
        {
            if(killMoneyFlag == null || Bukkit.getPluginManager().getPlugin("WorldGuard") == null)
            {
                return true;
            }

            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();

            return query.testState(BukkitAdapter.adapt(location), (LocalPlayer) null, killMoneyFlag);
        }
        catch(Throwable t)
        {
            Bukkit.getLogger().log(Level.WARNING, "[KillMoney] Fallo comprobando la flag 'killmoney' de WorldGuard, se permite el robo por defecto.", t);

            return true;
        }
    }
}
