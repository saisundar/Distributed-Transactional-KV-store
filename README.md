Features a Distributed Travel Reservation System, which uses 2PC to perform Distributed transacations.

This implmentation uses Undo-redo logs, and in-memory memtables, for fast commits.
It pipes all its logs via a blocking queue, so that the disk does not become a bottelneck for transacations.

RPC is employed for clinet-server interaction.
