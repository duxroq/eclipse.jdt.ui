package object_in;

public class TestAssignmentInForLoop {

	private int i;
	
	public void foo() {
		for (i = 0; i/2 < 10; i = i*2) {
			i= i*2;
		}
	}
}
