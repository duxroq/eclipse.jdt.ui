package object_in;

public class TestReturnAssignment {
	public int i= 12;
	
	public int getI() {
		return i=12;
	}
	
	public int getI2() {
		return i= i + i + i + 12;
	}
	
	public int getI3() {
		return i*= 2;
	}
}