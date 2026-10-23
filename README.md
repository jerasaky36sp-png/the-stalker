# THE STALKER (Forge 1.20.1) - Stage 1-3

1. Copy `gradlew`, `gradlew.bat` and the `gradle/` folder from the official Forge MDK 1.20.1-47.x into this folder
   (https://files.minecraftforge.net -> 1.20.1 -> Mdk).
2. Open in IntelliJ IDEA (JDK 17) -> import as Gradle project -> run `genIntellijRuns`.
3. Run the `runClient` configuration. In a creative world with cheats:
       /summon stalker:the_stalker
4. Build jar: `./gradlew build` -> build/libs/stalker-0.1.0.jar
