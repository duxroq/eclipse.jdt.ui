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
	
	public synchronized int getI4() {
		return i;
	}
	
	public synchronized int getI5() {
		return i= i + i;
	}
	
	public synchronized int getI6() {
		return i+=i;
	}
}