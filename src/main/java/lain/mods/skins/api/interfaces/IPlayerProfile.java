package lain.mods.skins.api.interfaces;

import java.util.UUID;

public interface IPlayerProfile
{

    /**
     * @return the actual profile object.
     */
    Object getOriginal();

    /**
     * @return the uuid of the profile.
     */
    UUID getPlayerID();

    /**
     * @return the name of the profile.
     */
    String getPlayerName();

}