package transaction.logmgr;

public class VariableLogger implements Runnable {
	
	private String logMsg;
	
	public VariableLogger(String logMsg){
		this.logMsg = logMsg;
	}

	@Override
	public void run() {
		//Write the message to the file.
		LogWriter.write(logMsg);
		
	}

}
