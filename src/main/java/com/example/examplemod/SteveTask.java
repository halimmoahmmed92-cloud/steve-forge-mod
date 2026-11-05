package com.example.examplemod;

/**
 * The jobs Steve can be given from his tablet.
 * Later we can add MINE (pickaxe), FARM (hoe), DIG (shovel)...
 */
public enum SteveTask {
    FOLLOW,
    CHOP_WOOD;

    public static SteveTask byId(int id) {
        SteveTask[] all = values();
        return id >= 0 && id < all.length ? all[id] : FOLLOW;
    }
}
