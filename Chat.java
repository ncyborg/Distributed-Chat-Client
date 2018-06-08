import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.net.ConnectException;
// for Socket 
import java.net.Socket;
import java.util.*;

public class Chat {
	private Socket socket; // a socket connection to a chat server
	private InputStream rawIn; // an input stream from the server
	private DataInputStream in; // a filtered input stream from the server
	private DataOutputStream out; // a filtered output stream to the server
	private BufferedReader stdin; // the standart input

	/*
	 * Chat Class:
	 * 
	 * A class which does not require a chat server Each chat client continuously
	 * does the following 1. if the user wants to send someting, send to all clients
	 * 2. if
	 * 
	 */
	private static int NUMHOSTS; //represents number of hosts in cluster
	
	private static ObjectInputStream[] inputs; // used to hold the input streams of all other nodes
	private static ObjectOutputStream[] outputs; // used to hold the output streams of all other nodes
	private static String[] hosts;
	private static int rank = 0;
	private static DataInputStream[] indata;

	// for establishing connections
	
	//hashtable which holds an associated socket and an int representing a rank
	//hashtable represents all successful socket connections
	private static Hashtable<Socket, Integer> acceptedSockets = new Hashtable<Socket, Integer>(); 
	
	//hashtable which holds an associated socket and an int representing a rank 
	//used to hold all socket connections that were not successful 
	private static Hashtable<String, Integer> needToConnectList = new Hashtable<String, Integer>();//temp list which holds list of sockets that were not able to be connected to
	
	private static int[] localVector; //vector clocked used to pass from node to node everytime a message is sent 
	
	private static Hashtable<int[], String> waitingList = new Hashtable<int[], String>(); //waiting list for vectors out of order
	
	
	public static void main(String[] args) throws IOException {

		// verify the arguments
		NUMHOSTS = args.length - 1; //number of hosts you put in
		
		int port = Integer.parseInt(args[0]);
		String localHost = InetAddress.getLocalHost().getHostName();
		rank = getRank(args, localHost);

		// Print out server info
		System.out.println("port = " + port + ", rank = " + rank + ", localhost = " + localHost);

		// take in all other outputs and record their output and inputs in the arrays

		//initialize all vectors based on number of hosts
		outputs = new ObjectOutputStream[NUMHOSTS];
		inputs = new ObjectInputStream[NUMHOSTS];
		hosts = new String[NUMHOSTS];
		indata = new DataInputStream[NUMHOSTS];
		localVector = new int[NUMHOSTS]; 

		establishConnectionToHosts(args, localHost);
		
		fillHostList(args); //fill host[] with all hosts given in String[] args
		
		System.out.println("All sockets have connected");
		//System.out.println("Filling outputs, inputs, hosts and indata arrays");
		
		Set<Socket> keyset = acceptedSockets.keySet(); //need keys to interate through
		
		for (Socket sock: keyset) {

			int rank = acceptedSockets.get(sock); //rank of the current socket
			
			ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream());
			ObjectInputStream ois = new ObjectInputStream(sock.getInputStream());
			
			inputs[rank] = ois; // setting input stream to current sockets objectinputstream for future use
			outputs[rank] = oos; // setting outputs to current sockets objectoutputstream for future use

			// Used to get the datainputstream from the socket
			// saves into indata for future use
			InputStream rawIn = sock.getInputStream();
			DataInputStream in = new DataInputStream(rawIn);
			indata[rank] = in;

		}

