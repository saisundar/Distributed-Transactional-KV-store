	package project.transaction;

	import project.lockmgr.*;
	import project.transaction.bean.*;

	import java.rmi.*;
	import java.util.HashSet;
	import java.util.concurrent.ConcurrentHashMap;

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

		private static Boolean stopAndWait = new Boolean(false);
		private static Boolean HashSetEmpty = new Boolean(false);

		// Data Sets
		private ConcurrentHashMap<String,Flight> flightTable;
		private ConcurrentHashMap<String,Cars> carTable;
		private ConcurrentHashMap<String,Hotels> hotelTable;
		private ConcurrentHashMap<String,Flight> reservationTable;

		// in this toy, we don't care about location or flight number
		protected int flightcounter, flightprice, carscounter, carsprice, roomscounter, roomsprice;

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
		//1.  Routine to check for active xids == this requires a hashset of active Xids..
		// hashtables for each relation == will throw the invalid
		//2.  We need to appened the name of the relation to the string .. for eg:
		// IRVINE_c for cars in irvine, and IRVINE_h for rooms in irvine ,so that locks dont clash.
		// we need to also lock the xid when its being returned for the start of each trasnction. We CANNOT assume the incrementation is atomic.
		//3.  we need to handle the exceptions everywree in all routines:
		//	a)Deadlocks
		//	b)invalid transactions 
		//	c)redundant Lock exceptions :
		//	d)throw trascated aborted exception based on unidentified exceptions or deadlocks..
		//	e)add a logging routine, which has to be invoke from all other routines. 
		//4. synchronous checkpointing needs to be done  in two cases
		//a)  after n new transactions have entered the system.
		// b) when graceful system shutdown - in both cases we need to wait for the currently exeucting
		// trnsactions to commit and then do it. 
		// c)  

		//
		public ResourceManagerImpl() throws RemoteException {
			lockManager = new LockManager();
			activeTxns = new ConcurrentHashMap<Integer,Object>();
			flightTable = new ConcurrentHashMap<String, Flight>();
			carTable = new ConcurrentHashMap<String, Flight>();
			hotelTable = new ConcurrentHashMap<String, Flight>();
			reservationTable = new ConcurrentHashMap<String, Flight>();
			xidCounter = 1;
		}


		public void isValidTrxn(int xid)
		throws InvalidTransactionException
		{

			if(!activeTxns.contains(xidCounter)){
			
					throw new InvalidTransactionException();
			}

			return ;

		}

		private void stopIncoming()
		{

			synchronised(stopAndWait)
			{
				if(!stopAndWait){
					stopAndWait=stopAndWait.valueOf(TRUE);
					
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
			synchronised(HashSetEmpty){}
			while(HashSetEmpty)
				HashSetEmpty.wait();
			}
			// need to add code in places where removing elements from the hashset and it becomes empty , wake up everyone sleeping on it.
			return;
		}

		// TRANSACTION INTERFACE
		public int start()
		throws RemoteException {
			
				synchronised(stopAndWait)
				{
					while(stopAndWait)
						stopAndWait.wait();
				}

				if(activeTxns.contains(xidCounter)){
					// HOW TO HANDLE THIS ?
					System.out.println("SHOULD NOT REACH: XID DUPLICATE");
				}
				synchronised("dummy"){
				int temp=xidCounter++;
			}
				activeTxns.add(temp,DUMMY);
				return (temp);
		}

		//TODO: Remove Xid from active Transactions
		public boolean commit(int xid)
		throws RemoteException, 
		TransactionAbortedException, 
		InvalidTransactionException {
			System.out.println("Committing");
			return true;
		}

		//TODO: Remove Xid from active Transactions
		public void abort(int xid)
		throws RemoteException, 
		InvalidTransactionException {
			return;
		}


		// ADMINISTRATIVE INTERFACE
		public boolean addFlight(int xid, String flightNum, int numSeats, int price) 
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
			//TODO: Check if valid xid
			String lockString = "Flight."+flightNum;
			try {
				if(lockManager.lock(xid, lockString, WRITE) == false){
					//TODO: Handle false return value from lock
					//Parameters are Invalid, abort the transaction here. 	
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
			flightcounter += numSeats;
			flightprice = price;
			return true;
		}

		public boolean deleteFlight(int xid, String flightNum)
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
			flightcounter = 0;
			flightprice = 0;
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
		

			if(location==null)
				return false;
			try{
				String lockString = "Hotels."+location;
				if(lockManager.lock(xid, lockString, WRITE) == false){
					return false;
				}
				
				int numAvail = numRooms;
				if(hotelTable.containsKey(location)){

					Hotels oldData = hotelTable.get(location);
					if(price>=0)oldData.setPrice(price);
					numAvail = numAvail + oldData.getNumAvail();
					oldData.setNumAvail(numAvail);
					numRooms = numRooms + oldData.getNumRooms();
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
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to deadlock");
			}
			catch( Exception e)	{
				e.printStackTrace();
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to unknown exception"+e.getMessage());
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
			if(location==null)
				return false;
			try{
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
				}
				else{
					// should not happen ... if it happens return false.
					return false;
				}
				return true;

			}catch (DeadlockException e) {
					// TODO: Handle DeadLock !
				e.printStackTrace();
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to deadlock");
			}
			catch( Exception e)	{
				e.printStackTrace();
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to unknown exception"+e.getMessage());
			}
		}

		/** 
	     * Add cars to a location.  In general this will be used to create
	     * a new Location, but it should be possible to add cars to an
	     * existing location.  Adding to an existing location should overwrite
	     * the current price of the available seats.
	     *
	     * @param xid id of transaction.
	     * @param location place, cannot be null.
	     * @param numCars number of cars to be added to the location
	     * @param price price of each seat. If price < 0,
	     *                    don't overwrite the current price.
	     * @return true on success, false on failure.
	     *
	     * @throws RemoteException on communications failure.
	     * @throws TransactionAbortedException if transaction was aborted.
	     * @throws InvalidTransactionException if transaction id is invalid.
	     *
	     * @see #addRooms
	     * @see #addFlight
	    */
		public boolean addCars(int xid, String location, int numCars, int price) 
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
				//TODO: 
				//throw InvalidTransactionException;

			if(location==null)
				return false;

			try{
				String lockString = "Cars."+location;
				if(lockManager.lock(xid, lockString, WRITE) == false){
					return false;
				}
				
				int numAvail = numCars;
				if(carTable.containsKey(location)){

					Cars oldData = carTable.get(location);
					if(price>=0)oldData.setPrice(price);
					numAvail = numAvail + oldData.getNumAvail();
					oldData.setNumAvail(numAvail);
					numCars = numCars + oldData.getNumCars();
					oldData.setNumCars(numCars);

				}
				else{
					
					Cars newData = new Cars(location, price, numCars, numAvail);
					carTable.put(location, newData);

				}
				return true;	
			}catch (DeadlockException e) {
					// TODO: Handle DeadLock !
				e.printStackTrace();
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to deadlock");
			}
			catch( Exception e)	{
				e.printStackTrace();
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to unknown exception"+e.getMessage());
			}
		}

		/**
	     * Delete cars from a location.
	     * Implies whole deletion of the location record: all cars, all reservations.
	     * Should fail if a customer has booked a car from this location.
	     *
	     * @param xid id of transaction.
	     * @param location , cannot be null.
	     * @return true on success, false on failure.
	     *
	     * @throws RemoteException on communications failure.
	     * @throws TransactionAbortedException if transaction was aborted.
	     * @throws InvalidTransactionException if transaction id is invalid.
	     *
	     * @see #deleteRooms
	     * @see #deleteFlight
	     */
		public boolean deleteCars(int xid, String location, int numCars) 
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
					//TODO: 
				//throw InvalidTransactionException;

			if(location==null)
				return false;

			try{
				String lockString = "Cars."+location;
				if(lockManager.lock(xid, lockString, WRITE) == false){
					return false;
				}
				
				int numAvail = numCars;
				if(carTable.containsKey(location)){

					Cars oldData = carTable.get(location);
					if(price>=0)oldData.setPrice(price);
					numAvail = numAvail + oldData.getNumAvail();
					oldData.setNumAvail(numAvail);
					numCars = numCars + oldData.getNumCars();
					oldData.setNumCars(numCars);

				}
				else{
					
					Cars newData = new Cars(location, price, numCars, numAvail);
					carTable.put(location, newData);

				}
				return true;	
			}catch (DeadlockException e) {
					// TODO: Handle DeadLock !
				e.printStackTrace();
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to deadlock");
			}
			catch( Exception e)	{
				e.printStackTrace();
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to unknown exception"+e.getMessage());
			}
			
		}

		public boolean newCustomer(int xid, String custName) 
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
			return true;
		}

		public boolean deleteCustomer(int xid, String custName) 
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
			return true;
		}


		// QUERY INTERFACE
		public int queryFlight(int xid, String flightNum)
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
			return flightcounter;
		}

		public int queryFlightPrice(int xid, String flightNum)
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
			return flightprice;
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
				return false;
			try{
				String lockString = "Hotels."+location;
				if(lockManager.lock(xid, lockString, READ) == false){
					return false;
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
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to deadlock");
			}
			catch( Exception e)	{
				e.printStackTrace();
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to unknown exception"+e.getMessage());
			}
			return 0;
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
				return false;
			try{
				String lockString = "Hotels."+location;
				if(lockManager.lock(xid, lockString, READ) == false){
					return false;
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
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to deadlock");
			}
			catch( Exception e)	{
				e.printStackTrace();
				abort();
				throw new TransactionAbortedException(" throwing TransactionAbortedException due to unknown exception"+e.getMessage());
			}
			return 0;
		}

		public int queryCars(int xid, String location)
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
			return carscounter;
		}

		public int queryCarsPrice(int xid, String location)
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
			return carsprice;
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
			flightcounter--;
			return true;
		}

		public boolean reserveCar(int xid, String custName, String location) 
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
			carscounter--;
			return true;
		}

		public boolean reserveRoom(int xid, String custName, String location) 
		throws RemoteException, 
		TransactionAbortedException,
		InvalidTransactionException {
			roomscounter--;
			return true;
		}


		// TECHNICAL/TESTING INTERFACE
		/** 
     * Shutdown gracefully. Stop accepting new transactions, wait for
     * running transactions to terminate, and clean up disk state.
     * When this RM restarts, it should not attempt to recover its
     * state if the client called shutdown to terminate it.
     *
     * @return true on success, false on failure.
     * @throws RemoteException on communications failure.
     */
		public boolean shutdown()
		throws RemoteException {
			System.exit(0);
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

	}
