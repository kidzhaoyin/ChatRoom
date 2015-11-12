import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Client {


	private static String serverIP;
	private static int port;
	
	public Client(String ip, int port) {
		this.serverIP = ip;
		this.port = port;
	}
	
	public static void main(String[] args) {
		
		if (args.length != 2) {
			System.out.println(args.length);
			System.err.println("Usage: Client <Server_IP> <port_number>");
			System.exit(1);
		}
		
		int port = Integer.valueOf(args[1]);
		System.out.println(port);
		
		
		Socket socket = null;
		
		try {
			socket = new Socket(args[0], port);
		} catch (UnknownHostException e) {
			System.out.println("Unknow host: " + args[0]);
			e.printStackTrace();
			System.exit(1);
		} catch (IOException e) {
			System.out.println("Socket Closed");
			//e.printStackTrace();
		}
		if (socket.isConnected()) {
		ListeningClient lc = new ListeningClient(socket);
		Thread lt = new Thread(lc);
		lt.start();
		SendingClient sc = new SendingClient(socket);
		Thread st = new Thread(sc);
		st.start();	
		}

	}
	
}

class ListeningClient implements Runnable {
	private Socket sock;
	
	public ListeningClient(Socket socket) {
			this.sock = socket;
		System.out.println("listening");
	}
	
	
	public void run() {
		BufferedReader br = null;
		String line;
		boolean running = true;
		if (sock.isClosed())
			return;
			try{
				br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			} catch (IOException e) {
				System.out.println("IOException");
			}
			while (running) {
				try {

					line = br.readLine();
					if (line == null)
						break;
					System.out.println(line);
					if (line.equals("logout")) {
						break;
					}
				
				} catch (IOException e) {
					running = false;
				}
			
			}
			try {
				br.close();
			} catch (IOException e) {
				System.out.println("unable to close writer.");
				e.printStackTrace();
			}
		
		
	}
}

class SendingClient implements Runnable {
	
	private Socket sock;
	
	public SendingClient(Socket socket) {
		this.sock = socket;
		
		System.out.println("sending client");
	}
	public void run() {
		boolean running = true;
		PrintWriter pw = null;
		//BufferedReader in = null;
		Scanner in = new Scanner(System.in);
		try{
			//in = new BufferedReader(new InputStreamReader(System.in));
			pw = new PrintWriter(sock.getOutputStream());
			String userInput;
			while (running) {
				userInput = in.nextLine();
				if (userInput != null) {
					pw.println(userInput);
					pw.flush();
					if (userInput.equals("logout")) {
						running = false;
						break;
					}
				}
			}
			
		} catch (IOException e) {
			System.out.println("IOException");
		}
		in.close();
		pw.close();
//		try {
//			sock.close();
//		} catch (IOException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
	
}