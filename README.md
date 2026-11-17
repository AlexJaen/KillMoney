# KillMoney

Plugin de Paper (1.21.x / 26.x) que, al matar a un jugador en PvP, le roba al asesino un **porcentaje del dinero total** de la víctima mediante Vault.

## Características
- Porcentaje configurable, saldo mínimo de la víctima y tope máximo por kill.
- Bloqueo si asesino y víctima comparten IP (`block-same-ip`).
- Flag de WorldGuard `killmoney` para desactivar el robo por región: `/rg flag <región> killmoney deny`.
- Mensajes y broadcast configurables.

## Requisitos
- Java 21 y Paper 1.21+ (probado en 1.21.11 y 26.2).
- **Vault** + un plugin de economía (p. ej. EssentialsX). Obligatorio.
- WorldGuard y Essentials: opcionales.

## Compilar
```
mvn clean package
```
El jar queda en `target/killmoney-1.0.0.jar`; cópialo a `plugins/`.

## Comandos y permisos
| | |
|---|---|
| `/killmoney reload` (`/km`) | recarga la config — `killmoney.admin` (op) |
| `killmoney.bypass` | si la **víctima** lo tiene, no se le puede robar |

## Configuración
Ver `src/main/resources/config.yml` (porcentaje, mínimo, tope, mensajes).
