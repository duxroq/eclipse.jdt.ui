package object_in;

public class TestMultipleFieldRefsInEnclosingStatementSynchBlock {

	int i;
	int f;
	
	public void foo() {
		synchronized (this) {
			i = f++;
		}
	}
}
