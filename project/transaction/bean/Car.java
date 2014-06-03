package transaction.bean;

import java.io.Serializable;

public class Car implements Serializable{
	
	private String location;
	private int price;
	private int numCars;
	private int numAvail;

	public Car(String location, int price, int numCars, int numAvail) {
		this.location = location;
		this.price = price;
		this.numCars = numCars;
		this.numAvail = numAvail;
	}
	public Car(Car orig)
	{
		this.location = orig.location;
		this.price = orig.price;
		this.numCars = orig.numCars;
		this.numAvail = orig.numAvail;
	}
	public void copyCar(Car orig)
	{
		this.location = orig.location;
		this.price = orig.price;
		this.numCars = orig.numCars;
		this.numAvail = orig.numAvail;
	}
	
	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public int getPrice() {
		return price;
	}

	public void setPrice(int price) {
		this.price = price;
	}

	public int getNumCars() {
		return numCars;
	}

	public void setNumCars(int numCars) {
		this.numCars = numCars;
	}

	public int getNumAvail() {
		return numAvail;
	}

	public void setNumAvail(int numAvail) {
		this.numAvail = numAvail;
	}
	
	
}
