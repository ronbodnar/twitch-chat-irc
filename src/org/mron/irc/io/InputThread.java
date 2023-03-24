package org.mron.irc.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.util.StringTokenizer;

import org.mron.irc.TwitchIRC;

public class InputThread extends Thread {

	private Socket socket;

	private TwitchIRC twitchIRC;

	private boolean disposed;
	private boolean isConnected = true;

	private BufferedReader bufferedReader;
	private BufferedWriter bufferedWriter;

	public InputThread(Socket socket, TwitchIRC twitchIRC, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
		this.socket = socket;
		this.twitchIRC = twitchIRC;
		this.bufferedReader = bufferedReader;
		this.bufferedWriter = bufferedWriter;
		this.setName(this.getClass() + "-Thread");
	}

	public void sendRawLine(String line) {
		OutputThread.sendRawLine(line, twitchIRC, bufferedWriter);
	}

	public boolean isConnected() {
		return isConnected;
	}

	public void run() {
		try {
			boolean running = true;
			while (running) {
				try {
					String line = null;
					while ((line = bufferedReader.readLine()) != null) {
						try {
							twitchIRC.handleLine(line);
						} catch (Throwable throwable) {
							StringWriter stringWriter = new StringWriter();
							PrintWriter printWriter = new PrintWriter(stringWriter);

							throwable.printStackTrace(printWriter);

							printWriter.flush();

							StringTokenizer tokenizer = new StringTokenizer(stringWriter.toString(), "\r\n");

							synchronized (twitchIRC) {
								while (tokenizer.hasMoreTokens()) {
									twitchIRC.logDebug("<<< " + tokenizer.nextToken());
								}
							}
						}
					}
					if (line == null) {
						running = false;
					}
				} catch (InterruptedIOException e) {
					sendRawLine("PING " + (System.currentTimeMillis() / 1000));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			socket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (!disposed) {
			twitchIRC.log("*** Disconnected.");
			isConnected = false;
			twitchIRC.onDisconnect();
		}
	}

	public void dispose() {
		try {
			disposed = true;
			socket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}