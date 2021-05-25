package net.taus.webcrawler.filter;

public interface Filter<T> {
	
	public boolean execute(T toFilter); 

}
