# Steve (Forge mod)

A Minecraft Forge mod that adds **Steve**, a companion mob who follows you, fights hostile mobs, and can be given jobs from a tablet.

> Work in progress. Built for **Minecraft 26.1.2** with **Forge 26.1.2-64.1.6**.

## Features

- **Steve mob** with the vanilla Steve skin (no extra textures needed).
- **Spawn egg** in the creative tab so you can place him in the world.
- **Follows you** and attacks hostile mobs (and anything that hurts him).
- **Tablet** (right-click Steve):
  - his own 9-slot inventory,
  - **Follow me** button,
  - **Chop wood** button, unlocked when he carries an axe. He looks for logs around the spot where you gave the order, chops them and keeps the wood.
- His inventory and task are saved with him, and drop if he dies.

## Planned

- Mining with a pickaxe, farming with a hoe, digging with a shovel (tasks come from the tools in his inventory).
- Axe durability, tool shown in his hand.
- Chat lines and more helper behaviour.

## Requirements

- **JDK 25** (for example Temurin 25)
- An IDE such as IntelliJ IDEA

## Run it

1. Clone this repository and open it in IntelliJ IDEA as a Gradle project.
2. Wait for the first Gradle sync (it downloads Minecraft and Forge, so it can take a while).
3. Run the **runClient** task.
4. In a creative world, open the example tab and use the **Steve Spawn Egg**.

To build a `.jar` for a launcher such as Prism, run the Gradle **build** task. The file appears in `build/libs`.

## Credits

- **Idea, direction and in-game testing:** the repository owner.
- **Code written with Claude** ([Claude Sonnet 5.5](https://www.anthropic.com/claude) by Anthropic), in a chat session: the Steve mob, spawn egg, tablet screen and wood-chopping behaviour.
- Built on the Forge MDK template.

## Notes

- The mod id is still `examplemod` (from the Forge MDK template) and will be renamed later.
- Based on the [Forge MDK](https://files.minecraftforge.net/). The Forge license is in `LICENSE.txt`.
