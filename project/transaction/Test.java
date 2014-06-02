//Transaction Aborted Cause of lockString 
				abort(xid);
				throw new TransactionAbortedException(xid, "Lock Manager returned false while acquiring lock on: " + lockString);

// Transaction aborted because of deadlock				
				abort(xid);
				throw new TransactionAbortedException(xid, "Aborted transaction because deadlock detected for XID: "+xid);
				e.printStackTrace();
// Transaction aborted because of 'Other ' Exception
				abort(xid);
				throw new TransactionAbortedException(xid, "Aborted transaction because 'Other' Exception found: "+xid);
				e.printStackTrace();