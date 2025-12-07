# Adding Custom Particle Textures to RhythmMod

## Overview

RhythmMod now supports custom particle textures! You can add any Minecraft particle to the config screen dropdown by simply editing the config file.

## How to Add Custom Particles

### Step 1: Open the Config File

Navigate to your Minecraft instance folder and open:
```
config/rhythmmod-config.json5
```

### Step 2: Add Particle Names

Find the `customParticleTextures` section and add particle names:

```json5
{
  // ... other settings ...
  
  "customParticleTextures": [
    "end_rod",
    "firework",
    "electric_spark",
    "dragon_breath"
  ]
}
```

### Step 3: Reload or Restart

- **Option A**: Close and reopen the config screen to see new particles
- **Option B**: Restart Minecraft to ensure everything loads properly

### Step 4: Select Your Custom Particle

Open the config screen (Mods → RhythmMod → Config) and you'll see your custom particles in the dropdown!

## Example Config File

Here's what a config file looks like with custom particles added:

```json5
{
  // Particle texture used by rhythm lamps and bulbs.
  // 
  // Built-in options:
  // - flash: Bright, glowing particle with sharp edges (default)
  // - portal: Swirling portal-like particle
  // - flame: Fire-like particle
  // - heart: Heart-shaped particle
  // - note: Musical note particle
  // - enchant: Enchantment table particle
  // - snowflake: Snow particle
  // - glow: Smooth glowing particle
  // - soul: Soul flame particle
  // 
  // You can also use any custom particle you add to customParticleTextures below.
  // Custom particles are colored to match the frequency band.
  // Default: flash
  "particleTexture": "end_rod",

  // Custom particle textures that will appear in the config screen dropdown.
  // Add any Minecraft particle texture name here to make it selectable.
  // 
  // Examples you can add:
  // - "end_rod": End rod particles
  // - "firework": Firework sparkle
  // - "angry_villager": Angry villager particle
  // - "happy_villager": Happy villager particle
  // - "cloud": Cloud particle
  // - "crit": Critical hit particle
  // - "damage_indicator": Damage number particle
  // - "dragon_breath": Dragon breath particle
  // - "dripping_water": Dripping water
  // - "electric_spark": Electric spark (1.21+)
  // - "explosion": Explosion particle
  // - "falling_dust": Falling dust particle
  // - "fishing": Fishing bobber particle
  // - "lava": Lava drip
  // - "mycelium": Mycelium spore
  // - "poof": Smoke puff
  // - "rain": Rain particle
  // - "smoke": Regular smoke
  // - "splash": Water splash
  // - "witch": Witch magic particle
  // 
  // Just add the particle name (without "minecraft:" prefix) to this list.
  // The config screen will automatically include these options.
  // 
  // Default: [] (empty list)
  "customParticleTextures": [
    "end_rod",
    "firework",
    "electric_spark",
    "dragon_breath",
    "witch",
    "angry_villager"
  ]
}
```

## Available Minecraft Particles (1.21.1)

Here's a list of particle types you can add. Just use the name without `minecraft:`:

### Visual Effects
- `flash` - Bright glowing burst (built-in)
- `portal` - Swirling portal effect (built-in)
- `glow` - Smooth glowing orb (built-in)
- `end_rod` - End rod beam particle
- `firework` - Firework sparkle
- `enchant` - Enchanting table runes
- `electric_spark` - Lightning/electric effect (1.21+)

### Elemental
- `flame` - Fire particle (built-in)
- `soul` - Soul fire (built-in)
- `lava` - Lava drip
- `dripping_water` - Water drops
- `dripping_lava` - Lava drops
- `falling_water` - Falling water
- `splash` - Water splash
- `bubble` - Underwater bubbles
- `rain` - Rain drops

### Nature
- `heart` - Heart particle (built-in)
- `snowflake` - Snow (built-in)
- `cloud` - White cloud
- `mycelium` - Mycelium spores
- `falling_dust` - Dust particles
- `pollen` - Pollen particles (1.21+)

### Magical
- `enchant` - Enchanting symbols (built-in)
- `witch` - Witch magic
- `dragon_breath` - Dragon breath
- `soul_fire_flame` - Soul fire
- `reverse_portal` - Reverse portal effect

### Musical
- `note` - Musical notes (built-in)

### Combat
- `crit` - Critical hit stars
- `damage_indicator` - Damage numbers
- `explosion` - Explosion cloud
- `explosion_emitter` - Large explosion

### Villager
- `happy_villager` - Green sparkles
- `angry_villager` - Red anger mark

