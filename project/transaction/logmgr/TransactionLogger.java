package project.transaction.logmgr;

import java.util.concurrent.Callable;

public class TransactionLogger implements Callable<Boolean> {

	private String logMsg;

	public TransactionLogger(String logMsg){
		this.logMsg = logMsg;
	}

	@Override
	public Boolean call() throws Exception {
		LogWriter.write(logMsg);
		return null;
	}
	

}
