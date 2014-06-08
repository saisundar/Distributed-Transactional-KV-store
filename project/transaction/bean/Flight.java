package transaction.bean;

import java.io.Serializable;

public class Flight implements Serializable{
private String flightNum;
private int price;
private int numSeats;
private int numAvail;


public Flight(String flightNum, int price, int numSeats, int numAvail) {
	super();
	this.flightNum = flightNum;
	this.price = price;
	this.numSeats = numSeats;
	this.numAvail = numAvail;
}

public Flight(Flight orig) {
	this.flightNum = orig.flightNum;
	this.price = orig.price;
	this.numSeats = orig.numSeats;
	this.numAvail = orig.numAvail;
}	

public void copyFlight(Flight orig){
	this.flightNum = orig.flightNum;
	this.price = orig.price;
	this.numSeats = orig.numSeats;
	this.numAvail = orig.numAvail;
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
	return numSeats;
}


public void setNumSeats(int numSeats) {
	this.numSeats = numSeats;
}


public int getNumAvail() {
	return numAvail;
}


public void setNumAvail(int numAvail) {
	this.numAvail = numAvail;
}


}