### Smoke & Vapor
- `smoke` - Regular gray smoke
- `large_smoke` - Big smoke clouds
- `poof` - Quick puff
- `campfire_cosy_smoke` - Campfire smoke
- `campfire_signal_smoke` - Signal smoke

### Redstone
- `dust` - Redstone dust particles

### Misc
- `fishing` - Fishing bobber splash
- `squid_ink` - Black ink
- `totem_of_undying` - Totem revival effect

## Tips & Tricks

### Finding the Right Particle

1. **Test in Creative Mode**: Use `/particle` command to preview particles
   ```
   /particle minecraft:end_rod ~ ~1 ~ 0.5 0.5 0.5 0.1 50
   ```

2. **Check Particle Compatibility**: Some particles may not work well with coloring
   - Best: `flash`, `glow`, `portal`, `end_rod`, `firework`
   - Good: `enchant`, `witch`, `electric_spark`
   - Limited: `smoke`, `cloud` (may not show colors well)

3. **Performance Considerations**:
   - Simple particles (flash, glow): Fast, efficient
   - Complex particles (dragon_breath, explosion): More demanding
   - Many particles (firework): Can cause lag with lots of bulbs

### Recommended Combinations by Music Genre

**Electronic/Dubstep**:
```json5
"customParticleTextures": ["electric_spark", "end_rod", "firework"]
```

**Classical/Orchestral**:
```json5
"customParticleTextures": ["enchant", "witch", "happy_villager"]
```

**Rock/Metal**:
```json5
"customParticleTextures": ["explosion", "crit", "lava"]
```

**Chill/Ambient**:
```json5
"customParticleTextures": ["cloud", "snowflake", "mycelium"]
```

**Fantasy/Magical**:
```json5
"customParticleTextures": ["dragon_breath", "soul", "reverse_portal"]
```

## Troubleshooting

### Particle Not Showing Up in Dropdown

1. **Check JSON syntax**: Make sure commas and quotes are correct
2. **Restart game**: Sometimes requires a full restart
3. **Check spelling**: Particle names are case-sensitive (use lowercase)
4. **Remove prefix**: Don't include `minecraft:`, just the particle name

### Particle Doesn't Display or Looks Wrong

1. **Check Minecraft version**: Some particles only exist in newer versions
2. **Test with `/particle`**: Verify the particle exists in your version
3. **Try a different particle**: Some don't support coloring

### Config File Won't Save

1. **Check file permissions**: Ensure config folder is writable
2. **Close the game first**: Edit config while game is closed
3. **Use a text editor**: Don't use Word or rich text editors
4. **Validate JSON**: Use a JSON validator to check syntax

## How It Works Technically

### Dynamic Loading
- Built-in particles are always available (flash, portal, flame, etc.)
- Custom particles are loaded from `customParticleTextures` array
- Config screen automatically populates dropdown with all options
- No duplicates - custom particles won't override built-ins

### Color Application
- All particles receive RGB color based on frequency band
- Sub-bass → Red, Bass → Orange, Mids → Purple, Highs → Cyan, etc.
- Particle system uses shader-based coloring
- Some vanilla particles may not support full coloring

### Sync Behavior
- `customParticleTextures` is synced from server to client
- Players on servers see server's particle options
- Single-player uses local config
- Changes take effect on config screen reload

## Advanced Usage

### Per-Frequency Custom Particles (Future Feature)

While not yet implemented, you could extend the system to support per-frequency particles:

```json5
"bassParticle": "lava",
"trebleParticle": "electric_spark",
"midsParticle": "enchant"
```

This would require code modifications to RhythmConfig and the bulb entities.

### Resource Pack Integration

You can create custom particle textures via resource packs, then reference them in the config. This requires:
1. Custom particle texture in resource pack
2. Particle JSON definition
3. Add particle name to `customParticleTextures`

## Example Configurations

### Minimalist (Performance Focus)
```json5
{
  "particleTexture": "glow",
  "customParticleTextures": []
}
```

### Kitchen Sink (All Options)
```json5
{
  "particleTexture": "flash",
  "customParticleTextures": [
    "end_rod", "firework", "electric_spark", "dragon_breath",
    "witch", "enchant", "happy_villager", "crit", "explosion",
    "lava", "soul", "cloud", "mycelium", "reverse_portal"
  ]
}
```

### Themed Build (Magic/Fantasy Server)
```json5
{
  "particleTexture": "enchant",
  "customParticleTextures": [
    "witch",
    "dragon_breath",
    "soul",
    "reverse_portal",
    "totem_of_undying"
  ]
}
```

## Support

If you have issues or questions:
1. Check this guide first
2. Verify your Minecraft version (1.21.1)
3. Test particles with `/particle` command
4. Report bugs with your config file attached

Happy particle customization! 🎉✨

