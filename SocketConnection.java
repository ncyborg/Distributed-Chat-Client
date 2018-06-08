
// for IOException 
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
// for Socket 
import java.net.Socket;

public class SocketConnection{

	private Socket sock;
	private String name;
	private InputStream rawIn; // an input stream from the server
	private DataInputStream in; // a filtered input stream from the server
	private DataOutputStream out; // a filtered output stream to the server
	private BufferedReader stdin; // the standart input
	private boolean corrupted; //used to record if the connection 
							   //during a read/write is corrupted
	
	public SocketConnection(Socket sock) throws IOException {
		this.sock = sock;
		rawIn = this.sock.getInputStream();
		in = new DataInputStream(rawIn);
		out = new DataOutputStream(this.sock.getOutputStream());
		stdin = new BufferedReader(new InputStreamReader(System.in));
		corrupted = false;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public boolean getCorrupted() {
		//returns this variable to show if read/write was corrupted
		return this.corrupted; 
	}
	
	/*
	 * returns boolean if in.available
	 */
	public boolean available() throws IOException {
		return in.available() > 0;
	}

	/*
	 * Purpose of this method is to read a new message using the socket and
	 * readUTF();
	 */
	public String readMessage() {
		try {
			// something can be read
			String input = in.readUTF();
			return input;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			//used to show data was corrupted
			corrupted = true;
			return "";
		}

	}

	/*
	 * Purpose of this method is to write to the socket using writeUTF();
	 */
	public void writeMessage(String output) {
		try {
			out.writeUTF(output);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
			//used later to show that your write was corrupted
			corrupted = true; 
		
		}
	}

}
