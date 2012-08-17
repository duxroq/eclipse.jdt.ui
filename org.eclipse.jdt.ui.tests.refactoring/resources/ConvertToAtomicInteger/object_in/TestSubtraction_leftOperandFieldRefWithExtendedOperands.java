package object_in;

public class TestSubtraction_leftOperandFieldRefWithExtendedOperands {

	private int i;
	private int j;
	
	public void foo() {
		i = i - i - j - (i*3);
	}
}