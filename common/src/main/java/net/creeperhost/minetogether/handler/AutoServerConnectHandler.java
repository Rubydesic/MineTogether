package net.creeperhost.minetogether.handler;

import dev.architectury.hooks.client.screen.ScreenAccess;
import io.sentry.Sentry;
import net.creeperhost.minetogether.MineTogetherCommon;
import net.creeperhost.minetogether.lib.chat.ChatCallbacks;
import net.creeperhost.minetogether.lib.serverlists.Server;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.io.IOException;

public class AutoServerConnectHandler
{
    static boolean first = true;

    //This is Aaron code and was copy pasta
    public static void onScreenOpen(Screen screen, ScreenAccess screenAccess)
    {
        if (screen instanceof TitleScreen && first)
        {
            first = false;
            String server = System.getProperty("mt.server");
            int serverId = -1;
            if (server != null)
            {
                try
                {
                    serverId = Integer.parseInt(server);
                } catch (Throwable t)
                {
                    MineTogetherCommon.logger.error("Unable to auto connect to server as unable to parse server ID");
                }

                Server serverObj = null;
                try
                {
                    serverObj = ChatCallbacks.getServer(serverId);
                } catch (IOException e)
                {
                    Sentry.captureException(e);
                }

                if (serverObj != null)
                {
                    String[] serverSplit = serverObj.host.split(":");

                    int realPort = -1;
                    try
                    {
                        realPort = Integer.parseInt(serverSplit[1]);
                    } catch (Throwable t)
                    {
                        MineTogetherCommon.logger.error("Unable to auto connect to server as unable to parse server port for ID " + serverId);
                    }

                    if (realPort != -1)
                    {
                        ServerData serverData = new ServerData(serverSplit[0], String.valueOf(realPort), false);
                        ConnectScreen.startConnecting(new JoinMultiplayerScreen(screen), Minecraft.getInstance(), ServerAddress.parseString(serverData.ip), serverData);
                    }
                }
            }
        }
    }
}
