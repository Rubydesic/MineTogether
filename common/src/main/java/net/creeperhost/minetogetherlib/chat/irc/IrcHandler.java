package net.creeperhost.minetogether.minetogetherlib.chat.irc;

import net.creeperhost.minetogether.minetogetherlib.chat.ChatConnectionStatus;
import net.creeperhost.minetogether.minetogetherlib.chat.ChatHandler;
import net.creeperhost.minetogether.minetogetherlib.chat.MineTogetherChat;
import net.creeperhost.minetogether.minetogetherlib.chat.data.PrivateChat;
import net.creeperhost.minetogether.minetogetherlib.chat.data.Profile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IrcHandler
{
    public static Logger logger = LogManager.getLogger();
    public static final String nickname = MineTogetherChat.INSTANCE.ourNick;
    public static AtomicBoolean first = new AtomicBoolean(true);
    private static OutputStreamWriter outputStreamWriter;
    private static BufferedWriter bufferedWriter;
    private static Socket socket;

    private static int errCount = 0;

    private static final char CTCP_DELIMITER = '\u0001';
    private static final char CTCP_MQUOTE = '\u0016';

    private static final Pattern CTCP_ESCAPABLE_CHAR = Pattern.compile("[\n\r\u0000" + CTCP_DELIMITER + CTCP_MQUOTE + "\\\\]");
    private static final Pattern CTCP_ESCAPED_CHAR = Pattern.compile("([" + CTCP_MQUOTE + "\\\\])(.)");
    private static CompletableFuture chatFuture = null;
    private static String lastMessage;
    private static long lastMessageTime;

    public static void start(IRCServer ircServer)
    {
        try
        {
            if(chatFuture != null) return;
            errCount = 0;

            logger.info("Starting new Chat socket");
            socket = new Socket(ircServer.address, ircServer.port);
            outputStreamWriter = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            bufferedWriter = new BufferedWriter(outputStreamWriter);

            chatFuture = CompletableFuture.runAsync(() ->
            {
                logger.info("Starting new Chat thread");

                String line = null;
                try
                {
                    while ((line = bufferedReader.readLine()) != null)
                    {
                        if (line.startsWith("PING "))
                        {
                            sendString("PONG " + line.substring(5) + "\r\n", true);
                            if(first.get()) {
                                ChatHandler.connectionStatus = ChatConnectionStatus.CONNECTED;
                                sendString("USER " + "MineTogether" + " 8 * :" + MineTogetherChat.INSTANCE.realName, true);//"MineTogether" + " 8 * :" + "{\"p\":\"m35\",\"b\":\"MzUxNzQ\\u003d\"}");
                                sendString("JOIN " + ircServer.channel, true);
                                first.getAndSet(false);
                            }
                        } else {
                            String finalLine = line;
                            CompletableFuture.runAsync(() -> handleInput(finalLine), MineTogetherChat.messageHandlerExecutor);
                        }
                    }
                } catch (Exception ignored) {}
            });
            sendString("NICK " + nickname, true);

            while (!socket.isClosed())
            {
                Thread.sleep(100);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void stop(boolean force)
    {
        try
        {
            first.set(true);
            ChatHandler.connectionStatus = ChatConnectionStatus.DISCONNECTED;
            logger.info("Stopping IRC Socket");
            if(!socket.isClosed()) socket.close();
            logger.info("Force quitting chat thread");
            if(chatFuture.cancel(true))
            {
                logger.info("Chat thread stopped");
            }
            chatFuture = null;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    private static boolean reconnecting = false;
    public static void reconnect()
    {
        if(reconnecting) return;
        reconnecting = true;
        try {
            stop(true);
//            MineTogether.proxy.startChat();
        } catch(Throwable ignored) {}
        reconnecting = false;
    }

    public static void sendString(String str, boolean errorCount)
    {
        if(socket.isClosed() || !socket.isConnected()) return;
        if(str.isEmpty()) return;

        String preFormat = str + "\r\n";
        byte[] out = preFormat.getBytes(StandardCharsets.UTF_8);
        String s = new String(out, StandardCharsets.UTF_8);
        char[] chars = s.toCharArray();

        try
        {
            bufferedWriter.write(chars);
            bufferedWriter.flush();
            errCount = 0;
        }
        catch (SocketException e)
        {
            if(errorCount) errCount++;

            if(errCount > 5)
            {
                reconnect();
            }
            e.printStackTrace();
        }
        catch (Exception e)
        {
            if(errorCount) errCount++;
            e.printStackTrace();
        }
    }

    public static boolean sendMessage(String channel, String message)
    {
        long messageTime = System.currentTimeMillis();
        if((lastMessage != null && lastMessage.equalsIgnoreCase(message)) || (lastMessageTime != 0 && lastMessageTime > (messageTime-300)))
        {
            ChatHandler.addMessageToChat(channel,"System", "Please refrain from flooding.");
            return false;
        } else {
            sendString("PRIVMSG " + channel + " " + new String(message.getBytes(), StandardCharsets.UTF_8), true);
            lastMessage = message;
            lastMessageTime = System.currentTimeMillis();
            return true;
        }
    }

    public static void whois(String nick)
    {
        if(ChatHandler.connectionStatus != ChatConnectionStatus.CONNECTED) return;
        if(nick.length() < 28) return;

        sendString("WHOIS " + nick, false);
    }

    public static void sendCTCPMessage(String target, String type, String value)
    {
        sendString("NOTICE " + target + " :" + toCtcp(type + " " + value), true);
    }

    public static void sendCTCPMessagePrivate(String target, String type, String value)
    {
        sendString("PRIVMSG " + target + " :" + toCtcp(type + " " + value), true);
    }

    public static void joinChannel(String channel)
    {
        sendString("JOIN " + channel, true);
    }

    public static String toCtcp(String message)
    {
        StringBuilder builder = new StringBuilder(message.length());
        builder.append(CTCP_DELIMITER);
        int currentIndex = 0;
        Matcher matcher = CTCP_ESCAPABLE_CHAR.matcher(message);
        while (matcher.find()) {
            if (matcher.start() > currentIndex) {
                builder.append(message, currentIndex, matcher.start());
            }
            switch (matcher.group()) {
                case "\n":
                    builder.append(CTCP_MQUOTE).append('n');
                    break;
                case "\r":
                    builder.append(CTCP_MQUOTE).append('r');
                    break;
                case "\u0000":
                    builder.append(CTCP_MQUOTE).append('0');
                    break;
                case CTCP_MQUOTE + "":
                    builder.append(CTCP_MQUOTE).append(CTCP_MQUOTE);
                    break;
                case CTCP_DELIMITER + "":
                    builder.append("\\a");
                    break;
                case "\\":
                    builder.append("\\\\");
                    break;
                default:
                    // NOOP
                    break;
            }
            currentIndex = matcher.end();
        }
        if (currentIndex < message.length()) {
            builder.append(message.substring(currentIndex));
        }
        builder.append(CTCP_DELIMITER);
        return builder.toString();
    }

    public static void handleInput(String s)
    {
        if(s.contains(" :Nickname is already in use") && s.contains("433"))
        {
            ChatHandler.reconnectTimer.set(30000);
//                        ChatHandler.addStatusMessage("You appear to be connected elsewhere delaying reconnect for 30 seconds");
        }
        else if(s.contains("PRIVMSG"))
        {
            CompletableFuture.runAsync(() ->
            {
                Pattern pattern = Pattern.compile(".*(MT[A-Za-z0-9]{28}).*PRIVMSG.*(\\#\\w+) \\:(.*)");
                Matcher matcher = pattern.matcher(s);
                if (matcher.matches())
                {
                    String name = matcher.group(1);
                    String channel = matcher.group(2);
                    String message = matcher.group(3);
                    ChatHandler.addMessageToChat(channel, name, Format.stripAll(message));
                                /*if(channel.equalsIgnoreCase(ChatHandler.CHANNEL))
                                {
                                    ChatHandler.addMessageToChat(ChatHandler.CHANNEL, name, Format.stripAll(message));
                                }*/
                } else {
                    pattern = Pattern.compile("\\:(\\w+).*PRIVMSG.*\\:\\x01(.*)\\x01");
                    matcher = pattern.matcher(s);
                    if (matcher.matches()) {
                        String name = matcher.group(1);
                        String message = matcher.group(2);

                        ChatHandler.onCTCP(name, message);
                    } else {
                        pattern = Pattern.compile("\\:(\\w+).*PRIVMSG.*\\:(.*)");
                        matcher = pattern.matcher(s);
                        if (matcher.matches()) {
                            String name = matcher.group(1);
                            String message = matcher.group(2);
                            ChatHandler.addMessageToChat(name, name, Format.stripAll(message));
                        } else {
                            System.out.println("Unknown message! "+s);
                        }
                    }
                }
            }, MineTogetherChat.chatMessageExecutor);
        } else if(s.contains("NOTICE"))
        {
            CompletableFuture.runAsync(() ->
            {
                Pattern pattern = Pattern.compile("\\:(\\w+).*NOTICE (?:MT.{28}|\\#\\w+) \\:(.*)");
                Matcher matcher = pattern.matcher(s);
                if(matcher.matches())
                {
                    String name = matcher.group(1);
                    String message = matcher.group(2);
                    boolean op = !name.startsWith("MT");

                    if (op)
                    {
                        //Channel Message
                        if (s.contains(ChatHandler.CHANNEL))
                        {
                            ChatHandler.onChannelNotice(name, message);
                        }
                        else if(s.contains(ChatHandler.nick))
                        {
                            ChatHandler.onNotice(name, message);
                        }
                    }
                }
            }, MineTogetherChat.chatMessageExecutor);
        }
        else if(s.contains("MODE"))
        {
            CompletableFuture.runAsync(() ->
            {
                Pattern pattern = Pattern.compile("\\:(\\w+).*MODE.*\\#\\w+ (.)b (MT[a-zA-Z0-9]{28}).*");
                Matcher matcher = pattern.matcher(s);
                if(matcher.matches())
                {
                    String nick = matcher.group(3);
                    String modify = matcher.group(2);
                    if(modify.equals("+"))
                    {
                        ChatHandler.onUserBanned(nick);
                        ChatHandler.rebuildChat = true;
                    }
                    if(modify.equals("-"))
                    {
                        Profile profile = ChatHandler.knownUsers.findByNick(nick);
                        if(profile != null) {
                            profile.setBanned(false);
                            ChatHandler.knownUsers.update(profile);
                            ChatHandler.rebuildChat = true;
                        }
                        if(ChatHandler.backupBan.get().contains(nick)) {
                            ChatHandler.backupBan.getAndUpdate((bans) -> {
                                bans.remove(nick);
                                return bans;
                            });
                        }
                    }
                }
            }, MineTogetherChat.ircEventExecutor);
        } else if(s.contains("JOIN"))
        {
            CompletableFuture.runAsync(() ->
            {
                Pattern pattern = Pattern.compile(":(MT.{28})!.*JOIN (#\\w+) . \\:(\\{.*\\})");
                Matcher matcher = pattern.matcher(s);
                if (matcher.matches()) {
                    String nick = matcher.group(1);
                    String channel = matcher.group(2);
                    String json = matcher.group(3);
                    if(ChatHandler.curseSync.containsKey(nick)) {
                        if(!ChatHandler.curseSync.get(nick).equals(json))
                        {
                            ChatHandler.curseSync.remove(nick);
                            ChatHandler.curseSync.put(nick, json);
                        }
                    } else {
                        ChatHandler.curseSync.put(nick, json);
                    }
                    CompletableFuture.runAsync(() -> ChatHandler.updateFriends(ChatHandler.knownUsers.getFriends()), MineTogetherChat.profileExecutor);

                    Profile profile = ChatHandler.knownUsers.findByNick(nick);
                    if (profile != null) {
                        profile.setOnline(true);
                        profile.setPackID(json);
                        ChatHandler.knownUsers.update(profile);
                    }
                }
            }, MineTogetherChat.ircEventExecutor);
        } else if(s.contains(" 352 ")||s.contains(" 311 ")) //&& s.split(" ")[1].contains("352"))//WHOIS responses
        {
            CompletableFuture.runAsync(() ->
            {
                Pattern pattern = Pattern.compile(":.* \\d{3} MT.{28} (?:\\#?\\w+)?.*?(MT.{28}) .*? \\:(\\{.*\\})");//:.*\\d{3} MT.{28} (\\#\\w+) .*(MT.{28}) \\w\\+? :\\d (\\{.*\\})");
                Matcher matcher = pattern.matcher(s);
                if (matcher.matches()) {
                    String nick = matcher.group(1);
                    String json = matcher.group(2);
                    if(ChatHandler.curseSync.containsKey(nick)) {
                        if(!ChatHandler.curseSync.get(nick).equals(json))
                        {
                            ChatHandler.curseSync.remove(nick);
                            ChatHandler.curseSync.put(nick, json);
                        }
                    } else {
                        ChatHandler.curseSync.put(nick, json);
                    }
                    Profile profile = ChatHandler.knownUsers.findByNick(nick);
                    if (profile != null) {
                        if(profile.isFriend()) {
                            profile.setOnline(true);
                        }
                        profile.setPackID(json);
                        ChatHandler.knownUsers.update(profile);
                    }
                }
            }, MineTogetherChat.ircEventExecutor);
        }
        else if(s.contains(" 401 ") && s.split(" ")[1].contains("401"))//WHOIS failure responses
        {
            CompletableFuture.runAsync(() ->
            {
                Pattern pattern = Pattern.compile(":.*401 MT.{28} (MT.{28}) :.*");
                Matcher matcher = pattern.matcher(s);
                if (matcher.matches()) {
                    String nick = matcher.group(1);
                    Profile profile = ChatHandler.knownUsers.findByNick(nick);
                    if (profile != null) {
                        if(profile.isFriend()) {
                            profile.setOnline(false);
                            ChatHandler.knownUsers.update(profile);
                        }
                    }
                }
            }, MineTogetherChat.ircEventExecutor);
        }
        else if(s.contains("QUIT") || s.contains("LEAVE") || s.contains("PART"))
        {
            Pattern pattern = Pattern.compile("\\:(MT\\w{28})!");
            Matcher matcher = pattern.matcher(s);
            if(matcher.matches())
            {
                String name = matcher.group(1);
                if (ChatHandler.privateChatList != null && ChatHandler.privateChatList.getOwner().equals(name)) {
//                    ChatHandler.host.closeGroupChat();
                }
            }
        }
        else if (s.contains(" INVITE "))
        {
            Pattern pattern = Pattern.compile("\\:(MT.{28})\\!.* INVITE (MT.{28}) \\:(#.*)");

            Matcher matcher = pattern.matcher(s);
            if(matcher.matches())
            {
                String from = matcher.group(1);
                String channel = matcher.group(3);
                PrivateChat pc = new PrivateChat(channel, from);
                ChatHandler.acceptPrivateChatInvite(pc);
            }
            else
            {
//                System.out.println(s);
            }
        }
        else
        {
//            logger.error("Unhandled IRC message!\n"+s);
        }
    }
}