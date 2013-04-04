package object_in;

public class TestSubtraction_fieldRefInExtendedOperands {

	private int i;
	private int j;
	
	public void foo() {
		i = j - 12 - i;
	}
}