package transaction.logmgr;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class LogReader {

	private BufferedReader fr;

	public void loadFile() throws FileNotFoundException{
		fr = new BufferedReader(new FileReader("/undo-redo.log"));
	}

	public String nextLine(){
		String line = null;
		try {
			line = fr.readLine();
		} catch (IOException e) {
			System.out.println("Error in writing the logs");
		}
		return line;
	}


	public void close(){
		try {
			fr.close();
		} catch (IOException e) {
			System.out.println("Error in closing the file");
		}
	}

}
