package object_in;

public class TestMultipleInfixExpressionsAssignmentInSynchMethod {

	int f;
	int a;
	int b;
	
	public synchronized void foo() {
		f= b + 12 + a;
	}
}