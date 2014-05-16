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

public class ResourceManagerImplAbilash 
extends java.rmi.server.UnicastRemoteObject
implements ResourceManager {
	//Book keeping and other variables
	private HashSet<Integer> activeTxns;
	private LockManager lockManager;
	private final int WRITE = 1;
	private final int READ = 0;

	// Data Sets
	private ConcurrentHashMap<String,Flight> flightTable;
	private ConcurrentHashMap<String,Car> carTable;
	private ConcurrentHashMap<String,Flight> hotelTable;
	private ConcurrentHashMap<String,HashSet<Reservation>> reservationTable;

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
			ResourceManagerImplAbilash obj = new ResourceManagerImplAbilash();
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
	public ResourceManagerImplAbilash() throws RemoteException {
		lockManager = new LockManager();
		activeTxns = new HashSet<Integer>();
		flightTable = new ConcurrentHashMap<String, Flight>();
		carTable = new ConcurrentHashMap<String, Car>();
		hotelTable = new ConcurrentHashMap<String, Flight>();
		reservationTable = new ConcurrentHashMap<String, HashSet<Reservation>>();
		xidCounter = 1;
	}


	// TRANSACTION INTERFACE
	public int start()
			throws RemoteException {
		synchronized("Dummy"){
			if(activeTxns.contains(xidCounter)){
				// HOW TO HANDLE THIS ?
				System.out.println("SHOULD NOT REACH: XID DUPLICATE");
			}
			activeTxns.add(xidCounter);
			return (xidCounter++);
		}
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

	public boolean addRooms(int xid, String location, int numRooms, int price) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		roomscounter += numRooms;
		roomsprice = price;
		return true;
	}

	public boolean deleteRooms(int xid, String location, int numRooms) 
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		roomscounter = 0;
		roomsprice = 0;
		return true;
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

	public int queryRooms(int xid, String location)
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		return roomscounter;
	}

	public int queryRoomsPrice(int xid, String location)
			throws RemoteException, 
			TransactionAbortedException,
			InvalidTransactionException {
		return roomsprice;
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
		flightcounter--;
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
		roomscounter--;
		return true;
	}


	// TECHNICAL/TESTING INTERFACE
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
