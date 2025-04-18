# 🏰 Sovereignty

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20-green)
![API](https://img.shields.io/badge/API-PaperMC-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

> Create, manage, and expand your nation in a world of diplomacy, war, and trade.

## 📖 Description

Sovereignty is a comprehensive nation management plugin for Minecraft servers that transforms your gameplay experience with geopolitical elements. Players can form nations, claim territory, engage in diplomacy, wage war, and establish trade relations.

### Key Features

- **Nation Creation**: Establish your own sovereignty with a unique identity
- **Land Management**: Claim and protect chunks from outsiders
- **Governance System**: Hierarchical roles with President, Senators, and Soldiers
- **Warfare**: Declare wars against rival nations and conquer their territories
- **Diplomacy**: Form alliances with other nations for mutual benefits
- **Economy**: Engage in trade using in-game items through a dedicated vault system
- **Power Scaling**: Nations evolve through a 6-level power classification system

## 🔧 Commands

### Nation Commands

| Command                    | Description                                | Permission       |
| -------------------------- | ------------------------------------------ | ---------------- |
| `/nation create <name>`    | Create a new nation                        | Default          |
| `/nation claim`            | Claim the current chunk for your nation    | Default          |
| `/nation unclaim`          | Remove nation claim from the current chunk | Default          |
| `/nation info [nation]`    | View information about a nation            | Default          |
| `/nation join <nation>`    | Request to join a nation                   | Default          |
| `/nation leave`            | Leave your current nation                  | Default          |
| `/nation invite <player>`  | Invite a player to your nation             | Nation officers  |
| `/nation kick <player>`    | Remove a player from your nation           | Nation officers  |
| `/nation promote <player>` | Promote a nation member                    | Nation president |
| `/nation demote <player>`  | Demote a nation officer                    | Nation president |
| `/nation disband`          | Disband your nation                        | Nation president |
| `/nation ally <nation>`    | Request or accept an alliance              | Nation officers  |
| `/nation unally <nation>`  | End an alliance                            | Nation officers  |

### War Commands

| Command                 | Description                   | Permission               |
| ----------------------- | ----------------------------- | ------------------------ |
| `/war declare <nation>` | Declare war on another nation | Nation officers          |
| `/war info [war-id]`    | View information about a war  | Default                  |
| `/war surrender`        | Surrender in your current war | Nation officers          |
| `/war list`             | List all active wars          | Default                  |
| `/war cancel <war-id>`  | Cancel a war (admin only)     | `sovereignty.admin.wars` |

### Vault Commands

| Command  | Description              | Permission |
| -------- | ------------------------ | ---------- |
| `/vault` | Open your nation's vault | Default    |

### Admin Permissions

| Permission                 | Description                       |
| -------------------------- | --------------------------------- |
| `sovereignty.admin.bypass` | Bypass all protection             |
| `sovereignty.admin.chunks` | Claim chunks beyond normal limits |
| `sovereignty.admin.wars`   | Cancel ongoing wars               |

## ⚙️ Configuration

The plugin is highly configurable through the `config.yml` file. Here are the key configuration options:

```yaml
# Database configuration
database:
  # Type: mysql or sqlite
  type: sqlite
  mysql:
    host: localhost
    port: 3306
    database: sovereignty
    username: root
    password: password
    use-ssl: false

# Language setting (defaults to en_US)
language: en_US

# Nation settings
nations:
  # Maximum chunks claimable per power level
  max-claims:
    level-1: 20
    level-2: 40
    level-3: 80
    level-4: 160
    level-5: 320
    level-6: 640

  # Minimum members required for power levels
  min-members:
    level-1: 1
    level-2: 5
    level-3: 10
    level-4: 20
    level-5: 35
    level-6: 50

# War settings
war:
  # Whether blocks can be destroyed in enemy territory during war
  allow-destruction: false

  # Whether killing a president results in instant victory
  assassination-mode: false

  # Cooldown between wars (in hours)
  cooldown: 24

  # Duration after which wars automatically end in a draw (in hours)
  max-duration: 72
```

## 🔄 Power System

Nations progress through six power levels that determine their capabilities and territorial limits:

1. **Tribal Civilization** - Small, isolated communities with minimal trade or alliances
2. **Developing Nation** - Basic trade and alliances forming
3. **Emerging Power** - Moderate population with occasional trade and few alliances
4. **Urbanized Nation** - Large population, frequent trades, several alliances
5. **Regional Power** - Dominates land, trades well, multiple allies
6. **Global Superpower** - Massive playerbase with strong global influence

Power grows through:

- Nation population growth
- Forming alliances
- Successful trade relations
- War victories

## 🗄️ MySQL Setup

While Sovereignty works with SQLite by default, for larger servers MySQL is recommended:

1. **Create a MySQL Database**:

   ```sql
   CREATE DATABASE sovereignty;
   CREATE USER 'sovereignty'@'localhost' IDENTIFIED BY 'yourpassword';
   GRANT ALL PRIVILEGES ON sovereignty.* TO 'sovereignty'@'localhost';
   FLUSH PRIVILEGES;
   ```

2. **Configure the Plugin**:
   Update your `config.yml`:

   ```yaml
   database:
     type: mysql
     mysql:
       host: localhost
       port: 3306
       database: sovereignty
       username: sovereignty
       password: yourpassword
       use-ssl: false
   ```

3. **Restart Your Server**:
   The plugin will automatically create the necessary tables.

## 🤝 Support

For issues, suggestions, or contributions, please visit our GitHub repository or contact the developer.

---

_Made with ❤️ by Tatayless_
