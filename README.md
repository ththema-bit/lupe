# Lupe

A small Java Swing screen magnifier. It shows a region of your screen, enlarged, in its own window. Typical use: put the League of Legends minimap on a second monitor at a larger size.

## Features

- Pick any screen region with the mouse (hold Shift for a square).
- Separate display window, resizable, movable to any monitor.
- Fullscreen mode on the monitor the display is currently on.
- Frame rate selectable: 15, 30 or 60 frames per second.
- Smoothing on, or off for sharp pixels.
- Optional: keep aspect ratio, always on top, borderless window.
- Settings and last region are saved and restored on the next start.
- No dependencies beyond a Java runtime.

## Usage

Double-click `Lupe.jar`, or run:

```
java -jar Lupe.jar
```

Requires Java 17 or newer.

## Controls

1. **Choose region**: the screen is frozen and dimmed. Drag a rectangle with the mouse (Shift = square). Releasing the button confirms. Esc or right click cancels.
2. The **display window** opens (on first use, centered on the second monitor). Move it where you want it and drag its edges to resize.
3. **Double-click** the display to go fullscreen on the monitor it is on. Esc or another double-click leaves fullscreen.
4. **Right-click** the display for a menu: choose region again, fullscreen, border, smoothing, always on top, close.
5. Without a window border, move the window by dragging it and resize at the bottom right corner.

Settings are stored in `.lupe.properties` in your user home directory.

## League of Legends notes

- Run the game in **borderless windowed** mode. In exclusive fullscreen, Windows often delivers only a black image.
- Lupe only reads screen pixels (like OBS or the Windows Snipping Tool) and does not touch the game. Whether a tool like this is acceptable under the game's rules is up to the publisher, so use it at your own risk.
- With Windows display scaling (125%, 150%), Lupe captures at full pixel resolution.

## Build and self-test

```
javac --release 17 -encoding UTF-8 -d out src/lupe/*.java && cp src/resources/* out/ && printf 'Main-Class: lupe.Lupe\n' > manifest.txt && jar cfm Lupe.jar manifest.txt -C out . && rm -rf out manifest.txt
java -Djava.awt.headless=true -jar Lupe.jar --selftest
```

## Fonts

The UI uses Barlow Condensed and IBM Plex Mono, both licensed under the SIL Open Font License 1.1. The license texts are in `src/resources/`. The fonts are bundled as TTF because Java cannot read WOFF2.

## License

MIT, see `LICENSE`.
