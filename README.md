# Drive By Wire Port

This project is a port of Drive By Wire for the current Create/Sable-based environment.

Original Drive By Wire project pages:

- [Modrinth](https://modrinth.com/mod/drive-by-wire)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/drive-by-wire)

## Wire limits

Open Create's config editor, select **Other Mods → Drive By Wire → Common
Config**, and edit the limits. **Mods → Drive By Wire → Config** opens the same
editor. Settings are saved in `config/drivebywire-common.toml`.

These are common settings: editing them on a multiplayer client changes that
client's file, not the dedicated server's limits. Configure the file on the
server to change multiplayer limits.

Available limits:

- `maxSourcesPerStructure`: source blocks per Sable sublevel (default: 64).
- `maxSourcesInWorld`: source blocks outside Sable sublevels, counted separately
  for each dimension (default: 4096).
- `maxSinksPerSource`: sink connections per source **channel** (default: 64).

Values must be nonnegative integers; 0 prevents new sources or connections in
that category. Existing connections are preserved when limits are lowered.
Sources with existing connections can still add connections up to their sink
limit even when the source limit has been reached.
