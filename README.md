# The Stalker - Forge 1.20.1 horror mod

## Build
1. Download the Forge **1.20.1-47.2.0 MDK** from files.minecraftforge.net (for the Gradle wrapper).
2. Copy this project's `src/`, `build.gradle`, `settings.gradle`, `gradle.properties` over the MDK's.
3. `./gradlew build` -> jar in `build/libs/`. (`./gradlew runClient` to test.) Needs JDK 17.

## Testing commands (op level 2)
- `/stalker summon`  spawn one now (normally it appears after the first sunset)
- `/stalker info`    see what it has learned about you
- `/stalker reset`   wipe its memory of you

Spawn egg is in the Spawn Eggs creative tab.

## Behaviour
STALK (circle + freeze when watched) -> HIDE -> BLEND (tree-still) -> PEEK (lean out) -> FAKE_RUSH / CHASE -> RETREAT.
Tune speeds at the top of `StalkerBrainGoal`; spawn timing in `StalkerEvents`; tree density in `TreeChopper.CHUNK_CHANCE`.
