package transaction.bean;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.ClassNotFoundException;

public class TableWriter implements Callable<Integer> {

	private Object table;
	private String fileName;

	public TableWriter(Object table, String fileName) {
		this.table = table;
		this.fileName = fileName;
	}

	public Integer call() throws Exception{
		try
		{
			OutputStream fileOut = new FileOutputStream(fileName);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(table);
			out.close(); // required !
			fileOut.close();
		}
		catch(IOException e)
		{
			System.out.println(" exception message"+e.getMessage());
			return 1;

		}

		return 0;
	}

}
