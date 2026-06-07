# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

A single-file Asteroids-style arcade game written in plain Java (Swing/AWT, no external
dependencies). All game code lives in [src/main/java/org/example/Asteroids.java](src/main/java/org/example/Asteroids.java).

## Build & Run

Maven project (`pom.xml`), Java 8 source/target.

- Build: `mvn package` (produces a runnable JAR via maven-jar-plugin with main class `org.example.Asteroids`)
- Run directly: `mvn compile exec:java -Dexec.mainClass=org.example.Asteroids` or `java -cp target/classes org.example.Asteroids`
- No test suite exists in this repo.

## Architecture

Everything is in one file, organized as a sequence of top-level classes:

- `Asteroids` — entry point; creates the `JFrame` and `GamePanel` (800x600) and starts the game loop.
- `Vec` — 2D vector math utility (position/velocity).
- `Ship`, `Bullet`, `Asteroid` — game entity state/data classes.
- `GamePanel` (extends `JPanel`, implements `ActionListener`, `KeyListener`) — the core of the
  game: owns the Swing timer-driven update/render loop, handles keyboard input, collision
  detection, spawning/scoring, pause/restart, and rendering of all entities via `paintComponent`.
- `Sound` — sound effect playback helper.
- `Heartbeat` — timing/pacing helper (e.g. for the asteroid heartbeat audio cue that speeds up).

## Controls (for reference when testing gameplay)

- Left/Right arrows: rotate ship
- Up arrow: thrust
- Space: fire bullet
- P: pause/unpause
- R: restart after game over
