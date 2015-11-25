Features a Distributed Travel Reservation System, which uses 2PC to perform Distributed transactions.

This implementation uses Undo-redo logs, and in-memory memtables, for fast commits.
It pipes all its logs via a blocking queue, so that the disk does not become a bottelneck for transacations.
RPC is employed for client-server interaction.

Checkpointing is done every 5 secs, and this is a tunable parameter.
