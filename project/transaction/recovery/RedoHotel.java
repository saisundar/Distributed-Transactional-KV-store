package transaction.recovery;

import java.util.concurrent.ConcurrentHashMap;

import transaction.bean.Hotels;

public class RedoHotel {
	private ConcurrentHashMap<String, Hotels> table;
	
	public RedoHotel(ConcurrentHashMap<String, Hotels> input){
		table = input;
	}
	
	public void insert(String key) {
		Hotels value = new Hotels(key,0,0,0);
		table.put(key, value);
	}

	public void delete(String key) {
		table.remove(key);
	}

	public void updatePrice(String key, int price) {
		Hotels value = table.get(key);
		value.setPrice(price);
	}

	public void updateNumAvail(String key, int numAvail) {
		Hotels value = table.get(key);
		value.setNumAvail(numAvail);
	}

	public void updateNumRooms(String key, int numRooms) {
		Hotels value = table.get(key);
		value.setNumRooms(numRooms);
	}
}
