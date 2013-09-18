package ar.edu.itba.pod.tp.player;

import java.rmi.RemoteException;
import java.util.List;

import ar.edu.itba.pod.tp.interfaces.Player;

public class PlayerThread implements Runnable {

	private int loops;
	private PlayerServer server;
	private List<Player> players;
	private int threadNo;
	private int maxLoops;

	public PlayerThread(PlayerServer server, List<Player> players, int loops,
			int maxLoops, int threadNo) {
		this.maxLoops = maxLoops;
		this.loops = loops;
		this.server = server;
		this.players = players;
		this.threadNo = threadNo;
	}

	@Override
	public void run() {
		int i = 0;
		while (server.getCalls() < maxLoops || server.getPlays() < maxLoops) {
			synchronized (players) {
				play();
			}
			i++;
		}
		System.out.println("Thread " + threadNo + " ended, plays = " + i);
	}

	private void checkEx(Exception e) {
		if (e.getMessage() != null && e.getMessage().startsWith("[LOSER]")) {
			System.err.println("PERDISTE GATO");
			e.printStackTrace();
			System.exit(1);
		}

	}
	
	private void play() {
		int opt = (int) (java.lang.Math.random() * players.size());
		Player other;
		if (opt > 0) {
			other = players.get(opt);
		} else {
			other = server;
		}
		try {
			server.play(
					"hola susana! te estamos llamando "
							+ server.playInc(), other);
		} catch (RemoteException e) {
			checkEx(e);
			System.err.println("******************\nClient exception: "
					+ e.toString() + "\n********************");
			players.remove(other);
		} catch (Exception e) {
			checkEx(e);
			System.err.println("Other client exception: "
					+ e.toString());
		}
	}

	public int getLoops() {
		return loops;
	}

	public void setLoops(int loops) {
		this.loops = loops;
	}
}
