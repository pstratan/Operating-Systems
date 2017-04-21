import java.net.*;
import java.io.*;
import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class ChatServer
{
	public static void main(String[] args) throws IOException 
	{
		ServerSocket serverSocket = null;
		MsgBuffer mb = new MsgBuffer();
		
		try
		{	// Listen on on port 7777
			serverSocket = new ServerSocket(7777);
        } 
		catch (IOException e) 
		{
			System.err.println("Could not listen on port: 7777");
			System.exit(-1);
		}

		try
				{
		while (true) 
		{
			Socket s = serverSocket.accept();
			String userName;
			BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
			
			userName = br.readLine();
			mb.addNewConnection(s, userName);
			System.out.println("Clients: " + mb.numOfCon());
			
			ArrayList<Socket> active = mb.clientList();
			
			for(Socket so: active)
			{
				try
				{
					PrintWriter socketOut = new PrintWriter(so.getOutputStream(), true);
					socketOut.println(userName + " just joined the chatroom...");
				}
				catch(IOException e){}
			}
		
			new Reader(mb, s).start();
			new Printer(mb).start();
        }
		}
		finally
		{
			serverSocket.close();
		}
    }
}

class Reader extends Thread
{
	MsgBuffer mb;
	Socket s;

	Reader(MsgBuffer mb, Socket s)
	{
		this.mb=mb;
		this.s=s;
	}

	public void run()
	{
		try
		{
			while(true)
			{
				BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
				String msg = "";
				msg = br.readLine();
				mb.insert(msg, s);
			}
		}
		catch(IOException e){}
		finally
		{
			String userName = mb.exitConnection(s);
			ArrayList<Socket> active = mb.clientList();
			
			for(Socket so: active)
			{
				try
				{
					PrintWriter socketOut = new PrintWriter(so.getOutputStream(), true);
					socketOut.println(userName + " just left the chatroom...");
				}
				catch(IOException e){}
			}
			
			System.out.println("Clients: " + mb.numOfCon());
		}
	}
}

class Printer extends Thread
{
	MsgBuffer mb;
	
	Printer(MsgBuffer mb) throws IOException
	{
		this.mb=mb;
	}

	public void run()
	{
		while(true)
		{
			ArrayList<Socket> active = mb.clientList();
			String msg = mb.remove();
			
			for(Socket so: active)
			{
				try
				{
					PrintWriter socketOut = new PrintWriter(so.getOutputStream(), true);
					socketOut.println(msg);
				}
				catch(IOException e){}
			}
		}
	}
}

class MsgBuffer
{
	private ArrayList<Socket> connections = new ArrayList<Socket>();
	private ArrayList<String> userNames = new ArrayList<String>();
	int conCount = 0;
	
	private String [] msgs = new String[20];
	private boolean dataAva, spaceAva;
	private int nextIn, nextOut, occupied;
	
	MsgBuffer()
	{
		dataAva = false;
		spaceAva = true;
		nextIn = 0; 
		nextOut = 0; 
		occupied = 0;
	}
	
	void addNewConnection(Socket s, String n)
	{
		connections.add(s);
		userNames.add(n);
		conCount++;
	}
	
	String exitConnection(Socket s)
	{
		String name = "";
		
		for(int i=0; i<conCount; i++)
		{
			if(s == connections.get(i))
			{
				connections.remove(i);
				name = userNames.get(i);
				userNames.remove(i);
				conCount--;
			}
		}
		
		return name;
	}
	
	String getUserName(Socket s)
	{
		for(int i=0; i<conCount; i++)
		{
			if(connections.get(i) == s)
				return userNames.get(i);
		}
		
		return "";
	}
	
	int numOfCon()
	{
		return conCount;
	}
	
	ArrayList<Socket> clientList()
	{
		return connections;
	}
	
	synchronized void insert(String m, Socket s)
	{
		while(!spaceAva)
		{
			try
			{
				wait();
			}
			catch(InterruptedException e){}
		}
		
		String ms = getUserName(s) + " says: " +m;
		msgs[nextIn] = ms;
		occupied++;
		nextIn++;
		
		if(occupied == msgs.length)
			spaceAva = false;
			
		nextIn = nextIn%msgs.length;
		dataAva = true;
		notifyAll();
	}
	
	synchronized String remove()
	{
		while(!dataAva)
		{
			try
			{
				wait();
			}
			catch(InterruptedException e){}
		}
		
		String m = msgs[nextOut];
		occupied--;
		nextOut++;
		
		if(occupied == 0)
			dataAva = false;
			
		nextOut = nextOut%msgs.length;
		spaceAva = true;
		notifyAll();
		return m;
	}
}