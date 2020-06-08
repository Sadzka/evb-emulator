import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Scanner;
import java.math.BigInteger;
import java.io.File;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.awt.Toolkit;
import java.awt.Robot;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
/**
 * Host
 * Autor: Konrad Paluch
 * Data: 2020 06 08
 * Na potrzeby projektu z przedmiotu Systemy Wbudowane
 * Grupa lab06 PK 2020
 */
public class Host {
	
    public static void main(String[] args) {
		
		
        Scanner sc = new Scanner(System.in);
		InputStream is;
		OutputStream os;
		Listener listener;

		Thread thr_listen;
			
        try {
            final String host = "127.0.0.1";
            final int port = 9999;
			Utils utils = new Utils();

            System.out.println("[System] Laczenie z " + host + ":" + port);

            Socket s = new Socket(host, port);
            is = s.getInputStream();
            os = s.getOutputStream();
			listener = new Listener(is, os);
			thr_listen = new Thread(listener, "Listener-Thread");
			thr_listen.start();
		
            System.out.println("[System] Polaczono!");
			System.out.println("[0] Wyjscie.");
			//System.out.println("[64] Prosba o odczytanie glosnosci.");
			System.out.println("buttons");
			System.out.println("send:");
			System.out.println("\t[128] [message] Ping");
			
            String line;
			while (true) {
                System.out.print("> ");
				line = sc.nextLine();
				
                String[] command = line.split(" ");
				

				if (command[0].equals("0"))
					listener.terminate();
				
				if (command[0].equals("send")) {
						
					byte [] packet = utils.emptyPacket((byte)128);
					if (command.length >= 2){
						byte[] b = command[1].getBytes();
						for (int i = 1; i < 8 && i < command[1].length()+1; i++) {
							packet[i] = b[i-1];
						}
					}
					utils.send(packet, os);
				}
				
				if (command[0].equals("buttons")) {
					ButtonsConfigure bf = new ButtonsConfigure(listener.getButtons());
					bf.run();
					listener.setButtons(bf.getButtons());
				}
				
				// W przypadku zakonczenie procesu nasluchujacego, zakoncz proces w tle
				if (!listener.isRunning()) {
					thr_listen.stop();
					s.close();
					break;
				}
            }
			listener.terminate();
			thr_listen.join();
            s.close();
        }
		catch (Exception er) {
			System.err.println( "[1] Napotkano problem: " + er.getMessage() );
			return;
        }
    }
	
}

class Listener implements Runnable {
	InputStream is;
	OutputStream os;
	boolean isRunning;
	Utils utils;
	double glosnosc; // TODO
	Runtime runtime;
	Button [] buttons;
	long r1, g1, b1;
	
	Listener(InputStream is, OutputStream os) {
		this.is = is;
		this.os = os;
		this.isRunning = true;
		this.utils = new Utils();
		this.utils.showLogs(false);
		this.glosnosc = 75;
		this.runtime = Runtime.getRuntime();
		this.buttons = new Button[8];
		for (int i=0; i<8; i++) {
			buttons[i] = new Button();
		}
		this.r1 = 0;
		this.g1 = 0;
		this.b1 = 0;
		
		//DEBUG
		loadButtons();
	}
	
	void terminate() {
		this.isRunning = false;
		saveButtons();
	}
	public boolean isRunning() {
		return this.isRunning;
	}
	public Button[] getButtons() {
		return this.buttons;
	}
	public void setButtons(Button [] buttons) {
		this.buttons = buttons;
	}
	
