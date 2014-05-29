package transaction.bean;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import de.ruedigermoeller.serialization.FSTObjectOutput;

public class TableWriter implements Callable<Integer> {

	private ConcurrentHashMap<String,Object> table;
	private String fileName;

	public TableWriter(ConcurrentHashMap<String, Object> table, String fileName) {
		this.table = table;
		this.fileName = fileName;
	}

	public Integer call() throws Exception{
		try
		{
			OutputStream fileOut = new FileOutputStream(fileName);
			FSTObjectOutput out = new FSTObjectOutput(fileOut);
			out.writeObject(table);
			out.close(); // required !
			fileOut.close();
		}
		catch(Exception e)
		{
			System.out.println(" exception message"+e.getMessage());
			return 1;

		}

		return 0;
	}

}
