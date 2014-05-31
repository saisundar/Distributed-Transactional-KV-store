package project.logmgr;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class LogReader {

private static BufferedReader fr;
	
	static{
		try {
			fr = new BufferedReader(new FileReader("/undo-redo.log"));
		} catch (IOException e) {
			System.out.println("Error creating the log file");
		}
	}

	
	public String read(){
		String line = null;
		try {
			line = fr.readLine();
		} catch (IOException e) {
			System.out.println("Error in writing the logs");
		}
		return line;
	}
	
	
	public static void close(){
		try {
			fr.close();
		} catch (IOException e) {
			System.out.println("Error in closing the file");
		}
	}
	
}
