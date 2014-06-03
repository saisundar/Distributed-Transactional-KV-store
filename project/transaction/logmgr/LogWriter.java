package transaction.logmgr;

import java.io.FileWriter;
import java.io.IOException;

public class LogWriter {
	
	private static FileWriter fw;
	
	static{
		try {
			System.out.println("Creating UNDI-REDO log file");
			fw = new FileWriter("/undo-redo.log");
		} catch (IOException e) {
			System.out.println("Error creating the log file");
		}
	}

	
	public static void write(String msg){
		try {
			fw.write(msg);
		} catch (IOException e) {
			System.out.println("Error in writing the logs");
		}
	}
	
	public static void flush(){
		try {
			fw.flush();
			System.out.println("Flushed the disk logs");
		} catch (IOException e) {
			System.out.println("Error in flushing the log file");
		} catch (Exception e){
			System.out.println("Should not come here "+e);
		}
	}
	
	public static void close(){
		try {
			fw.close();
		} catch (IOException e) {
			System.out.println("Error in closing the file");
		}
	}
}
