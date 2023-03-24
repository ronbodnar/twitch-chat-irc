package org.mron.irc.io;

import java.io.BufferedWriter;

import org.mron.irc.TwitchIRC;
import org.mron.irc.util.Queue;

public class OutputThread extends Thread {

	private Queue queue;

	private TwitchIRC twitchIRC;

	public OutputThread(Queue queue, TwitchIRC twitchIRC) {
		this.queue = queue;
		this.twitchIRC = twitchIRC;
		this.setName(this.getClass() + "-Thread");
	}

	public static void sendRawLine(String line, TwitchIRC twitchIRC, BufferedWriter bufferedWriter) {
		if (line.length() > 510) {
			line = line.substring(0, 510);
		}
		synchronized (bufferedWriter) {
			try {
				bufferedWriter.write(line + "\r\n");
				bufferedWriter.flush();
				twitchIRC.logDebug(">>>" + line);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void run() {
		try {
			boolean running = true;
			while (running) {
				Thread.sleep(1000); // precaution for preventing spamming the server
				String line = (String) queue.next();
				if (line != null) {
					twitchIRC.sendRawLine(line);
				} else {
					running = false;
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}