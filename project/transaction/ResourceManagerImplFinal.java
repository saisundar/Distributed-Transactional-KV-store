package project.transaction;

import project.lockmgr.*;
import project.recovery.LoadFiles;
import project.logmgr.LogWriter;
import project.logmgr.TransactionLogger;
import project.logmgr.VariableLogger;
import project.transaction.bean.*;

import java.rmi.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;

/** 
 * Resource Manager for the Distributed Travel Reservation System.
 * 
 * Description: toy implementation of the RM, for initial testing
 */

public class ResourceManagerImpl 
extends java.rmi.server.UnicastRemoteObject
implements ResourceManager {

	//Book keeping and other variables
	private ConcurrentHashMap<Integer,Object> activeTxns;
	private static final Object DUMMY = new Object();
	private LockManager lockManager;
	private final int WRITE = 1;
	private final int READ = 0;
	private static volatile AtomicInteger shuttingDown = new AtomicInteger();
	private volatile AtomicInteger committedTrxns = new  AtomicInteger();
	private volatile Integer enteredTxnsCount=0;
	private static final int CHECKPOINT_TRIGGER = 10;
	private static Boolean stopAndWait = new Boolean(false);
	private static Boolean HashSetEmpty = new Boolean(true);
	private ExecutorService checkPointers ;
	private Set<Callable<Integer>> callables;

	// Other Variables
	private static final Object DUMMY = new Object();
	private final int WRITE = 1;
	private final int READ = 0;
	private static final int CHECKPOINT_TRIGGER = 10;
	private static final int SLEEPSHUTDOWN = 5000;
	// Data Sets
	private ConcurrentHashMap<String,Flight> flightTable;
	private ConcurrentHashMap<String,Car> carTable;
	private ConcurrentHashMap<String,Hotels> hotelTable;
	private ConcurrentHashMap<String,HashSet<Reservation>> reservationTable;
	private ConcurrentHashMap<String,Integer> reservedflights;
	private ExecutorService checkPointers ;
	private Set<Callable<Integer>> callables;

	private ExecutorService executor ;
	//<----------UNDOING--------------------->
	private ConcurrentHashMap<Integer,Stack<UndoIMLog> > UndoIMTable;
	//</----------UNDOING--------------------->

	protected int xidCounter;

	//<----------UNDOING--------------------->
	private static final int empty				= 0;
	private static final int FlightTable  		= 1;
	private static final int CarTable 			= 2;
	private static final int HotelTable 		= 3;
	private static final int ReservationTable 	= 4;
	private static final int Flights 			= 5;

	private static final int insert 			= 1;
	private static final int delete				= 2;
	private static final int overWrite			= 3;
	private static final int partialInsert 		= 4;
	//</----------UNDOING--------------------->

	public static void main(String args[]) {
		System.setSecurityManager(new RMISecurityManager());

		String rmiName = System.getProperty("rmiName");
		if (rmiName == null || rmiName.equals("")) {
			rmiName = ResourceManager.DefaultRMIName;
		}

		String rmiRegPort = System.getProperty("rmiRegPort");
		if (rmiRegPort != null && !rmiRegPort.equals("")) {
			rmiName = "//:" + rmiRegPort + "/" + rmiName;
		}

		try {
			ResourceManagerImpl obj = new ResourceManagerImpl();
			Naming.rebind(rmiName, obj);
			System.out.println("RM bound");
		} 
		catch (Exception e) {
			System.err.println("RM not bound:" + e);
			System.exit(1);
		}
	}

	///TODO:
	//e) add a logging routine, which has to be invoke from all other routines. 
	//4. synchronous checkpointing needs to be done  in two cases
	//a) after n new transactions have entered the system.
	//b) when graceful system shutdown - in both cases we need to wait for the currently exeucting
	//   trnsactions to commit and then do it. 
	//c) Identify the places to abort , and places to return false for all database query routines.
	//d) implmenen the 4 die/shutdown routines.
	//e) check if excception handling is done in all the routines. 
	//f) design logging.
	//g) design the recoevey mechanism - impkemnt the abort/commit functions.
	//h ) restart/startup functions - to read from the disk ,waht to read, perform recovery on startup.
	//i) Add volatile to some variables

	public ResourceManagerImpl() throws RemoteException {
		lockManager = new LockManager();
		activeTxns = new ConcurrentHashMap<Integer,Object>();
		flightTable = new ConcurrentHashMap<String, Flight>();
		carTable = new ConcurrentHashMap<String, Car>();
		hotelTable = new ConcurrentHashMap<String, Hotels>();
		reservationTable = new ConcurrentHashMap<String, HashSet<Reservation>>();
		reservedflights = new ConcurrentHashMap<String,Integer>();

		//<----------UNDOING--------------------->
		UndoIMTable = new ConcurrentHashMap<Integer,Stack<UndoIMLog>>();
		//</----------UNDOING--------------------->

		xidCounter = 1;
		callables = new HashSet<Callable<Integer>>();
		checkPointers = Executors.newFixedThreadPool(5); // how many threads do we want ?
		// this is a oconfigurable value.need to set it to optimal value.
		callables.add(new TableWriter((Object)flightTable,"flightTable"));
		callables.add(new TableWriter((Object)carTable,"carTable"));
		callables.add(new TableWriter((Object)hotelTable,"hotelTable"));
		callables.add(new TableWriter((Object)reservationTable,"reservationTable"));
		callables.add(new TableWriter((Object)flights,"flights"));
	}


	public void isValidTrxn(int xid)
			throws InvalidTransactionException
			{

		if(!activeTxns.contains(xidCounter)){

			throw new InvalidTransactionException(xid,"");
		}

		return ;

			}

	private void updateCheckPointVariables()
	{
		// if there is no shutdown , then checkpoointing is over, this means we can reset the stopAndWait flag.
		committedTrxns.set(0);
		synchronized(HashSetEmpty)
		{
			HashSetEmpty=HashSetEmpty.valueOf(true);
		}
		synchronized(enteredTxnsCount)
		{
			enteredTxnsCount=0;
		}
		synchronized(stopAndWait)
		{
			stopAndWait=stopAndWait.valueOf(false);
			stopAndWait.notifyAll();
		}

	}

	private void checkPoint (int tries) throws RemoteException
	{
		boolean failed=false;
		try
		{
			List<Future<Integer>> futures = checkPointers.invokeAll(callables);


			if(tries==3)
			{
				System.out.println("FATAL ERROR: Unable to commit.killing system!!!");
				dieNow();
			}

			for(Future<Integer> future : futures){
				if(future.get()==1)
				{
					System.out.println("FATAL ERROR: aiayaoooo checkpoint failed da deei!!!");
					failed=true;
				}
			}

		}
		catch(InterruptedException e)
		{
			System.out.println(" dafaq woke me up ?"+e.getMessage());
		}
		catch(ExecutionException e)
		{
			System.out.println("dafaq?"+e.getMessage());

		}
		if(failed)checkPoint(tries+1);
		LogWriter.flush();
		return;
		//executorService.shutdown();
	}

	private void stopIncoming() throws RemoteException
	{
		synchronized(stopAndWait)
		{
			if(!stopAndWait){
				stopAndWait=stopAndWait.valueOf(true);
			}
			else
				return;
			// {
			// 	//means stopandwait already raised due to some other condition.
			// 	//makes no difference , can only happen in case of shutdown/Cp or CP/shutdown.
			// 	// hence do nothing.
			// }
		}
		//wait for all transactions to get over. Sleep on the HashSetEmpty object.
		synchronized(HashSetEmpty){
			while(!HashSetEmpty)
			{
				try{

					HashSetEmpty.wait();
				}
				catch(InterruptedException e)
				{

					System.out.println("dafaq woke me up ?"+e.getMessage());
					System.out.println(" value of HashSetEmpty"+HashSetEmpty.toString());
				}
			}
		}

		//do a checkpoint always. if shutdown flag is enabled, then also shutdown the system.
		//code for checkpointing

		checkPoint(0);
		if(shuttingDown.get()>0)
			System.exit(0);
		updateCheckPointVariables();

		return;
	}

	// TRANSACTION INTERFACE
	public int start()
			throws RemoteException {
		int temp;
		synchronized(enteredTxnsCount)
		{
			synchronized(stopAndWait)
			{
				while(stopAndWait)
				{
					try{
						stopAndWait.wait();
					}
					catch(InterruptedException e)
					{

						System.out.println(" dafaq woke me up ?"+e.getMessage());
						System.out.println(" value of stopandwait"+stopAndWait.toString());
					}
				}
			}
			// do a checkpoint if atleast CPT transactions have entered, and atleast half of them have committed.
			if(enteredTxnsCount>=CHECKPOINT_TRIGGER && committedTrxns.get() >= (CHECKPOINT_TRIGGER/2))
			{
				stopIncoming(); //note here that the checkpointing is being done on a thread which has not been allocated a Xid yet.

			}//else check if already some process is trying to stop incoming
			if(activeTxns.contains(xidCounter)){
				// HOW TO HANDLE THIS ?
				System.out.println("SHOULD NOT REACH: XID DUPLICATE");
			}
			enteredTxnsCount++;
			temp=xidCounter++;
		}

		synchronized(HashSetEmpty)
		{
			activeTxns.put(temp,DUMMY);
			HashSetEmpty=HashSetEmpty.valueOf(false);
		}

		//<----------UNDOING--------------------->
		UndoIMTable.put(temp,new Stack<UndoIMLog>() );
		//</----------UNDOING--------------------->

		return (temp);
	}

	public void removeXID (int xid) throws InvalidTransactionException
	{
		isValidTrxn(xid);
		synchronized(activeTxns){
			activeTxns.remove(xid);
			if(activeTxns.size()==shuttingDown.get())
				HashSetEmpty=HashSetEmpty.valueOf(true);
			HashSetEmpty.notify();
		}

		UndoIMTable.remove(xid);
		lockManager.unlockAll(xid);
		return;
	}

	//TODO: Remove Xid from active Transactions
	public boolean commit(int xid)
			throws RemoteException, 
			TransactionAbortedException, 
			InvalidTransactionException {
		System.out.println("Committing");
		//TODO: when xid is removed from the hashset , see if the hashset becomes equal to the shuttingDown.get() value -
		// implies there are no more useful processes left. hence can shutdown the system.

		removeXID(xid);
		Future returnVal = executor.submit(new TransactionLogger(xid+" " + "COMMIT\n"));
		try
		{
			returnVal.get();
		}
		catch(Exception e)
		{
			System.out.println("Something hapened while retrieving value of atomic integer retunVal.Lets all zink about zees now"+e.getMessage());
		}
		LogWriter.flush();
		return true;
	}

	//<----------UNDOING--------------------->
	public void performUndo(UndoIMLog entry)
	{

		switch(entry.tableName)
		{
		case FlightTable:
			if(entry.operation==insert)
			{
				flightTable.remove(entry.Key);
			}
			else if(entry.operation == overWrite)
			{

				Flight oldData = (Flight)(entry.ObjPointer);
				Flight newData = flightTable.get(entry.Key);
				newData.copyFlight(oldData);
			}
			else if(entry.operation == delete)
			{
				flightTable.put(entry.Key,(Flight)(entry.ObjPointer));
			}
			break;
		case HotelTable:
			if(entry.operation==insert)
			{
				hotelTable.remove(entry.Key);
			}
			else if(entry.operation == overWrite)
			{

				Hotels oldData = (Hotels)(entry.ObjPointer);
				Hotels newData = hotelTable.get(entry.Key);
				newData.copyHotels(oldData);
			}
			else if(entry.operation == delete)//not required .. not going to happen..
			{
				hotelTable.put(entry.Key,(Hotels)(entry.ObjPointer));
			}
			break;
		case CarTable:
			if(entry.operation==insert)
			{
				carTable.remove(entry.Key);
			}
			else if(entry.operation == overWrite)
			{

				Car oldData = (Car)(entry.ObjPointer);
				Car newData = carTable.get(entry.Key);
				newData.copyCar(oldData);
			}
			else if(entry.operation == delete)
			{
				carTable.put(entry.Key,(Car)(entry.ObjPointer));
			}
			break;
		case ReservationTable:
			if(entry.operation==insert)
			{
				reservationTable.remove(entry.Key);
			}
			else if(entry.operation == partialInsert)
			{

				HashSet<Reservation> checkForFlights = reservationTable.get(entry.Key);
				checkForFlights.remove(entry.AuxKey);
			}
			else if(entry.operation == delete)
			{
				HashSet<Reservation> ref = (HashSet<Reservation>)(entry.ObjPointer);
				reservationTable.put(entry.Key,ref);
			}
			break;
		case Flights:
			if(entry.operation==insert)
			{
				flights.remove(entry.Key);
			}
			else if(entry.operation == overWrite)
			{

				Integer oldData = (Integer)(entry.ObjPointer);
				Integer newData = (Integer)flights.get(entry.Key);
				newData = oldData;
			}
			else if(entry.operation == delete)
			{
				//has to be decide based on kewals design.
			}
			break;
		default:
			System.out.println("should not freaking happen......");
			break;
		}
	}
	//</----------UNDOING--------------------->

	//TODO: Remove Xid from active Transactions
	public void abort(int xid)
			throws RemoteException, 
			InvalidTransactionException {
		//TODO: when xid is removed from the hashset , see if the hashset becomes empty, if so notify the hashSetEmpty thread.
		//TODO: undo all the work that ahs bee done by the transaction.
		//<----------UNDOING--------------------->
		Stack<UndoIMLog> undo = UndoIMTable.get(xid);
		UndoIMLog entry = null;
		while(!undo.empty())
		{
			entry = undo.peek();
			if(entry==null)
			{
				System.out.println("oh my god.... ! why the f$%^ is this null?");
			}
			performUndo(entry);
		}
		//</----------UNDOING--------------------->
		removeXID(xid);
		Future returnVal = executor.submit(new TransactionLogger(xid+" " + "ABORT\n"));
		try
		{
			returnVal.get();
		}
		catch(Exception e)
		{
			System.out.println("Something hapened while retrieving value of atomic integer retunVal.Lets all zink about zees now"+e.getMessage());
		}
		LogWriter.flush();
		return;
	}

	// ADMINISTRATIVE INTERFACE
	public boolean addFlight(int xid, String flightNum, int numSeats, int price) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		String lockString = "Flight."+flightNum;

		//Check if valid xid
		isValidTrxn(xid);

		try {
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO check handling
				//Handle false return value from lock
				//Parameters are Invalid, abort the transaction here.
				// Handle transaction aborted exception : Pass xid and "lock string" ???.
				throw new TransactionAbortedException(xid, lockString);
			}
		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		}

		int numAvail = numSeats;

		//<----------UNDOING--------------------->
		Flight OldVal = null;
		UndoIMLog logRec = null;
		//</----------UNDOING--------------------->

		StringBuilder logMsg = new StringBuilder("");
		if(flightTable.containsKey(flightNum)){
			Flight oldData = flightTable.get(flightNum);

			//<----------UNDOING--------------------->
			OldVal = new Flight(oldData);
			logRec = new UndoIMLog(FlightTable,overWrite,OldVal,flightNum,null);
			//</----------UNDOING--------------------->

			if(price>=0){
				oldData.setPrice(price);
				logMsg.append(xid).append("@#@").append("Flights@#@").append(flightNum).append("@#@").append("Price@#@").append(oldData.getPrice()).append("@#@").append(price).append("\n");
			}
			numAvail = numAvail + oldData.getNumAvail();
			oldData.setNumAvail(numAvail);
			logMsg.append(xid).append("@#@").append("Flights@#@").append(flightNum).append("@#@").append("NumAvail@#@").append(oldData.getNumAvail()).append("@#@").append(numAvail).append("\n");
			numSeats = numSeats + oldData.getNumSeats();
			oldData.setNumSeats(numSeats);
			logMsg.append(xid).append("@#@").append("Flights@#@").append(flightNum).append("@#@").append("NumSeats@#@").append(oldData.getNumSeats()).append("@#@").append(numSeats).append("\n");

		}
		else{
			Flight newData = new Flight(flightNum, price, numSeats, numAvail);

			//<----------UNDOING--------------------->
			logRec = new UndoIMLog(FlightTable,insert,null,flightNum,null);
			//</----------UNDOING--------------------->

			flightTable.put(flightNum, newData);
			logMsg.append(xid).append("@#@").append("Flights@#@").append(flightNum).append("@#@@#@INSERT\n");
			logMsg.append( xid).append("@#@").append("Flights@#@").append(flightNum).append("@#@").append("Price@#@").append("NULL").append("@#@").append(price).append("\n");
			logMsg.append( xid).append("@#@").append("Flights@#@").append(flightNum).append("@#@").append("NumAvail@#@").append("NULL").append("@#@").append(numAvail).append("\n");
			logMsg.append( xid).append("@#@").append("Flights@#@").append(flightNum).append("@#@").append("NumSeats@#@").append("NULL").append("@#@").append(numSeats).append("\n");
		}

		//<----------UNDOING--------------------->
		Stack<UndoIMLog> undo = UndoIMTable.get(xid);
		undo.push(logRec);
		//</----------UNDOING--------------------->

		executor.execute(new VariableLogger(logMsg.toString()));
		return true;
	}

	public boolean deleteFlight(int xid, String flightNum)
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		if(reservedflights.contains(flightNum) && reservedflights.get(flightNum)!=0){
			// Reservation on this flight exists.
			// TODO: Abort or return false ?
		}

		String lockString = "Flight."+flightNum;
		StringBuilder logMsg = new StringBuilder("");

		//Check if valid xid
		isValidTrxn(xid);

		try {
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO check handling
				//Handle false return value from lock
				//Parameters are Invalid, abort the transaction here.
				// Handle transaction aborted exception : Pass xid and "lock string" ???.
				throw new TransactionAbortedException(xid, lockString);
			}
		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		}
		if(!flightTable.contains(flightNum)){
			// Deleting a flight which does not exist
			// Return False ?
			return false;
		}

		//<----------UNDOING--------------------->
		Flight OldVal = flightTable.get(flightNum);
		UndoIMLog logRec = new UndoIMLog(FlightTable,delete,OldVal,flightNum,null);;
		//</----------UNDOING--------------------->

		flightTable.remove(flightNum);

		//<----------UNDOING--------------------->
		Stack<UndoIMLog> undo = UndoIMTable.get(xid);
		undo.push(logRec);
		//</----------UNDOING--------------------->

		logMsg.append(xid).append("@#@").append("Flights@#@").append(flightNum).append("@#@@#@DELETE\n");
		executor.execute(new VariableLogger(logMsg.toString()));
		return true;
	}

	/*
	 * Add rooms to a location.  
	 * This should look a lot like addFlight, only keyed on a location
	 * instead of a flight number.
	 *
	 * @return true on success, false on failure.
	 *
	 * @throws RemoteException on communications failure.
	 * @throws TransactionAbortedException if transaction was aborted.
	 * @throws InvalidTransactionException if transaction id is invalid.
	 *
	 * @see #addFlight
	 */
	public boolean addRooms(int xid, String location, int numRooms, int price) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//TODO: 
		//throw InvalidTransactionException;
		isValidTrxn(xid);
		try{
			if(location==null)
				return false;
			String lockString = "Hotels."+location;
			if(lockManager.lock(xid, lockString, WRITE) == false){
				return false;
				//TODO: to Abort/ return false

			}
			//<----------UNDOING--------------------->
			Hotels OldVal = null;
			UndoIMLog logRec = null;
			//</----------UNDOING--------------------->

			int numAvail = numRooms;
			StringBuilder logMsg = new StringBuilder("");
			if(hotelTable.containsKey(location)){

				Hotels oldData = hotelTable.get(location);

				//<----------UNDOING--------------------->
				OldVal = new Hotels(oldData);
				logRec = new UndoIMLog(HotelTable,overWrite,OldVal,location,null);
				//</----------UNDOING--------------------->

				if(price>=0){
					oldData.setPrice(price);
					logMsg.append(xid).append("@#@").append("Rooms@#@").append(location).append("@#@").append("Price@#@").append(oldData.getPrice()).append("@#@").append(price).append("\n");
				}
				numAvail = numAvail + oldData.getNumAvail();
				oldData.setNumAvail(numAvail);
				logMsg.append( xid).append("@#@").append("Rooms@#@").append(location).append("@#@").append("NumAvail@#@").append(oldData.getNumAvail()).append("@#@").append(numAvail).append("\n");
				numRooms = numRooms + oldData.getnumRooms();
				oldData.setNumRooms(numRooms);
				logMsg.append(xid).append("@#@").append("Rooms@#@").append(location).append("@#@").append("NumRooms@#@").append(oldData.getnumRooms()).append("@#@").append(numRooms).append("\n");
			}
			else{

				//<----------UNDOING--------------------->
				Hotels newData = new Hotels(location, price, numRooms, numAvail);
				logRec = new UndoIMLog(HotelTable,insert,null,location,null);
				//</----------UNDOING--------------------->

				hotelTable.put(location, newData);
				logMsg.append(xid).append("@#@").append("Rooms@#@").append(location).append("@#@@#@INSERT\n");
				logMsg.append(xid).append("@#@").append("Rooms@#@").append(location).append("@#@").append("Price@#@").append("NULL").append("@#@").append(price).append("\n");
				logMsg.append(xid).append("@#@").append("Rooms@#@").append(location).append("@#@").append("NumAvail@#@").append("NULL").append("@#@").append(numAvail).append("\n");
				logMsg.append(xid).append("@#@").append("Rooms@#@").append(location).append("@#@").append("NumRooms@#@").append("NULL").append("@#@").append(numRooms).append("\n");
			}

			//<----------UNDOING--------------------->
			Stack<UndoIMLog> undo = UndoIMTable.get(xid);
			undo.push(logRec);
			//</----------UNDOING--------------------->

			executor.execute(new VariableLogger(logMsg.toString()));
			return true;	
		}catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," throwing TransactionAbortedException due to deadlock");
		}
		catch( Exception e)	{
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," throwing TransactionAbortedException due to unknown exception"+e.getMessage());
		}
	}

	/**
	 * Delete rooms from a location.
	 * This subtracts from the available room count (rooms not allocated
	 * to a customer).  It should fail if it would make the count of
	 * available rooms negative.
	 *
	 * @return true on success, false on failure.
	 *
	 * @throws RemoteException on communications failure.
	 * @throws TransactionAbortedException if transaction was aborted.
	 * @throws InvalidTransactionException if transaction id is invalid.
	 *
	 * @see #deleteFlight
	 */
	public boolean deleteRooms(int xid, String location, int numRooms) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {

		//throw InvalidTransactionException;
		isValidTrxn(xid);
		try{
			if(location==null)
				return false;

			String lockString = "Hotels."+location;
			if(lockManager.lock(xid, lockString, WRITE) == false){
				return false;
			}
			StringBuilder logMsg = new StringBuilder("");
			int numAvail = 0;
			if(hotelTable.containsKey(location)){

				Hotels data = hotelTable.get(location);
				numAvail = data.getNumAvail();
				if(numRooms>numAvail)
					return false;

				//<----------UNDOING--------------------->
				Hotels OldVal = new Hotels(data);
				UndoIMLog logRec = new UndoIMLog(HotelTable,overWrite,OldVal,location,null);;
				Stack<UndoIMLog> undo = UndoIMTable.get(xid);
				undo.push(logRec);
				//</----------UNDOING--------------------->

				data.setNumAvail(numAvail-numRooms);
				logMsg.append(xid).append("@#@").append("Rooms@#@").append(location).append("@#@").append("NumAvail@#@").append(numAvail).append("@#@").append(numAvail - numRooms).append("\n");
				data.setNumRooms(data.getnumRooms()-numRooms);
				logMsg.append(xid).append("@#@").append("Rooms@#@").append(location).append("@#@").append("NumRooms@#@").append(data.getnumRooms()).append("@#@").append(data.getnumRooms() - numRooms).append("\n");
			}
			else{
				// should not happen ... if it happens return false.
				return false;
			}
			executor.execute(new VariableLogger(logMsg.toString()));
			return true;

		}catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," throwing TransactionAbortedException due to deadlock");
		}
		catch( Exception e)	{
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," throwing TransactionAbortedException due to unknown exception"+e.getMessage());
		}
	}


	public boolean addCars(int xid, String location, int numCars, int price) 
			throws RemoteException, 

			TransactionAbortedException,
			InvalidTransactionException {
		//TODO: Check if valid xid
		String lockString = "Cars."+location;
		StringBuilder logMsg = new StringBuilder("");
		try {
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here. 	
			}

			int numAvail = numCars;

			//<----------UNDOING--------------------->
			Car OldVal = null;
			UndoIMLog logRec = null;
			//</----------UNDOING--------------------->

			if(carTable.containsKey(location)){
				Car oldData = carTable.get(location);

				//<----------UNDOING--------------------->
				OldVal = new Car(oldData);
				logRec = new UndoIMLog(CarTable,overWrite,OldVal,location,null);
				//</----------UNDOING--------------------->

				if(price>=0){
					oldData.setPrice(price);
					logMsg.append(xid).append("@#@").append("Cars@#@").append(location).append("@#@").append("Price@#@").append(oldData.getPrice()).append("@#@").append(price).append("\n");
				}
				numAvail = numAvail + oldData.getNumAvail();
				oldData.setNumAvail(numAvail);
				logMsg.append( xid).append("@#@").append("Cars@#@").append(location).append("@#@").append("NumAvail@#@").append(oldData.getNumAvail()).append("@#@").append(numAvail).append("\n");
				numCars = numCars + oldData.getNumCars();
				oldData.setNumCars(numCars);
				logMsg.append(xid).append("@#@").append("Cars@#@").append(location).append("@#@").append("NumCars@#@").append(oldData.getNumCars()).append("@#@").append(numCars).append("\n");
			}
			else{
				Car newData = new Car(location, price, numCars, numAvail);
				carTable.put(location, newData);

				//<----------UNDOING--------------------->
				logRec = new UndoIMLog(CarTable,insert,null,location,null);
				//</----------UNDOING--------------------->
				logMsg.append(xid).append("@#@").append("Cars@#@").append(location).append("@#@@#@INSERT\n");
				logMsg.append(xid).append("@#@").append("Cars@#@").append(location).append("@#@").append("Price@#@").append("NULL").append("@#@").append(price).append("\n");
				logMsg.append(xid).append("@#@").append("Cars@#@").append(location).append("@#@").append("NumAvail@#@").append("NULL").append("@#@").append(numAvail).append("\n");
				logMsg.append(xid).append("@#@").append("Cars@#@").append(location).append("@#@").append("NumCars@#@").append("NULL").append("@#@").append(numCars).append("\n");
			}
			////<----------UNDOING--------------------->
			Stack<UndoIMLog> undo = UndoIMTable.get(xid);
			undo.push(logRec);
			//</----------UNDOING--------------------->

		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		} catch (Exception e) {
			//Throw aborted exception
			throw new TransactionAbortedException(xid, "Transaction aborted for unknown reasons" + "MSG: " + e.getMessage());
		}
		executor.execute(new VariableLogger(logMsg.toString()));
		return true;
	}

	public boolean deleteCars(int xid, String location, int numCars) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//TODO: Check if valid xid
		String lockString = "Cars."+location;
		StringBuilder logMsg = new StringBuilder("");
		try {
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here. 	
			}

			if(carTable.containsKey(location)){
				Car oldData = carTable.get(location);
				int numCarsAvail = oldData.getNumAvail();
				if(numCarsAvail >= numCars){
					//Delete successfully
					logMsg.append(xid).append("@#@").append("Cars@#@").append(location).append("@#@").append("NumAvail@#@").append(numCarsAvail).append("@#@").append(numCarsAvail - numCars).append("\n");

					//<----------UNDOING--------------------->
					Car OldVal = new Car(oldData);
					UndoIMLog logRec = new UndoIMLog(CarTable,overWrite,OldVal,location,null);;
					Stack<UndoIMLog> undo = UndoIMTable.get(xid);
					undo.push(logRec);
					//</----------UNDOING--------------------->

					oldData.setNumAvail(numCarsAvail - numCars);
					logMsg.append(xid).append("@#@").append("Cars@#@").append(location).append("@#@").append("NumCars@#@").append(oldData.getNumCars()).append("@#@").append(oldData.getNumCars() - numCars).append("\n");
					oldData.setNumCars(oldData.getNumCars() - numCars);
				}else{
					//TODO: Invalid parameters, abort transaction/ return false
				}
			}else{
				//TODO: no cars in the location. Abort/Ignore transaction
			}
		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		} catch (Exception e) {
			//Throw aborted exception
			throw new TransactionAbortedException(xid, "Transaction aborted for unknown reasons" + "MSG: " + e.getMessage());

		}
		executor.execute(new VariableLogger(logMsg.toString()));
		return true;
	}

	// Make a new entry in Reservations Table for this Customer.
	// If customer already exists ?
	public boolean newCustomer(int xid, String custName) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		isValidTrxn(xid);

		// Null Customer Name
		if(custName==null)
			return false;
		try{
			// Acquire Lock
			String lockString = "Reservations." + custName;
			if(lockManager.lock(xid, lockString, READ) == false){
				return false;
			}

			//Check if customer already exists
			//ASK KEWAL TO CHANGE THIS>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
			if(!reservationTable.contains(custName)){
				reservationTable.put(custName, new HashSet<Reservation>());

				//<----------UNDOING--------------------->
				UndoIMLog logRec = new UndoIMLog(ReservationTable,insert,null,custName,null);
				Stack<UndoIMLog> undo = UndoIMTable.get(xid);
				undo.push(logRec);
				//</----------UNDOING--------------------->

			}

		}catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," Transaction aborted because of deadlock detected in:");
		}
		catch( Exception e)	{
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid, "Transaction aborted because of error detected in:"+e.getMessage());
		}
		return true;
	}

	public boolean deleteCustomer(int xid, String custName) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		// Null Customer Name
		if(custName==null)
			return false;
		try{
			// Acquire Lock
			String lockString = "Reservations." + custName;
			if(lockManager.lock(xid, lockString, READ) == false){
				return false;
			}

			//Check if customer exists
			if(!reservationTable.contains(custName)){
				//TODO: Handle not existing Customers
			}

			//Over Here Customer exists
			//Check if customer has made any flight reservations
			HashSet<Reservation> checkForFlights = reservationTable.get(custName);
			UndoIMLog logRec = null;
			Stack<UndoIMLog> undo = null;
			undo = UndoIMTable.get(xid);

			for (Reservation r : checkForFlights) {
				String key = r.getResKey();
				int numAvail = 0;
				switch(r.getResType()){
				case 1:
					// Reduce number of seats reserved in reserved flights
					int avail = (Integer)reservedflights.get(key);

					//<----------UNDOING--------------------->
					Integer oldVal= new Integer(avail);
					logRec = new UndoIMLog(Flights,overWrite,oldVal,key,null);
					undo.push(logRec);
					//</----------UNDOING--------------------->

					reservedsflights.put(r.getResKey(),avail-1);

					// Increase number of seats available in that particular flight
					Flight flight = flightTable.get(key);

					//<----------UNDOING--------------------->
					Flight oldValF= new Flight(flight);
					logRec = new UndoIMLog(FlightTable,overWrite,oldValF,key,null);
					undo.push(logRec);
					//</----------UNDOING--------------------->

					numAvail = flight.getNumAvail();
					flight.setNumAvail(numAvail+1);

					break;
				case 2:
					// Increase number of rooms available in that particular Hotel Location
					Hotels hotel = hotelTable.get(key);

					//<----------UNDOING--------------------->
					Hotels oldValH= new Hotels(hotel);
					logRec = new UndoIMLog(HotelTable,overWrite,oldValH,key,null);
					undo.push(logRec);
					//</----------UNDOING--------------------->

					numAvail = hotel.getNumAvail();
					hotel.setNumAvail(numAvail+1);
					break;
				case 3:
					// Increase number of cars available in that particular Car location
					Car car = carTable.get(key);

					//<----------UNDOING--------------------->
					Car oldValC= new Car(car);
					logRec = new UndoIMLog(CarTable,overWrite,oldValC,key,null);
					undo.push(logRec);
					//</----------UNDOING--------------------->

					numAvail = car.getNumAvail();
					car.setNumAvail(numAvail+1);
					break;
				default:
					break;
				}
			}

			reservationTable.remove(custName);

			//<----------UNDOING--------------------->
			logRec = new UndoIMLog(ReservationTable,delete,checkForFlights,custName,null);
			undo = UndoIMTable.get(xid);
			undo.push(logRec);
			//</----------UNDOING--------------------->

		}catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," Transaction aborted because of deadlock detected in:");
		}
		catch( Exception e)	{
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid, "Transaction aborted because of error detected in:"+e.getMessage());
		}
		return true;
	}


	// QUERY INTERFACE
	public int queryFlight(int xid, String flightNum)
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//Check for invalid xid.
		isValidTrxn(xid);

		if(flightNum == null)
			throw new InvalidTransactionException(xid, "message");

		// Acquiring read locks
		String lockString = "Flight."+flightNum;
		try {
			if(lockManager.lock(xid, lockString, READ) == false){
				//TODO check handling
				//Handle false return value from lock
				//Parameters are Invalid, abort the transaction here.
				// Handle transaction aborted exception : Pass xid and "lock string" ???.
				throw new TransactionAbortedException(xid, lockString);
			}
		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		}
		if(!flightTable.contains(flightNum)){
			//TODO:
			//Decide on how to handle if flight is not present in database 
			//1. return 0 2. Abort transaction 3. invalid transaction
		};
		Flight flight = flightTable.get(flightNum);
		return flight.getNumSeats();
	}

	public int queryFlightPrice(int xid, String flightNum)
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//Check for invalid xid.
		isValidTrxn(xid);

		if(flightNum == null)
			throw new InvalidTransactionException(xid, "message");

		// Acquiring read locks
		String lockString = "Flight."+flightNum;
		try {
			if(lockManager.lock(xid, lockString, READ) == false){
				//TODO check handling
				//Handle false return value from lock
				//Parameters are Invalid, abort the transaction here.
				// Handle transaction aborted exception : Pass xid and "lock string" ???.
				throw new TransactionAbortedException(xid, lockString);
			}
		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		}
		if(!flightTable.contains(flightNum)){
			//TODO:
			//Decide on how to handle if flight is not present in database 
			//1. return 0 2. Abort transaction 3. invalid transaction
		};
		Flight flight = flightTable.get(flightNum);
		return flight.getPrice();
	}

	/**
	 * Return the number of rooms available at a location. 
	 * Implies whole deletion of the location record: all cars, all reservations.
	 * Should fail if a customer has booked a car from this location.
	 *
	 * @param xid id of transaction.
	 * @param location , cannot be null.
	 * @return 0 on 0 availability or absence of record, else return available 
	 *
	 * @throws RemoteException on communications failure.
	 * @throws TransactionAbortedException if transaction was aborted.
	 * @throws InvalidTransactionException if transaction id is invalid.
	 *
	 * @see #deleteRooms
	 * @see #deleteFlight
	 */
	public int queryRooms(int xid, String location)
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//TODO: 
		//throw InvalidTransactionException;
		isValidTrxn(xid);

		if(location==null)
			return 0;
		try{
			String lockString = "Hotels."+location;
			if(lockManager.lock(xid, lockString, READ) == false){
				return 0;
			}
			int numAvail =0;
			if(hotelTable.containsKey(location)){

				Hotels oldData = hotelTable.get(location);
				numAvail = oldData.getNumAvail();
			}
			return numAvail;	
		}catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," throwing TransactionAbortedException due to deadlock");
		}
		catch( Exception e)	{
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," throwing TransactionAbortedException due to unknown exception"+e.getMessage());
		}
		//		return 0;
	}
	/**
	 * Return the price of rooms at this location.
	 * Implies whole deletion of the location record: all cars, all reservations.
	 * Should fail if a customer has booked a car from this location.
	 *
	 * @param xid id of transaction.
	 * @param location , cannot be null.
	 * @return 0 on 0 availability or absence of record, else return actual price.
	 *
	 * @throws RemoteException on communications failure.
	 * @throws TransactionAbortedException if transaction was aborted.
	 * @throws InvalidTransactionException if transaction id is invalid.
	 *
	 * @see #deleteRooms
	 * @see #deleteFlight
	 */

	public int queryRoomsPrice(int xid, String location)
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//TODO: 
		//throw InvalidTransactionException;
		isValidTrxn(xid);
		if(location==null)
			return 0;
		try{
			String lockString = "Hotels."+location;
			if(lockManager.lock(xid, lockString, READ) == false){
				return 0;
			}
			int price =0;
			if(hotelTable.containsKey(location)){

				Hotels oldData = hotelTable.get(location);
				price = oldData.getPrice();
			}
			return price;	
		}catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," throwing TransactionAbortedException due to deadlock");
		}
		catch( Exception e)	{
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," throwing TransactionAbortedException due to unknown exception"+e.getMessage());
		}
		//		return 0;
	}

	public int queryCars(int xid, String location)
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//TODO: Check if valid xid and location null -> throw invalid transaction exception
		String lockString = "Cars."+location;
		try {
			if(lockManager.lock(xid, lockString, READ) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here. 	
			}

			if(carTable.containsKey(location)){
				return carTable.get(location).getNumAvail();
			}else{
				//TODO: Should we throw an exception here?
				return 0;
			}
		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		} catch (Exception e) {
			//Throw aborted exception
			throw new TransactionAbortedException(xid, "Transaction aborted for unknown reasons" + "MSG: " + e.getMessage());
		}

		return 0;
	}

	public int queryCarsPrice(int xid, String location)
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//TODO: Check if valid xid
		String lockString = "Cars."+location;
		try {
			if(lockManager.lock(xid, lockString, READ) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here. 	
			}

			if(carTable.containsKey(location)){
				return carTable.get(location).getPrice();
			}else{
				//TODO: Should we throw an exception here?
				return 0;
			}
		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		} catch (Exception e) {
			//Throw aborted exception
			throw new TransactionAbortedException(xid, "Transaction aborted for unknown reasons" + "MSG: " + e.getMessage());
		}

		return 0;
	}

	public int queryCustomerBill(int xid, String custName)
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		return 0;
	}


	// RESERVATION INTERFACE
	public boolean reserveFlight(int xid, String custName, String flightNum) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		// Check for valid xid
		isValidTrxn(xid);

		String lockString = "Flight." + flightNum;
		try {
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here. 	
			}
			lockString = "Reservations."+custName;
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here.
			}
		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		}
		int numAvail = 0;
		if(!flightTable.contains(flightNum)){
			//Trying to make a resrvation on a flight that does not exist
			throw new TransactionAbortedException(xid, "Trying to make a resrvation on a flight that does not exist");
		}
		Flight data = flightTable.get(flightNum);
		int avail = data.getNumAvail();
		if(avail < 1){
			//No Seats Available. Flight is full
			throw new TransactionAbortedException(xid, "No Seats Available");
		}

		//<----------UNDOING--------------------->//entry for flights table
		Flight oldVal = new Flight(data);
		UndoIMLog logRec=new UndoIMLog(FlightTable,overWrite,oldVal,flightNum,null);
		Stack<UndoIMLog> undo = UndoIMTable.get(xid);
		undo.push(logRec);
		//</----------UNDOING--------------------->

		Reservation newReservation = new Reservation(custName, 1, flightNum);
		HashSet<Reservation> reservations;
		StringBuilder logMsg = new StringBuilder("");
		if(reservationTable.containsKey(custName)){
			//Customer has a reservation
			reservations = reservationTable.get(custName);
			if(reservations.contains(newReservation)){
				//TODO: Duplicate reservation. What to do??
				//Should abort or return for sure.
			}
		}else{
			// First reservation for this customer.
			// Create a new hashset

			//<----------UNDOING--------------------->//entry for reservations table.
			logRec = new UndoIMLog(ReservationTable,insert,null,custName,null);
			//<----------UNDOING--------------------->

			reservations = new HashSet<Reservation>();
			reservationTable.put(custName, reservations);
		}

		//<----------UNDOING--------------------->
		if(logRec==null)
			logRec = new UndoIMLog(ReservationTable,partialInsert,null,custName,newReservation);
		undo.push(logRec);
		//<----------UNDOING--------------------->

		//Sure of making a reservation
		reservations.add(newReservation);
		logMsg.append(xid).append("@#@").append("Reservations@#@").append(newReservation.toString()).append("@#@@#@INSERT\n");

		//Make entry in flights because reservation is made
		if(!flights.containsKey(flightNum))
		{
			flights.put(flightNum,1);

			//<----------UNDOING--------------------->
			logRec = new UndoIMLog(FlightTable,insert,null,flightNum,null);
			//<----------UNDOING--------------------->
		}
		else
		{

			//<----------UNDOING--------------------->
			Integer num=(Integer)reservedflights.get(flightNum);
			logRec = new UndoIMLog(FlightTable,overWrite,num,flightNum,null);
			//<----------UNDOING--------------------->

			reservedflights.put(flightNum, num+1);
		}

		//Decrement number of available seats
		data.setNumAvail(avail - 1);

		//</----------UNDOING--------------------->
		undo.push(logRec);
		//</----------UNDOING--------------------->

		logMsg.append(xid).append("@#@").append("Flights@#@").append(flightNum).append("@#@").append("NumAvail@#@").append(avail).append("@#@").append(avail - 1).append("\n");
		executor.execute(new VariableLogger(logMsg.toString()));
		return true;
	}

	public boolean reserveCar(int xid, String custName, String location) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//TODO: Check if valid xid
		//Acquire an XLock for cars record
		String lockString = "Cars." + location;
		try {
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here. 	
			}
			lockString = "Reservations."+custName;
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here.
			}
		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		}
		int numCarsAvail = 0;
		Car data = carTable.get(location);
		if(data != null){
			numCarsAvail = data.getNumAvail();
		}
		if(!(numCarsAvail > 0)){
			//TODO: abort/return false
		}

		//<----------UNDOING--------------------->//entry for flights table
		Car oldVal = new Car(data);
		UndoIMLog logRec=new UndoIMLog(CarTable,overWrite,oldVal,location,null);
		Stack<UndoIMLog> undo = UndoIMTable.get(xid);
		undo.push(logRec);
		//</----------UNDOING--------------------->	

		Reservation newReservation = new Reservation(custName, 3, location);
		HashSet<Reservation> reservations;
		StringBuilder logMsg = new StringBuilder("");
		if(reservationTable.containsKey(custName)){
			//Customer has a reservation
			reservations = reservationTable.get(custName);
			if(reservations.contains(newReservation)){
				//TODO: Duplicate reservation. What to do??
				//Should abort or return for sure.
			}

		}else{
			//<----------UNDOING--------------------->//entry for reservations table.
			logRec = new UndoIMLog(ReservationTable,insert,null,custName,null);
			//<----------UNDOING--------------------->

			reservations = new HashSet<Reservation>();
			reservationTable.put(custName, reservations);
		}

		//<----------UNDOING--------------------->
		if(logRec==null)
			logRec = new UndoIMLog(ReservationTable,partialInsert,null,custName,newReservation);
		undo.push(logRec);
		//<----------UNDOING--------------------->

		//Sure of making a reservation
		reservations.add(newReservation);
		logMsg.append(xid).append("@#@").append("Reservations@#@").append(newReservation.toString()).append("@#@@#@INSERT\n");
		data.setNumAvail(numCarsAvail - 1);
		logMsg.append(xid).append("@#@").append("Cars@#@").append(location).append("@#@").append("NumAvail@#@").append(numCarsAvail).append("@#@").append(numCarsAvail - 1).append("\n");
		executor.execute(new VariableLogger(logMsg.toString()));
		return true;
	}

	public boolean reserveRoom(int xid, String custName, String location) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//TODO: Check if valid xid
		//Acquire an XLock for cars record
		isValidTrxn(xid);	
		try{
			String lockString = "Hotels." + location;

			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here. 	
			}
			lockString = "Reservations."+custName;
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here.
			}

			int numRoomsAvail = 0;
			Hotels data = hotelTable.get(location);
			if(data != null){
				numRoomsAvail = data.getNumAvail();
			}
			if(!(numRoomsAvail > 0)){
				//TODO: abort/return false
			}
			//<----------UNDOING--------------------->//entry for flights table
			Hotels oldVal = new Hotels(data);
			UndoIMLog logRec=new UndoIMLog(HotelTable,overWrite,oldVal,location,null);
			Stack<UndoIMLog> undo = UndoIMTable.get(xid);
			undo.push(logRec);
			//</----------UNDOING--------------------->	

			Reservation newReservation = new Reservation(custName, 2, location);
			HashSet<Reservation> reservations;
			StringBuilder logMsg = new StringBuilder("");
			if(reservationTable.containsKey(custName)){
				//Customer has a reservation
				reservations = reservationTable.get(custName);
				if(reservations.contains(newReservation)){
					//TODO: Duplicate reservation. What to do??
					//Should abort or return for sure.
				}

			}else{
				//<----------UNDOING--------------------->//entry for reservations table.
				logRec = new UndoIMLog(ReservationTable,insert,null,custName,null);
				//<----------UNDOING--------------------->

				reservations = new HashSet<Reservation>();
				reservationTable.put(custName, reservations);
			}

			//<----------UNDOING--------------------->
			if(logRec==null)
				logRec = new UndoIMLog(ReservationTable,partialInsert,null,custName,newReservation);
			undo.push(logRec);
			//<----------UNDOING--------------------->

			//Sure of making a reservation
			reservations.add(newReservation);
			logMsg.append(xid).append("@#@").append("Reservations@#@").append(newReservation.toString()).append("@#@@#@INSERT\n");
			data.setNumAvail(numRoomsAvail - 1);
			logMsg.append(xid).append("@#@").append("Rooms@#@").append(location).append("@#@").append("NumAvail@#@").append(numRoomsAvail).append("@#@").append(numRoomsAvail - 1).append("\n");
			executor.execute(new VariableLogger(logMsg.toString()));
			return true;
		}
		catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," throwing TransactionAbortedException due to deadlock");
		}
		catch( Exception e)	{
			e.printStackTrace();
			abort(xid);
			throw new TransactionAbortedException(xid," throwing TransactionAbortedException due to unknown exception"+e.getMessage());
		}
		//				return true;
	}



	// TECHNICAL/TESTING INTERFACE
	public boolean shutdown()
			throws RemoteException {

		shuttingDown.incrementAndGet();
		stopIncoming();
		try
		{
			Thread.sleep(SLEEPSHUTDOWN);
		}
		catch(InterruptedException e)
		{
			System.out.println("not enough sleep time.. increase it");

		}
		//testcases have to be analysed as to whether this thread has to freeze or can it return?
		return true;
	}

	public boolean dieNow() 
			throws RemoteException {
		System.exit(1);
		return true; // We won't ever get here since we exited above;
		// but we still need it to please the compiler.
	}

	public boolean dieBeforePointerSwitch() 
			throws RemoteException {
		return true;
	}

	public boolean dieAfterPointerSwitch() 
			throws RemoteException {
		return true;
	}

	//RECOVERY/ STARTUP INTERFACE

	public void loadFiles() throws RemoteException{
		LoadFiles loadObject = new LoadFiles(checkPointers);
		loadObject.loadSetup();
		if(loadObject.load(0)==false){
			// Shut the system down
			dieNow();
		}

		flightTable = (ConcurrentHashMap<String, Flight>) loadObject.getTR("flightTable").getTable();
		carTable = (ConcurrentHashMap<String, Car>) loadObject.getTR("carTable").getTable();
		hotelTable = (ConcurrentHashMap<String, Hotels>) loadObject.getTR("hotelTable").getTable();;
		reservationTable = (ConcurrentHashMap<String, HashSet<Reservation>>) loadObject.getTR("reservationTable").getTable();;
		reservedflights = (ConcurrentHashMap<String,Integer>) loadObject.getTR("reservedflights").getTable();;

	}
}
