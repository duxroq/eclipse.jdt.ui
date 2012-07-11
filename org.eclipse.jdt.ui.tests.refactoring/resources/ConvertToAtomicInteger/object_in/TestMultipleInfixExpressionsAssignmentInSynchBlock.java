package object_in;

public class TestMultipleInfixExpressionsAssignmentInSynchBlock {

	int f;
	int a;
	int b;
	
	public void foo() {
		synchronized (this) {
			f= 12 + a + b;			
		}
	}
}
