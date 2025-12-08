# Custom Particle Textures

Add any Minecraft or modded particle to the RhythmMod config screen by editing the config file.

## Quick Setup

1. Open `config/rhythmmod-config.json5`
2. Add particle names to `customParticleTextures`:

```json5
{
  "particleTexture": "flash",
  "customParticleTextures": [
    "end_rod",
    "firework",
    "create:air_flow",
    "botania:sparkle"
  ]
}
```

3. Restart Minecraft or reopen the config screen
4. Select your particle in **Mods → RhythmMod → Config**

## Particle Name Format

| Format | Example | Description |
|--------|---------|-------------|
| `particle_name` | `end_rod` | Vanilla Minecraft particle |
| `modid:particle_name` | `create:air_flow` | Modded particle |

## For Mod Developers

You can create custom particles under your mod's namespace and use them with RhythmMod:

1. Register your particle type in your mod
2. Add the particle texture to `assets/<modid>/textures/particle/`
3. Create the particle JSON in `assets/<modid>/particles/<particle_name>.json`
4. Reference it using `modid:particle_name` in the config

RhythmMod will apply RGB coloring to your particle based on the active frequency band.

## Built-in Particles

These are already available without adding them to the config:

| Particle | Description |
|----------|-------------|
| `flash` | Bright glowing burst (default) |
| `portal` | Swirling portal effect |
| `flame` | Fire particle |
| `heart` | Heart shape |
| `note` | Musical notes |
| `enchant` | Enchanting runes |
| `snowflake` | Snow particle |
| `glow` | Smooth glowing orb |
| `soul` | Soul flame |

## Recommended Additions

| Particle | Best For |
|----------|----------|
| `end_rod` | Clean, bright beams |
| `firework` | Sparkly effects |
| `electric_spark` | Electronic/EDM vibes |
| `dragon_breath` | Magical themes |
| `witch` | Purple magic |
| `crit` | Energetic, punchy |

**Preview particles in-game:**
```
/particle minecraft:end_rod ~ ~1 ~ 0.5 0.5 0.5 0.1 50
```

## Color Compatibility

Particles are colored based on frequency band. Some work better than others:

- **Best:** `flash`, `glow`, `portal`, `end_rod`, `firework`
- **Good:** `enchant`, `witch`, `electric_spark`  
- **Limited:** `smoke`, `cloud` (may not show colors well)

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Not in dropdown | Check JSON syntax, restart game |
| Doesn't display | Verify particle name with `/particle` |
| No color | Some particles don't support tinting |
| Modded particle missing | Ensure the mod is installed and use `modid:particle` format |

**Note:** Use lowercase particle names. For vanilla particles, omit the `minecraft:` prefix.
