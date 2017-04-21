import javax.sound.sampled.*;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

class StudentPlayer extends Panel implements Runnable 
{
    private static final long serialVersionUID = 1L;
    private TextField textfield;
    private TextArea textarea;
    private Font font;
    private String filename;
	private SourceDataLine line;
	private BoundedBuffer bf;
	private float lvl = (float) -10;
	private FloatControl volume;
	private Producer producer;
	private Consumer consumer;
	
    public StudentPlayer(String filename)
	{
		font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
		textfield = new TextField();
		textarea = new TextArea();
		textarea.setFont(font);
		textfield.setFont(font);
		setLayout(new BorderLayout());
		add(BorderLayout.SOUTH, textfield);
		add(BorderLayout.CENTER, textarea);
		textfield.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				textfield.setText("");
				
				String command = e.getActionCommand();
				
				BooleanControl muteCtrl = (BooleanControl)line.getControl(BooleanControl.Type.MUTE);

				float sound = (float)2;
				
				if(line != null) 
				{			
					if(command.equals("m"))
					{
						muteCtrl.setValue(true);
						textarea.append("Command received: Mute\n");
					}
					else if(command.equals("u"))
					{
						muteCtrl.setValue(false);
						textarea.append("Command received: UnMute\n");
					}
					else if(command.equals("p"))
					{
						bf.pause();
						textarea.append("Command received: Pause\n");
					}
					else if(command.equals("r"))
					{
						bf.resume();
						textarea.append("Command received: Resume\n");
					}
					else if(command.equals("q"))
					{
						if(lvl < 6)
						{
							lvl += sound;
							volume.setValue(lvl);
							textarea.append("Command received: Volume Up " + lvl + " \n");
						}
					}
					else if(command.equals("a"))
					{
						if(lvl > -80)
						{
							lvl -= sound;
							volume.setValue(lvl);
							textarea.append("Command received: Volume Down "+ lvl + " \n");
						}
					}
					else if(command.equals("x"))
					{
						textarea.append("Command received: Exit: " + " \n");
						/*producer.closeMe();
						consumer.closeMe();*/
						producer.interrupt();
						consumer.interrupt();
					}
				}
			}
		});
		
		this.filename = filename;
		new Thread(this).start();
    }
    public void run() 
	{
		try
		{
			AudioInputStream s = AudioSystem.getAudioInputStream(new File(filename));
			AudioFormat format = s.getFormat();	    
			System.out.println("Audio format: " + format.toString());
			
			int oneSecond =((int) (format.getChannels() * format.getSampleRate() * format.getSampleSizeInBits() / 8));
			
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		
			if (!AudioSystem.isLineSupported(info)) 
			{
				System.out.println("Cannot handle that audio format");
				System.exit(1);
			}
			
			int dur = (s.available()/oneSecond);
			
			line = (SourceDataLine) AudioSystem.getLine(info);
			line.open(format);
			line.start();
			
			textarea.append("Audio file: " + filename + "\n");
			textarea.append("Audio file format: " + format + "\n");
			textarea.append("Audio file duration: " + dur + " seconds\n");
			
			bf = new BoundedBuffer(oneSecond);
			volume = (FloatControl)line.getControl(FloatControl.Type.MASTER_GAIN);
			volume.setValue(lvl);
			producer = new Producer(s, oneSecond, bf, textarea);
			consumer = new Consumer(s, line, bf, textarea);
			producer.start();
			consumer.start();
			try
			{
				producer.join();
				consumer.join();
			}
			catch(InterruptedException e){}
			
			line.drain();
			line.stop();
			line.close();	
		}
		catch(UnsupportedAudioFileException e)
		{
			textarea.append("Player initialisation failed");
			e.printStackTrace();
			System.exit(1);
		}
		catch(LineUnavailableException e)
		{
			textarea.append("Player initialisation failed");
			e.printStackTrace();
			System.exit(1);
		}
		catch(IOException e)
		{
			textarea.append("Player initialisation failed");
			e.printStackTrace();
			System.exit(1);
		}
		textarea.append("Main: Says bye.\n");
    }
}
public class Player extends Applet
{
	private static final long serialVersionUID = 1L;
	
