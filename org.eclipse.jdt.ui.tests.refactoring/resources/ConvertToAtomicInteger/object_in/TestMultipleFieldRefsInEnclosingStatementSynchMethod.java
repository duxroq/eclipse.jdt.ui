package object_in;

public class TestMultipleFieldRefsInEnclosingStatementSynchMethod {

	int i;
	int f;
	
	public synchronized void foo() {
		i= f++;
	}
}