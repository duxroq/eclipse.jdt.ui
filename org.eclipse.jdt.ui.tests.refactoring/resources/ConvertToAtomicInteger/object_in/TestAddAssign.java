package object_in;

public class TestAddAssign {

	int f;
	
	public void bar() {
		f+=12;
	}
	
	public synchronized void fooFoo() {
		f += 12 + bar();
	}
	
	public synchronized void fooFoo2() {
		f += f + (f*3) + f;
	}
	
	public synchronized void fooFoo3() {
		f +=(f-bar());
	}
}
