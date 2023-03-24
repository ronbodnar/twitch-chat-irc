package org.mron.irc;

import java.util.Scanner;

import org.mron.irc.user.User;

public class Main extends TwitchIRC {

	private Scanner scanner;

	public static void main(String[] arguments) {
		new Main(arguments);
	}

	public Main(String[] arguments) {
		if (arguments.length > 0) {
			for (String argument : arguments) {
				if (argument.equals("--debug")) {
					debug = true;
				} else if (argument.equals("--verbose")) {
					verbose = true;
				}
			}
		}
		scanner = new Scanner(System.in);
		
		connect();
		sendRawLine("TWITCHCLIENT 3");

		System.out.print("Enter a channel to join: ");
		joinChannel("#" + scanner.nextLine());
	}

	public void processSpecialMessage(String message) {
		String[] split = message.split(" ");
		if (split[0].equals("SPECIALUSER") && split.length == 3) {
			String name = split[1];
			String status = split[2];
			User user = getUser(name);
			switch (status) {
				case "admin":
				case "staff":
					user.setTwitchStaff(true);
					break;

				case "turbo":
					user.setTurbo(true);
					break;

				case "subscriber":
					user.setSubscriber(true);
					break;

				default:
					log("STATUS NOT SUPPORTED: " + status);
					break;
			}
			addUser(user);
		}
	}

	@Override
	public void onMessage(String channel, String sender, String login, String hostname, String message) {
		if (sender.equals("jtv")) {
			processSpecialMessage(message);
			return;
		}
		if (sender.equalsIgnoreCase("twitchnotify") && message.contains("subscribed!")) {
			String subscriberName = message.split(" ")[0];
			
			log(" ");
			log("New subscriber detected: " + subscriberName);
			log(" ");

			User user = getUser(subscriberName);
			user.setSubscriber(true);
			addUser(user);
			return;
		}
		addUser(sender);
		log("[" + channel + "]" + getPrefixTag(sender) + " " + sender + ": " + message);
	}

	@Override
	public void onPrivateMessage(String sender, String login, String hostname, String message) {
		if (sender.equals("jtv")) {
			processSpecialMessage(message);
			return;
		}
		if (sender.equalsIgnoreCase("twitchnotify") && message.contains("subscribed!")) {
			String subscriberName = message.split(" ")[0];
			
			log(" ");
			log("New subscriber detected: " + subscriberName);
			log(" ");

			User user = getUser(subscriberName);
			user.setSubscriber(true);
			addUser(user);
			return;
		}
		addUser(new User(sender));
		log("[PRIV MSG] " + getPrefixTag(sender) + " " + sender + ": " + message);
	}

	@Override
	public void onConnect() {
		// TODO Auto-generated method stub
	}

	@Override
	public void onDisconnect() {
		// TODO Auto-generated method stub
	}

	@Override
	public void onUnknown(String line) {
		log("[UNKNOWN] " + line);
	}

	@Override
	public void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onPart(String target, String sourceNick, String sourceLogin, String sourceHostname) {
		// TODO Auto-generated method stub
	}

}