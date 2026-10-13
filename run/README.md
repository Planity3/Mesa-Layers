# Mesa Layers (NeoForge 1.21.1)

Adds custom terracotta layers to badlands bands via datapack JSON.

## Build
Copy the Gradle wrapper from the NeoForge 1.21.1 MDK (or run `gradle wrapper`), then:

    ./gradlew build

The jar is in `build/libs/`. Adjust `neoForge.version` in `build.gradle` to match your pack.

## Defining layers
Put files in `data/<namespace>/mesa_layers/<name>.json` (any datapack, or the mod's own data folder).

Random mode (vanilla style):
    { "block": "minecraft:lime_terracotta", "count": 6, "min_thickness": 1, "max_thickness": 2 }

Fixed mode (exact Y range):
    { "block": "minecraft:cyan_terracotta", "y_min": 70, "y_max": 72 }

Pool mode (pick from a mod's blocks without listing IDs; one random block per band):
    { "pool": { "namespace": "somemod", "path_suffix": "_terracotta", "exclude_contains": ["glazed"] }, "count": 4 }

Optional mods: add `"requires_mods": ["modid"]` to skip a layer silently when that mod isn't installed.
Use exactly one of `block` or `pool` per file.

Layers apply in filename order, after vanilla's bands. The band array is built when a world loads,
so changes need a world re-enter and only affect newly generated chunks.

## Optional Dye Depot compat
`dye_depot_terracotta.json` adds random bands using every `dye_depot:*_terracotta` block (glazed excluded).
It only loads when the `dye_depot` mod is installed. Delete the file to turn it off.