	public void init() 
	{
		setLayout(new BorderLayout());
		add(BorderLayout.CENTER, new StudentPlayer(getParameter("file")));
	}
}

class Producer extends Thread
{
	private int size, oneSecond, ins;
	private AudioInputStream s;
	private BoundedBuffer bf;
	//private boolean exit;
	TextArea textarea;
	
	Producer(AudioInputStream ss, int second, BoundedBuffer b, TextArea t) throws IOException
	{
		bf=b;
		s=ss;
		
		oneSecond = second;
		size = s.available();
		
		textarea=t;
	}
	
	public void run()
	{
		try
		{
			while(ins < size /*&& !exit*/)
			{	
				byte [] temp = new byte[oneSecond];
				
				try
				{
					int read = s.read(temp);
					ins += read;
				}
				catch(IOException e){}
				
				bf.insertIntoBuffer(temp);
			}
		}
		catch(InterruptedException e)
		{
			textarea.append("Producer: Says no more audio\n");
			textarea.append("Producer: Says Bye.\n");
			Runtime.getRuntime().halt(0);
		}
	}
	
	/*public void closeMe()
	{
		exit = true;
		textarea.append("Producer: Says Bye.\n");
	}*/
}

class Consumer extends Thread
{
	private int size, outs;
	private AudioInputStream s;
	private BoundedBuffer bf;
	private SourceDataLine line;
	//private boolean exit;
	TextArea textarea;
	
	Consumer(AudioInputStream ss, SourceDataLine l, BoundedBuffer b, TextArea t) throws IOException
	{
		s=ss;
		line = l;
		size = s.available();
		textarea = t;
		bf=b;
		//exit=false;
	}
	
	public void run()
	{
		try
		{
			while(outs < size /*&& !exit*/)
			{
				byte [] temp = bf.readFromBuffer();
				outs += temp.length;
				line.write(temp, 0, temp.length);
			}
		}
		catch(InterruptedException e)
		{
			textarea.append("Consumer: Says Bye.\n");
			Runtime.getRuntime().halt(0);
		}
	}
	
	/*public void closeMe()
	{
		exit = true;
	}*/
	
}

class BoundedBuffer
{
	private int nextIn, nextOut, occupied, oneSecond;
	private boolean dataAvailable, roomAvailable;
	private byte [] buffer;
	private boolean pause;
	
	BoundedBuffer(int second)
	{		
		oneSecond = second;
		buffer = new byte[oneSecond*10];
		
		dataAvailable = false;
		roomAvailable = true;
		pause = false;
		
		nextIn = 0;
		nextOut = 0;
		occupied = 0;
	}
	
	synchronized void insertIntoBuffer(byte [] t) throws InterruptedException
	{	
		while(!roomAvailable || pause)
		{
			wait();
		}
		
		for(int i=0; i<t.length; i++)
		{
			buffer[nextIn]=t[i];
			nextIn++;
		}
		
		occupied +=  t.length;
		
		if(occupied == buffer.length)
			roomAvailable=false;
			
		nextIn = nextIn%buffer.length;
		dataAvailable = true;
		notifyAll();
	}
	
	synchronized byte [] readFromBuffer() throws InterruptedException
	{
		byte [] temp = new byte[oneSecond];
		
		while(!dataAvailable || pause)
		{
			wait();
		}
		
		for(int i=0; i<temp.length; i++)
		{
			temp[i]=buffer[nextOut];
			nextOut++;
		}
		
		occupied -= temp.length;
		
		if(occupied == 0)
			dataAvailable = false;
			
		nextOut = nextOut%buffer.length;
		roomAvailable = true;
		notifyAll();
		
		return temp;
	}
	
	void pause()
	{
		pause = true;
	}
	
	void resume()
	{
		pause = false;
		wakeUp();
	}
	
	synchronized void wakeUp()
	{
		notifyAll();
	}
}