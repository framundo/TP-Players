package ar.edu.itba.pod.tp.player;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import ar.edu.itba.pod.tp.interfaces.Master;
import ar.edu.itba.pod.tp.interfaces.Player;
import ar.edu.itba.pod.tp.interfaces.PlayerDownException;
import ar.edu.itba.pod.tp.interfaces.Referee;

public class PlayerApp implements Runnable {
	private String host, name;
	private Integer port, gameIn;
	private PlayerServer server;

	public PlayerApp(String host, String port, String name, int gameIn) {
		this.host = host == null ? HOST_D : host;
		this.port = Integer.valueOf(port == null ? PORT_D : port);
		this.name = name;
		this.gameIn = gameIn;
	}
	
//	public PlayerApp(String[] args) {
//		CommandLine cmdLine;
//		try {
//			cmdLine = parseArguments(args);
//			port = Integer
//				.valueOf(cmdLine.getOptionValue(PORT_L, PORT_D));
//		host = cmdLine.getOptionValue(HOST_L, HOST_D);
//		name = cmdLine.getOptionValue(NAME_L);
//		gameIn = Integer
//				.valueOf(cmdLine.getOptionValue(GAME_IN));
//		if (cmdLine.hasOption("help")) {
//			HelpFormatter formatter = new HelpFormatter();
//			formatter.printHelp("player", options);
////			System.exit(0);
//		}	
//		} catch (ParseException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
	
	public void run() {
		try {
			System.out.println("Registering player " + name + " on referee: "
					+ host + ":" + port + " game " + gameIn);
			Registry registry = LocateRegistry.getRegistry(host, port);
//			String owner = name.substring(0, name.indexOf(':'));
			String host = name.substring(name.indexOf('/')+1);
			System.out.println("Looking up referee " + host + "...");
			Master master = (Master) registry.lookup("master");
			Referee referee = (Referee) master.lookup("referees/" + host);

			// create the player server
			server = new PlayerServer(name);
			System.out.println("Player ready to play");
			
			server.init(referee);
			
			List<Player> players = server.getOpponents();
			int plays = 0;
			int loop = server.total;
			System.out.println("Let's play! " + loop + " loops");
			do {
				int opt = (int) (java.lang.Math.random() * players.size());
				try {
					Player other = players.get(opt);
					if (other != null) {
						server.play("Let's roll!" + plays, other);
					}
				} catch (PlayerDownException e) {
					players.remove(opt);
				}
			} while (++plays < loop);

			System.out.println("WON!");
//			System.exit(0);
		} catch (Exception e) {
//			e.printStackTrace();
			System.err.println("Client exception: " + e.toString());
			System.err.println("LOST! :(");
//			System.exit(1);
		}
	}

	private static CommandLine parseArguments(String[] args)
			throws ParseException {
		CommandLineParser parser = new BasicParser();
		try {
			// parse the command line arguments
			return parser.parse(options, args, false);
		} catch (ParseException exp) {
			// oops, something went wrong
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
			throw exp;
		}
	}

	private static Options createOptions() {
		final Options result = new Options();
		@SuppressWarnings("static-access")
		final Option name = OptionBuilder.withArgName(NAME_S)
				.withLongOpt(NAME_L).hasArg().withDescription("Player name")
				.isRequired(true).create(NAME_S);
		result.addOption(name);
		result.addOption(HOST_S, HOST_L, true, "Referee server host");
		result.addOption(PORT_S, PORT_L, true, "Referee server port");
		result.addOption("help", false, "Help");
		return result;
	}

	public Player getPlayer() {
		return server;
	}
	
	private static final String PORT_L = "port";
	private static final String PORT_S = "p";
	private static final String PORT_D = "7242";
	private static final String HOST_L = "host";
	private static final String HOST_S = "h";
	private static final String HOST_D = "localhost";
	private static final String NAME_L = "name";
	private static final String NAME_S = "n";
	private static final String GAME_IN = "g";
	private static Options options = createOptions();
}