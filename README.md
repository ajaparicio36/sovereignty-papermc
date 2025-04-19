# 🏰 Sovereignty

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21-purple)
![API](https://img.shields.io/badge/API-PaperMC-blue)
![Latest Release](https://img.shields.io/github/v/release/ajaparicio36/sovereignty-papermc?include_prereleases&color=green)
![License](https://img.shields.io/badge/License-LGPL%20v3-yellow)

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

| Command                                       | Description                                      | Permission       |
| --------------------------------------------- | ------------------------------------------------ | ---------------- |
| `/nation create <name>`                       | Create a new nation                              | Default          |
| `/nation disband [confirm]`                   | Disband your nation                              | Nation president |
| `/nation info [nation]`                       | View information about a nation                  | Default          |
| `/nation claim [toggle]`                      | Claim the current chunk or toggle auto-claiming  | Nation officers  |
| `/nation unclaim [toggle]`                    | Unclaim the current chunk or toggle auto-unclaim | Nation officers  |
| `/nation invite <player>`                     | Invite a player to your nation                   | Nation officers  |
| `/nation join <nation>`                       | Join a nation you've been invited to             | Default          |
| `/nation leave`                               | Leave your current nation                        | Default          |
| `/nation appoint <player> <senator\|soldier>` | Appoint a player to a role in your nation        | Nation officers  |
| `/nation alliance list`                       | View current alliances and pending requests      | Default          |
| `/nation alliance propose <nation>`           | Propose an alliance to another nation            | Nation officers  |
| `/nation alliance accept <nation>`            | Accept an alliance proposal                      | Nation officers  |
| `/nation alliance deny <nation>`              | Deny an alliance proposal                        | Nation officers  |
| `/nation alliance break <nation>`             | Break an existing alliance                       | Nation officers  |
| `/nation vaultnpc [remove]`                   | Create, move or remove a Vault NPC               | Nation officers  |
| `/nation trade create <nation> [interval]`    | Create a trade agreement with another nation     | Nation officers  |
| `/nation trade list`                          | View your nation's trade agreements              | Nation officers  |
| `/nation trade delete`                        | Delete a trade agreement                         | Nation officers  |
| `/nation trade npc create`                    | Create a trade NPC                               | Nation officers  |
| `/nation trade npc delete`                    | Delete a trade NPC                               | Nation officers  |

### War Commands

| Command                                | Description                                | Permission                |
| -------------------------------------- | ------------------------------------------ | ------------------------- |
| `/war declare <nation>`                | Declare war on another nation              | Nation president          |
| `/war list [all]`                      | List your nation's wars or all active wars | Default / Admin for "all" |
| `/war info <nation> [nation2]`         | View information about wars                | Default                   |
| `/war cancel <warId/nation> [nation2]` | Cancel an ongoing war                      | `sovereignty.admin.wars`  |

### Vault Command

| Command         | Description              | Permission |
| --------------- | ------------------------ | ---------- |
| `/vault [page]` | Open your nation's vault | Default    |

### Admin Commands

| Command                                  | Description                | Permission                   |
| ---------------------------------------- | -------------------------- | ---------------------------- |
| `/nationadmin setpower <nation> <power>` | Set a nation's power level | `sovereignty.admin.setpower` |

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
