package org.mron.irc.util;

import java.util.Vector;

public class Queue {

	private Vector<Object> queue;

	public Queue() {
		queue = new Vector<Object>();
	}

	public void add(Object o) {
		synchronized (queue) {
			queue.addElement(o);
			queue.notify();
		}
	}

	public void addFront(Object o) {
		synchronized (queue) {
			queue.insertElementAt(o, 0);
			queue.notify();
		}
	}

	public Object next() {
		Object o = null;
		synchronized (queue) {
			if (queue.size() == 0) {
				try {
					queue.wait();
				} catch (InterruptedException e) {
					return null;
				}
			}
			try {
				o = queue.firstElement();
				queue.removeElementAt(0);
			} catch (ArrayIndexOutOfBoundsException e) {
				throw new InternalError("race hazard in queue");
			}
		}
		return o;
	}

	public boolean hasNext() {
		return (this.size() != 0);
	}

	public void clear() {
		synchronized (queue) {
			queue.removeAllElements();
		}
	}

	public int size() {
		return queue.size();
	}

}