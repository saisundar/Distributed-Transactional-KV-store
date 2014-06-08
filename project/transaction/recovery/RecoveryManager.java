package transaction.recovery;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import transaction.logmgr.LogReader;
import transaction.bean.Car;
import transaction.bean.Flight;
import transaction.bean.Hotels;
import transaction.bean.Reservation;

public class RecoveryManager {
	RedoCar redoCar;
	RedoFlight redoFlight;
	RedoHotel redoHotel;
	RedoReservation redoReservation;
	RedoReservedFlights redoReservedFlights;
	private HashSet<Integer> comtdTxns;
	private HashSet<Integer> abrtdTxns;
	private MAXid = -1;
	LogReader logReader;

	public RecoveryManager(ConcurrentHashMap<String, Flight> flightTable, ConcurrentHashMap<String, Car> carTable, ConcurrentHashMap<String, Hotels> hotelTable, ConcurrentHashMap<String, HashSet<Reservation>> reservationTable, ConcurrentHashMap<String,Integer> reservedflights){
		redoCar = new RedoCar(carTable);
		redoFlight = new RedoFlight(flightTable);
		redoHotel = new RedoHotel(hotelTable);
		redoReservation = new RedoReservation(reservationTable);
		redoReservedFlights = new RedoReservedFlights(reservedflights);
		logReader = new LogReader();
	}

	public boolean analyze() throws FileNotFoundException{
		// Load Undo Redo Logs
		comtdTxns = new HashSet<Integer>();
		abrtdTxns = new HashSet<Integer>();
		logReader.loadFile();
		System.out.println("Loaded undo-redo log file");
		// Create HashSet of Committed Transactions
		String nextLine = logReader.nextLine();
		if(nextLine==null){
			System.out.println("File Empty, No recovery required !");
			return false;
		}

		while(nextLine != null){
			if(nextLine.contains("COMMIT")){
				System.out.println("I see a commit");
				String[] xid = nextLine.split(" ");
				int XID = Integer.parseInt(xid[0]);
				comtdTxns.add(XID);
				abrtdTxns.remove(XID);
				MAXid = (XID>MAXid)?XID:MAXid;
			}
			else if(nextLine.contains("ABORT")){
				String[] xid = nextLine.split(" ");
				int XID = Integer.parseInt(xid[0]);
				abrtdTxns.add(XID);
				MAXid = (XID>MAXid)?XID:MAXid;
			}
			else {
				String[] xid = nextLine.split("@#@");
				int XID = Integer.parseInt(xid[0]);
				abrtdTxns.add(XID);
				MAXid = (XID>MAXid)?XID:MAXid;
			}
			nextLine = logReader.nextLine();
		}

		if(comtdTxns.size()==0)return false;
		logReader.close();
		return true;
	}


	public boolean redo() throws FileNotFoundException{
		logReader.loadFile();
		String nextLine = logReader.nextLine();
		while(nextLine != null){
			// The Log is commit, abort or start
			if(!nextLine.contains("@#@")){
				nextLine = logReader.nextLine();
				continue;
			}
			String[] xid = nextLine.split("@#@");
			// The transaction is not committed. No need to redo(unod has handled it)
			if(!comtdTxns.contains(Integer.parseInt(xid[0]))){
				nextLine = logReader.nextLine();
				continue;
			}

			// PERFORM REDO
			System.out.println("LOG RECORD is : " + nextLine);
			if(xid[1].equals("Flights")){
				if(xid[3].equals("")){
					if(xid[4].equals("INSERT")){
						
						redoFlight.insert(xid[2]);
					}
					else if(xid[4].equals("DELETE")){
						redoFlight.delete(xid[2]);
					}
				}
				else{
					if(xid[3].equals("Price")){
						System.out.println("Updating the Price");
						redoFlight.updatePrice(xid[2],Integer.parseInt(xid[5]));
					}
					else if(xid[3].equals("NumAvail")){
						redoFlight.updateNumAvail(xid[2],Integer.parseInt(xid[5]));
					}
					else if(xid[3].equals("NumSeats")){
						redoFlight.updateNumSeats(xid[2],Integer.parseInt(xid[5]));
					}

				}
			}

			// REDO for Cars
			else if(xid[1].equals("Cars")){
				if(xid[3].equals("")){
					if(xid[4].equals("INSERT")){
						redoCar.insert(xid[2]);
					}
					else if(xid[4].equals("DELETE")){
						redoCar.delete(xid[2]);
					}
				}
				else{
					if(xid[3].equals("Price")){
						redoCar.updatePrice(xid[2],Integer.parseInt(xid[5]));
					}
					else if(xid[3].equals("NumAvail")){
						redoCar.updateNumAvail(xid[2],Integer.parseInt(xid[5]));
					}
					else if(xid[3].equals("NumCars")){
						redoCar.updateNumCars(xid[2],Integer.parseInt(xid[5]));
					}

				}
			}

			// REDO for Hotels
			else if(xid[1].equals("Rooms")){
				if(xid[3].equals("")){
					if(xid[4].equals("INSERT")){
						redoHotel.insert(xid[2]);
					}
					else if(xid[4].equals("DELETE")){
						redoHotel.delete(xid[2]);
					}
				}
				else{
					if(xid[3].equals("Price")){
						redoHotel.updatePrice(xid[2],Integer.parseInt(xid[5]));
					}
					else if(xid[3].equals("NumAvail")){
						redoHotel.updateNumAvail(xid[2],Integer.parseInt(xid[5]));
					}
					else if(xid[3].equals("NumRooms")){
						redoHotel.updateNumRooms(xid[2],Integer.parseInt(xid[5]));
					}

				}
			}

			// REDO for Reservations
			else if(xid[1].equals("Reservations")){
				if(xid[4].equals("INSERT")){
					redoReservation.insert(xid[2]);
				}
				else if(xid[4].equals("DELETE")){
					redoReservation.delete(xid[2]);
				}
				else if(xid[4].equals("UPDATE")){
					redoReservation.update(xid[2], xid[5]);
				}
			}

			else if(xid[1].equals("ReservedFlights")){
				if(xid[3].equals("")){
					if(xid[4].equals("INSERT")){
						redoReservedFlights.insert(xid[2]);
					}
					else if(xid[4].equals("DELETE")){
	
					}
				}
				else{
					if(xid[3].equals("NumReserved")){
						redoReservedFlights.updateNumReserved(xid[2],Integer.parseInt(xid[5]));
					}

				}
			}
			// Read Next line
			nextLine = logReader.nextLine();
		}
		logReader.close();
		return true;
	}

	public boolean deleteLogs() throws FileNotFoundException, SecurityException{
			File f = new File(".data/undo-redo.log"); 
			if(f.exists()){
				f.delete();
			}
		return true;
	}

}
