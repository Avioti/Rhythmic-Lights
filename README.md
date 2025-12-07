# Rhythmic-Lights

A Minecraft modthat introduces real-time audio visualization mechanics to Minecraft. This project implements a custom audio processing system to synchronize in-game lighting blocks and particle effects with music discs and custom audio files.

### Photosensitivity Warning
**This mod contains flashing lights and rapid color changes.**

---

### Key Features

*   **Audio-Visual Synchronization:** Algorithms that parse audio playback to trigger lighting events in real-time.
*   **The "Composer Station" (GUI):** A custom User Interface allowing precise control over:
    *   **Equalization (EQ):** Adjust frequency responses.
    *   **Volume & Bass:** Fine-tune the audio output.
    *   **Playback Control:** Loop, Play, Pause, and Stop functionality.
*   **Dynamic Lighting Assets:**
    *   **Tunable Bulb:** A single light source that reacts to specific audio triggers.
    *   **12 Pre-Tuned Lamps:** Colored lamps calibrated to specific frequency ranges.
    *   **Vanilla Integration:** Fully compatible with standard Redstone lamps for seamless building.
*   **Particle System:**
    *   Custom colored particles that react to the beat.
    *   Fully configurable via config files to manage performance and visual intensity.

### 🛠️ Technical Implementation
*   **Language:** Java
*   **Configuration:** Implemented a robust config system to handle audio buffers and particle limits.
*   **State Management:** Complex handling of audio states (looping vs. single play) and block updates.

### 🎥 Demo
[https://streamable.com/9xvb9o]
[https://streamable.com/bsos3n]
