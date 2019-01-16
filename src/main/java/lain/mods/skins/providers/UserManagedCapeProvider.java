package lain.mods.skins.providers;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import lain.mods.skins.api.interfaces.IPlayerProfile;
import lain.mods.skins.api.interfaces.ISkin;
import lain.mods.skins.api.interfaces.ISkinProvider;
import lain.mods.skins.impl.SkinData;

public class UserManagedCapeProvider implements ISkinProvider
{

    private File _dirN;
    private File _dirU;
    private Function<ByteBuffer, ByteBuffer> _filter;

    public UserManagedCapeProvider(Path workDir)
    {
        _dirN = new File(workDir.toFile(), "capes");
        _dirN.mkdirs();
        _dirU = new File(_dirN, "uuids");
        _dirU.mkdirs();
    }

    @Override
    public ISkin getSkin(IPlayerProfile profile)
    {
        ByteBuffer data = null;
        if (!Shared.isOfflinePlayerProfile(profile))
            data = readFile(_dirU, "%s.png", profile.getPlayerID().toString().replaceAll("-", ""));
        if (data == null && !Shared.isBlank(profile.getPlayerName()))
            data = readFile(_dirN, "%s.png", profile.getPlayerName());
        if (data == null)
            return null;
        SkinData skin = new SkinData();
        if (_filter != null)
            skin.setSkinFilter(_filter);
        skin.put(data, "cape");
        return skin;
    }

    private ByteBuffer readFile(File dir, String filename)
    {
        try
        {
            return ByteBuffer.wrap(Files.readAllBytes(new File(dir, filename).toPath()));
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private ByteBuffer readFile(File dir, String filename, Object... args)
    {
        return readFile(dir, String.format(filename, args));
    }

    public UserManagedCapeProvider withFilter(Function<ByteBuffer, ByteBuffer> filter)
    {
        _filter = filter;
        return this;
    }

}
