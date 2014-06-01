package project.transaction.bean;

import java.io.Serializable;

public class Hotels implements Serializable{
private String location;
private int price;
private int numRooms;
private int numAvail;


public Hotels(String location, int price, int numRooms, int numAvail) {
	super();
	this.location = location;
	this.price = price;
	this.numRooms = numRooms;
	this.numAvail = numAvail;
}

public Hotels(Hotels orig)
{
	this.location = orig.location;
	this.price = orig.price;
	this.numRooms = orig.numRooms;
	this.numAvail = orig.numAvail;
}
public void copyHotels(Hotels orig)
{
	this.location = orig.location;
	this.price = orig.price;
	this.numRooms = orig.numRooms;
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


public int getnumRooms() {
	return numRooms;
}


public void setNumRooms(int numRooms) {
	this.numRooms = numRooms;
}


public int getNumAvail() {
	return numAvail;
}


public void setNumAvail(int numAvail) {
	this.numAvail = numAvail;
}


}
