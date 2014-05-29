package project.transaction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import project.transaction.bean.Car;
import project.transaction.bean.Flight;
import project.transaction.bean.Hotels;
import project.transaction.bean.Reservation;
import project.transaction.bean.TableReader;

public class Recovery {

	private ExecutorService restoreThreads ;
	private Set<Callable<Integer>> callables;
	private TableReader flightTR;
	private TableReader carTR;
	private TableReader hotelTR;
	private TableReader reservationTR;
	private TableReader reservedflightsTR;


	public void restoreSetup(){
		callables = new HashSet<Callable<Integer>>();
		restoreThreads = Executors.newFixedThreadPool(5);

		flightTR = new TableReader("flightTable");
		carTR = new TableReader("carTable");
		hotelTR = new TableReader("hotelTable");
		reservationTR = new TableReader("reservationTable");
		reservedflightsTR = new TableReader("flights");

		callables.add(flightTR);
		callables.add(carTR);
		callables.add(hotelTR);
		callables.add(reservationTR);
		callables.add(reservedflightsTR);
	}

	public boolean restore(int nTries){

		boolean result = true;
		List<Future<Integer>> futures = restoreThreads.invokeAll(callables);
		if(nTries==3)
		{
			System.out.println("Cannot restore files");
			return false;
			// Kill system : Invoke dieNow
		}

		for(Future<Integer> future : futures){
			if(future.get() == 1)
				System.out.println("Recovery Attempt: "+nTries+" Failed");
			result = false;
		}
		
		if(result == false)
			result = restore(nTries+1);
		if(nTries == 0)
			restoreThreads.shutdown();
		return result;
	}
}


if(fileName == "flights")
	table =  (ConcurrentHashMap<String, Object>) in.readObject();
else if(fileName == "reservationTable")
	table =  (ConcurrentHashMap<String, HashSet<Reservation>>) in.readObject();
else if(fileName == "flightTable")
	table =  (ConcurrentHashMap<String, Flight>) in.readObject();
else if(fileName == "carTable")
	table =  (ConcurrentHashMap<String, Car>) in.readObject();
else if(fileName == "hotelTable")
	table =  (ConcurrentHashMap<String, Hotels>) in.readObject();