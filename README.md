# RhythmicLights
A Minecraft mod that brings **real-time audio visualization** to the game. Synchronize in-game lighting and particle effects with music discs or custom audio from YouTube, SoundCloud, and direct URLs.

**Minecraft 1.21+ | Fabric | Java 21**

---

### Photosensitivity Warning
**This mod contains flashing lights and rapid color changes that may affect those with photosensitive epilepsy.**

---

## Features

- **Real-time FFT audio analysis** syncs lighting to music
- **12 frequency-tuned lamps** - each responds to a specific frequency band
- **Tunable rhythm bulbs** - configurable to any frequency channel
- **10-band equalizer** with presets (Bass Heavy, Club, Concert, etc.)
- **YouTube/SoundCloud support** - play any URL directly in-game
- **Spatial audio** with distance-based volume falloff
- **Particle effects** that react to the beat
- **Vanilla redstone lamp support**

---

## Usage

### Basic Setup
| Step | Action |
|------|--------|
| 1 | Place a **Jukebox** and a **DJ Station** nearby |
| 2 | Use the **Tuning Wand** to link them (Right-click DJ Station, then Jukebox) |
| 3 | Place **Rhythm Bulbs/Lamps** around your build |
| 4 | Link lights to DJ Station with the Tuning Wand |
| 5 | Insert a disc and enjoy the show |

![Basic Setup Demo](https://github.com/Avioti/Rhythmic-Lights/blob/main/docs/guides/gifs/Setup.mp4)

### URL Discs
| Step | Action |
|------|--------|
| 1 | Right-click the **URL Disc** to open the input screen |
| 2 | Paste a YouTube, SoundCloud, or direct audio URL |
| 3 | Click "Fetch Metadata" to get song info |
| 4 | Save and insert into a jukebox |

![URL Disc Setup](https://github.com/Avioti/Rhythmic-Lights/blob/main/docs/guides/gifs/Custom_Discs.mp4)

### Tuning Wand Controls
| Action | Function |
|--------|----------|
| Right-Click DJ Station | Select controller |
| Right-Click Jukebox | Link to selected controller |
| Right-Click Bulb/Lamp | Link to controller |
| Shift+Right-Click Bulb | Cycle frequency channel |
| Ctrl+Right-Click | Unlink block |

---

## Commands

| Command | Description |
|---------|-------------|
| `/rhythmmod cancel` | Cancel all downloads and clear overlays |
| `/rhythmmod clearoverlay` | Clear stuck overlay text |
| `/rm cancel` | Short alias |
| `/rm clearoverlay` | Short alias |

---

## Frequency Lamps

| Lamp | Frequency | Color |
|------|-----------|-------|
| Sub-Bass | 20-40 Hz | Red |
| Deep Bass | 40-80 Hz | Orange |
| Bass | 80-150 Hz | Green |
| Low-Mids | 150-300 Hz | Blue |
| Mid-Lows | 300-500 Hz | White |
| Mids | 500-800 Hz | Purple |
| Mid-Highs | 800-1.2k Hz | Pink |
| High-Mids | 1.2-2k Hz | Yellow |
| Highs | 2-4k Hz | Cyan |
| Very Highs | 4-8k Hz | Magenta |
| Ultra | 8-12k Hz | Gold |
| Top | 12-20k Hz | Silver |

---

## Configuration

Access via **Mod Menu** or edit `.minecraft/config/rhythmmod-config.json5`

| Option | Description | Default |
|--------|-------------|---------|
| `particleTexture` | Particle style | `flash` |
| `particleScale` | Particle size | `0.3` |
| `useRandomColors` | Random color mode | `false` |
| `coloredShaderLightEnabled` | Glow effects | `true` |
| `spatialAudioEnabled` | Distance-based volume | `true` |
| `maxAudioDistance` | Max hearing distance | `24` |

---

## Disclaimer

This mod automatically downloads the following external programs in the background to ensure proper functionality. By using this mod, you agree to the automated download and use of these programs:

- **yt-dlp** (Unlicense): Downloads media from YouTube/SoundCloud
- **FFmpeg** (GPL-3.0): Converts audio files

It is the sole responsibility of each user to comply with applicable copyright laws and terms of service of any music provider. The developers assume no liability for unauthorized use.

---

## Light Show Demo & GUI

[![Light Show Demo](https://github.com/Avioti/Rhythmic-Lights/blob/main/docs/guides/gifs/img.png)](https://www.youtube.com/shorts/en_kQGN4Pq4)



![DJ Station GUI](https://github.com/Avioti/Rhythmic-Lights/blob/main/docs/guides/gifs/Gui-display-Made-with-Clipchamp.gif)

---

## License

GPL-3.0 License (with Apache 2.0 for FabricMC components) - see [LICENSE](LICENSE) for details.




