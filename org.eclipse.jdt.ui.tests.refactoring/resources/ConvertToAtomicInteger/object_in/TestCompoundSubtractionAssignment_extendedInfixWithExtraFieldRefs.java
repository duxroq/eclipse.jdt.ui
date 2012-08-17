package object_in;

public class TestCompoundSubtractionAssignment_extendedInfixWithExtraFieldRefs {

	private int i;
	
	public void foo() {
		i-=(i*3) + i + i*2;
	}
}