package project.recovery;

import java.util.concurrent.ConcurrentHashMap;

import project.transaction.bean.Car;


public class RedoCar {

	private ConcurrentHashMap<String, Car> table;
	
	public RedoCar(ConcurrentHashMap<String, Car> input){
		table = input;
	}
	
	public void insert(String key) {
		Car value = new Car(key,0,0,0);
		table.put(key, value);
	}

	public void delete(String key) {
		table.remove(key);
	}

	public void updatePrice(String key, int price) {
		Car value = table.get(key);
		value.setPrice(price);
	}

	public void updateNumAvail(String key, int numAvail) {
		Car value = table.get(key);
		value.setNumAvail(numAvail);
	}

	public void updateNumCars(String key, int numCars) {
		Car value = table.get(key);
		value.setNumCars(numCars);
	}
}
