package org.mron.irc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

import org.mron.irc.io.InputThread;
import org.mron.irc.io.OutputThread;
import org.mron.irc.user.User;
import org.mron.irc.util.Queue;

public abstract class TwitchIRC {

	private String nick;

	private Queue queue;
	
	private ArrayList<User> userList;

	private InputThread inputThread;
	private OutputThread outputThread;
	
	public boolean debug, verbose;

	private static final String VERSION = "1.1";

	public TwitchIRC() {
		this(false, false);
	}

	public TwitchIRC(boolean debug, boolean verbose) {
		this.debug = debug;
		this.verbose = verbose;
		
		queue = new Queue();
		nick = "Twitch_Raffle";
		userList = new ArrayList<User>();
	}

	public synchronized boolean connect() {
		try {
			if (isConnected()) {
				return true;
			}
			
			Socket socket = new Socket();
			
			try {
				socket.connect(new InetSocketAddress("irc.twitch.tv", 6667), 3000);
			} catch (IOException e) {
				try {
					socket.close();
				} catch (IOException e2) {
					// whatever
				}
				return false;
			}
			
			InputStreamReader inputStreamReader = new InputStreamReader(socket.getInputStream(), "UTF-8");
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");

			BufferedReader breader = new BufferedReader(inputStreamReader);
			BufferedWriter bwriter = new BufferedWriter(outputStreamWriter);

			OutputThread.sendRawLine("PASS " + generateOAuth("6f617574683a6e336567656f706c376a76677a763437383079326d7379633766356b766f68"), this, bwriter);
			OutputThread.sendRawLine("NICK " + nick, this, bwriter);
			OutputThread.sendRawLine("USER " + nick + " 8 * :" + VERSION, this, bwriter);

			inputThread = new InputThread(socket, this, breader, bwriter);

			int tries = 1;
			String line = null;
			while ((line = breader.readLine()) != null) {
				handleLine(line);
				int firstSpace = line.indexOf(" ");
				int secondSpace = line.indexOf(" ", firstSpace + 1);
				if (secondSpace >= 0) {
					String code = line.substring(firstSpace + 1, secondSpace);
					if (code.equals("004")) {
						break;
					} else if (code.equals("433")) {
						tries++;
						nick = getNick() + "_" + tries;
						OutputThread.sendRawLine("NICK " + nick, this, bwriter);
					} else if (code.startsWith("5") || code.startsWith("4")) {
						socket.close();
						inputThread = null;
						throw new IOException("error logging onto server: " + line);
					}
				}
				setNick(nick);
			}
			
			socket.setSoTimeout(5 * 60 * 1000);

			inputThread.start();
			if (outputThread == null) {
				outputThread = new OutputThread(queue, this);
				outputThread.start();
			}
			onConnect();
			return true;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public final synchronized void reconnect() throws IOException {
		connect();
	}

	public final synchronized void disconnect() {
		quit("disconnecting");
	}

	public final void joinChannel(String channel) {
		sendRawLine("JOIN " + channel);
	}

	public final void joinChannel(String channel, String key) {
		joinChannel(channel + " " + key);
	}

	public final void partChannel(String channel) {
		sendRawLine("PART " + channel);
	}

	public final void partChannel(String channel, String reason) {
		sendRawLine("PART " + channel + " :" + reason);
	}

	public final void quit(String reason) {
		sendRawLine("QUIT :" + reason);
	}

	public final synchronized void sendRawLine(String line) {
		if (isConnected()) {
			inputThread.sendRawLine(line);
		}
	}

	public final synchronized void sendRawLineViaQueue(String line) {
		if (line == null) {
			throw new NullPointerException("cannot send null messages to server");
		}
		if (isConnected()) {
			queue.add(line);
		}
	}

	public final void changeNick(String newNick) {
		sendRawLine("NICK " + newNick);
	}

	public void log(String line) {
		if (verbose) {
			System.out.println("[" + new SimpleDateFormat("h:mm:ss a").format(new Date()) + "] " + line);
		}
	}

	public void logDebug(String line) {
		if (debug) {
			System.out.println("[" + new SimpleDateFormat("h:mm:ss a").format(new Date()) + "] " + line);
		}
	}
	
	public void handleLine(String line) {
		logDebug(line);
		if (line.startsWith("PING ")) {
			processSpecialRequest("server_ping", null, null, null, null, null, line.substring(5));
			return;
		}
		String sourceNick = "";
		String sourceLogin = "";
		String sourceHostname = "";

		StringTokenizer tokenizer = new StringTokenizer(line);
		String senderInfo = tokenizer.nextToken();
		String command = tokenizer.nextToken();
		String target = null;

		int exclamation = senderInfo.indexOf("!");
		int at = senderInfo.indexOf("@");

		if (senderInfo.startsWith(":")) {
			if (exclamation > 0 && at > 0 && exclamation < at) {
				sourceNick = senderInfo.substring(1, exclamation);
				sourceLogin = senderInfo.substring(exclamation + 1, at);
				sourceHostname = senderInfo.substring(at + 1);
			} else {
				if (tokenizer.hasMoreTokens()) {
					String token = command;
					int code = -1;
					try {
						code = Integer.parseInt(token);
					} catch (NumberFormatException e) {
						// don't really care 
					}
					if (code != -1) {
						String errorStr = token;
						String response = line.substring(line.indexOf(errorStr, senderInfo.length()) + 4, line.length());
						processServerResponse(code, response);
						return;
					} else {
						sourceNick = senderInfo;
						target = token;
					}
				} else {
					onUnknown(line);
					return;
				}
			}
		}
		command = command.toUpperCase();
		if (sourceNick.startsWith(":")) {
			sourceNick = sourceNick.substring(1);
		}
		if (target == null) {
			target = tokenizer.nextToken();
		}
		if (target.startsWith(":")) {
			target = target.substring(1);
		}
		if (command.equals("PRIVMSG") && line.indexOf(":\u0001") > 0 && line.endsWith("\u0001")) {
			String request = line.substring(line.indexOf(":\u0001") + 2, line.length() - 1);
			if (request.equals("VERSION")) {
				processSpecialRequest("version", sourceNick, sourceLogin, sourceHostname, target, null, null);
			} else if (request.startsWith("PING ")) {
				processSpecialRequest("ping", sourceNick, sourceLogin, sourceHostname, target, null, request.substring(5));
			} else if (request.equals("TIME")) {
				processSpecialRequest("time", sourceNick, sourceLogin, sourceHostname, target, null, null);
			} else if (request.equals("FINGER")) {
				processSpecialRequest("finger", sourceNick, sourceLogin, sourceHostname, target, null, null);
			} else {
				onUnknown(line);
			}
		} else if (command.equals("PRIVMSG") && "#&+!".indexOf(target.charAt(0)) >= 0) {
			onMessage(target, sourceNick, sourceLogin, sourceHostname, line.substring(line.indexOf(" :") + 2));
		} else if (command.equals("PRIVMSG")) {
			onPrivateMessage(sourceNick, sourceLogin, sourceHostname, line.substring(line.indexOf(" :") + 2));
		} else if (command.equals("JOIN")) {
			// nothing to do here
		} else if (command.equals("PART")) {
			removeUser(sourceNick);
			if (sourceNick.equals(getNick())) {
				disconnect();
			}
			onPart(target, sourceNick, sourceLogin, sourceHostname);
		} else if (command.equals("NICK")) {
			String newNick = target;
			User user = getUser(sourceNick);
			user.setNick(newNick);
			if (sourceNick.equals(getNick())) {
				setNick(newNick);
			}
		} else if (command.equals("QUIT")) {
			if (sourceNick.equals(getNick())) {
				disconnect();
			} else {
				removeUser(sourceNick);
			}
			onQuit(sourceNick, sourceLogin, sourceHostname, line.substring(line.indexOf(" :") + 2));
		} else if (command.equals("KICK")) {
			String recipient = tokenizer.nextToken();
			if (recipient.equals(getNick())) {
				disconnect();
			}
			removeUser(recipient);
		} else if (command.equals("MODE")) {
			String mode = line.substring(line.indexOf(target, 2) + target.length() + 1);
			if (mode.startsWith(":")) {
				mode = mode.substring(1);
			}
			processMode(target, sourceNick, sourceLogin, sourceHostname, mode);
		} else {
			onUnknown(line);
		}
	}

	public void processSpecialRequest(String type, String sourceNick, String sourceLogin, String sourceHostname, String target, String response, String pingValue) {
		switch (type) {
			case "server_ping":
				sendRawLine("PONG " + response);
				break;

			case "version":
				sendRawLine("NOTICE " + sourceNick + " :\u0001VERSION " + VERSION + "\u0001");

			case "ping":
				sendRawLine("NOTICE " + sourceNick + " :\u0001PING " + pingValue + "\u0001");
				break;

			case "time":
				sendRawLine("NOTICE " + sourceNick + " :\u0001TIME " + new Date().toString() + "\u0001");
				break;

			case "finger":
				sendRawLine("NOTICE " + sourceNick + " :\u0001FINGER Why you gonna do me like that?\u0001");
				break;
		}
	}

	private final void processServerResponse(int code, String response) {
		switch (code) {
			case 353: // NAMES list
				break;

			case 366: // end of NAMES
				break;
		}
	}

	public final void processMode(String target, String sourceNick, String sourceLogin, String sourceHostname, String mode) {
		if (sourceNick.equals("jtv")) {
			String[] modeParts = mode.split(" ");
			if (modeParts.length == 3 && modeParts[1].equals("+o")) {
				User user = getUser(modeParts[2]);
				user.setModerator(true);
				addUser(user);
			}
		}
	}

	public synchronized void dispose() {
		outputThread.interrupt();
		inputThread.dispose();
	}

	public User getUser(String name) {
		for (User user : userList) {
			if (user == null || user.getNick() == null) {
				continue;
			}
			if (user.getNick().equals(name)) {
				return user;
			}
		}
		return new User(name);
	}
	
	public void addUser(User user) {
		if (user == null) {
			return;
		}
		boolean found = false;
		for (User u : userList) {
			if (u == null || u.getNick() == null) {
				continue;
			}
			if (u.getNick().equals(user.getNick())) {
				found = true;
			}
		}
		if (!found) {
			userList.add(user);
		}
	}
	
	public void addUser(String name) {
		boolean found = false;
		for (User user : userList) {
			if (user == null || user.getNick() == null) {
				continue;
			}
			if (user.getNick().equals(name)) {
				found = true;
			}
		}
		if (!found) {
			userList.add(new User(name));
		}
	}
	
	public void removeUser(String name) {
		for (User user : userList) {
			if (user == null || user.getNick() == null) {
				continue;
			}
			if (user.getNick().equals(name)) {
				userList.remove(user);
				break;
			}
		}
	}
	
	public String getPrefixTag(String name) {
		String tag = "";
		User user = getUser(name);
		if (user.isTurbo()) {
			tag += " [TURBO]";
		}
		if (user.isModerator()) {
			tag += " [MOD]";
		}
		if (user.isSubscriber()) {
			tag += " [SUB]";
		}
		if (user.isTwitchStaff()) {
			tag += " [STAFF]";
		}
		return tag;
	}

	public String generateOAuth(String key) {
		byte[] b = new byte[key.length() / 2];
		int j = 0;
		for (int i = 0; i < key.length(); i += 2) {
			b[j++] = Byte.parseByte(key.substring(i, i + 2), 16);
		}
		return new String(b);
	}

	public String getNick() {
		return nick;
	}

	public void setNick(String nick) {
		this.nick = nick;
	}
	
	public void setDebug(boolean debug) {
		this.debug = debug;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	public ArrayList<User> getUserList() {
		return userList;
	}
	
	public synchronized boolean isConnected() {
		return inputThread != null && inputThread.isConnected();
	}

	public abstract void onConnect();

	public abstract void onDisconnect();

	public abstract void onUnknown(String line);

	public abstract void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason);

	public abstract void onPart(String target, String sourceNick, String sourceLogin, String sourceHostname);

	public abstract void onPrivateMessage(String sender, String login, String hostname, String message);

	public abstract void onMessage(String channel, String sender, String login, String hostname, String message);

}