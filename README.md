# Steve (Forge horror mod)

A Minecraft Forge mod where **Steve** is not your friend. He watches you from far away, stays out of sight, and quietly ruins your day.

> Work in progress. Built for **Minecraft 26.1.2** with **Forge 26.1.2-64.1.6**.

## Behaviour

- **Stalker**: Steve keeps 24 to 44 blocks away. If you look at him for too long, or get too close, he vanishes and reappears somewhere else out of your line of sight.
- **Hidden arrival**: in the dark (light level 7 or lower) he can appear on his own near you.
- **Chest saboteur**: after you open a chest or barrel and walk away, Steve walks to it later, empties it and sets it on fire.
- **Inventory aware**: he checks what you carry (water bucket, elytra, totem of undying, weapons) to choose his moves.
- **Cliff push**: if you stand near a big drop or lava and carry nothing that saves you from a fall, he runs at you and shoves you over the edge.
- **Torch snuffer**: he removes torches behind your back.
- **Finisher**: if you are badly hurt and have no totem, he attacks.
- He cannot be hurt.

## Requirements

- **JDK 25** (for example Temurin 25)
- An IDE such as IntelliJ IDEA

## Run it

1. Clone this repository and open it in IntelliJ IDEA as a Gradle project.
2. Wait for the first Gradle sync.
3. Run the **runClient** task.
4. Use the **Steve Spawn Egg** from the example tab, or just play in the dark.

To build a `.jar` for a launcher such as Prism, run the Gradle **build** task. The file appears in `build/libs`.

## Credits

- **Idea, direction and in-game testing:** the repository owner.
- **Code written with Claude** ([Claude Sonnet 5.5](https://www.anthropic.com/claude) by Anthropic), in a chat session.
- Built on the Forge MDK template.

## Notes

- The mod id is still `examplemod` (from the Forge MDK template) and will be renamed later.
- Based on the [Forge MDK](https://files.minecraftforge.net/). The Forge license is in `LICENSE.txt`.