		chatMode();

	}

	
	/*
	 * Utility function to fill host lists
	 */
	public static void fillHostList(String[] args) {
		
		for (int i = 1; i < args.length; i++) {
			
			hosts[i-1] = args[i];
			
		}
		
	}
	
	/*
	 * Purpose is to establish connection to other hosts This is done by first
	 * testing if each host other than itself is online if they are then establish
	 * the socket connection if not then add to a list to connect to later Once you
	 * have established a connection to all hosts that are currently online
	 * continually listen for n conections, where n is the number of hosts needed.
	 * When a socket connects after you have listened you must remove the matching
	 * socket from the list
	 */
	public static void establishConnectionToHosts(String[] args, String curHost) throws IOException {
		
		int port = Integer.parseInt(args[0]); //get port
		
		//Attempt to connect to all hosts currently online
		for (int i = 1; i < args.length; i++) {
			
			String address = args[i];
			
			int rank = i - 1; // i - 1 because rank should start at 0
			
			//make sure the address isn't the currentHost
			if (!curHost.equals(address) ) {
				
				try {
					
					Socket sock = new Socket(address, port);
				
					//socket connection success 
					System.out.println("connected to " + address);
				
					//add to successful socket connection hashTable
					acceptedSockets.put(sock, rank);
					
				}catch(ConnectException ce) {
				
					//socket connection not successful
					//System.out.println(address + " not online");
				
					//add this socket to the needToConnect Hashtable to connect to later
					needToConnectList.put(address + ".uwb.edu", rank); 
					
				}
			}
		}
		
		// this next part is responsible for continuously listening on the network for connections
		// when there is a connection then you need to accept the connect, determine who the host is, 
		// if the host is one that you need to connect to
		// 		add socket to successful socket list, remove address from need to connectList
		// else 
		// 		ignore connection
		// stop listening when the needToConnectList is empty
		
		ServerSocket socket = new ServerSocket(port); //used for listening 
		
		while (needToConnectList.size() > 0) {
			
			Socket sock = socket.accept(); //waits for a connection
			
			String sockHostName = sock.getInetAddress().getHostName();

			if (needToConnectList.containsKey(sockHostName)) {
				
				System.out.println("accepted from " + sockHostName);
				
				int rank = needToConnectList.get(sockHostName); //gets rank
				needToConnectList.remove(sockHostName); //removes from needtoconnectlist as we just connected
						
				acceptedSockets.put(sock, rank); //socket was accepted so add to this list 
				
			}
			
		}
		
	}

	
	
	/*
	 * Returns a int rank based on order of localHost name in arg list Retuns -1 if
	 * not in arg list
	 */
	public static int getRank(String[] args, String localHost) {

		for (int i = 1; i < args.length; i++) {
			//System.out.println(args[i]);
			if (args[i].equals(localHost)) {

				return i - 1; // - 1 because we want rank to be 0 to #machines

			}

		}

		return -1;

	}

	public static void chatMode() throws IOException {
		
		System.out.println("In chat Mode");
		
		// now goes into a chat

		BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
		
		while (true) {

			// read a message from keyboard and broadcast it to all the others.

			if (keyboard.ready()) {
				
				// since keyboard is ready, read one line.

				String message = keyboard.readLine();

				if (message == null) {

					// keyboard was closed by "^d"

					break; // terminate the program

				}

				// broadcast a message to each of the chat members.
				
				//increment my localvector at [rank] and send my localvector to node[i]
				localVector = createNewArrayToSend(rank);
				
				for (int i = 0; i < hosts.length; i++)

					if (i != rank) {

						// of course I should not send a message to myself
			
						//send my localVector
						outputs[i].writeObject(localVector);
						
						outputs[i].writeObject(message);
						
						outputs[i].flush(); // make sure the message was sent

					}

			}

			// read a message from each of the chat members

			for (int i = 0; i < hosts.length; i++) {

				// to intentionally create a misordered message deliveray,

				// let's slow down the chat member #2.

				try {

					if (rank == 2)
						
						//System.out.println("rank is 2, slowing down message send");
						
						Thread.currentThread().sleep(5000); // sleep 5 sec.

				} catch (InterruptedException e) {
				
				}

				// check if chat member #i has something
				if (i != rank && indata[i].available() > 0) {

					// read a message from chat member #i and print it out

					// to the monitor

					try {

												
						//read in senders vector and compare to local hosts vector
						int[] sendersVector = (int[]) inputs[i].readObject(); 
						
						boolean acceptNextMessage = compareVectors(i, sendersVector);
						
						String message = (String) inputs[i].readObject(); //read in the actual message
						
						String displayMessage = hosts[i] + ": " + message;
						
						if (acceptNextMessage) {
							//message was in valid order so we can print 
							System.out.println(hosts[i] + ": " + message); //original print out message
							
							localVector[i] = localVector[i] + 1; //increment my vector to match the senders vector 
							
							//check if there is something in the waiting list that is next in order
							//if there is print out the displayMessagy 
							checkWaitingList();
							
						}else {
							//put on waiting list for when we receive another message 
							waitingList.put(sendersVector, displayMessage);	
						
						}
							
					} catch (ClassNotFoundException e) {
						
					}

				}

			}

		}

	}
	
	//iterates through each value in the waiting list 
		//checks each value and compares to local list
		//for 0 to hashset size 
			//if valid to be next 
			//print out message 
			//remove node from list
	public static void checkWaitingList () {
		
		Set<int[]> keys = waitingList.keySet();
		for (int[] senderVector: keys) {
			
			//another for loop to check each index
			for (int j = 0; j < senderVector.length; j++) {
				
				if (compareVectors(j, senderVector)) {
					String message = waitingList.get(senderVector);
					System.out.println(message);
					localVector = senderVector; //gotta set my own vector to the vector that is valid
					waitingList.remove(senderVector);
				}
				
			}
			
		}
		
	}
	
	//read in a vector from rank i
	//compare to local hosts own vector
	//arguments: int j is the sender node rank 
	public static boolean compareVectors (int j, int[] sendersVector) {

		
		for (int i = 0; i < sendersVector.length; i++) {
			
			if (i == j && sendersVector[i] != localVector[i] + 1) {
				//not one larger so return false
				return false;
				
			}else if (i != j && sendersVector[i] > localVector[i]) {
				//sender vector not valid because sender[i] is not <= localVector[i]
				return false;
			}
			
		}
		
		//went through whole loop so the conditions 
		//If i == j, the senders vector[i] must be one larger than the local hosts vector[i].  
		//Otherwise the senders vector[i] must be smaller than or equal to the local hosts vector[i]. 
		//are satisfied 
		return true;
		
	}
	
	//creates a new array 
	//sets all values to localArray
	//increments i an extra time at index rank 
	//need to create a new array copy of local vector every time you send it over
	public static int[] createNewArrayToSend(int rank) {
		
		int[] temp = new int[localVector.length];
		
		for (int i = 0; i < temp.length; i++) {
			
			temp[i] = localVector[i];
			if (i == rank) {
				
				temp[i] = temp[i] + 1;
				
			}
			
		}
		
		return temp;
		
	}

}