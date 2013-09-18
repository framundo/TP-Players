package ar.edu.itba.pod.tp.player;

import java.rmi.RemoteException;
import java.util.List;

import ar.edu.itba.pod.tp.interfaces.Player;
import ar.edu.itba.pod.tp.interfaces.PlayerDownException;

public class PlayerJob implements Runnable {
	
	private List<Player> players;
	private PlayerServer server;
	private int loops;
	
	public PlayerJob(PlayerServer server, List<Player> players, int loops) {
		this.server = server;
		this.players = players;
		this.loops = loops;
	}
	
	@Override
	public void run() {
		System.out.println("THREAD(" + Thread.currentThread().getId()+ "):" + loops + "loops");
		
		int plays = 0;
		do {
			int opt = (int) (java.lang.Math.random() * players.size());
			try {
				Player other = players.get(opt);
				if (other != null) {
					server.play("hola! estamos jugando " + plays, other);
				}
			}
			catch (PlayerDownException e) {
				players.remove(opt);
			} catch (RemoteException | InterruptedException e) {
				e.printStackTrace();
				System.err.println("Client exception: " + e.toString());			
				System.err.println("PERDI!");
				System.exit(1);
			}
		} while (++plays < loops);
	}

}
