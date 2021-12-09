package net.creeperhost.minetogether;

import dev.architectury.platform.Platform;
import dev.architectury.utils.EnvExecutor;
import net.creeperhost.minetogether.config.Config;
import net.creeperhost.minetogether.lib.chat.MineTogetherChat;
import net.fabricmc.api.EnvType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class MineTogetherCommon
{
    public static Logger logger = LogManager.getLogger();
    public static String base64 = "";
    public static Path configFile = Platform.getConfigFolder().resolve(Constants.MOD_ID + ".json");

    public static void init()
    {
        Config.init(configFile.toFile());
        MineTogetherChat.DEBUG_MODE = Config.getInstance().isDebugMode();
    }

    public static void clientInit()
    {
        EnvExecutor.runInEnv(EnvType.CLIENT, () -> MineTogetherClient::init);
    }

    public static void serverInit()
    {
        EnvExecutor.runInEnv(EnvType.SERVER, () -> MineTogetherServer::init);
    }
}