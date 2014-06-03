package project.transaction.recovery;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import project.transaction.bean.Flight;
import project.transaction.bean.Reservation;
import project.transaction.bean.UndoIMLog;

public class RedoReservation {
	
	private ConcurrentHashMap<String, HashSet<Reservation>> table;
	
	public RedoReservation(ConcurrentHashMap<String, HashSet<Reservation>> input){
		table = input;
	}
	
	public void insert(String key) {
		HashSet<Reservation> value = new HashSet<Reservation>();
		table.put(key, value);
	}

	public void delete(String key) {
		table.remove(key);
	}
	
	public void update(String key, String value){
		String[] fields = value.split("#@#");
		Reservation r = new Reservation(fields[0],Integer.parseInt(fields[1]), fields[2]);
		HashSet<Reservation> reservations = table.get(key);
		reservations.add(r);
	}
	
}
