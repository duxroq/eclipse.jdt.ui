package object_in;

public class TestAddition_FieldRefsInExtOperands {

	private int i;
	int j;
	
	public void foo() {
		i= j + 12 + i + i;
	}
}
