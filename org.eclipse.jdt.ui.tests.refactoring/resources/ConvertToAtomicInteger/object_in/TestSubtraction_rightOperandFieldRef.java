package object_in;

public class TestSubtraction_rightOperandFieldRef {

	private int i;
	
	public void foo() {
		i = 12 - i;
	}
}