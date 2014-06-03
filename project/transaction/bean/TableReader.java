package transaction.bean;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.Callable;

import de.ruedigermoeller.serialization.FSTObjectInput;

public class TableReader implements Callable<Integer>{
	private Object table;
	private String fileName;

	public TableReader(String fileName) {
		this.fileName = fileName;
	}

	public Integer call() throws Exception{
		try
		{
			InputStream fileIn = new FileInputStream(fileName);
			FSTObjectInput in = new FSTObjectInput(fileIn);
			table =   in.readObject();
			in.close(); // required !
			fileIn.close();
		}
		catch(Exception e)
		{
			System.out.println(" exception message"+e.getMessage());
			return 1;
		}

		return 0;
	}

	public Object getTable() {
		return table;
	}

	public String getFileName(){
		return fileName;
	}
}
