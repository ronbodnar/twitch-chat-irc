package org.mron.irc.user;

public class User {
	
	private String nick;

	private boolean isTurbo;
	private boolean isModerator;
	private boolean isSubscriber;
	private boolean isTwitchStaff;

	public User(String nick) {
		this.nick = nick;
	}

	public String getNick() {
		return nick;
	}
	
	public void setNick(String nick) {
		this.nick = nick;
	}

	public boolean isTurbo() {
		return isTurbo;
	}

	public void setTurbo(boolean isTurbo) {
		this.isTurbo = isTurbo;
	}

	public boolean isModerator() {
		return isModerator;
	}

	public void setModerator(boolean isModerator) {
		this.isModerator = isModerator;
	}

	public boolean isSubscriber() {
		return isSubscriber;
	}

	public void setSubscriber(boolean isSubscriber) {
		this.isSubscriber = isSubscriber;
	}

	public boolean isTwitchStaff() {
		return isTwitchStaff;
	}

	public void setTwitchStaff(boolean isTwitchStaff) {
		this.isTwitchStaff = isTwitchStaff;
	}

	@Override
	public String toString() {
		return "[USER] " + getNick() + ": turbo? " + isTurbo() + ", moderator? " + isModerator() + ", subscriber?" + isSubscriber() + ", twitch staff? " + isTwitchStaff();
	}
}