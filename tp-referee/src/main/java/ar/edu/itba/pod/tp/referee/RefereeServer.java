package ar.edu.itba.pod.tp.referee;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import ar.edu.itba.pod.tp.interfaces.GameResult;
import ar.edu.itba.pod.tp.interfaces.GameResult.PlayerResult;
import ar.edu.itba.pod.tp.interfaces.GameResult.Status;
import ar.edu.itba.pod.tp.interfaces.Master;
import ar.edu.itba.pod.tp.interfaces.Player;
import ar.edu.itba.pod.tp.interfaces.PlayerLoserException;
import ar.edu.itba.pod.tp.interfaces.Referee;
import ar.edu.itba.pod.tp.interfaces.Registration;
import ar.edu.itba.pod.tp.interfaces.Request;
import ar.edu.itba.pod.tp.interfaces.Response;
import ar.edu.itba.pod.tp.interfaces.Utils;
import ar.edu.itba.pod.tp.player.PlayerApp;

public class RefereeServer implements Referee {

	public static final String ENDPOINT = "endpoint:";

	final List<Player> playerServers = new ArrayList<Player>();
	final Map<Player, Registration> registrations = new HashMap<Player, Registration>();
	final Map<Player, Registration> initialRegistrations = new HashMap<Player, Registration>();
	final List<Registration> winners = new ArrayList<Registration>();
	final Random random = new Random();
	final Map<Integer, List<Request>> requests = new HashMap<Integer, List<Request>>();
	boolean playing;
	final int requestsTotal;
	final Map<String, List<Player>> players = new HashMap<String, List<Player>>();
	final String name;
	final Registry registry;
	final AtomicInteger idSeq = new AtomicInteger();
	final String host, port;

