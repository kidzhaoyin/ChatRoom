import java.io.BufferedReader;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;




public class Server {

	
	private static ServerSocket serverSocket;
	private int portNumber;
	final static int BLOCK_TIME = 60;
	
	
	public Server(int port) {
		this.portNumber = port;
	}


	public static void main(String[] args) throws Exception {
		
		if ( args.length != 1) {
			
			throw new Exception("usage: java Server <port_number>");
		}
		
		int portNumber = Integer.valueOf(args[0]);
		
		Server server = new Server(portNumber);
		
		HashMap<String, String> userPass = server.readUserPass();
		
		//open the server socket for listening
		server.initialize();
		
		System.out.println("initialized");
		
		//map to record who are online
		ConcurrentHashMap<String, ClientHandler> onlineCilents = new ConcurrentHashMap<String, ClientHandler>();
		//record who are blocked
		ConcurrentHashMap<String, Instant> blocked = new ConcurrentHashMap<String, Instant>();
		ConcurrentHashMap<String, Instant> last = new ConcurrentHashMap<String, Instant>();
		
		ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<String, Timer>();
		
		//listen for clients to connect
		while (true) {
			
			Socket client = null;
			try {
				client = serverSocket.accept();
				System.out.println("accepted");
			} catch (IOException e) {
				System.out.println("Unable to accept the client.");
				e.printStackTrace();
			}
		
			ClientHandler serveOne = new ClientHandler(client, userPass, onlineCilents, blocked, last, timers);
			Thread t = new Thread(serveOne);
			t.start();	
		}
	}
	
	
	//open the listening socket
	public void initialize() {
		
		System.out.println("Starting the server at port: " + portNumber);
		try {
			serverSocket = new ServerSocket(portNumber);
			System.out.println("socket opening on port: "+portNumber);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Chat room starts! ");	
		
	}
	//read the file containing username-password information
	private HashMap<String, String> readUserPass() throws IOException {
		
		HashMap<String, String> userPassList = new HashMap<String, String>();
		BufferedReader fReader = new BufferedReader(new FileReader("user_pass.txt"));
		String line = fReader.readLine();
		while (line != null) {
			String[] record = line.split(" ");
			if (!userPassList.containsKey(record[0]) && record.length > 1)
				userPassList.put(record[0], record[1]);
			line = fReader.readLine();
		}
		fReader.close();
		return userPassList;
	}
	
}


class ClientHandler implements Runnable {
	
	final static int BLOCK_TIME = 60;
	final static int MAX_WRONG = 3;
	final static int TIME_OUT = 30;
	
	private ConcurrentHashMap<String, ClientHandler> onlineClients;
	private Socket client;
	private String name;
	PrintWriter out = null;
	BufferedReader in = null;
	private Instant loginTime = null;
	private String input = "";
	private HashMap<String, String> userPasses;
	private ConcurrentHashMap<String, Instant> blocked;
	private ConcurrentHashMap<String, Instant> lastSixty;
	private ConcurrentHashMap<String, Timer> timers;
	
