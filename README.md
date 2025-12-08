# RhythmicLights
A Minecraft mod that brings **real-time audio visualization** to the game. Synchronize in-game lighting and particle effects with music discs or custom audio from YouTube, SoundCloud, and direct URLs.

**Minecraft 1.21+ | Fabric | Java 21**

***

<div><h1>Photosensitivity Warning</h1></div>

**This mod contains flashing lights and rapid color changes that may affect those with photosensitive epilepsy.**

***

<div><p>A Minecraft mod that brings&nbsp;<strong>real-time audio visualization</strong> to the game. Synchronize in-game lighting and particle effects with music discs or custom audio from YouTube, SoundCloud, and direct URLs.</p><h2>Features</h2></div>

*   **Real-time FFT audio analysis** syncs lighting to music
*   **12 frequency-tuned lamps** - each responds to a specific frequency band
*   **Tunable rhythm bulbs** - configurable to any frequency channel
*   **10-band equalizer** with presets (Bass Heavy, Club, Concert, etc.)
*   **YouTube/SoundCloud support** - play any URL directly in-game
*   **Spatial audio** with distance-based volume falloff
*   **Particle effects** that react to the beat
*   **Vanilla redstone lamp support**

***

<div><h2>Usage</h2></div>

<div><h3>Basic Setup</h3></div>

| Step |Action                                                                  |
| ---- |----------------------------------------------------------------------- |
| 1    |Place a&nbsp;<strong>Jukebox</strong>&nbsp;and a&nbsp;<strong>DJ Station</strong>&nbsp;nearby |
| 2    |Use the&nbsp;<strong>Tuning Wand</strong>&nbsp;to link them (Right-click DJ Station, then Jukebox) |
| 3    |Place&nbsp;<strong>Rhythm Bulbs/Lamps</strong>&nbsp;around your build   |
| 4    |Link lights to DJ Station with the Tuning Wand                          |
| 5    |Insert a disc and enjoy the show                                        |

<div><h3>&nbsp;<img class="AnimatedImagePlayer-animatedImage" src="https://github.com/Avioti/Rhythmic-Lights/raw/main/docs/gifs/setup.gif" alt="Basic Setup Demo" data-target="animated-image.replacedImage"></h3><h3>&nbsp;</h3><h3>URL Discs</h3></div>

| Step |Action                                            |
| ---- |------------------------------------------------- |
| 1    |Right-click the&nbsp;<strong>URL Disc</strong>&nbsp;to open the input screen |
| 2    |Paste a YouTube, SoundCloud, or direct audio URL  |
| 3    |Click "Fetch Metadata" to get song info           |
| 4    |Save and insert into a jukebox                    |

<div><h3>&nbsp;<img src="https://github.com/Avioti/Rhythmic-Lights/raw/main/docs/gifs/custom_discs.gif" width="431" height="232"></h3><h3>&nbsp;</h3><h3>Tuning Wand Controls</h3></div>

| Action                 |Function                    |
| ---------------------- |--------------------------- |
| Right-Click DJ Station |Select controller           |
| Right-Click Jukebox    |Link to selected controller |
| Right-Click Bulb/Lamp  |Link to controller          |
| Shift+Right-Click Bulb |Cycle frequency channel     |
| Ctrl+Right-Click       |Unlink block                |
 

***

<div><h2>Commands</h2></div>

| Command                 |Description                             |
| ----------------------- |--------------------------------------- |
| <code>/rhythmmod cancel</code> |Cancel all downloads and clear overlays |
| <code>/rhythmmod clearoverlay</code> |Clear stuck overlay text                |
| <code>/rm cancel</code> |Short alias                             |
| <code>/rm clearoverlay</code> |Short alias                             |
 

***

<div><h2>Frequency Lamps</h2></div>

| Lamp       |Frequency   |Color   |
| ---------- |----------- |------- |
| Sub-Bass   |20-40 Hz    |Red     |
| Deep Bass  |40-80 Hz    |Orange  |
| Bass       |80-150 Hz   |Green   |
| Low-Mids   |150-300 Hz  |Blue    |
| Mid-Lows   |300-500 Hz  |White   |
| Mids       |500-800 Hz  |Purple  |
| Mid-Highs  |800-1.2k Hz |Pink    |
| High-Mids  |1.2-2k Hz   |Yellow  |
| Highs      |2-4k Hz     |Cyan    |
| Very Highs |4-8k Hz     |Magenta |
| Ultra      |8-12k Hz    |Gold    |
| Top        |12-20k Hz   |Silver  |
 

***

<div><h2>Configuration</h2></div>

Access via **Mod Menu** or edit `.minecraft/config/rhythmmod-config.json5`

| Option                    |Description           |Default |
| ------------------------- |--------------------- |------- |
| <code>particleTexture</code> |Particle style        |<code>flash</code> |
| <code>particleScale</code> |Particle size         |<code>0.3</code> |
| <code>useRandomColors</code> |Random color mode     |<code>false</code> |
| <code>coloredShaderLightEnabled</code> |Glow effects          |<code>true</code> |
| <code>spatialAudioEnabled</code> |Distance-based volume |<code>true</code> |
| <code>maxAudioDistance</code> |Max hearing distance  |<code>24</code> |
 

***

<div><h2>Disclaimer</h2></div>

This mod automatically downloads the following external programs in the background to ensure proper functionality. By using this mod, you agree to the automated download and use of these programs:

*   **yt-dlp** (Unlicense): Downloads media from YouTube/SoundCloud
*   **FFmpeg** (GPL-3.0): Converts audio files

It is the sole responsibility of each user to comply with applicable copyright laws and terms of service of any music provider. The developers assume no liability for unauthorized use.

***

<div><h2>Light Show Demo &amp; GUI</h2></div>

[![Light Show Demo](https://github.com/Avioti/Rhythmic-Lights/blob/main/docs/gifs/img.png?raw=true)](https://www.youtube.com/shorts/en_kQGN4Pq4)


![DJ Station GUI](https://media.githubusercontent.com/media/Avioti/Rhythmic-Lights/main/docs/gifs/Gui-display-Made-with-Clipchamp.gif)

***

<div><h2>License</h2></div>

Creative Commons Zero v1.0 Universal (CC0 1.0)
Full text: https://creativecommons.org/publicdomain/zero/1.0/




