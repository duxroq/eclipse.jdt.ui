package object_in;

public class TestSetter {
	
	int value = 0;

    public synchronized void setCounter(int counter) {
        this.value = counter;
    }
}