	ClientHandler( Socket client,
			HashMap<String, String> userPasses, 
			ConcurrentHashMap<String, ClientHandler> onlineUsers,
			ConcurrentHashMap<String, Instant> blockList,
			ConcurrentHashMap<String, Instant> lastSixty,
			ConcurrentHashMap<String, Timer> timers) throws IOException {

		this.client = client;

		this.onlineClients = onlineUsers;
		this.userPasses = userPasses;
		this.blocked = blockList;
		this.name = "dummy";
		this.lastSixty = lastSixty;
		this.timers = timers;	
	}
	
	
	public void run() {
//		BufferedReader in = null;
//		PrintWriter out = null;
		
		try {
			in = new BufferedReader(new InputStreamReader(client.getInputStream()));
			out = new PrintWriter(client.getOutputStream(), true);
		} catch (IOException e2) {
			System.out.println("cannot create reader/writer");
			e2.printStackTrace();
		}
		
		//boolean running = false;
		
		try {
			if (this.authentication(userPasses, blocked, in, out)) {
				onlineClients.put(this.name, this);
				lastSixty.put(this.name, Instant.now());
				//running = true;
			}
			else {
				out.println("logout");
				blocked.put((client.getInetAddress().getHostName()+this.name), Instant.now());
				
				client.close();
				System.out.println("closed client.");
			}
		} catch (IOException e1) {
			System.out.println("IO exception");
			e1.printStackTrace();
		}
		
		
		loginTime = Instant.now();
		
		out.println("Command: ");
		//Timeout timer = new Timeout(TIME_OUT, last, client, out, onlineClients, name);
		//Thread t = new Thread(timer);
		
		Timer timer = new Timer();
		TimerTask t = new TimeoutLogout(client, out, onlineClients, name);
		timer.schedule(t, TIME_OUT * 60000);
		timers.put(name, timer);
		
		while (true) {
	
			try {
				input = in.readLine();
				
			} catch (IOException e) {
				System.out.println("I/O exception.");
				e.printStackTrace();
			}
			//cancel the scheduled timeout once getting a responce from user
			timers.get(name).cancel();
			//restart timer
			Timer newtimer = new Timer();
			newtimer.schedule(new TimeoutLogout(client, out, onlineClients, name), TIME_OUT * 60000);
			timers.put(name, newtimer);
			
			if (input == null) continue;
			
			String[] commands = input.split(" ");
			
			//execute whoelse: display names of other users
			if (commands[0].equals("whoelse")) {
				try {
					whoElse();
				} catch (IOException e) {
					out.println("I/O exception");
					e.printStackTrace();
				}
			}
			
			//execute wholast: display the names of users who logged in during the last <seconds> seconds
			else if (commands[0].equals("wholast")) {
				if (commands.length < 2) {
					out.println("Please retry specifying the number of seconds.");
					continue;
				}
				int seconds = Integer.valueOf(commands[1]);
				whoLast(seconds);
			}
			
			else if (commands[0].equals("broadcast")) {
				if (commands.length < 2) {
					out.println("Please retry with a message");
					continue;
				}
				
				//String message = null;
				
				//execute broadcast: send the message to all users
				if (commands[1].equals("message")) {
					String message = "";
					for (int i = 2 ; i < commands.length; i++) {
						message += commands[i];
						message += " ";
					}
					broadCast(message);
				}
				//execute broadcast user <user1> <user2> ... message <message>: send message to all specified users
				else if (commands[1].equals("user")) {
					ArrayList<String> receivers = new ArrayList<String>();
					int i = 2;
					while (i < commands.length) {
						if (commands[i].equals("message")) {
							break;
						}
						else {
							receivers.add(commands[i]);
							i++;
						}
					}

					if (!commands[i].equals("message")) {
						out.println("retry with a message");
						continue;
					}
					else {
						String message = "";
						i++;
						while ( i < commands.length) {
							
							message += commands[i];
							message += " ";
							i++;
						}
						broadCastTo(message, receivers);
					}
						
				}		
			}
			
			//execute message <user> <message>: send message to the specified user
			else if (commands[0].equals("message")) {
				if (commands.length < 3) {
					out.println("please enter a receiver and a message");
					continue;
				}
				else {
					String msg = "";
					for (int i = 2; i < commands.length; i++) {
						msg += commands[i];
						msg += " ";
					}
					messageTo(commands[1], msg, out);
				}
			}
			
			//execute logout
			else if (commands[0].equals("logout")) {
				onlineClients.remove(this.name);
				//running = false;
				//logOut();
				break;
			}
			
			else
				out.println("invalid commands");
			
		}
		try{
			in.close();
			out.close();
			client.close();
		} catch (IOException e) {
			System.out.println("error closing stream or socket");
		}
		
	}
	
	public String getName() {
		return this.name;
	}
	
	public Instant getLoginTime() {
		return this.loginTime;
	}
	
