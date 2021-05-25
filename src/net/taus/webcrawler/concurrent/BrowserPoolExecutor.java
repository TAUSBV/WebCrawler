package net.taus.webcrawler.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BrowserPoolExecutor extends ThreadPoolExecutor {
	
	public BrowserPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
			BlockingQueue<Runnable> workQueue) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
		
	}
	
	protected void beforeExecute(Thread t, Runnable r) {
		
	}
	
	protected void afterExecute(Runnable r, Throwable t) {
		
	}
	
	protected void terminated() {
		
	}

}



