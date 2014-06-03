package transaction.recovery;

import java.util.concurrent.ConcurrentHashMap;

public class RedoReservedFlights {

	ConcurrentHashMap<String, Integer> table;
	
	public RedoReservedFlights(ConcurrentHashMap<String, Integer> reservedflights) {
		table = reservedflights;
	}

	public void insert(String key) {
		table.put(key, 1);
	}

	public void updateNumReserved(String key, int value) {
		table.put(key, value);
	}

}