	//returns the username if fails 3 tries
	public boolean authentication(HashMap<String, String> userPasses,
			ConcurrentHashMap<String, Instant> blocked,
			BufferedReader in, PrintWriter out) throws IOException
	{
		int infoWrong = 0;
		String username;
		boolean again = true;
		do {
			out.println("Username: ");
			//bw.flush();
			username = in.readLine();
			this.name = username;
			if (!userPasses.containsKey(username)) {
				out.println("Unidentified username. Please try again: ");
			}
			else if (onlineClients.containsKey(username)) {
				out.println("username already in use. Please try another.");
			}
			else {
				String info = client.getInetAddress().getHostName() + username;
				if ( blocked.containsKey(info)) {
					Duration d = Duration.between(blocked.get(info), Instant.now());
					if (d.getSeconds() > BLOCK_TIME) {
						blocked.remove(info);
						again = false;
					}
					else {
						out.println("This IP address and username is blocked.");
						return false;
					}
				}
				else
					again = false;
			}
		} while (again );
		String password;
		do {
			out.println("Please enter your password: ");
			password = in.readLine();
			if (!userPasses.get(username).equals(password)) {
				infoWrong++;
				out.println("Incorrect password. You have " + ( MAX_WRONG - infoWrong) + "tries remaining.");
			}
			else
				break;
		} while ( infoWrong > 0 && infoWrong < MAX_WRONG);
		
		if (infoWrong < MAX_WRONG) {
			onlineClients.put(username, this);
			this.name = username;
			out.println("Welcome to simple chat server!");
		}
		return infoWrong < MAX_WRONG;
	}
	

	public void whoElse() throws IOException {
		Set<String> onlineNames = onlineClients.keySet();
		out.println("There are currently " + (onlineNames.size()-1) + " other users online: ");
		for (String user : onlineNames) {
			if (!user.equals(name))
				out.println(user);	
		}
	}

	
	public void whoLast(int seconds) {
		
		for (String aName : lastSixty.keySet()) {
			if (aName.equals(this.name))
				continue;
			if (Duration.between(lastSixty.get(aName), Instant.now()).getSeconds() > 60 ) {
				lastSixty.remove(aName);
				continue;
			}
			else if (Duration.between(lastSixty.get(aName), Instant.now()).getSeconds() <= seconds) {
				out.println(aName);
			}
		
		}
	}
	
	
	public void broadCast(String msg)  {
		for (ClientHandler aClient: onlineClients.values()) {
			if (aClient.getName().equals(name))
				continue;
			try {
				send(aClient, msg);
			} catch (IOException e) {
				out.println("Error sending message.");
				e.printStackTrace();
			}
		}
	}
	
	
	public void send(ClientHandler aClient, String msg) throws IOException {
		aClient.getWriter().print(this.name + ": ");
		aClient.getWriter().println(msg);
		//bw.flush();
	}
	
	public void broadCastTo(String msg, ArrayList<String> receiverNames) {
		for (String aReceiver : receiverNames) {
			if (aReceiver.equals(name))
				continue;
			if (!onlineClients.containsKey(aReceiver)) {
				out.println("User " + aReceiver + "does not exist.");
				continue;
			}
			try {
				//onlineClients.get(aReceiver).
				send(onlineClients.get(aReceiver), msg);
			} catch (IOException e) {
				out.println("Unable to send message to " + aReceiver);
				e.printStackTrace();
			}
		}
	}
	
	public void messageTo(String receiver, String msg, PrintWriter out) {
		if (!onlineClients.containsKey(receiver)) {
			System.out.println("User " + receiver + "does not exist.");
		}
		try {
			//onlineClients.get(receiver).
			send(onlineClients.get(receiver), msg);
		} catch (IOException e) {
			System.out.println("Unable to send to user " + receiver);
			e.printStackTrace();
		}
	}
	
	public PrintWriter getWriter() {
		return this.out;
	}

}

class TimeoutLogout extends TimerTask {
	Socket client;
	PrintWriter out;
	ConcurrentHashMap<String, ClientHandler> onlineUsers;
	String user;
	
	public TimeoutLogout(Socket sock, PrintWriter writer, ConcurrentHashMap<String, ClientHandler> users,
			String username) {
		client = sock;
		out = writer;
		onlineUsers = users;
		user = username;
	}
	
	public void run() {

		onlineUsers.remove(user);
		out.println("logout");
	}
	
}



