# BlockDisplay (Paper 1.21.5)

Spawn a glowing block-display on any block you look at, choose the glow color, and have it vanish the moment that block breaks.

---

## Quick Features
- `/bd color <ChatColor>` – live glow-color swap
- Configurable spawn offsets (`x y z`)
- Tab-complete for sub-commands & colors
- Auto-remove when support block is destroyed
- Cleans up on reload/disable, reuses one scoreboard team

---

## Commands & Permissions
| Command                   | Perm     | Note                           |
|---------------------------|----------|--------------------------------|
| `/bd spawn`               | `bd.use` | Place display on looked-at block |
| `/bd color <color>`       | `bd.admin` | Change glow outline            |
| `/bd reload`              | `bd.admin` | Reload `config.yml`            |

Press **TAB** after `/bd` or `/bd color` for suggestions.

---

## Install
1. Build: `./gradlew shadowJar`
2. Copy jar to `plugins/`
3. Start / reload server → `plugins/BlockDisplay/config.yml` appears.

---

## Default `config.yml`
```yaml
default-color: GREEN
offset:
  x: 0
  y: 0
  z: 0