	public RefereeServer(String name, int requestsTotal, Registry registry, String host, String port)
	{
		this.name = name;
		this.requestsTotal = requestsTotal;
		this.registry = registry;
		this.host = host;
		this.port = port;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public Registration newPlayer(String playerName, Player playerClient) throws RemoteException
	{
//		if (playing) {
//			throw new RemoteException("Already playing!");
//		}
		String playerId = buildPlayerName(playerName, playerClient);
		System.out.println("new player: " + playerId);
		Registration result = register(playerId, playerClient);
		return result;
	}

	@Override
	public synchronized void registerRequest(Player player, Request request) throws RemoteException
	{
		Registration clientReg = findRegistration(player);
//		System.out.println("REQ: " + clientReg.name + " - " + request);
		if (clientReg.id != request.playerId) {
			throw kickOutPlayer(player, "PLAYER SEQ FAILED!!!");
		}
		if (clientReg.clientSeq != request.clientSeq) {
			throw kickOutPlayer(player, "PLAYER OP SEQ FAILED!!! " + clientReg.clientSeq + "/" + request.clientSeq);
		}
		String check = hashMessage(clientReg, request.clientSeq, request.message);
		if (!check.equals(request.hash)) {
			throw kickOutPlayer(player, "Hash failed!!!");
		}
		clientReg.clientSeq++;
		clientReg.clientCount++;

		List<Request> playerRequests = requests.get(clientReg.id);
		playerRequests.add(request);

		if (clientReg.clientCount >= requestsTotal && clientReg.serverCount >= requestsTotal) {
			if (!winners.contains(clientReg)) {
				winners.add(clientReg);
			}
		}
	}

	@Override
	public synchronized void registerResponse(Player player, Response response) throws RemoteException
	{
		Registration clientReg = findRegistration(player);
//		System.out.println("RES: " + clientReg.name + " - " + response);
		if (clientReg.id != response.rspPlayerId) {
			throw kickOutPlayer(player, "PLAYER SEQ FAILED!!!");
		}
		// si habilitamos este check empieza a fallar enseguida
		//            if (clientReg.serverSeq != response.rspServerSeq) {
		//                    throw new RemoteException("Fallo el SERVER OP SEQ!!!" + clientReg.serverSeq + "/" + response.rspServerSeq);
		//            }
		String check = hashMessage(clientReg, response.rspServerSeq, response.rspMessage);
		if (!check.equals(response.rspHash)) {
			throw kickOutPlayer(player, "Hash failed!!!");
		}
		clientReg.serverSeq++;
		clientReg.serverCount++;


		List<Request> clientRequests = requests.get(response.reqPlayerId);
		if (!clientRequests.contains(response.toRequest())) {
			throw kickOutPlayer(player, "NO OP!!!");
		}

		if (clientReg.clientCount >= requestsTotal && clientReg.serverCount >= requestsTotal) {
			if (!winners.contains(clientReg)) {
				winners.add(clientReg);
			}
		}
	}

	@Override
	public Map<Player, GameResult.PlayerResult> showResults() throws RemoteException
	{
		Map<Player, GameResult.PlayerResult> map = new HashMap<Player, GameResult.PlayerResult>();

		for (Map.Entry<Player, Registration> e : initialRegistrations
				.entrySet()) {
			if (!map.containsKey(e.getKey())) {
				map.put(e.getKey(), new GameResult.PlayerResult(e.getValue().name, Status.SUCCESS, 0, 0));
			}
			PlayerResult p = map.get(e.getKey());
			p.serverCount += e.getValue().serverCount;
			p.playerCount += e.getValue().clientCount;
		}
		return map;
	}

	private Registration register(String playerName, Player playerClient)
	{

		final String salt = UUID.randomUUID().toString();
		final int seq = random.nextInt();
		final int clientSeq = random.nextInt();
		final int serverSeq = random.nextInt();

		Registration result = new Registration(playerName, seq, clientSeq, serverSeq, salt, playerServers, requestsTotal);

		synchronized (registrations) {
			playerServers.add(playerClient);
			registrations.put(playerClient, result);
			initialRegistrations.put(playerClient, result);
			requests.put(result.id, new ArrayList<Request>());
		}

		synchronized (this) {
			return result;
		}
	}

	private String hashMessage(Registration registration, int opSeq, String message)
	{
		return Utils.hashMessage(registration.id, opSeq, message, registration.salt);
	}

	private RemoteException kickOutPlayer(Player playerClient, String message) throws RemoteException
	{
		synchronized (registrations) {
			registrations.remove(playerClient);
		}
		return new PlayerLoserException("[LOSER] "+ message + "\n" + showResults());
	}

	private Registration findRegistration(Player player) throws RemoteException
	{
		Registration clientReg = registrations.get(player);
		if (clientReg == null) {
			clientReg = initialRegistrations.get(player);
			if (clientReg != null) {
				throw new RemoteException("You lost already " + clientReg.name);
			} else {
				throw new RemoteException("Don't know you!");
			}
		}
		return clientReg;
	}

	private String buildPlayerName(String playerName, Player playerClient)
	{
		String tmp = playerClient.toString();
		int i = tmp.indexOf(ENDPOINT);
		tmp = tmp.substring(i + ENDPOINT.length());
		int j = tmp.indexOf("]") + 1;
		tmp = tmp.substring(0, j);
		String playerId = playerName + "-" + tmp;
		return playerId;
	}

	@Override
	public GameResult hostGame(int gameIn, String gameHash, List<String> guests) throws RemoteException
	{
		System.out.println("hostGame " + gameHash);
		for (String guest : guests) {
			System.out.println("Looking for referee guest " + guest);
			Referee guestReferee;
			try {
				guestReferee = lookupReferee(guest);

				guestReferee.joinGame(gameIn, gameHash, getName());
			}
			catch (Exception ex) {
				Logger.getLogger(RefereeServer.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
//		try {
//			//                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//			Thread.sleep(20);
//		}
//		catch (InterruptedException ex) {
//			Logger.getLogger(RefereeServer.class.getName()).log(Level.SEVERE, null, ex);
//		}
		GameResult gameResult = new GameResult();
		for (Map.Entry<Player, PlayerResult> e : showResults().entrySet()) {
			PlayerResult pr = e.getValue();
			System.out.println("Results of " + pr.player);
			gameResult.addPlayerResult(pr);
		}
		return gameResult;
	}

	@Override
	public void joinGame(int gameIn, String gameHash, String host) throws RemoteException
	{
		String name = this.name + ":" + idSeq.getAndIncrement() + "/" + host;
		Player player = createPlayer(name, gameIn, gameHash);
		try {
			Referee otherReferee = lookupReferee(host);
			System.out.println("Referee found: " + otherReferee.getName());
			otherReferee.newPlayer(name, player);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	Player createPlayer(String name, Integer gameIn, String gameHash) {
		System.out.println("Creating new player...");
		PlayerApp player = new PlayerApp(host, port, name, gameIn);
		player.run();
		Player p;
		do {
			p = player.getPlayer();
		} while (p == null);
		System.out.println("Created player: " + p);
		return p;
	}
	
	private Referee lookupReferee(String name) throws AccessException, RemoteException, NotBoundException {
		Master master = (Master) this.registry.lookup("master");
		return (Referee) master.lookup("referees/" + name);
	}

}