	private void loadButtons(){
		try {
			File myObj = new File("buttons.txt");
			Scanner myReader = new Scanner(myObj);
			for (int i=0; i<8; i++) {
				String description = myReader.nextLine();
				buttons[i].setDescription(description);
				String command = myReader.nextLine();
				buttons[i].setCommand(command);
				String emptyLine = myReader.nextLine();
			}
			myReader.close();
		}
		catch (FileNotFoundException e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		}
		
	}
	private void saveButtons(){
		try {
			FileWriter myWriter = new FileWriter("buttons.txt");
			for (int i=0; i<8; i++) {
				myWriter.write(buttons[i].getDescription() + '\n');
				myWriter.write(buttons[i].getCommand() + '\n');
				myWriter.write('\n');
			}
			myWriter.close();
		}
		catch (IOException e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {	
		try {
			while(this.isRunning)
			{
				byte [] packet = utils.emptyPacket();
				
				utils.receive(packet, is);
				
				if (utils.readableByte(packet[0]) != 0)
					utils.log( "Packed received: " + utils.readableByte(packet[0]) );
				
				switch( utils.readableByte(packet[0]) ) {
					case 1:
					{
						utils.log("\t[Prosba o wyslanie glosnosci]" );
						
						// send response
						byte [] outgoing_packet = utils.emptyPacket((byte)(75));
						byte [] value = utils.intToBytes((int)(this.glosnosc*100));
						for (int i=0; i<value.length; i++) {
							outgoing_packet[i+1] = value[i];
						}
						utils.send(outgoing_packet, os);
						break;
					}
					case 2:
					{
						utils.log("\t[Prosba o wyslanie danych obciazenia systemu]" );

						// send response
						long allocatedMemory = runtime.totalMemory()/1024;
						utils.log("\t[allocatedMemory] " + allocatedMemory);
						byte [] outgoing_packet = utils.emptyPacket((byte)(76));
						byte [] RAM = utils.intToBytes( (int)(allocatedMemory) );
						
						for (int i = 0; i < RAM.length; i++) {
							outgoing_packet[i+1] = RAM[i];
						}
						outgoing_packet[6] = (byte)(60); // Brak łatwo dostepnego czujnika w javie, więc wartość testowo ustawiona na "sztywno".
						utils.send(outgoing_packet, os);
						break;
					}
					case 3:
					{
						utils.log("\t[Prosba o informacje o systemie]" );
						
						// send response
						long maxMemory = runtime.maxMemory()/1024;
						utils.log("\t[maxMemory] " + maxMemory);
						byte [] outgoing_packet = utils.emptyPacket((byte)(74));
						byte [] RAM = utils.intToBytes( (int)(maxMemory) );
						for (int i = 0; i < RAM.length; i++) {
							outgoing_packet[i+1] = RAM[i];
						}
						outgoing_packet[6] = (byte)(85);
						utils.send(outgoing_packet, os);
						break;
					}
					case 4:
					{
						int button_number = utils.byteToInt( (byte)(0), packet[1] );
						utils.log("\t[Prosba o informacje o przycisku "+button_number+"]" );
						// send response
						
						byte[] b = this.buttons[button_number].getDescription().getBytes();

						byte [] outgoing_packet = utils.emptyPacket((byte)78);
						outgoing_packet[1] = (byte)(button_number);
						
						for (int i = 2; i < 8 && i < b.length+2; i++) {
							outgoing_packet[i] = b[i-2];
						}
						utils.send(outgoing_packet, os); // First Packet
						
						outgoing_packet = utils.emptyPacket();
						for (int i = 0; i < 8 && (i+6) < b.length; i++) {
							outgoing_packet[i] = b[i+6];
						}
						utils.send(outgoing_packet, os); // Second Packet
						break;
					}
					case 10:
					{
						int value = utils.byteToInt( packet[1], packet[2]);
						this.glosnosc = value / 1023.0;
						utils.log("\tcontent: '" + value + "'" );
						utils.log("Ustawiono glosnosc na: " + (int)(this.glosnosc*100) + "%" );
						utils.setVolume((int)(this.glosnosc*100));
						break;
					}
					case 11:
					{
						try {
							int button_number = utils.byteToInt( (byte)(0), packet[1] );
							utils.log("Wcisnieto przycisk: " + button_number );
							if (buttons[button_number].getCommand().equals("")) break;
							// Host po otrzymaniu powinien wykonać przypisaną do danego przycisku funkcję. Host sam ustala przypisane funkcje.
							String [] cmd = {"/bin/bash", "-c", buttons[button_number].getCommand()};
							Process process = runtime.exec(cmd, null);
							
							// deal with OutputStream to send inputs
							process.getOutputStream();
							 
							// deal with InputStream to get ordinary outputs
							process.getInputStream();
							 
							// deal with ErrorStream to get error outputs
							process.getErrorStream();
						}
						catch (Exception e) {
							utils.log("Invalid command in button or command arguments.");
							break;
						}

						break;
					}
					case 75:
					{
						int intvalue = utils.byteToInt( packet[1], packet[2]);
						utils.log("\tcontent: '" + intvalue + "'" );
						break;
					}
					case 128:
					{
						
						// send response
						String str = new String(packet);
						utils.log("\tcontent: '" + str.substring(1, str.length()).replace("\0", "") + "'");
						byte[] outgoing_packet = utils.emptyPacket( (byte)129 );
						for (int i = 1; i < 8; i++) {
							outgoing_packet[i] = packet[i];
						}
						utils.send(outgoing_packet, os);
						break;
					}
					case 129:
					{
						String str = new String(packet);
						utils.log("\tcontent: '" + str.substring(1, str.length()).replace("\0", "") + "'");
						break;
					}
				}
				
				BufferedImage image = new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
				
				long r = 0, g = 0, b = 0;
				
				for (int x = 0; x < image.getWidth(); x++) {
					for (int y = 0; y < image.getHeight(); y++) {
						Color pixel = new Color(image.getRGB(x, y));
						r += pixel.getRed();
						g += pixel.getGreen();
						b += pixel.getBlue();
					}
				}
				r = r / (image.getWidth()*image.getHeight());
				g = g / (image.getWidth()*image.getHeight());
				b = b / (image.getWidth()*image.getHeight());
				
				if (utils.colorDifference(r, g, b, r1, g1, b1) > 30) {
					byte [] outgoing_packet = utils.emptyPacket((byte)77);
					outgoing_packet[1] = (byte)r;
					outgoing_packet[2] = (byte)g;
					outgoing_packet[3] = (byte)b;
					r1 = r;
					g1 = g;
					b1 = b;
					utils.send(outgoing_packet, os);
				}
			}	
		}
		catch (Exception er) {
			if (!er.getMessage().equals("Connection reset")
			&&  !er.getMessage().equals("Socket closed")){
				System.err.println( "[2] Error occurred: " + er.getMessage() );
			}
			this.isRunning = false;
			saveButtons();
			return;
        }
		saveButtons();
	}
	
}

class Button {
	private String description;
	private String command;
	
	Button() {
		description = "empty";
		command = "";
	}
	
	public void setDescription(String desc) {
		if (desc.length() > 14){
			desc = desc.substring(0, 13);
		}
		this.description = desc;
	}
	
	public void setCommand(String command) {
		this.command = command;
	}
	
	public void set(String desc, String command) {
		if (desc.length() > 14){
			desc = desc.substring(0, 13);
		}
		this.description = desc;
		this.command = command;
	}
	
	public String getDescription() {
		return this.description;
	}
	public String getCommand() {
		return this.command;
	}
	
}

class ButtonsConfigure {
	
	Button [] buttons;
	Scanner sc;
	ButtonsConfigure(Button [] buttons) {
        this.sc = new Scanner(System.in);
		this.buttons = buttons;
	}
	
	public void run() {
		try {
			System.out.println("Konfiguracja funkcji przyciskow:");
			System.out.println("0. Wyjscie");
			for (int i=1; i<9; i++) {
				System.out.println(i + ". " + buttons[i-1].getDescription());
			}
			System.out.println("Wybierz przycisk do konfiguracji");
			
			while (true) {
				System.out.print(">> ");
				String line = sc.nextLine();
				int value = Integer.parseInt(line);
				if (value >= 1 && value <= 8) {
					selectButton(value-1);
				}
				else if (value == 0) {
					break;
				}
				else {
					System.out.println("Invalid command.");
				}
				
			}
		}
		catch (Exception ex) {
			return;
		}
	}
	
	private void selectButton(int i) {
		try {
			System.out.println("Wybrano przycisk " + i);
			System.out.println("0. Powrot");
			System.out.println("1. Opis: " + buttons[i].getDescription());
			System.out.println("2. Funkcja: " + buttons[i].getCommand());
			System.out.println("Co chcesz zmienic?");
			System.out.print(">>> ");
			String line = sc.nextLine();
			int value = Integer.parseInt(line);
			while (true) {
				if (value == 0) {
					break;
				}
				else if (value == 1) {
					System.out.println("Podaj opis (do 14 znakow).");
					System.out.print(">>>> ");
					line = sc.nextLine();
					buttons[i].setDescription(line);
					System.out.println("Zmieniono opis przycisku " + i + ".");
					break;
				}
				else if (value == 2) {
					System.out.println("Podaj komende.");
					System.out.print(">>> ");
					line = sc.nextLine();
					buttons[i].setCommand(line);
					System.out.println("Zmieniono komende przycisku " + i + ".");
					break;
				}
			}
			
		}
		catch (Exception ex) {
			return;
		}
	}

	public Button[] getButtons() {
		return this.buttons;
	}
}










