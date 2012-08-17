package object_in;

public class TestAddition_NoFieldRefs {

	private int i;
	int j;
	
	public void foo() {
		i= j + 12 + j + j;
	}
}
