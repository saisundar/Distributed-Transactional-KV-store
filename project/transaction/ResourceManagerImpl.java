package project.transaction;

import project.lockmgr.*;
import project.recovery.LoadFiles;
import project.transaction.bean.*;

import java.rmi.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/** 
 * Resource Manager for the Distributed Travel Reservation System.
 * 
 * Description: toy implementation of the RM, for initial testing
 */

public class ResourceManagerImpl 
extends java.rmi.server.UnicastRemoteObject
implements ResourceManager {

	//Book keeping
	private ConcurrentHashMap<Integer,Object> activeTxns;
	private LockManager lockManager;
	private static volatile AtomicInteger shuttingDown = new AtomicInteger();
	private volatile AtomicInteger committedTrxns = new  AtomicInteger();
	private volatile int enteredTxnsCount=0;
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

	protected int xidCounter;

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
		xidCounter = 1;
		activeTxnsCount = 0 ;
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
	private void stopIncoming()
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
				HashSetEmpty.wait();
		}

		//do a checkpoint always. if shutdown flag is enabled, then also shutdown the system.
		//code for checkpointing

		checkPoint();
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
					stopAndWait.wait();
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
		return (temp);
	}

	public void removeXID(int xid) throws InvalidTransactionException
	{
		isValidTrxn(xid);
		synchronized(activeTxns){
			activeTxns.remove(xid);
			if(activeTxns.size()==shuttingDown.get())
				HashSetEmpty=HashSetEmpty.valueOf(true);
			HashSetEmpty.notify();
		}
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
		return true;
	}

	//TODO: Remove Xid from active Transactions
	public void abort(int xid)
			throws RemoteException, 
			InvalidTransactionException {
		//TODO: when xid is removed from the hashset , see if the hashset becomes empty, if so notify the hashSetEmpty thread.
		//TODO: undo all the work that ahs bee done by the transaction.
		removeXID(xid);
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
		if(flightTable.containsKey(flightNum)){
			Flight oldData = flightTable.get(flightNum);
			if(price>=0)oldData.setPrice(price);
			numAvail = numAvail + oldData.getNumAvail();
			oldData.setNumAvail(numAvail);
			numSeats = numSeats + oldData.getNumSeats();
			oldData.setNumSeats(numSeats);
		}
		else{
			Flight newData = new Flight(flightNum, price, numSeats, numAvail);
			flightTable.put(flightNum, newData);
		}
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

		//Check if valid xid
		isValidTrxn(xid);

		try {
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO check handling
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

		flightTable.remove(flightNum);
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

			int numAvail = numRooms;
			if(hotelTable.containsKey(location)){

				Hotels oldData = hotelTable.get(location);
				if(price>=0)oldData.setPrice(price);
				numAvail = numAvail + oldData.getNumAvail();
				oldData.setNumAvail(numAvail);
				numRooms = numRooms + oldData.getnumRooms();
				oldData.setNumRooms(numRooms);
			}
			else{

				Hotels newData = new Hotels(location, price, numRooms, numAvail);
				hotelTable.put(location, newData);
			}
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

			int numAvail = 0;
			if(hotelTable.containsKey(location)){

				Hotels data = hotelTable.get(location);
				numAvail = data.getNumAvail();
				if(numRooms>numAvail)
					return false;
				data.setNumAvail(numAvail-numRooms);
				data.setNumRooms(data.getnumRooms()-numRooms);
			}
			else{
				// should not happen ... if it happens return false.
				return false;
			}
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
		try {
			if(lockManager.lock(xid, lockString, WRITE) == false){
				//TODO: Handle false return value from lock
				//Parameters are Invalid, abort the transaction here. 	
			}

			int numAvail = numCars;
			if(carTable.containsKey(location)){
				Car oldData = carTable.get(location);
				if(price>=0)oldData.setPrice(price);
				numAvail = numAvail + oldData.getNumAvail();
				oldData.setNumAvail(numAvail);
				numCars = numCars + oldData.getNumCars();
				oldData.setNumCars(numCars);
			}
			else{
				Car newData = new Car(location, price, numCars, numAvail);
				carTable.put(location, newData);
			}
		} catch (DeadlockException e) {
			// TODO: Handle DeadLock !
			e.printStackTrace();
		} catch (Exception e) {
			//Throw aborted exception
			throw new TransactionAbortedException(xid, "Transaction aborted for unknown reasons" + "MSG: " + e.getMessage());
		}

		return true;
	}

	public boolean deleteCars(int xid, String location, int numCars) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		//TODO: Check if valid xid
		String lockString = "Cars."+location;
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
					oldData.setNumAvail(numCarsAvail - numCars);
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
			if(reservationTable.contains(custName)){
				//TODO: Handle duplicate Customers
			}
			reservationTable.put(custName, new HashSet<Reservation>());
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
			for (Reservation r : checkForFlights) {
				String key = r.getResKey();
				int numAvail = 0;
				switch(r.getResType()){
				case 1:
					// Reduce number of seats reserved in reserved flights
					int avail = reservedflights.get(key);
					reservedflights.put(r.getResKey(),avail-1);
					
					// Increase number of seats available in that particular flight
					Flight flight = flightTable.get(key);
					numAvail = flight.getNumAvail();
					flight.setNumAvail(numAvail+1);
					break;
				case 2:
					// Increase number of rooms available in that particular Hotel Location
					Hotels hotel = hotelTable.get(key);
					numAvail = hotel.getNumAvail();
					hotel.setNumAvail(numAvail+1);
					break;
				case 3:
					// Increase number of cars available in that particular Car location
					Car car = carTable.get(key);
					numAvail = car.getNumAvail();
					car.setNumAvail(numAvail+1);
					break;
				default:
					break;
				}
			}
			reservationTable.remove(custName);
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


		Reservation newReservation = new Reservation(custName, 1, flightNum);
		HashSet<Reservation> reservations;
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
			reservations = new HashSet<Reservation>();
			reservationTable.put(custName, reservations);
		}

		//Sure of making a reservation
		reservations.add(newReservation);

		//Make entry in reservedflights because reservation is made
		reservedflights.put(flightNum, reservedflights.get(flightNum)+1);

		//Decrement number of available seats
		data.setNumAvail(avail - 1);
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


		Reservation newReservation = new Reservation(custName, 3, location);
		HashSet<Reservation> reservations;
		if(reservationTable.containsKey(custName)){
			//Customer has a reservation
			reservations = reservationTable.get(custName);
			if(reservations.contains(newReservation)){
				//TODO: Duplicate reservation. What to do??
				//Should abort or return for sure.
			}

		}else{
			reservations = new HashSet<Reservation>();
			reservationTable.put(custName, reservations);
		}

		//Sure of making a reservation
		reservations.add(newReservation);
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

			Reservation newReservation = new Reservation(custName, 2, location);
			HashSet<Reservation> reservations;
			if(reservationTable.containsKey(custName)){
				//Customer has a reservation
				reservations = reservationTable.get(custName);
				if(reservations.contains(newReservation)){
					//TODO: Duplicate reservation. What to do??
					//Should abort or return for sure.
				}

			}else{
				reservations = new HashSet<Reservation>();
				reservationTable.put(custName, reservations);
			}

			//Sure of making a reservation
			reservations.add(newReservation);
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

		shuttingDown.incremenetAndGet();
		stopIncoming();
		Thread.sleep(SleepShutDown);
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
