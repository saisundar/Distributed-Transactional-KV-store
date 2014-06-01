package project.transaction.bean;

public class UndoIMLog {
	
	private static final int empty				= 0;
	
	public int tableName 	= empty;
	public int operation	= empty;;
	public Object ObjPointer= null;
	public String Key		= null;
	public Reservation AuxKey = null;
	
	public UndoIMLog(int tablName, int operation, Object objPointer,
			String key, Reservation auxKey) {
		this.tableName = tableName;
		this.operation = operation;
		ObjPointer = objPointer;
		Key = key;
		AuxKey = auxKey;
	}
	
}
