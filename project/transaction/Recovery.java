package project.transaction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import project.transaction.bean.Car;
import project.transaction.bean.Flight;
import project.transaction.bean.Hotels;
import project.transaction.bean.Reservation;
import project.transaction.bean.TableReader;

public class Recovery {

	private ExecutorService restoreService ;
	private Set<Callable<Integer>> callables;
	private TableReader flightTR;
	private TableReader carTR;
	private TableReader hotelTR;
	private TableReader reservationTR;
	private TableReader reservedflightsTR;


	public void restoreSetup(){
		callables = new HashSet<Callable<Integer>>();
		restoreService = Executors.newFixedThreadPool(5);

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
		List<Future<Integer>> futures;
		try {
			futures = restoreService.invokeAll(callables);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		if(nTries==3)
		{
			System.out.println("Cannot restore files");
			return false;
			// Kill system : Invoke dieNow
		}

		for(Future<Integer> future : futures){
			try {
				if(future.get() == 1)
					System.out.println("Recovery Attempt: "+nTries+" Failed");
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			result = false;
		}
		
		if(result == false)
			result = restore(nTries+1);
// To be commented out if same service is to be used
/*		if(nTries == 0)
			restoreService.shutdown();*/
		return result;
	}
	
	public TableReader getTR(String fileName){
		if(fileName == "flights")
			return reservedflightsTR;
		else if(fileName == "reservationTable")
			return reservationTR;
		else if(fileName == "flightTable")
			return flightTR;
		else if(fileName == "carTable")
			return carTR;
		else if(fileName == "hotelTable")
			return hotelTR;
		
		return null;
	}
	
	public ExecutorService getExecutorService(){
		return restoreService;
	}
}


