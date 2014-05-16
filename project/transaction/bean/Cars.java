package project.transaction.bean;

public class Cars {
private String flightNum;
private int price;
private int numCars;
private int numAvail;


public Flight(String flightNum, int price, int numSeats, int numAvail) {
	super();
	this.flightNum = flightNum;
	this.price = price;
	this.numCars = numCars;
	this.numAvail = numAvail;
}


public String getFlightNum() {
	return flightNum;
}


public void setFlightNum(String flightNum) {
	this.flightNum = flightNum;
}


public int getPrice() {
	return price;
}


public void setPrice(int price) {
	this.price = price;
}


public int getNumSeats() {
	return numCars;
}


public void setNumSeats(int numSeats) {
	this.numCars = numCars;
}


public int getNumAvail() {
	return numAvail;
}


public void setNumAvail(int numAvail) {
	this.numAvail = numAvail;
}


}
