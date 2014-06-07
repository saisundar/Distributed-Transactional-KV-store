package transaction.bean;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.ClassNotFoundException;

public class TableReader implements Callable<Integer>{
	private Object table;
	private String fileName;

	public TableReader(String fileName) {
		this.fileName = fileName;
	}

	public Integer call(){
	try{
			InputStream fileIn = new FileInputStream("data/"+fileName);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			System.out.println("Reading the object from " + fileName);
			table =   in.readObject();
			in.close(); // required !
			fileIn.close();
			System.out.println("Reading done in Callable from "+fileName);
	}
	catch(FileNotFoundException e){
		System.out.println("File not found to read");
		return 2;
	}
	catch(IOException e)
	{
		System.out.println("I/O exceptiuon ... should not happen");
		e.printStackTrace();
	}
	catch(ClassNotFoundException ex){
		System.out.println(ex.getMessage());
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
