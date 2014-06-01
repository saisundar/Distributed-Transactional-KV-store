package project.transaction.bean;

public class Reservation {
	
	private String customer;
	private int resType;
	private String resKey;
	
	
	public Reservation(String customer, int resType, String resKey) {
		this.customer = customer;
		this.resType = resType;
		this.resKey = resKey;
	}


	public Reservation(Reservation orig) {
		this.customer = orig.customer;
		this.resType = orig.resType;
		this.resKey = orig.resKey;
	}

	public void copyReservation(Reservation orig) {
		this.customer = orig.customer;
		this.resType = orig.resType;
		this.resKey = orig.resKey;
	}
	
	public String getCustomer() {
		return customer;
	}


	public void setCustomer(String customer) {
		this.customer = customer;
	}


	public int getResType() {
		return resType;
	}


	public void setResType(int resType) {
		this.resType = resType;
	}


	public String getResKey() {
		return resKey;
	}


	public void setResKey(String resKey) {
		this.resKey = resKey;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((customer == null) ? 0 : customer.hashCode());
		result = prime * result + ((resKey == null) ? 0 : resKey.hashCode());
		result = prime * result + resType;
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Reservation other = (Reservation) obj;
		if (customer == null) {
			if (other.customer != null)
				return false;
		} else if (!customer.equals(other.customer))
			return false;
		if (resKey == null) {
			if (other.resKey != null)
				return false;
		} else if (!resKey.equals(other.resKey))
			return false;
		if (resType != other.resType)
			return false;
		return true;
	}


	@Override
	public String toString() {
		return customer + "#@#" + resType + "#@#" + resKey;
	}
	
	

}
