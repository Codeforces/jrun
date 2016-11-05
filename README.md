# jrun

Run process API for Java. 

It supports:

* process home directory
* time limit (wall time)
* redirect input file
* redirect output file
* redirect error file

Invocation outcome has:

* exit code
* process standard output (truncated to 5MB)
* process standard error (truncated to 5MB)
* comment (may be useful in case of failure)
